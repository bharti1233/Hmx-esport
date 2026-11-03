package com.hmx.arena.data.remote

import com.hmx.arena.data.remote.Query.select
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDataSource @Inject constructor(
    private val rest: RestApi,
    private val storage: StorageApi,
    private val json: Json,
) {
    suspend fun <T> select(
        table: String,
        params: Map<String, String>,
        deserializer: DeserializationStrategy<T>,
    ): List<T> = guardApi {
        val element = rest.select(table, params + select())
        element.decodeList(deserializer)
    }

    suspend fun <T> selectOne(
        table: String,
        params: Map<String, String>,
        deserializer: DeserializationStrategy<T>,
    ): T? = guardApi {
        val element = rest.select(table, params + select() + Query.limit(1))
        element.decodeList(deserializer).firstOrNull()
    }

    suspend fun selectRaw(table: String, params: Map<String, String>): JsonArray = guardApi {
        rest.select(table, params) as? JsonArray ?: JsonArray(emptyList())
    }

    suspend fun <T> rpc(
        name: String,
        params: JsonObject = JsonObject(emptyMap()),
        deserializer: DeserializationStrategy<T>,
    ): T = guardApi {
        val element = rest.rpc(name, params)
        when (element) {
            is JsonArray -> element.firstOrNull()?.let { json.decodeFromJsonElement(deserializer, it) }
            is JsonObject -> json.decodeFromJsonElement(deserializer, element)
            else -> null
        } ?: throw ApiException("The server returned an unexpected response.")
    }

    suspend fun <T> rpcNullable(
        name: String,
        params: JsonObject = JsonObject(emptyMap()),
        deserializer: DeserializationStrategy<T>,
    ): T? = guardApi {
        val element = rest.rpc(name, params)
        when (element) {
            is JsonArray -> element.firstOrNull()?.let { json.decodeFromJsonElement(deserializer, it) }
            is JsonObject -> json.decodeFromJsonElement(deserializer, element)
            else -> null
        }
    }

    suspend fun <T> rpcList(
        name: String,
        params: JsonObject = JsonObject(emptyMap()),
        deserializer: DeserializationStrategy<T>,
    ): List<T> = guardApi {
        rest.rpc(name, params).decodeList(deserializer)
    }

    suspend fun rpcUnit(name: String, params: JsonObject = JsonObject(emptyMap())) = guardApi {
        rest.rpc(name, params)
        Unit
    }

    suspend fun insert(table: String, body: JsonObject) = guardApi {
        // return=minimal yields 204 No Content; do not try to decode a row.
        val response = rest.insert(table, body)
        if (!response.isSuccessful) {
            val message = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(mapDbMessage(parseErrorMessage(message ?: "")), message)
        }
        Unit
    }

    suspend fun delete(table: String, params: Map<String, String>) = guardApi {
        val response = rest.delete(table, params)
        if (!response.isSuccessful) {
            val message = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(mapDbMessage(parseErrorMessage(message ?: "")), message)
        }
        Unit
    }

    suspend fun update(table: String, params: Map<String, String>, body: JsonObject) = guardApi {
        val response = rest.update(table, params, body)
        if (!response.isSuccessful) {
            val message = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(mapDbMessage(parseErrorMessage(message ?: "")), message)
        }
        Unit
    }

    suspend fun upload(
        bucket: String,
        path: String,
        bytes: ByteArray,
        mimeType: String,
    ): String = guardApi {
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val response = storage.upload(bucket, path, body, mimeType)
        if (!response.isSuccessful) {
            val message = runCatching { response.errorBody()?.string() }.getOrNull()
            throw ApiException(mapDbMessage(parseErrorMessage(message ?: "")), message)
        }
        path
    }

    suspend fun signedUrl(bucket: String, path: String, expiresInSeconds: Long = 3600): String? = guardApi {
        val element = storage.sign(
            bucket,
            path,
            buildJsonObject {
                put("expiresIn", kotlinx.serialization.json.JsonPrimitive(expiresInSeconds))
            },
        )
        val obj = element as? JsonObject ?: return@guardApi null
        val signed = (obj["signedURL"] ?: obj["signedUrl"])?.jsonPrimitive?.contentOrNull ?: return@guardApi null
        if (signed.startsWith("http")) signed else SupabaseConfig.url + signed
    }

    fun publicUrl(bucket: String, path: String): String =
        "${SupabaseConfig.url}/storage/v1/object/public/$bucket/$path"

    private fun <T> JsonElement.decodeList(deserializer: DeserializationStrategy<T>): List<T> {
        val array = this as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            if (item is JsonNull) null else runCatching { json.decodeFromJsonElement(deserializer, item) }.getOrNull()
        }
    }
}
