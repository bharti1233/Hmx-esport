package com.hmx.arena.data.repository

import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.MatchCredentials
import com.hmx.arena.core.model.MatchRegistration
import com.hmx.arena.core.model.MatchResult
import com.hmx.arena.core.model.Registration
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.core.model.TournamentRule
import com.hmx.arena.data.local.CachedTournament
import com.hmx.arena.data.local.HmxDatabase
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.remote.RemoteDataSource
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SupabaseConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class TournamentFilter(
    val query: String = "",
    val categoryId: String? = null,
    val gameModeId: String? = null,
    val freeOnly: Boolean = false,
    val status: String? = null,
)

data class MatchFeedItem(
    val match: Match,
    val tournament: Tournament? = null,
    val matchRegistration: MatchRegistration? = null,
) {
    /** Public match eligibility never depends on a tournament registration. */
    val registered: Boolean get() = matchRegistration != null
}

private const val LOOKUP_EMBED =
    "category:categories(id,slug,name,description,logo_url,image_url,is_active,is_archived,display_order)," +
        "gameMode:game_modes(id,slug,name,description,logo_url,image_url,is_active,is_archived,display_order)," +
        "tournamentType:tournament_types(id,slug,name,description,logo_url,image_url,is_active,is_archived,display_order)"

private const val LOOKUP_COLUMNS =
    "id,slug,name,description,logo_url,image_url,is_active,is_archived,display_order"

private const val TOURNAMENT_COLUMNS = "*,$LOOKUP_EMBED"
private const val MATCH_COLUMNS = "*,$LOOKUP_EMBED"

/**
 * Published tournament catalogue. Draft visibility is additionally enforced by
 * RLS; these filters make the intended public feed explicit and keep cancelled
 * or archived events out of Arena even when a cached row exists.
 */
internal fun tournamentQueryParams(filter: TournamentFilter = TournamentFilter()): Map<String, String> {
    val params = mutableMapOf(
        "status" to (filter.status?.let { "eq.$it" } ?: "not.in.(cancelled)"),
        "archived_at" to "is.null",
        "order" to "is_featured.desc,starts_at.asc",
        "limit" to "60",
        "select" to TOURNAMENT_COLUMNS,
    )
    filter.categoryId?.let { params["category_id"] = "eq.$it" }
    filter.gameModeId?.let { params["game_mode_id"] = "eq.$it" }
    if (filter.freeOnly) params["entry_type"] = "eq.free"
    if (filter.query.isNotBlank()) {
        val q = filter.query.trim().replace("(", "").replace(")", "").replace(",", " ")
        params["or"] = "(title.ilike.*$q*,description.ilike.*$q*)"
    }
    return params
}

/** Public match collection: published, not archived and not cancelled. */
internal fun publishedMatchQueryParams(limit: Int = 60): Map<String, String> = mapOf(
    "is_published" to "eq.true",
    "archived_at" to "is.null",
    "status" to "neq.cancelled",
    "order" to "scheduled_at.asc",
    "limit" to limit.toString(),
    "select" to MATCH_COLUMNS,
)

