package net.kuafuai.andee.device

/** Validate before handing an action to Android; rejected requests must have no side effects. */
object DeviceCommandInputs {
    fun alarm(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59) { "alarm time must be hour 0..23 and minute 0..59" }
    }

    fun calendarEnd(beginMs: Long, endMs: Long?): Long {
        require(beginMs > 0) { "calendar begin_ms must be a positive Unix timestamp in milliseconds" }
        val end = endMs ?: Math.addExact(beginMs, 3_600_000L)
        require(end > beginMs) { "calendar end_ms must be after begin_ms" }
        return end
    }

    fun sms(number: String, body: String) {
        require(number.isNotBlank() && number.length <= 128 && number.none { it == '\r' || it == '\n' }) {
            "SMS requires one recipient phone number"
        }
        require(body.isNotBlank()) { "SMS body must not be empty" }
    }
}
