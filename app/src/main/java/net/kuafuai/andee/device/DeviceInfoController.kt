package net.kuafuai.andee.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Read-only device context for the brain: connectivity, installed apps,
 * last known location, step counter, battery. Lives in the `device.*`
 * namespace — deliberately NOT `screen.*` — because these are queries,
 * not actuations: asking "am I online" must not fold the card into the
 * corner ball the way a screen gesture does (see CommandDispatcher's
 * ensureCompact, which triggers on the `screen.` prefix).
 *
 * Permission model:
 *  - connectivity / battery / app list: declared only (QUERY_ALL_PACKAGES)
 *  - location: runtime permission, requested via [ensurePermission]; without
 *    it we return the graceful "denied" answer, never a crash
 *  - steps: ACTIVITY_RECOGNITION runtime permission, same story
 *
 * Everything here is a snapshot query with a bounded wait (sensors take a
 * beat to deliver the first reading) — no background services, no
 * listeners outliving the call. The step counter is cumulative-since-boot
 * by Android contract; a "today" figure would need persistence we don't
 * have yet, so we report the raw number and say so.
 */
class DeviceInfoController(private val context: Context) {

    // ---- connectivity ----------------------------------------------------

    fun connectivity(): JSONObject {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val nw = cm.activeNetwork
        val caps = nw?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val typeName = when {
            caps == null -> "none"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return JSONObject()
            .put("online", online)
            .put("type", typeName)
            .put("network", if (nw != null) "available" else "unavailable")
    }

    // ---- installed apps ----------------------------------------------------

    /**
     * Launchable apps only (apps with a launcher intent) — the list a human
     * means when they ask "what's installed", not the 400 system packages.
     */
    fun installedApps(): JSONObject {
        val pm = context.packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = JSONArray()
        for (ri in pm.queryIntentActivities(launcher, 0)) {
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(ri.activityInfo.packageName)
            apps.put(
                JSONObject()
                    .put("label", label)
                    .put("pkg", ri.activityInfo.packageName)
            )
        }
        return JSONObject().put("count", apps.length()).put("apps", apps)
    }

    // ---- location ----------------------------------------------------

    fun location(timeoutMs: Long = 8000): JSONObject {
        require(timeoutMs in 1..20_000) { "location timeout_ms must be between 1 and 20000" }
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val approximate = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!precise && !approximate) {
            return permissionDenied("location",
                "Ask the user to grant Location to the body app once (Settings → Apps → Permissions), then retry.")
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Prefer the freshest cached fix; fall back to waiting for one.
        val providers = if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            else listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                lm.getLastKnownLocation(p)
            } catch (_: SecurityException) {
                return permissionDenied("location", "Location access changed while reading it. Grant Location, then retry.")
            } catch (_: IllegalArgumentException) {
                null // This device does not provide every LocationManager provider.
            }
            if (loc != null) {
                if (best == null || loc.time > best!!.time) best = loc
            }
        }
        if (best == null || System.currentTimeMillis() - best!!.time > STALE_LOC_MS) {
            val latch = CountDownLatch(1)
            val got = arrayOfNulls<Location>(1)
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    got[0] = loc
                    latch.countDown()
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("required override on old APIs")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            try {
                var requested = false
                for (p in providers) {
                    try {
                        if (lm.isProviderEnabled(p)) {
                            lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                            requested = true
                        }
                    } catch (_: IllegalArgumentException) {
                        // An optional provider may disappear between enumeration and registration.
                    }
                }
                if (requested) {
                    latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                    best = got[0] ?: best
                }
            } catch (_: SecurityException) {
                return permissionDenied("location", "Location access changed while reading it. Grant Location, then retry.")
            } finally {
                // Covers partial registration, permission revocation and interrupted waits too.
                runCatching { lm.removeUpdates(listener) }
            }
        }
        val b = best
            ?: return JSONObject().put("error", "no fix available (GPS off / indoors / just booted)")
        return JSONObject()
            .put("lat", b.latitude)
            .put("lng", b.longitude)
            .put("accuracy_m", b.accuracy.toDouble())
            .put("precise_permission", precise)
            .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(b.time)))
            .put("age_ms", System.currentTimeMillis() - b.time)
    }

    // ---- steps ----------------------------------------------------

    fun steps(timeoutMs: Long = 3000): JSONObject {
        require(timeoutMs in 1..10_000) { "step timeout_ms must be between 1 and 10000" }
        if (!ensurePermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            return permissionDenied("steps",
                "Ask the user to grant 'Physical activity' to the body app once, then retry.")
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            return JSONObject().put("error", "no step counter sensor on this device")
        }
        val latch = CountDownLatch(1)
        val got = arrayOfNulls<Float>(1)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                got[0] = e.values.firstOrNull()
                latch.countDown()
                sm.unregisterListener(this)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        try {
            if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                return JSONObject().put("error", "step sensor could not be started")
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            sm.unregisterListener(listener)
        }
        val v = got[0]
            ?: return JSONObject().put("error", "sensor gave no reading within ${timeoutMs}ms")
        return JSONObject()
            .put("steps_since_boot", v.toInt())
            .put("note", "cumulative since last reboot, not per-day")
    }

    // ---- battery ----------------------------------------------------

    fun battery(): JSONObject {
        val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val bat = context.registerReceiver(null, ifilter) ?: return JSONObject().put("error", "no battery data")
        val level = bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val status = bat.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val plugged = bat.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return JSONObject()
            .put("percent", pct)
            .put("charging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL)
            .put("plugged", plugged != 0)
    }

    // ---- permissions ----------------------------------------------------

    private fun ensurePermission(perm: String): Boolean =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun permissionDenied(what: String, guidance: String): JSONObject =
        JSONObject().put("error", "permission denied: $what").put("permission_id", what).put("hint", guidance)

    private companion object {
        /** Older than this and a cached location is decoration, not data. */
        const val STALE_LOC_MS = 5 * 60_000L
    }
}
