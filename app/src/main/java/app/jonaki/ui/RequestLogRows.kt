package app.jonaki.ui

import app.jonaki.core.storage.MessageEntity
import app.jonaki.feature.chat.RequestTimeUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The usage sheet's request log: the thread's latest timed model requests (D-132). */
object RequestLogRows {
    const val SHOWN_REQUESTS = 10

    fun build(
        rows: List<MessageEntity>,
        limit: Int = SHOWN_REQUESTS,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<RequestTimeUi> {
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
        return rows
            .filter { row -> row.requestSentAtMillis != null && row.requestSentElapsedMillis != null }
            .takeLast(limit)
            .reversed()
            .map { row -> requestOf(row, timeFormat.withZone(zone)) }
    }

    private fun requestOf(row: MessageEntity, timeFormat: DateTimeFormatter): RequestTimeUi {
        val sentElapsed = row.requestSentElapsedMillis
        val firstText = row.firstTextElapsedMillis
        val firstShown = row.firstShownElapsedMillis
        val providerWait = if (sentElapsed != null && firstText != null) firstText - sentElapsed else null
        val shownAfter = if (firstText != null && firstShown != null) firstShown - firstText else null
        return RequestTimeUi(
            sentAt = timeFormat.format(Instant.ofEpochMilli(row.requestSentAtMillis ?: 0)),
            providerWaitMillis = providerWait,
            shownAfterMillis = shownAfter,
        )
    }
}
