package com.hmx.arena.core.util

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val inr: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    private val dateOnlyFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun money(paise: Long): String {
        val rupees = paise / 100.0
        return "₹" + inr.format(rupees)
    }

    fun moneyPlain(paise: Long): String = inr.format(paise / 100.0)

    fun points(value: Long): String = inr.format(value)

    fun parseInstant(iso: String?): Instant? = runCatching {
        if (iso.isNullOrBlank()) null else Instant.parse(iso)
    }.getOrNull()

    fun dateTime(iso: String?): String {
        val instant = parseInstant(iso) ?: return "—"
        return dateFmt.format(instant.atZone(ZoneId.systemDefault()))
    }

    fun date(iso: String?): String {
        val instant = parseInstant(iso) ?: return "—"
        return dateOnlyFmt.format(instant.atZone(ZoneId.systemDefault()))
    }

    fun relative(iso: String?): String {
        val instant = parseInstant(iso) ?: return ""
        val diff = Duration.between(instant, Instant.now())
        return when {
            diff.isNegative -> countdown(iso)
            diff.toMinutes() < 1 -> "just now"
            diff.toHours() < 1 -> "${diff.toMinutes()}m ago"
            diff.toDays() < 1 -> "${diff.toHours()}h ago"
            diff.toDays() < 7 -> "${diff.toDays()}d ago"
            else -> date(iso)
        }
    }

    /** "2d 04:12" style countdown; "Starting soon" once under a minute. */
    fun countdown(iso: String?): String {
        val instant = parseInstant(iso) ?: return "—"
        val diff = Duration.between(Instant.now(), instant)
        if (diff.isNegative) return "Starting now"
        val days = diff.toDays()
        val hours = diff.toHours() % 24
        val minutes = diff.toMinutes() % 60
        val seconds = diff.seconds % 60
        return when {
            days > 0 -> "${days}d ${hours.toString().padStart(2, '0')}h"
            diff.toHours() > 0 -> "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            else -> "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    fun tournamentStatusLabel(status: String): String = when (status) {
        "registration_open" -> "Registration open"
        "registration_closed" -> "Registration closed"
        "live" -> "Live"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Upcoming"
    }

    fun modeLabel(mode: String): String = when (mode) {
        "solo" -> "Solo"
        "duo" -> "Duo"
        "squad" -> "Squad"
        "clash_squad" -> "Clash Squad"
        else -> mode.replaceFirstChar { it.uppercase() }
    }

    fun initials(name: String): String =
        name.trim().split(" ", "_", "-").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }
            .ifBlank { "H" }
}
