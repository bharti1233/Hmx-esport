package com.hmx.arena.core.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A validated password-recovery deep link.
 *
 * Only links that match the registered HMX Arena callback are accepted:
 *
 *   hmxarena://auth/reset?type=recovery&token_hash=...&token_hash=...
 *   hmxarena://auth/reset#type=recovery&access_token=...&refresh_token=...
 *
 * The parser is pure JVM code (no android.net.Uri) so the security rules can be
 * pinned by unit tests. Any other scheme, host, path, type or missing token is
 * rejected. In particular, an arbitrary `hmxarena://auth/...` link can no
 * longer inject a forged access token into the session.
 */
data class RecoveryLink(
    val tokenHash: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
) {
    val hasTokenHash: Boolean get() = !tokenHash.isNullOrBlank()
    val hasSessionTokens: Boolean get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()
    val isUsable: Boolean get() = hasTokenHash || hasSessionTokens
}

object RecoveryLinkParser {
    const val SCHEME = "hmxarena"
    const val HOST = "auth"
    const val RESET_PATH = "/reset"

    fun parse(raw: String?): RecoveryLink? {
        if (raw.isNullOrBlank()) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (!HOST.equals(uri.host, ignoreCase = true)) return null

        val path = uri.path.orEmpty()
        if (path.isNotBlank() && path != RESET_PATH && path != "/") return null

        val query = parseParams(uri.rawQuery)
        val fragment = parseParams(uri.rawFragment)
        fun value(key: String): String? =
            fragment[key]?.takeIf { it.isNotBlank() } ?: query[key]?.takeIf { it.isNotBlank() }

        if (value("type") != "recovery") return null

        val link = RecoveryLink(
            tokenHash = value("token_hash"),
            accessToken = value("access_token"),
            refreshToken = value("refresh_token"),
        )
        if (!link.isUsable) return null
        // A recovery link carries either a one-time token hash or a session
        // pair. Identical "access" and "refresh" values are never a real
        // session and indicate a hand-crafted link.
        if (link.hasSessionTokens && link.accessToken == link.refreshToken) return null
        return link
    }

    private fun parseParams(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2 || pieces[0].isBlank()) return@mapNotNull null
            val key = decode(pieces[0]) ?: return@mapNotNull null
            val value = decode(pieces[1]) ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()
}
