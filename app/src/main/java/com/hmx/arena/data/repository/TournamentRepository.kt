package com.hmx.arena.data.repository

import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.MatchCredentials
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class TournamentFilter(
    val query: String = "",
    val mode: String? = null,
    val freeOnly: Boolean = false,
    val status: String? = null,
)

data class MyMatch(
    val match: Match,
    val tournament: Tournament,
    val registration: Registration,
)

@Singleton
class TournamentRepository @Inject constructor(
    private val remote: RemoteDataSource,
    private val session: SessionManager,
    private val cache: HmxDatabase,
    private val json: Json,
) {
    suspend fun tournaments(filter: TournamentFilter = TournamentFilter()): List<Tournament> {
        val cacheable = filter.query.isBlank() && filter.mode == null &&
            !filter.freeOnly && filter.status == null
        return try {
            val items = fetchTournaments(filter)
            if (cacheable) cacheCatalogue(items)
            items
        } catch (e: ApiException) {
            if (cacheable && e.cause is IOException) cachedCatalogue() else throw e
        }
    }

    private suspend fun fetchTournaments(filter: TournamentFilter): List<Tournament> {
        val params = mutableMapOf(
            "status" to "neq.draft",
            "order" to "is_featured.desc,starts_at.asc",
            "limit" to "60",
        )
        if (filter.status != null) params["status"] = "eq.${filter.status}"
        if (filter.mode != null) params["mode"] = "eq.${filter.mode}"
        if (filter.freeOnly) params["entry_type"] = "eq.free"
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim().replace("(", "").replace(")", "").replace(",", " ")
            params["or"] = "(title.ilike.*$q*,description.ilike.*$q*)"
        }
        return remote.select("tournaments", params, Tournament.serializer())
    }

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
        }
    }.getOrDefault(emptyList())

    suspend fun featured(): List<Tournament> = remote.select(
        "tournaments",
        mapOf(
            "is_featured" to "eq.true",
            "status" to "neq.draft",
            "order" to "starts_at.asc",
            "limit" to "5",
        ),
        Tournament.serializer(),
    )

    suspend fun tournament(id: String): Tournament? = remote.selectOne(
        "tournaments",
        mapOf("id" to "eq.$id"),
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
            mapOf("id" to "in.(${ids.joinToString(",")})"),
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
        mapOf("tournament_id" to "eq.$tournamentId", "order" to "scheduled_at.asc"),
        Match.serializer(),
    )

    suspend fun myMatches(): List<MyMatch> {
        val registrations = registrations().filter { it.status == "confirmed" || it.status == "waitlisted" }
        if (registrations.isEmpty()) return emptyList()
        val tournamentIds = registrations.map { it.tournamentId }.distinct()
        // The two reads are independent; running them sequentially made the
        // home dashboard pay both latencies back to back.
        return coroutineScope {
            val tournamentsById = async {
                remote.select(
                    "tournaments",
                    mapOf("id" to "in.(${tournamentIds.joinToString(",")})"),
                    Tournament.serializer(),
                ).associateBy { it.id }
            }
            val matches = async {
                remote.select(
                    "matches",
                    mapOf(
                        "tournament_id" to "in.(${tournamentIds.joinToString(",")})",
                        "order" to "scheduled_at.asc",
                    ),
                    Match.serializer(),
                )
            }
            val tournaments = tournamentsById.await()
            matches.await().mapNotNull { match ->
                val registration = registrations.firstOrNull { it.tournamentId == match.tournamentId }
                    ?: return@mapNotNull null
                val tournament = tournaments[match.tournamentId] ?: return@mapNotNull null
                MyMatch(match, tournament, registration)
            }
        }
    }

    suspend fun match(id: String): Match? = remote.selectOne(
        "matches",
        mapOf("id" to "eq.$id"),
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