@Singleton
class TournamentRepository @Inject constructor(
    private val remote: RemoteDataSource,
    private val session: SessionManager,
    private val cache: HmxDatabase,
    private val json: Json,
) {
    suspend fun tournaments(filter: TournamentFilter = TournamentFilter()): List<Tournament> {
        val cacheable = filter.query.isBlank() && filter.categoryId == null &&
            filter.gameModeId == null && !filter.freeOnly && filter.status == null
        return try {
            val items = fetchTournaments(filter)
            if (cacheable) cacheCatalogue(items)
            items
        } catch (e: ApiException) {
            if (cacheable && e.cause is IOException) cachedCatalogue() else throw e
        }
    }

    private suspend fun fetchTournaments(filter: TournamentFilter): List<Tournament> =
        remote.select("tournaments", tournamentQueryParams(filter), Tournament.serializer())

    /** Dynamic Arena filters. Archived rows stay readable through the embed but are not offered. */
    suspend fun categories(): List<LookupOption> = remote.select(
        "categories",
        mapOf(
            "select" to LOOKUP_COLUMNS,
            "is_active" to "eq.true",
            "is_archived" to "eq.false",
            "order" to "display_order.asc,name.asc",
            "limit" to "100",
        ),
        LookupOption.serializer(),
    )

    suspend fun gameModes(): List<LookupOption> = remote.select(
        "game_modes",
        mapOf(
            "select" to LOOKUP_COLUMNS,
            "is_active" to "eq.true",
            "is_archived" to "eq.false",
            "order" to "display_order.asc,name.asc",
            "limit" to "100",
        ),
        LookupOption.serializer(),
    )

    private suspend fun cacheCatalogue(items: List<Tournament>) = runCatching {
        val dao = cache.tournaments()
        dao.clear()
        val now = System.currentTimeMillis()
        dao.insertAll(
            items.map {
                CachedTournament(it.id, json.encodeToString(Tournament.serializer(), it), now)
            },
        )
    }

    private suspend fun cachedCatalogue(): List<Tournament> = runCatching {
        cache.tournaments().all().mapNotNull {
            runCatching { json.decodeFromString(Tournament.serializer(), it.payload) }.getOrNull()
        }.filter { it.status != "cancelled" && it.archivedAt == null }
    }.getOrDefault(emptyList())

    suspend fun featured(): List<Tournament> = remote.select(
        "tournaments",
        mapOf(
            "is_featured" to "eq.true",
            "status" to "not.in.(cancelled)",
            "archived_at" to "is.null",
            "order" to "starts_at.asc",
            "limit" to "5",
            "select" to TOURNAMENT_COLUMNS,
        ),
        Tournament.serializer(),
    )

    suspend fun tournament(id: String): Tournament? = remote.selectOne(
        "tournaments",
        mapOf("id" to "eq.$id", "select" to TOURNAMENT_COLUMNS),
        Tournament.serializer(),
    )

    suspend fun rules(tournamentId: String): List<TournamentRule> = remote.select(
        "tournament_rules",
        mapOf("tournament_id" to "eq.$tournamentId", "order" to "position.asc"),
        TournamentRule.serializer(),
    )

    /**
     * RLS already restricts this to the caller's own registrations, plus
     * registrations belonging to a team the caller is a member of.
     */
    suspend fun registrations(): List<Registration> = remote.select(
        "registrations",
        mapOf("order" to "created_at.desc"),
        Registration.serializer(),
    )

    suspend fun tournamentsByIds(ids: List<String>): List<Tournament> {
        if (ids.isEmpty()) return emptyList()
        return remote.select(
            "tournaments",
            mapOf("id" to "in.(${ids.joinToString(",")})", "select" to TOURNAMENT_COLUMNS),
            Tournament.serializer(),
        )
    }

    suspend fun register(tournamentId: String, teamId: String?): Registration = remote.rpc(
        "register_for_tournament",
        buildJsonObject {
            put("p_tournament_id", tournamentId)
            if (teamId != null) put("p_team_id", teamId) else put("p_team_id", kotlinx.serialization.json.JsonNull)
        },
        Registration.serializer(),
    )

    suspend fun cancelRegistration(registrationId: String) {
        remote.rpcUnit(
            "cancel_registration",
            buildJsonObject { put("p_registration_id", registrationId) },
        )
    }

    suspend fun tournamentMatches(tournamentId: String): List<Match> = remote.select(
        "matches",
        mapOf(
            "tournament_id" to "eq.$tournamentId",
            "is_published" to "eq.true",
            "archived_at" to "is.null",
            "status" to "neq.cancelled",
            "order" to "scheduled_at.asc",
            "select" to MATCH_COLUMNS,
        ),
        Match.serializer(),
    )

    /** Every published match is independently registerable, with or without a tournament. */
    suspend fun publishedMatches(limit: Int = 60): List<Match> =
        remote.select("matches", publishedMatchQueryParams(limit), Match.serializer())

    /**
     * Public feed. `registered` is derived exclusively from match_registrations;
     * a tournament registration never implies a match slot.
     */
    suspend fun matchFeed(): List<MatchFeedItem> {
        val matches = publishedMatches()
        if (matches.isEmpty()) return emptyList()
        val myRegistrations = runCatching { myMatchRegistrations() }
            .getOrDefault(emptyList())
            .associateBy { it.matchId }
        val tournamentsById = tournamentsFor(matches)
        return matches.map { match ->
            MatchFeedItem(
                match = match,
                tournament = match.tournamentId?.let { tournamentsById[it] },
                matchRegistration = myRegistrations[match.id],
            )
        }
    }

    /**
     * My Matches. Starts from the authenticated user's active match
     * registrations, then resolves only those matches. It can never fall back to
     * the public catalogue.
     */
    suspend fun myMatchFeed(): List<MatchFeedItem> {
        val registrations = myMatchRegistrations()
            .filter { it.status == "confirmed" || it.status == "waitlisted" }
        if (registrations.isEmpty()) return emptyList()
        val matches = matchesByIds(registrations.map { it.matchId }.distinct())
        if (matches.isEmpty()) return emptyList()
        val registrationsByMatch = registrations.associateBy { it.matchId }
        val tournamentsById = tournamentsFor(matches)
        return matches.map { match ->
            MatchFeedItem(
                match = match,
                tournament = match.tournamentId?.let { tournamentsById[it] },
                matchRegistration = registrationsByMatch[match.id],
            )
        }
    }

    private suspend fun matchesByIds(ids: List<String>): List<Match> {
        if (ids.isEmpty()) return emptyList()
        return remote.select(
            "matches",
            mapOf(
                "id" to "in.(${ids.joinToString(",")})",
                "is_published" to "eq.true",
                "archived_at" to "is.null",
                "status" to "neq.cancelled",
                "order" to "scheduled_at.asc",
                "select" to MATCH_COLUMNS,
            ),
            Match.serializer(),
        )
    }

    private suspend fun tournamentsFor(matches: List<Match>): Map<String, Tournament> {
        val ids = matches.mapNotNull { it.tournamentId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return tournamentsByIds(ids).associateBy { it.id }
    }

    suspend fun myMatchRegistrations(): List<MatchRegistration> = remote.select(
        "match_registrations",
        mapOf(
            "user_id" to "eq.${uid()}",
            "status" to "in.(confirmed,waitlisted)",
            "order" to "created_at.desc",
        ),
        MatchRegistration.serializer(),
    )

    suspend fun registerForMatch(matchId: String, teamId: String? = null): MatchRegistration = remote.rpc(
        "register_for_match",
        buildJsonObject {
            put("p_match_id", matchId)
            if (teamId != null) put("p_team_id", teamId) else put("p_team_id", kotlinx.serialization.json.JsonNull)
        },
        MatchRegistration.serializer(),
    )

    suspend fun cancelMatchRegistration(registrationId: String) {
        remote.rpcUnit(
            "cancel_match_registration",
            buildJsonObject { put("p_registration_id", registrationId) },
        )
    }

    suspend fun match(id: String): Match? = remote.selectOne(
        "matches",
        mapOf(
            "id" to "eq.$id",
            "is_published" to "eq.true",
            "archived_at" to "is.null",
            "select" to MATCH_COLUMNS,
        ),
        Match.serializer(),
    )

    suspend fun credentials(matchId: String): MatchCredentials? = remote.rpcList(
        "get_match_credentials",
        buildJsonObject { put("p_match_id", matchId) },
        MatchCredentials.serializer(),
    ).firstOrNull()

    suspend fun resultsForMatch(matchId: String): List<MatchResult> = remote.select(
        "match_results",
        mapOf("match_id" to "eq.$matchId", "order" to "created_at.desc"),
        MatchResult.serializer(),
    )

    suspend fun myResultForMatch(matchId: String): MatchResult? =
        resultsForMatch(matchId).firstOrNull { it.submittedBy == uid() }

    suspend fun submitResult(
        matchId: String,
        placement: Int,
        kills: Int,
        evidenceBytes: ByteArray,
        evidenceMime: String,
        notes: String?,
    ): MatchResult {
        val path = "${uid()}/${matchId}-${System.currentTimeMillis()}.jpg"
        remote.upload(SupabaseConfig.RESULT_EVIDENCE_BUCKET, path, evidenceBytes, evidenceMime)
        return remote.rpc(
            "submit_match_result",
            buildJsonObject {
                put("p_match_id", matchId)
                put("p_placement", placement)
                put("p_kills", kills)
                put("p_evidence_path", path)
                put("p_evidence_mime", evidenceMime)
                put("p_evidence_size", evidenceBytes.size)
                if (notes != null) put("p_notes", notes)
            },
            MatchResult.serializer(),
        )
    }

    suspend fun raiseDispute(resultId: String, reason: String) {
        remote.rpcUnit(
            "raise_dispute",
            buildJsonObject {
                put("p_result_id", resultId)
                put("p_reason", reason)
            },
        )
    }

    suspend fun leaderboard(
        scope: String = "player",
        monthly: Boolean = false,
        tournamentId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<LeaderboardRow> = remote.rpcList(
        "get_leaderboard",
        buildJsonObject {
            put("p_scope", scope)
            put("p_period", if (monthly) "monthly" else "all")
            put("p_tournament_id", tournamentId)
            put("p_limit", limit)
            put("p_offset", offset)
        },
        LeaderboardRow.serializer(),
    )

    suspend fun evidenceUrl(path: String): String? =
        remote.signedUrl(SupabaseConfig.RESULT_EVIDENCE_BUCKET, path, 3600)

    private fun uid(): String =
        session.snapshotUserId() ?: throw ApiException("Please sign in again.")
}
