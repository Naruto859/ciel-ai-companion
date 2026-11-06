package net.kuafuai.andee.screen

/** Null authorizes a different input channel, so it is only legal before the editor was modified. */
object TextInputAttempt {
    fun replace(
        text: String,
        clear: () -> Boolean,
        commit: (String) -> Unit,
        readback: () -> String?,
        beforeWrite: () -> Unit = {},
    ): String? {
        beforeWrite()
        if (!clear()) return null
        beforeWrite()
        try {
            commit(text)
        } catch (e: Exception) {
            throw IllegalStateException("The editor did not confirm text input. Inspect the field before trying again.", e)
        }
        return readback() ?: throw IllegalStateException(
            "Text was submitted, but the editor closed before it could be verified. Do not type it again automatically.",
        )
    }
}
