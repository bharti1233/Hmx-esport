package com.hmx.arena.data.repository

import com.hmx.arena.data.remote.AuthApi
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SupabaseConfig
import com.hmx.arena.data.remote.guardApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val session: SessionManager,
    private val json: Json,
) {
    data class SignUpOutcome(
        val needsVerification: Boolean,
        val email: String,
    )

    suspend fun signUp(username: String, email: String, password: String): SignUpOutcome = guardApi {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            put("data", buildJsonObject {
                put("username", username.trim())
                put("display_name", username.trim())
            })
        }
        val response = authApi.signUp(body)
        val obj = response.asObjectOrNull() ?: throw com.hmx.arena.data.remote.ApiException(
            "Registration failed. Please try again.",
        )
        if (obj["access_token"]?.jsonPrimitive?.contentOrNull != null) {
            session.save(json.decodeFromJsonElement(com.hmx.arena.core.model.AuthSession.serializer(), obj))
            SignUpOutcome(needsVerification = false, email = email.trim())
        } else {
            SignUpOutcome(needsVerification = true, email = email.trim())
        }
    }

    suspend fun signIn(email: String, password: String) = guardApi {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        val response = authApi.token("password", body).asObjectOrNull()
            ?: throw com.hmx.arena.data.remote.ApiException("Incorrect email or password.")
        session.save(json.decodeFromJsonElement(com.hmx.arena.core.model.AuthSession.serializer(), response))
    }

    suspend fun verifyEmail(email: String, token: String) = guardApi {
        val body = buildJsonObject {
            put("type", "signup")
            put("email", email.trim())
            put("token", token.trim())
        }
        val response = authApi.verify(body).asObjectOrNull()
            ?: throw com.hmx.arena.data.remote.ApiException("That code is not valid.")
        session.save(json.decodeFromJsonElement(com.hmx.arena.core.model.AuthSession.serializer(), response))
    }

    suspend fun resendVerification(email: String) = guardApi {
        val body = buildJsonObject {
            put("type", "signup")
            put("email", email.trim())
        }
        authApi.resend(body)
        Unit
    }

    suspend fun requestPasswordReset(email: String) = guardApi {
        val body = buildJsonObject { put("email", email.trim()) }
        authApi.recover(body, SupabaseConfig.AUTH_REDIRECT)
        Unit
    }

    suspend fun updatePassword(newPassword: String) = guardApi {
        val body = buildJsonObject { put("password", newPassword) }
        authApi.updateUser(body)
        Unit
    }

    /** Completes a token-hash style recovery link (Supabase PKCE-free flow). */
    suspend fun completeRecovery(tokenHash: String) = guardApi {
        val body = buildJsonObject {
            put("type", "recovery")
            put("token_hash", tokenHash)
        }
        val response = authApi.verify(body).asObjectOrNull()
            ?: throw com.hmx.arena.data.remote.ApiException("This reset link has expired.")
        session.save(json.decodeFromJsonElement(com.hmx.arena.core.model.AuthSession.serializer(), response))
        Unit
    }

    suspend fun updateEmail(email: String) = guardApi {
        val body = buildJsonObject { put("email", email.trim()) }
        authApi.updateUser(body)
        Unit
    }

    suspend fun signOut() {
        runCatching { guardApi { authApi.logout(); Unit } }
        session.clear()
    }

    /** Restores the session from a supabase recovery deep link fragment. */
    fun restoreFromRecovery(accessToken: String, refreshToken: String): Boolean {
        if (accessToken.isBlank() || refreshToken.isBlank()) return false
        session.save(
            com.hmx.arena.core.model.AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = 3600,
            ),
        )
        return true
    }

    fun currentUserId(): String? = session.snapshotUserId()

    fun currentEmail(): String? = session.snapshotEmail()

    private fun kotlinx.serialization.json.JsonElement.asObjectOrNull(): JsonObject? =
        runCatching { this.jsonObject }.getOrNull()
}
