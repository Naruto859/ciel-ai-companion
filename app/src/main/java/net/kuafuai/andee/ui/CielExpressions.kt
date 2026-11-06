package net.kuafuai.andee.ui

import net.kuafuai.andee.R
import net.kuafuai.andee.ui.ball.Mood

/** The same complete expression vocabulary in both the studio and overlay. */
internal object CielExpressions {
    fun label(mood: Mood): Int = when (mood) {
        Mood.CALM -> R.string.expr_calm
        Mood.HAPPY -> R.string.expr_happy
        Mood.CURIOUS -> R.string.expr_curious
        Mood.TENSE -> R.string.expr_tense
        Mood.ANXIOUS -> R.string.expr_anxious
        Mood.CONCERNED -> R.string.expr_concerned
        Mood.WINK -> R.string.expr_wink
        Mood.WAVE -> R.string.expr_wave
        Mood.PROTECT -> R.string.expr_protect
        Mood.CELEBRATE -> R.string.expr_celebrate
        Mood.REST -> R.string.expr_rest
        Mood.LISTENING -> R.string.expr_listening
        Mood.HOLDING -> R.string.expr_holding
        Mood.THINKING -> R.string.expr_thinking
        Mood.SPEAKING -> R.string.expr_speaking
        Mood.SEARCHING -> R.string.expr_searching
        Mood.SUCCESS -> R.string.expr_success
        Mood.FAILED -> R.string.expr_failed
        Mood.SLEEPING -> R.string.expr_sleeping
    }

    fun emoji(mood: Mood): String = when (mood) {
        Mood.CALM -> "🙂"
        Mood.HAPPY -> "😊"
        Mood.CURIOUS -> "🧐"
        Mood.TENSE -> "😠"
        Mood.ANXIOUS -> "😰"
        Mood.CONCERNED -> "😟"
        Mood.WINK -> "😉"
        Mood.WAVE -> "👋"
        Mood.PROTECT -> "🛡️"
        Mood.CELEBRATE -> "🎉"
        Mood.REST -> "😌"
        Mood.LISTENING -> "👂"
        Mood.HOLDING -> "🤔"
        Mood.THINKING -> "💭"
        Mood.SPEAKING -> "💬"
        Mood.SEARCHING -> "🔎"
        Mood.SUCCESS -> "✨"
        Mood.FAILED -> "😔"
        Mood.SLEEPING -> "😴"
    }
}
