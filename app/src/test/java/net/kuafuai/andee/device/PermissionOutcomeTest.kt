package net.kuafuai.andee.device

import org.junit.Assert.*
import org.junit.Test

class PermissionOutcomeTest {
    @Test fun `granting contacts does not falsely grant a missing overlay permission`() {
        assertEquals("waiting", PermissionsController.combinedResult(
            allGranted = false, launchFailed = false, runtimeResult = "granted",
        ))
        assertEquals("granted", PermissionsController.combinedResult(
            allGranted = true, launchFailed = false, runtimeResult = "granted",
        ))
    }

    @Test fun `a failed settings handoff is not reported as waiting on a dialog`() {
        assertEquals("no_dialog", PermissionsController.combinedResult(
            allGranted = false, launchFailed = true, runtimeResult = null,
        ))
        assertEquals("denied", PermissionsController.combinedResult(
            allGranted = false, launchFailed = false, runtimeResult = "denied",
        ))
    }

    @Test fun `precise location on Android 12 includes approximate location in the same request`() {
        val fine = "android.permission.ACCESS_FINE_LOCATION"
        val coarse = "android.permission.ACCESS_COARSE_LOCATION"
        assertEquals(setOf(fine, coarse), PermissionsController.runtimePermissions(listOf(fine), 31).toSet())
        assertEquals(listOf(fine), PermissionsController.runtimePermissions(listOf(fine), 30))
        assertEquals(2, PermissionsController.runtimePermissions(listOf(fine, coarse), 35).size)
    }

    @Test fun `choosing approximate location satisfies location access without granting unrelated permissions`() {
        val granted = setOf("android.permission.ACCESS_COARSE_LOCATION")
        val locationReady = PermissionsController.permissionSatisfied("android.permission.ACCESS_FINE_LOCATION", granted::contains)
        assertTrue(locationReady)
        assertEquals("granted", PermissionsController.combinedResult(locationReady, false, "denied"))
        assertFalse(PermissionsController.permissionSatisfied("android.permission.READ_CONTACTS", granted::contains))
        assertFalse(PermissionsController.permissionSatisfied("android.permission.ACCESS_FINE_LOCATION") { false })
    }

    @Test fun `a late permission dialog cannot settle a newer request`() {
        val first = PermissionRequestActivity.arm()
        assertNull(PermissionRequestActivity.await(first, 0))
        val second = PermissionRequestActivity.arm()
        PermissionRequestActivity.settle(first, "granted")
        assertNull(PermissionRequestActivity.await(second, 0))
    }

    @Test fun `a permission result arriving before await is not lost`() {
        val request = PermissionRequestActivity.arm()
        PermissionRequestActivity.settle(request, "denied")
        assertEquals("denied", PermissionRequestActivity.await(request, 0))
    }

    @Test fun `brightness is a system settings handoff`() {
        assertEquals("settings", PermissionsController.routeOf(listOf("brightness")))
    }
}
