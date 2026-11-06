package com.hmx.arena.data.connectivity

import com.hmx.arena.data.remote.Http
import com.hmx.arena.data.remote.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Result of the lightweight backend reachability probe. */
enum class BackendHealth {
    Reachable,
    Unavailable,
    Timeout,
}

/**
 * A single, tightly timed probe against Supabase Auth's public health endpoint.
 *
 * This is intentionally not a business API call: it verifies the backend host
 * and auth service are up without touching user data. The client is the bare
 * OkHttp client, so the probe never triggers the session refresh authenticator
 * or carries a user access token.
 */
@Singleton
class BackendHealthChecker @Inject constructor() {
    private val client by lazy {
        Http.bareClient().newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(): BackendHealth = withContext(Dispatchers.IO) {
        if (SupabaseConfig.url.isBlank()) return@withContext BackendHealth.Unavailable
        val request = Request.Builder()
            .url("${SupabaseConfig.url}/auth/v1/health")
            .get()
            .addHeader("apikey", SupabaseConfig.key)
            .addHeader("Accept", "application/json")
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (response.isSuccessful) BackendHealth.Reachable else BackendHealth.Unavailable
            }
        } catch (_: SocketTimeoutException) {
            BackendHealth.Timeout
        } catch (_: InterruptedIOException) {
            BackendHealth.Timeout
        } catch (_: Exception) {
            BackendHealth.Unavailable
        } finally {
            runCatching { call.cancel() }
        }
    }
}
