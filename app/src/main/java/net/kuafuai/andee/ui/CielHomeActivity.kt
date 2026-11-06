package net.kuafuai.andee.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import net.kuafuai.andee.BodyCoreService
import net.kuafuai.andee.R
import net.kuafuai.andee.config.VoiceConfig
import net.kuafuai.andee.device.SelfCheck
import net.kuafuai.andee.i18n.AppLocale
import net.kuafuai.andee.ui.ball.BallLooks
import net.kuafuai.andee.ui.ball.EmotionBallTextureView
import net.kuafuai.andee.ui.ball.Mood
import net.kuafuai.andee.ui.ball.label

/**
 * A persistent, native front door. Voice setup works before overlay or phone
 * control access is granted; the existing service remains the conversation and
 * task owner. Permission errands still use the shared SelfCheck implementation.
 */
class CielHomeActivity : AppCompatActivity() {
    private enum class Page { HOME, COMPANION, SETUP }

    private val main = Handler(Looper.getMainLooper())
    private var page = Page.HOME
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var lctx: android.content.Context = this
    private var resumed = false
    private var pendingStart = false
    private var operation = 0
    private var studioLook = "andee"
    private var studioMood = Mood.CALM
    private var studioCycle = false
    private var preview: EmotionBallTextureView? = null
    private var status: TextView? = null
    private var message: TextView? = null
    private var headerStop: Button? = null
    private var talkButton: Button? = null
    private var transcript: LinearLayout? = null
    private var permissionRows: LinearLayout? = null
    private var conversationInput: EditText? = null
    private var keyInput: EditText? = null
    private var modelInput: EditText? = null
    private var voiceInput: EditText? = null
    private var draftKey = ""
    private var draftModel: String? = null
    private var draftVoice: String? = null
    private var draftMessage = ""
    private var lastTranscript = ""
    private var lastState = ""
    private var lastPalette = false
    private var lastLanguage = ""
    private var notice = ""
    private var keyConfigured = false
    private var scroller: ScrollView? = null
    private var renderedPage = Page.HOME
    private val scrollPositions = mutableMapOf<Page, Int>()

    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            refreshConversation()
            main.postDelayed(this, 350L)
        }
    }

    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) startTalk() else {
            notice = text(R.string.ciel_mic_denied)
            refreshConversation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        keyConfigured = savedKey().isNotBlank()
        page = savedInstanceState?.getString("page")?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.HOME
        studioLook = savedInstanceState?.getString("look") ?: BallLooks.byName(VoiceConfig.ballLook(this)).name
        studioMood = savedInstanceState?.getString("mood")?.let { runCatching { Mood.valueOf(it) }.getOrNull() } ?: Mood.CALM
        draftMessage = savedInstanceState?.getString("message").orEmpty()
        BallLooks.requestByName(VoiceConfig.ballLook(this))
        render()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        preview?.setRenderingActive(true)
        Theme.load(this)
        keyConfigured = savedKey().isNotBlank()
        if (lastPalette != Theme.night || lastLanguage != VoiceConfig.uiLanguage(this)) render()
        BodyCoreService.get()?.noteOwnActivity()
        BodyCoreService.get()?.ballWindow()?.setPageShown(true)
        refreshPermissions()
        main.removeCallbacks(poll)
        main.post(poll)
    }

    override fun onPause() {
        resumed = false
        preview?.setRenderingActive(false)
        main.removeCallbacks(poll)
        BodyCoreService.get()?.ballWindow()?.setPageShown(false)
        super.onPause()
    }

    override fun onStop() {
        // A queued tap must not open a microphone after the person left Home.
        if (pendingStart) {
            operation++
            pendingStart = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        operation++
        main.removeCallbacksAndMessages(null)
        draftKey = ""
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureDrafts()
        outState.putString("page", page.name)
        outState.putString("look", studioLook)
        outState.putString("mood", studioMood.name)
        outState.putString("message", draftMessage)
        // Credentials deliberately never enter Activity saved-state bundles.
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        scroller?.let { scrollPositions[renderedPage] = it.scrollY }
        captureDrafts()
        preview = null
        status = null
        message = null
        headerStop = null
        talkButton = null
        transcript = null
        permissionRows = null
        conversationInput = null
        keyInput = null
        modelInput = null
        voiceInput = null
        lastTranscript = ""
        lastState = ""
        lctx = AppLocale.wrap(this)
        lastPalette = Theme.night
        lastLanguage = VoiceConfig.uiLanguage(this)
        keyConfigured = savedKey().isNotBlank()
        if (page == Page.SETUP) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = surfaceColor
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (Theme.night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root = column().apply { setBackgroundColor(backgroundColor) }
        root.addView(header(), fullWrap())
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        scroller = scroll
        renderedPage = page
        content = column().apply { setPadding(dp(22), dp(8), dp(22), dp(28)) }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        when (page) {
            Page.HOME -> buildHome()
            Page.COMPANION -> buildCompanion()
            Page.SETUP -> buildSetup()
        }
        root.addView(navigation(), fullWrap())
        setContentView(root)
        scroll.post { scroll.scrollTo(0, scrollPositions[page] ?: 0) }
        // Remove the secure flag only after the old credential views are gone.
        if (page != Page.SETUP) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        refreshConversation()
        refreshPermissions()
    }

    private fun header(): View = row().apply {
        setPadding(dp(24), dp(18), dp(20), dp(12))
        addView(label("ciel", 27f, true).apply {
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            contentDescription = text(R.string.app_name)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label(text(R.string.ciel_mobile_companion), 11f).apply {
            setTextColor(secondaryColor)
            letterSpacing = 0.05f
        })
        headerStop = button(text(R.string.ciel_stop_short), compact = true) {
            operation++
            pendingStart = false
            BodyCoreService.get()?.stopConversationForUser()
            refreshConversation()
        }.apply { visibility = View.GONE }
        addView(headerStop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
    }

    private fun buildHome() {
        content.addView(label(text(R.string.ciel_home_title), 32f, true).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(body(text(R.string.ciel_home_intro)), gap(8))
        val hero = FrameLayout(this).apply {
            background = shape(if (Theme.night) 0xFF172F29.toInt() else 0xFFE6F0E8.toInt(), 30)
        }
        preview = EmotionBallTextureView(this).apply {
            setLook(BallLooks.byName(VoiceConfig.ballLook(this@CielHomeActivity)).name)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        hero.addView(preview, FrameLayout.LayoutParams(dp(268), dp(268), Gravity.CENTER))
        status = label("", 13f, true).apply {
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        hero.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = dp(10)
        })
        content.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)).apply { topMargin = dp(20) })

        talkButton = button(text(R.string.ciel_talk), primary = true) { onTalkClick() }
        content.addView(talkButton, gap(16))
        message = body(text(R.string.ciel_live_explained), 12f).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(message, gap(8))

        val composer = row().apply { background = shape(surfaceColor, 20, lineColor); setPadding(dp(12), dp(4), dp(6), dp(4)) }
        conversationInput = EditText(this).apply {
            hint = text(R.string.ciel_message_hint)
            setText(draftMessage)
            setTextColor(inkColor)
            setHintTextColor(secondaryColor)
            textSize = 15f
            background = null
            minHeight = dp(48)
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            contentDescription = text(R.string.ciel_message_hint)
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { sendText(); true } else false }
        }
        composer.addView(conversationInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(button(text(R.string.ciel_send), compact = true) { sendText() })
        content.addView(composer, gap(22))

        content.addView(sectionTitle(text(R.string.ciel_try_asking)), gap(22))
        val suggestions = column()
        listOf(R.string.ciel_suggest_open, R.string.ciel_suggest_screen, R.string.ciel_suggest_plan).forEach { id ->
            suggestions.addView(button(text(id), compact = true) {
                conversationInput?.setText(text(id))
                conversationInput?.requestFocus()
                conversationInput?.setSelection(conversationInput?.text?.length ?: 0)
            }, gap(6))
        }
        content.addView(suggestions)

        transcript = column()
        content.addView(transcript, gap(22))
        content.addView(button(text(R.string.ciel_phone_controls)) { navigate(Page.SETUP) }, gap(18))
    }

    private fun buildCompanion() {
        content.addView(label(text(R.string.ciel_companion_title), 30f, true), gap(8))
        content.addView(body(text(R.string.ciel_companion_intro)), gap(8))
        val stage = FrameLayout(this).apply { background = shape(if (Theme.night) 0xFF202638.toInt() else 0xFFEDEDF6.toInt(), 30) }
        preview = EmotionBallTextureView(this).apply {
            setLook(studioLook)
            setMood(studioMood)
            setListening(studioMood == Mood.LISTENING)
            setDemoCycle(studioCycle)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        stage.addView(preview, FrameLayout.LayoutParams(dp(280), dp(280), Gravity.CENTER))
        stage.addView(label(text(R.string.ciel_preview), 12f, true).apply {
            setTextColor(secondaryColor)
            setPadding(dp(18), dp(16), 0, 0)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        content.addView(stage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)).apply { topMargin = dp(18) })
        val savedLook = BallLooks.byName(VoiceConfig.ballLook(this)).name
        for (look in BallLooks.ALL) {
            val selected = look.name == studioLook
            val subtitle = when (look.name) {
                "moss" -> R.string.ciel_moss_description
                "nova" -> R.string.ciel_nova_description
                else -> R.string.ciel_original_description
            }
            val title = look.label(lctx) + if (look.name == savedLook) " · " + text(R.string.ciel_current) else ""
            content.addView(button(title + "\n" + text(subtitle), selected = selected) {
                studioLook = look.name
                render()
            }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, gap(8))
        }
        content.addView(button(text(R.string.ciel_use_companion, BallLooks.byName(studioLook).label(lctx)), primary = true) {
            VoiceConfig.save(this, mapOf("ball" to studioLook))
            BallLooks.requestByName(studioLook)
            toast(text(R.string.ciel_companion_saved))
            render()
        }, gap(14))

        content.addView(sectionTitle(text(R.string.ciel_expressions)), gap(26))
        content.addView(body(text(R.string.ciel_expression_note), 13f), gap(6))
        val chips = row()
        for (mood in Mood.entries) {
            chips.addView(button(CielExpressions.emoji(mood) + " " + text(CielExpressions.label(mood)), compact = true, selected = studioMood == mood) {
                studioMood = mood
                studioCycle = false
                render()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, gap(12))
        content.addView(Switch(this).apply {
            text = text(R.string.ciel_play_expressions)
            textSize = 15f
            setTextColor(inkColor)
            minHeight = dp(52)
            isChecked = studioCycle
            setOnCheckedChangeListener { _, checked ->
                studioCycle = checked
                preview?.setDemoCycle(checked)
                if (!checked) { preview?.setMood(studioMood); preview?.setListening(studioMood == Mood.LISTENING) }
            }
        }, gap(10))
    }

    private fun buildSetup() {
        content.addView(label(text(R.string.ciel_setup_title), 30f, true), gap(8))
        content.addView(body(text(R.string.ciel_setup_intro)), gap(8))
        val voice = card()
        voice.addView(sectionTitle(text(R.string.ciel_gemini_live)))
        voice.addView(body(text(R.string.ciel_gemini_note), 13f), gap(6))
        val hasKey = savedKey().isNotBlank()
        voice.addView(fieldLabel(text(R.string.ciel_api_key)), gap(18))
        keyInput = input(if (hasKey) text(R.string.ciel_key_saved_hint) else text(R.string.ciel_key_hint), draftKey, secret = true).apply {
            contentDescription = text(R.string.ciel_api_key)
        }
        voice.addView(keyInput, gap(6))
        voice.addView(button(text(R.string.ciel_get_key), compact = true) { openUrl("https://aistudio.google.com/apikey") }, gap(8))
        val options = column().apply { visibility = View.GONE }
        options.addView(fieldLabel(text(R.string.ciel_live_model)), gap(10))
        modelInput = input(VoiceConfig.googleLiveModel(this), draftModel ?: VoiceConfig.googleLiveModel(this)).apply {
            contentDescription = text(R.string.ciel_live_model)
        }
        options.addView(modelInput, gap(6))
        options.addView(fieldLabel(text(R.string.ciel_live_voice)), gap(14))
        voiceInput = input(VoiceConfig.googleLiveVoice(this), draftVoice ?: VoiceConfig.googleLiveVoice(this)).apply {
            contentDescription = text(R.string.ciel_live_voice)
        }
        options.addView(voiceInput, gap(6))
        options.addView(body(text(R.string.ciel_voice_hint), 12f), gap(6))
        voice.addView(button(text(R.string.ciel_voice_options), compact = true) {
            options.visibility = if (options.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, gap(8))
        voice.addView(options)
        voice.addView(button(text(R.string.ciel_save_gemini), primary = true) { saveGemini() }, gap(16))
        voice.addView(body(text(R.string.ciel_quota_note), 12f), gap(10))
        content.addView(voice, gap(20))

        val controls = card()
        controls.addView(sectionTitle(text(R.string.ciel_phone_controls)))
        controls.addView(body(text(R.string.ciel_permissions_note), 13f), gap(6))
        permissionRows = column()
        controls.addView(permissionRows, gap(12))
        controls.addView(button(text(R.string.ciel_review_permissions)) { openPermissions() }, gap(12))
        content.addView(controls, gap(16))

        val appearance = card()
        appearance.addView(sectionTitle(text(R.string.ciel_appearance)))
        appearance.addView(Switch(this).apply {
            text = text(R.string.ciel_dark_theme)
            textSize = 15f
            setTextColor(inkColor)
            minHeight = dp(52)
            isChecked = Theme.night
            setOnCheckedChangeListener { _, checked -> Theme.set(this@CielHomeActivity, checked); render() }
        }, gap(8))
        appearance.addView(fieldLabel(text(R.string.ciel_language)), gap(10))
        appearance.addView(row().apply {
            listOf(VoiceConfig.LANG_EN to "English", VoiceConfig.LANG_ZH to "中文").forEach { (language, title) ->
                addView(button(title, compact = true, selected = VoiceConfig.uiLanguage(this@CielHomeActivity) == language) {
                    VoiceConfig.save(this@CielHomeActivity, mapOf("lang" to language))
                    BodyCoreService.get()?.ballWindow()?.onLocaleChanged()
                    render()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            }
        }, gap(8))
        content.addView(appearance, gap(16))
        content.addView(button(text(R.string.ciel_advanced_settings)) { openAdvanced() }, gap(18))
        content.addView(body(text(R.string.ciel_advanced_note), 12f), gap(8))
        content.addView(button(text(R.string.ciel_about)) { showAbout() }, gap(12))
    }

    private fun navigation(): View = row().apply {
        setBackgroundColor(surfaceColor)
        setPadding(dp(14), dp(9), dp(14), dp(10))
        listOf(Page.HOME to R.string.ciel_tab_home, Page.COMPANION to R.string.ciel_tab_companion, Page.SETUP to R.string.ciel_tab_setup).forEach { (destination, title) ->
            addView(button(text(title), compact = true, selected = page == destination) { navigate(destination) }.apply {
                isSelected = page == destination
                stateDescription = text(if (isSelected) R.string.ciel_selected else R.string.ciel_not_selected)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) })
        }
    }

    private fun navigate(destination: Page) {
        if (page == destination) return
        captureDrafts()
        page = destination
        notice = ""
        hideKeyboard()
        render()
    }

    private fun onTalkClick() {
        val svc = BodyCoreService.get()
        if (pendingStart || active(svc?.conversationStateForUser().orEmpty())) {
            operation++
            pendingStart = false
            svc?.stopConversationForUser()
            notice = ""
            refreshConversation()
            return
        }
        if (VoiceConfig.brainMode(this) == VoiceConfig.BRAIN_GEMINI_LIVE && savedKey().isBlank()) {
            navigate(Page.SETUP)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        else startTalk()
    }

    private fun startTalk() {
        notice = ""
        withService(microphone = true) { it.startConversationForUser() }
    }

    private fun sendText() {
        val value = conversationInput?.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        if (VoiceConfig.brainMode(this) == VoiceConfig.BRAIN_GEMINI_LIVE && savedKey().isBlank()) {
            navigate(Page.SETUP)
            toast(text(R.string.ciel_add_key_first))
            return
        }
        notice = ""
        withService(microphone = false) { svc ->
            svc.submitTextForUser(value)
            conversationInput?.setText("")
            draftMessage = ""
            hideKeyboard()
        }
    }

    private fun withService(microphone: Boolean, block: (BodyCoreService) -> Unit) {
        if (pendingStart) return
        val token = ++operation
        pendingStart = true
        refreshConversation()
        runCatching { BodyCoreService.start(this, microphone = microphone) }.onFailure {
            pendingStart = false
            notice = text(R.string.ciel_start_failed)
            refreshConversation()
            return
        }
        fun attempt(remaining: Int) {
            if (token != operation || isFinishing || isDestroyed || !resumed) return
            val svc = BodyCoreService.get()
            if (svc?.dispatcherForNotification() != null) {
                pendingStart = false
                svc.noteOwnActivity()
                svc.ballWindow()?.setPageShown(true)
                block(svc)
                refreshConversation()
            } else if (remaining > 0) main.postDelayed({ attempt(remaining - 1) }, 100L)
            else {
                pendingStart = false
                notice = text(R.string.ciel_start_failed)
                refreshConversation()
            }
        }
        main.post { attempt(50) }
    }

    private fun refreshConversation() {
        val svc = BodyCoreService.get()
        val state = if (pendingStart) "connecting" else svc?.conversationStateForUser().orEmpty().ifBlank { "idle" }
        headerStop?.visibility = if (active(state)) View.VISIBLE else View.GONE
        if (page != Page.HOME) return
        val missingKey = VoiceConfig.brainMode(this) == VoiceConfig.BRAIN_GEMINI_LIVE && !keyConfigured
        val title = when (state) {
            "connecting" -> R.string.ciel_connecting
            "listening" -> R.string.ciel_listening
            "speaking" -> R.string.ciel_speaking
            "working" -> R.string.ciel_working
            "meeting" -> R.string.ciel_recording_meeting
            "error" -> R.string.ciel_connection_failed
            else -> if (missingKey) R.string.ciel_add_key_first else R.string.ciel_ready
        }
        // Announce actual state changes, not every 350ms poll of this live region.
        val titleText = text(title)
        if (status?.text?.toString() != titleText) status?.text = titleText
        val talkText = text(when {
            state == "meeting" -> R.string.ciel_stop_meeting
            active(state) -> R.string.ciel_stop
            missingKey -> R.string.ciel_setup_voice
            else -> R.string.ciel_talk
        })
        if (talkButton?.text?.toString() != talkText) talkButton?.text = talkText
        val detail = notice.ifBlank {
            if (state == "meeting") text(R.string.ciel_meeting_explained) else svc?.lastConversationErrorForUser().orEmpty()
        }.ifBlank {
            text(if (VoiceConfig.brainMode(this) == VoiceConfig.BRAIN_GEMINI_LIVE) R.string.ciel_live_explained else R.string.ciel_standard_voice_explained)
        }
        if (message?.text?.toString() != detail) message?.text = detail
        if (state != lastState) {
            lastState = state
            preview?.setListening(state == "listening" || state == "meeting")
            preview?.setWorking(state == "working")
            preview?.setMood(when (state) {
                "listening", "meeting" -> Mood.LISTENING
                "speaking" -> Mood.SPEAKING
                "connecting" -> Mood.THINKING
                "working" -> Mood.SEARCHING
                "error" -> Mood.CONCERNED
                else -> Mood.CALM
            })
        }
        val entries = ChatHistory.snapshot().filter { it.role in setOf(ChatHistory.Role.USER, ChatHistory.Role.ASSISTANT, ChatHistory.Role.ERROR) }.takeLast(6)
        val signature = entries.joinToString("|") { "${it.id}:${it.text.hashCode()}" }
        if (signature != lastTranscript) {
            lastTranscript = signature
            transcript?.removeAllViews()
            if (entries.isNotEmpty()) transcript?.addView(sectionTitle(text(R.string.ciel_recent_conversation)), gap(0))
            entries.forEach { entry ->
                val bubble = card()
                bubble.addView(label(text(if (entry.role == ChatHistory.Role.USER) R.string.ciel_you else R.string.app_name), 11f, true).apply { setTextColor(secondaryColor) })
                bubble.addView(body(entry.text, 15f).apply { setTextColor(inkColor); setTextIsSelectable(true) }, gap(5))
                transcript?.addView(bubble, gap(9))
            }
        }
    }

    private fun refreshPermissions() {
        val rows = permissionRows ?: return
        rows.removeAllViews()
        val local = runCatching { SelfCheck.local(this) }.getOrElse { return }
        local.filter { it.id in setOf("microphone", "overlay", "accessibility", "notification_access") }.forEach { finding ->
            val ready = finding.level == SelfCheck.Level.OK
            val line = row()
            line.addView(label(text(finding.title), 14f).apply { setPadding(0, dp(9), dp(12), dp(9)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(label(text(if (ready) R.string.ciel_enabled else R.string.ciel_not_enabled), 12f, true).apply { setTextColor(if (ready) accentColor else secondaryColor) })
            rows.addView(line)
        }
    }

    private fun saveGemini() {
        captureDrafts()
        val key = draftKey.trim().ifBlank { savedKey() }
        if (key.isBlank()) {
            keyInput?.error = text(R.string.ciel_key_required)
            keyInput?.requestFocus()
            return
        }
        VoiceConfig.save(this, mapOf(
            "brain" to VoiceConfig.BRAIN_GEMINI_LIVE,
            "google_api_key" to key,
            "google_live_model" to draftModel.orEmpty().trim(),
            "google_live_voice" to draftVoice.orEmpty().trim(),
            "voice_provider" to VoiceConfig.VOICE_GOOGLE,
            "speak_provider" to VoiceConfig.VOICE_GOOGLE,
        ))
        BodyCoreService.get()?.applyConversationSettingsForUser()
        OnboardingState.complete(this)
        draftKey = ""
        keyInput?.setText("")
        hideKeyboard()
        toast(text(R.string.ciel_settings_saved))
        navigate(Page.HOME)
    }

    private fun openPermissions() {
        startActivity(Intent(this, SelfCheckActivity::class.java).putExtra(SelfCheckActivity.EXTRA_SHOW_CHECKS, true))
    }

    private fun openAdvanced() {
        if (!Settings.canDrawOverlays(this)) {
            toast(text(R.string.ciel_overlay_for_advanced))
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .onFailure { openPermissions() }
            return
        }
        withService(microphone = false) { it.openSettingsForUser() }
    }

    private fun captureDrafts() {
        keyInput?.let { draftKey = it.text?.toString().orEmpty() }
        modelInput?.let { draftModel = it.text?.toString().orEmpty() }
        voiceInput?.let { draftVoice = it.text?.toString().orEmpty() }
        conversationInput?.let { draftMessage = it.text?.toString().orEmpty() }
    }

    private fun savedKey(): String = VoiceConfig.current(this)["google_api_key"].orEmpty()
    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(text(R.string.ciel_about))
            .setMessage(text(R.string.ciel_about_text))
            .setPositiveButton(text(R.string.ciel_source)) { _, _ -> openUrl("https://github.com/kuafuai/andee") }
            .setNeutralButton(text(R.string.ciel_licenses)) { _, _ -> showLicenses() }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
    private fun showLicenses() {
        val license = listOf("licenses/CIEL_NOTICE.md", "licenses/LICENSE", "licenses/THIRD_PARTY_NOTICES.md").mapNotNull { name ->
            runCatching { assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
        }.joinToString("\n\n")
        val contents = body(license.ifBlank { text(R.string.ciel_license_unavailable) }, 13f).apply {
            setPadding(dp(20), dp(14), dp(20), dp(20))
            setTextColor(inkColor)
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle(text(R.string.ciel_licenses))
            .setView(ScrollView(this).apply { addView(contents) })
            .setPositiveButton(android.R.string.ok, null).show()
    }
    private fun active(state: String) = state in setOf("connecting", "listening", "speaking", "working", "meeting")
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast(text(R.string.ciel_no_browser)) }
    }
    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        currentFocus?.clearFocus()
    }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun text(id: Int, vararg args: Any): String = lctx.getString(id, *args)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private val inkColor get() = Color.parseColor(Glass.TITLE)
    private val secondaryColor get() = Color.parseColor(Glass.SECONDARY)
    private val accentColor get() = if (Theme.night) 0xFF86DDC0.toInt() else 0xFF176B52.toInt()
    private val backgroundColor get() = if (Theme.night) 0xFF101917.toInt() else 0xFFF6F8F4.toInt()
    private val surfaceColor get() = if (Theme.night) 0xFF1C2824.toInt() else Color.WHITE
    private val lineColor get() = if (Theme.night) 0xFF34463E.toInt() else 0xFFDBE4DB.toInt()

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun fullWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun gap(top: Int) = fullWrap().apply { topMargin = dp(top) }
    private fun card() = column().apply { setPadding(dp(18), dp(18), dp(18), dp(18)); background = shape(surfaceColor, 22, lineColor) }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(inkColor)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
    }
    private fun body(value: String, size: Float = 15f) = label(value, size).apply { setTextColor(secondaryColor); setLineSpacing(dp(3).toFloat(), 1.05f) }
    private fun sectionTitle(value: String) = label(value, 18f, true).apply { isAccessibilityHeading = true }
    private fun fieldLabel(value: String) = label(value, 13f, true)
    private fun input(hintText: String, value: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 15f
        setTextColor(inkColor)
        setHintTextColor(secondaryColor)
        minHeight = dp(52)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = shape(backgroundColor, 12, lineColor)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(true)
        contentDescription = hintText
        isSaveEnabled = !secret
        if (secret) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
    private fun button(value: String, primary: Boolean = false, compact: Boolean = false, selected: Boolean = false, click: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = if (compact) 13f else 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(if (compact) 48 else 54)
        minimumHeight = minHeight
        minWidth = dp(48)
        setPadding(dp(if (compact) 13 else 18), dp(12), dp(if (compact) 13 else 18), dp(12))
        gravity = Gravity.CENTER
        val fill = when { primary -> 0xFF176B52.toInt(); selected -> if (Theme.night) 0xFF29493B.toInt() else 0xFFE1F0E4.toInt(); else -> surfaceColor }
        setTextColor(if (primary) Color.WHITE else if (selected) accentColor else inkColor)
        background = RippleDrawable(ColorStateList.valueOf(if (Theme.night) 0x33FFFFFF else 0x18000000), shape(fill, if (compact) 14 else 18, if (primary || selected) fill else lineColor), null)
        // Keep the inherited Material button tint from replacing our contrast-tested fill.
        backgroundTintList = null
        isSelected = selected
        setOnClickListener { click() }
    }
    private fun shape(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
}
