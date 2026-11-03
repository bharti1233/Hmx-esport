package com.hmx.arena.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hmx.arena.core.model.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "hmx_session")

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val email: String?) : SessionState
}

/**
 * Owns the Supabase session. Access tokens live in memory; refresh tokens are
 * persisted in app-private DataStore and never logged.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Any()
    private val refreshing = AtomicBoolean(false)
    private val bareClient by lazy { Http.bareClient() }

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null
    @Volatile private var userId: String? = null
    @Volatile private var email: String? = null
    @Volatile private var expiresAtMillis: Long = 0

    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val user = stringPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val expires = longPreferencesKey("expires_at")
    }

    suspend fun bootstrap() {
        val prefs = context.sessionDataStore.data.first()
        accessToken = prefs[Keys.access]
        refreshToken = prefs[Keys.refresh]
        userId = prefs[Keys.user]
        email = prefs[Keys.email]
        expiresAtMillis = prefs[Keys.expires] ?: 0L
        val uid = userId
        _state.value = if (uid.isNullOrBlank()) SessionState.SignedOut else SessionState.SignedIn(uid, email)
    }

    fun snapshotUserId(): String? = userId

    fun snapshotEmail(): String? = email

    fun accessToken(): String? = accessToken

    fun save(session: AuthSession) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        expiresAtMillis = System.currentTimeMillis() + (session.expiresIn * 1000L)
        session.user?.id?.let { userId = it }
        session.user?.email?.let { email = it }
        val uid = userId
        if (uid != null) {
            _state.value = SessionState.SignedIn(uid, email)
        }
        scope.launch { persist() }
    }

    fun updateUser(id: String, mail: String?) {
        userId = id
        email = mail
        _state.value = SessionState.SignedIn(id, mail)
        scope.launch { persist() }
    }

    fun updateAccessToken(token: String, expiresIn: Long) {
        accessToken = token
        expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
        scope.launch { persist() }
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        userId = null
        email = null
        expiresAtMillis = 0L
        _state.value = SessionState.SignedOut
        scope.launch {
            context.sessionDataStore.edit { it.clear() }
        }
    }

    private suspend fun persist() {
        context.sessionDataStore.edit { prefs ->
            accessToken?.let { prefs[Keys.access] = it } ?: prefs.remove(Keys.access)
            refreshToken?.let { prefs[Keys.refresh] = it } ?: prefs.remove(Keys.refresh)
            userId?.let { prefs[Keys.user] = it } ?: prefs.remove(Keys.user)
            email?.let { prefs[Keys.email] = it } ?: prefs.remove(Keys.email)
            prefs[Keys.expires] = expiresAtMillis
        }
    }

    fun isExpired(): Boolean =
        accessToken == null || System.currentTimeMillis() > expiresAtMillis - 60_000L

    /**
     * Synchronous refresh used from the OkHttp Authenticator thread.
     * Returns the new access token, or null when the session is unrecoverable.
     */
    fun refreshBlocking(): String? {
        if (!refreshing.compareAndSet(false, true)) {
            // Another thread is refreshing; wait for it.
            synchronized(refreshLock) {
                return if (isExpired()) null else accessToken
            }
        }
        try {
            val token = refreshToken ?: return null
            val body = buildJsonObject { put("refresh_token", token) }
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${SupabaseConfig.url}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SupabaseConfig.key)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            bareClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    clear()
                    return null
                }
                val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
                val obj = element as? JsonObject ?: return null
                val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val newRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
                accessToken = newAccess
                if (newRefresh != null) refreshToken = newRefresh
                expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
                obj["user"]?.jsonObject?.let { user ->
                    user["id"]?.jsonPrimitive?.contentOrNull?.let { userId = it }
                    user["email"]?.jsonPrimitive?.contentOrNull?.let { email = it }
                }
                scope.launch { persist() }
                return newAccess
            }
        } catch (_: Exception) {
            return null
        } finally {
            refreshing.set(false)
        }
    }
}
