package dev.cao.finch

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val md: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

    fun hms(duration: Duration): String {
        val s = duration.seconds
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun hoursMinutes(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun range(start: LocalDateTime, end: LocalDateTime): String =
        "${start.format(md)} ${start.format(hm)} - ${end.format(hm)}"

    fun monthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    fun yearTitle(year: Int): String = "${year}年"

    fun dateLabel(date: LocalDate): String = date.format(md)
}
