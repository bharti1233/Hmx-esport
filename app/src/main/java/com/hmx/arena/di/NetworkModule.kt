package com.hmx.arena.di

import com.hmx.arena.data.remote.AuthApi
import com.hmx.arena.data.remote.Http
import com.hmx.arena.data.remote.RestApi
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.StorageApi
import com.hmx.arena.data.remote.SupabaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Http.json

    @Provides
    @Singleton
    fun provideOkHttp(session: SessionManager): OkHttpClient {
        val authenticator = Authenticator { _: Route?, response: Response ->
            val request = response.request
            if (request.header("Authorization") == null) return@Authenticator null
            if (responseCount(response) >= 2) return@Authenticator null
            val path = request.url.encodedPath
            if (path.contains("/auth/v1/token") || path.contains("/auth/v1/signup")) {
                return@Authenticator null
            }
            val token = session.refreshBlocking() ?: return@Authenticator null
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("apikey", SupabaseConfig.key)
                    .header("Accept", "application/json")
                val token = session.accessToken()
                if (!token.isNullOrBlank() && chain.request().header("Authorization") == null) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .authenticator(authenticator)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val base = SupabaseConfig.url.ifBlank { "https://invalid.local" }.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory(Http.mediaType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideRestApi(retrofit: Retrofit): RestApi = retrofit.create(RestApi::class.java)

    @Provides
    @Singleton
    fun provideStorageApi(retrofit: Retrofit): StorageApi = retrofit.create(StorageApi::class.java)
}
