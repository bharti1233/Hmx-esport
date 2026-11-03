package com.hmx.arena.core.util

/**
 * Input validation shared by the auth and profile flows. Pure functions so the
 * JVM unit tests can pin the rules without Android.
 */
object Validation {
    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val usernameRegex = Regex("^[A-Za-z0-9_]{3,24}$")
    private val uidRegex = Regex("^[A-Za-z0-9]{4,24}$")
    private val otpRegex = Regex("^\\d{6}$")

    fun isEmailValid(value: String): Boolean = emailRegex.matches(value.trim())

    fun isUsernameValid(value: String): Boolean = usernameRegex.matches(value.trim())

    fun isPasswordValid(value: String): Boolean = value.length >= 8

    fun passwordStrength(value: String): Int {
        var score = 0
        if (value.length >= 8) score++
        if (value.length >= 12) score++
        if (value.any { it.isDigit() }) score++
        if (value.any { it.isUpperCase() } && value.any { it.isLowerCase() }) score++
        if (value.any { !it.isLetterOrDigit() }) score++
        return score.coerceIn(0, 4)
    }

    fun isOtpValid(value: String): Boolean = otpRegex.matches(value.trim())

    fun isGameUidValid(value: String): Boolean = uidRegex.matches(value.trim())

    fun isIgnValid(value: String): Boolean = value.trim().length in 2..24

    fun isLevelValid(value: String): Boolean {
        val level = value.trim().toIntOrNull() ?: return false
        return level in 1..100
    }

    fun isTeamNameValid(value: String): Boolean = value.trim().length in 3..24

    fun isTeamTagValid(value: String): Boolean = value.trim().length in 2..5

    fun isDisputeReasonValid(value: String): Boolean = value.trim().length in 10..1000

    fun normalizeCode(value: String): String = value.trim().uppercase()
}
