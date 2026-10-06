package dev.cao.finch

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val md: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

    /** 会话编辑框 / 通关日期共用的格式常量（组合内不再反复分配 DateTimeFormatter） */
    val DATE_YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-M-d", Locale.CHINA)
    val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.CHINA)
    val DATE_MD_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    fun hms(duration: Duration): String {
        val s = duration.seconds
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.CHINA, h, m, sec)
        else "%d:%02d".format(Locale.CHINA, m, sec)
    }

    fun hoursMinutes(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** 起止时间段；跨夜/跨天时结束端也带日期，避免「1月5日 23:30 - 00:50」的歧义 */
    fun range(start: LocalDateTime, end: LocalDateTime): String =
        if (start.toLocalDate() == end.toLocalDate()) {
            "${start.format(md)} ${start.format(hm)} - ${end.format(hm)}"
        } else {
            "${start.format(md)} ${start.format(hm)} - ${end.format(md)} ${end.format(hm)}"
        }

    fun monthTitle(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    fun yearTitle(year: Int): String = "${year}年"

    fun dateLabel(date: LocalDate): String = date.format(md)
}
