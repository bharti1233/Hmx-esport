package com.hmx.arena.data.repository

import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.Dispute
import com.hmx.arena.core.model.LedgerEntry
import com.hmx.arena.core.model.NotificationItem
import com.hmx.arena.core.model.Participant
import com.hmx.arena.core.model.PlayerGameAccount
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.ReferralClaim
import com.hmx.arena.core.model.SupportMessage
import com.hmx.arena.core.model.SupportTicket
import com.hmx.arena.core.model.WalletAccount
import com.hmx.arena.data.remote.ApiException
import com.hmx.arena.data.remote.RemoteDataSource
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.remote.SupabaseConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val remote: RemoteDataSource,
    private val session: SessionManager,
) {
    suspend fun myProfile(): Profile =
        remote.rpc("my_profile", JsonObject(emptyMap()), Profile.serializer())

    suspend fun updateProfile(displayName: String, bio: String?, region: String): Profile {
        remote.update(
            "profiles",
            mapOf("id" to "eq.${uid()}"),
            buildJsonObject {
                put("display_name", displayName)
                if (bio != null) put("bio", bio)
                put("region", region)
            },
        )
        return myProfile()
    }

    suspend fun updateAvatar(publicUrl: String): Profile {
        remote.update("profiles", mapOf("id" to "eq.${uid()}"), buildJsonObject { put("avatar_url", publicUrl) })
        return myProfile()
    }

    suspend fun gameAccounts(): List<PlayerGameAccount> = remote.select(
        "player_game_accounts",
        mapOf("user_id" to "eq.${uid()}", "order" to "game.asc"),
        PlayerGameAccount.serializer(),
    )

    suspend fun saveGameAccount(
        existing: PlayerGameAccount?,
        ign: String,
        gameUid: String,
        level: Int?,
    ) {
        if (existing != null) {
            remote.update(
                "player_game_accounts",
                mapOf("id" to "eq.${existing.id}"),
                buildJsonObject {
                    put("ign", ign)
                    put("game_uid", gameUid)
                    if (level != null) put("level", level)
                },
            )
        } else {
            remote.insert(
                "player_game_accounts",
                buildJsonObject {
                    put("user_id", uid())
                    put("game", "free_fire")
                    put("ign", ign)
                    put("game_uid", gameUid)
                    if (level != null) put("level", level)
                },
            )
        }
    }

    suspend fun deleteGameAccount(id: String) {
        remote.delete("player_game_accounts", mapOf("id" to "eq.$id"))
    }

    suspend fun uploadAvatar(bytes: ByteArray, mime: String): String {
        val path = "${uid()}/avatar-${System.currentTimeMillis()}.jpg"
        remote.upload(SupabaseConfig.AVATARS_BUCKET, path, bytes, mime)
        val url = remote.publicUrl(SupabaseConfig.AVATARS_BUCKET, path)
        updateAvatar(url)
        return url
    }

    suspend fun referrals(): List<ReferralClaim> = remote.select(
        "referral_claims",
        mapOf("referrer_id" to "eq.${uid()}", "order" to "created_at.desc"),
        ReferralClaim.serializer(),
    )

    suspend fun claimReferral(code: String): ReferralClaim = remote.rpc(
        "claim_referral",
        buildJsonObject { put("p_code", code.trim().uppercase()) },
        ReferralClaim.serializer(),
    )

    suspend fun notifications(): List<NotificationItem> = remote.select(
        "notifications",
        mapOf("user_id" to "eq.${uid()}", "order" to "created_at.desc", "limit" to "60"),
        NotificationItem.serializer(),
    )

    suspend fun markNotificationsRead() {
        remote.rpcUnit("mark_notifications_read")
    }

    suspend fun announcements(): List<Announcement> = remote.select(
        "announcements",
        mapOf("is_published" to "eq.true", "order" to "published_at.desc", "limit" to "10"),
        Announcement.serializer(),
    )

    suspend fun announcement(id: String): Announcement? = remote.selectOne(
        "announcements",
        mapOf("id" to "eq.$id", "is_published" to "eq.true"),
        Announcement.serializer(),
    )

    suspend fun flags(): Map<String, Boolean> {
        val rows = remote.selectRaw("app_config", mapOf("select" to "key,value"))
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = obj["value"]?.toString()?.trim('"')?.toBooleanStrictOrNull() ?: false
            key to value
        }.toMap()
    }

    suspend fun wallet(): WalletAccount? = remote.selectOne(
        "wallet_accounts",
        mapOf("user_id" to "eq.${uid()}"),
        WalletAccount.serializer(),
    )

    suspend fun ledger(): List<LedgerEntry> = remote.select(
        "financial_ledger",
        mapOf("user_id" to "eq.${uid()}", "order" to "created_at.desc", "limit" to "50"),
        LedgerEntry.serializer(),
    )

    suspend fun submitSupportTicket(category: String, subject: String, body: String): SupportTicket = remote.rpc(
        "submit_support_ticket",
        buildJsonObject {
            put("p_category", category)
            put("p_subject", subject)
            put("p_body", body)
        },
        SupportTicket.serializer(),
    )

    suspend fun myTickets(): List<SupportTicket> = remote.select(
        "support_tickets",
        mapOf("user_id" to "eq.${uid()}", "order" to "created_at.desc"),
        SupportTicket.serializer(),
    )

    suspend fun supportTicket(id: String): SupportTicket? = remote.selectOne(
        "support_tickets",
        mapOf("id" to "eq.$id", "user_id" to "eq.${uid()}"),
        SupportTicket.serializer(),
    )

    suspend fun sendSupportMessage(ticketId: String, body: String) {
        remote.insert(
            "support_messages",
            buildJsonObject {
                put("ticket_id", ticketId)
                put("sender_id", uid())
                put("body", body)
            },
        )
    }

    suspend fun reportProblem(
        category: String,
        description: String,
        targetType: String = "user",
        targetId: String? = null,
    ) {
        remote.insert(
            "reports",
            buildJsonObject {
                put("reporter_id", uid())
                put("category", category)
                put("target_type", targetType)
                if (targetId != null) put("target_id", targetId)
                put("description", description)
            },
        )
    }

    suspend fun participants(tournamentId: String, limit: Int = 100): List<Participant> = remote.rpcList(
        "get_tournament_participants",
        buildJsonObject {
            put("p_tournament_id", tournamentId)
            put("p_limit", limit)
            put("p_offset", 0)
        },
        Participant.serializer(),
    )

    suspend fun requestAccountDeletion() {
        remote.rpcUnit("request_account_deletion")
    }

    suspend fun publicProfile(id: String): Profile? = remote.selectOne(
        "public_profiles",
        mapOf("id" to "eq.$id"),
        Profile.serializer(),
    )

    suspend fun searchPlayers(query: String): List<Profile> {
        val q = query.trim().replace("(", "").replace(")", "").replace(",", " ")
        if (q.isBlank()) return emptyList()
        return remote.select(
            "public_profiles",
            mapOf(
                "or" to "(username.ilike.*$q*,display_name.ilike.*$q*)",
                "order" to "points.desc",
                "limit" to "20",
            ),
            Profile.serializer(),
        )
    }

    suspend fun ticketMessages(ticketId: String): List<SupportMessage> = remote.select(
        "support_messages",
        mapOf("ticket_id" to "eq.$ticketId", "order" to "created_at.asc"),
        SupportMessage.serializer(),
    )

    suspend fun disputesForResult(resultId: String): List<Dispute> = remote.select(
        "result_disputes",
        mapOf("result_id" to "eq.$resultId", "order" to "created_at.desc"),
        Dispute.serializer(),
    )

    suspend fun myDisputes(): List<Dispute> = remote.select(
        "result_disputes",
        mapOf("raised_by" to "eq.${uid()}", "order" to "created_at.desc"),
        Dispute.serializer(),
    )

    suspend fun completeOnboarding() {
        remote.update(
            "profiles",
            mapOf("id" to "eq.${uid()}"),
            buildJsonObject { put("onboarding_completed", true) },
        )
    }

    private fun uid(): String =
        session.snapshotUserId() ?: throw ApiException("Please sign in again.")
}
