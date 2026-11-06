package net.kuafuai.andee.ui.ball

/** The look's name in the interface language. Lives here because the look's own data is shared with the desktop. */
fun net.kuafuai.andee.ui.ball.BallLook.label(ctx: android.content.Context): String = when (name) {
    "andee" -> net.kuafuai.andee.i18n.AppLocale.str(ctx, net.kuafuai.andee.R.string.ball_look_andee)
    "moss" -> net.kuafuai.andee.i18n.AppLocale.str(ctx, net.kuafuai.andee.R.string.ball_look_moss)
    "nova" -> net.kuafuai.andee.i18n.AppLocale.str(ctx, net.kuafuai.andee.R.string.ball_look_nova)
    else -> name
}
