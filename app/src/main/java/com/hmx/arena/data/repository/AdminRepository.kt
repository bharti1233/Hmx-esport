package com.hmx.arena.data.repository

import com.hmx.arena.core.model.AuditLog
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.remote.RemoteDataSource
import com.hmx.arena.data.remote.SupabaseConfig
import com.hmx.arena.ui.common.PickedImage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-authorised administration. Every write goes through a SECURITY
 * DEFINER RPC guarded by public.is_admin(); the client never writes admin
 * tables directly and never trusts a local role flag.
 */
@Singleton
class AdminRepository @Inject constructor(
    private val remote: RemoteDataSource,
) {
    private val lookupColumns =
        "id,slug,name,description,logo_url,is_active,is_archived,display_order,created_at,updated_at"

    private val tournamentColumns =
        "*,category:categories($lookupColumns),gameMode:game_modes($lookupColumns)," +
            "tournamentType:tournament_types($lookupColumns)"

    private val matchColumns =
        "*,category:categories($lookupColumns),gameMode:game_modes($lookupColumns)," +
            "tournamentType:tournament_types($lookupColumns)"

    suspend fun isAdmin(): Boolean = runCatching {
        remote.rpc("is_admin", JsonObject(emptyMap()), Boolean.serializer())
    }.getOrDefault(false)

    // ---------------------------------------------------------------------
    // Lookup data
    // ---------------------------------------------------------------------

    suspend fun categories(admin: Boolean = true): List<LookupOption> = lookup("categories", admin)

    suspend fun gameModes(admin: Boolean = true): List<LookupOption> = lookup("game_modes", admin)

    suspend fun tournamentTypes(admin: Boolean = true): List<LookupOption> =
        lookup("tournament_types", admin)

    private suspend fun lookup(table: String, admin: Boolean): List<LookupOption> = remote.select(
        table,
        mapOf(
            "select" to lookupColumns,
            "order" to "display_order.asc,name.asc",
            "limit" to if (admin) "200" else "100",
        ),
        LookupOption.serializer(),
    )

    suspend fun upsertCategory(id: String?, payload: JsonObject): LookupOption =
        remote.rpc("admin_upsert_category", idArgs(id, payload), LookupOption.serializer())

    suspend fun deleteCategory(id: String) = remote.rpcUnit(
        "admin_delete_category",
        buildJsonObject { put("p_id", id) },
    )

    suspend fun upsertGameMode(id: String?, payload: JsonObject): LookupOption =
        remote.rpc("admin_upsert_game_mode", idArgs(id, payload), LookupOption.serializer())

    suspend fun deleteGameMode(id: String) = remote.rpcUnit(
        "admin_delete_game_mode",
        buildJsonObject { put("p_id", id) },
    )

    suspend fun upsertTournamentType(id: String?, payload: JsonObject): LookupOption =
        remote.rpc("admin_upsert_tournament_type", idArgs(id, payload), LookupOption.serializer())

    suspend fun deleteTournamentType(id: String) = remote.rpcUnit(
        "admin_delete_tournament_type",
        buildJsonObject { put("p_id", id) },
    )

    // ---------------------------------------------------------------------
    // Tournaments
    // ---------------------------------------------------------------------

    suspend fun tournaments(): List<Tournament> = remote.select(
        "tournaments",
        mapOf(
            "select" to tournamentColumns,
            "order" to "created_at.desc",
            "limit" to "200",
        ),
        Tournament.serializer(),
    )

    suspend fun upsertTournament(id: String?, payload: JsonObject): Tournament =
        remote.rpc("admin_upsert_tournament", idArgs(id, payload), Tournament.serializer())

    suspend fun setTournamentStatus(id: String, status: String): Tournament = remote.rpc(
        "admin_set_tournament_status",
        buildJsonObject {
            put("p_id", id)
            put("p_status", status)
        },
        Tournament.serializer(),
    )

    suspend fun deleteTournament(id: String) = remote.rpcUnit(
        "admin_delete_tournament",
        buildJsonObject { put("p_id", id) },
    )

    // ---------------------------------------------------------------------
    // Matches
    // ---------------------------------------------------------------------

    suspend fun matches(): List<Match> = remote.select(
        "matches",
        mapOf(
            "select" to matchColumns,
            "order" to "scheduled_at.desc",
            "limit" to "200",
        ),
        Match.serializer(),
    )

    suspend fun upsertMatch(
        id: String?,
        payload: JsonObject,
        confirmLocked: Boolean = false,
    ): Match = remote.rpc(
        "admin_upsert_match",
        buildJsonObject {
            put("p_id", id ?: JsonNull)
            put("p_payload", payload)
            put("p_confirm_locked", confirmLocked)
        },
        Match.serializer(),
    )

    suspend fun setMatchStatus(id: String, status: String): Match = remote.rpc(
        "admin_set_match_status",
        buildJsonObject {
            put("p_id", id)
            put("p_status", status)
        },
        Match.serializer(),
    )

    suspend fun setMatchPublished(id: String, published: Boolean): Match = remote.rpc(
        "admin_set_match_published",
        buildJsonObject {
            put("p_id", id)
            put("p_published", published)
        },
        Match.serializer(),
    )

    suspend fun duplicateMatch(id: String): Match = remote.rpc(
        "admin_duplicate_match",
        buildJsonObject { put("p_id", id) },
        Match.serializer(),
    )

    suspend fun deleteMatch(id: String) = remote.rpcUnit(
        "admin_delete_match",
        buildJsonObject { put("p_id", id) },
    )

    // ---------------------------------------------------------------------
    // Artwork
    // ---------------------------------------------------------------------

    suspend fun uploadCategoryLogo(categoryId: String, image: PickedImage): String =
        upload(SupabaseConfig.CATEGORY_LOGOS_BUCKET, "categories/$categoryId/logo-${
            System.currentTimeMillis()
        }.${extension(image.mime)}", image)

    suspend fun uploadTournamentBanner(tournamentId: String, image: PickedImage): String =
        upload(SupabaseConfig.TOURNAMENT_BANNERS_BUCKET, "$tournamentId/banner-${
            System.currentTimeMillis()
        }.${extension(image.mime)}", image)

    suspend fun uploadTournamentLogo(tournamentId: String, image: PickedImage): String =
        upload(SupabaseConfig.MATCH_ASSETS_BUCKET, "tournaments/$tournamentId/logo-${
            System.currentTimeMillis()
        }.${extension(image.mime)}", image)

    suspend fun uploadMatchBanner(matchId: String, image: PickedImage): String =
        upload(SupabaseConfig.MATCH_ASSETS_BUCKET, "matches/$matchId/banner-${
            System.currentTimeMillis()
        }.${extension(image.mime)}", image)

    suspend fun uploadMatchLogo(matchId: String, image: PickedImage): String =
        upload(SupabaseConfig.MATCH_ASSETS_BUCKET, "matches/$matchId/logo-${
            System.currentTimeMillis()
        }.${extension(image.mime)}", image)

    private suspend fun upload(bucket: String, path: String, image: PickedImage): String {
        val mime = image.mime.lowercase()
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) {
            throw ApiException("Choose a JPG, PNG or WebP image.")
        }
        if (image.bytes.size > 5 * 1024 * 1024) {
            throw ApiException("That image is larger than 5 MB.")
        }
        remote.upload(bucket, path, image.bytes, mime)
        return remote.publicUrl(bucket, path)
    }

    private fun extension(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    // ---------------------------------------------------------------------
    // Audit
    // ---------------------------------------------------------------------

    suspend fun auditLogs(limit: Int = 50): List<AuditLog> = remote.select(
        "audit_logs",
        mapOf(
            "order" to "created_at.desc",
            "limit" to limit.toString(),
        ),
        AuditLog.serializer(),
    )

    private fun idArgs(id: String?, payload: JsonObject): JsonObject = buildJsonObject {
        put("p_id", id ?: JsonNull)
        put("p_payload", payload)
    }
}
