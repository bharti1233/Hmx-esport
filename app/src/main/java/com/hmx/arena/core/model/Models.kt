package com.hmx.arena.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    val region: String = "IN",
    val points: Int = 0,
    @SerialName("matches_played") val matchesPlayed: Int = 0,
    val wins: Int = 0,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("referral_code") val referralCode: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("referred_by") val referredBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val display: String get() = displayName.ifBlank { username }
}

@Serializable
data class PlayerGameAccount(
    val id: String,
    @SerialName("user_id") val userId: String,
    val game: String = "free_fire",
    val ign: String,
    @SerialName("game_uid") val gameUid: String,
    val level: Int? = null,
    val verified: Boolean = false,
)

@Serializable
data class LookupOption(
    val id: String,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val available: Boolean get() = isActive && !isArchived

    /** Wide artwork when an admin uploaded it, otherwise the square badge. */
    val artworkUrl: String? get() = imageUrl?.takeIf { it.isNotBlank() } ?: logoUrl?.takeIf { it.isNotBlank() }
}

@Serializable
data class Tournament(
    val id: String,
    val slug: String,
    val title: String,
    val game: String = "free_fire",
    val mode: String = "squad",
    @SerialName("entry_type") val entryType: String = "free",
    @SerialName("entry_fee_paise") val entryFeePaise: Long = 0,
    @SerialName("prize_pool_paise") val prizePoolPaise: Long = 0,
    @SerialName("per_kill_paise") val perKillPaise: Long = 0,
    @SerialName("max_slots") val maxSlots: Int,
    @SerialName("slots_filled") val slotsFilled: Int = 0,
    @SerialName("min_team_size") val minTeamSize: Int = 1,
    @SerialName("max_team_size") val maxTeamSize: Int = 1,
    val status: String = "draft",
    @SerialName("registration_opens_at") val registrationOpensAt: String? = null,
    @SerialName("registration_closes_at") val registrationClosesAt: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val description: String = "",
    val map: String? = null,
    val region: String = "IN",
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("game_mode_id") val gameModeId: String? = null,
    @SerialName("tournament_type_id") val tournamentTypeId: String? = null,
    val version: String = "1.0",
    val format: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    val category: LookupOption? = null,
    @SerialName("gameMode") val gameMode: LookupOption? = null,
    @SerialName("tournamentType") val tournamentType: LookupOption? = null,
) {
    val modeLabel: String
        get() = gameMode?.name?.takeIf { it.isNotBlank() } ?: when (mode) {
            "solo" -> "Solo"
            "duo" -> "Duo"
            "squad" -> "Squad"
            "clash_squad" -> "Clash Squad"
            else -> mode.replaceFirstChar { it.uppercase() }
        }

    val categoryLabel: String get() = category?.name ?: "—"
    val tournamentTypeLabel: String get() = tournamentType?.name ?: if (isFree) "Free Entry" else "Paid Entry"
    val isFree: Boolean get() = entryType == "free" || entryFeePaise == 0L
    val slotsRemaining: Int get() = (maxSlots - slotsFilled).coerceAtLeast(0)
    val isFull: Boolean get() = slotsFilled >= maxSlots
}

@Serializable
data class TournamentRule(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    val position: Int,
    val title: String,
    val body: String,
)

@Serializable
data class Registration(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("registration_type") val registrationType: String = "solo",
    val status: String = "confirmed",
    @SerialName("slot_no") val slotNo: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Match(
    val id: String,
    @SerialName("tournament_id") val tournamentId: String? = null,
    val round: Int = 1,
    val name: String = "Match",
    @SerialName("match_number") val matchNumber: String? = null,
    val status: String = "scheduled",
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("check_in_opens_at") val checkInOpensAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val map: String? = null,
    val mode: String? = null,
    @SerialName("max_teams") val maxTeams: Int? = null,
    val game: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("prize_pool_paise") val prizePoolPaise: Long = 0,
    @SerialName("per_kill_paise") val perKillPaise: Long = 0,
    @SerialName("entry_fee_paise") val entryFeePaise: Long = 0,
    val version: String = "1.0",
    val format: String? = null,
    @SerialName("max_slots") val maxSlots: Int? = null,
    @SerialName("slots_filled") val slotsFilled: Int = 0,
    @SerialName("registration_opens_at") val registrationOpensAt: String? = null,
    @SerialName("registration_closes_at") val registrationClosesAt: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("game_mode_id") val gameModeId: String? = null,
    @SerialName("tournament_type_id") val tournamentTypeId: String? = null,
    val category: LookupOption? = null,
    @SerialName("gameMode") val gameMode: LookupOption? = null,
    @SerialName("tournamentType") val tournamentType: LookupOption? = null,
) {
    val slotsRemaining: Int get() = maxSlots?.let { (it - slotsFilled).coerceAtLeast(0) } ?: 0
    val isFull: Boolean get() = maxSlots != null && slotsFilled >= maxSlots
    val capacityKnown: Boolean get() = maxSlots != null
    val modeLabel: String get() = gameMode?.name ?: mode?.replaceFirstChar { it.uppercase() } ?: "—"
    val categoryLabel: String get() = category?.name ?: "—"
    val typeLabel: String get() = tournamentType?.name ?: if (entryFeePaise == 0L) "Free Entry" else "Paid Entry"
}

@Serializable
data class MatchRegistration(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("registration_type") val registrationType: String = "solo",
    val status: String = "confirmed",
    @SerialName("slot_no") val slotNo: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
)

@Serializable
data class AuditLog(
    val id: String,
    @SerialName("actor_id") val actorId: String? = null,
    val action: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String? = null,
    val metadata: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MatchCredentials(
    @SerialName("room_id") val roomId: String,
    @SerialName("room_password") val roomPassword: String,
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("visible_until") val visibleUntil: String? = null,
)

@Serializable
data class MatchResult(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("registration_id") val registrationId: String,
    @SerialName("submitted_by") val submittedBy: String,
    val placement: Int,
    val kills: Int = 0,
    val points: Int = 0,
    val status: String = "pending",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val tag: String,
    val game: String = "free_fire",
    @SerialName("owner_id") val ownerId: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    val region: String = "IN",
)

@Serializable
data class TeamMember(
    @SerialName("team_id") val teamId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    @SerialName("joined_at") val joinedAt: String? = null,
)

@Serializable
data class TeamInvitation(
    val id: String,
    @SerialName("team_id") val teamId: String,
    @SerialName("invited_user_id") val invitedUserId: String,
    @SerialName("invited_by") val invitedBy: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class LeaderboardRow(
    @SerialName("rank_no") val rankNo: Long,
    @SerialName("entity_id") val entityId: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val subtitle: String? = null,
    val points: Long = 0,
    val matches: Long = 0,
    val wins: Long = 0,
)

@Serializable
data class NotificationItem(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String = "system",
    val title: String,
    val body: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val unread: Boolean get() = readAt == null
}

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * Admin-managed Home carousel record. Public reads only return active rows that
 * are inside their optional start/end window; the Android client never invents
 * a banner.
 */
@Serializable
data class PromotionalBanner(
    val id: String,
    val title: String,
    val subtitle: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    val destination: String? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Participant(
    @SerialName("registration_id") val registrationId: String,
    @SerialName("team_id") val teamId: String? = null,
    @SerialName("team_name") val teamName: String? = null,
    @SerialName("team_tag") val teamTag: String? = null,
    val ign: String,
    @SerialName("is_captain") val isCaptain: Boolean = false,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class WalletAccount(
    @SerialName("user_id") val userId: String,
    @SerialName("balance_paise") val balancePaise: Long = 0,
    @SerialName("reserved_paise") val reservedPaise: Long = 0,
    val currency: String = "INR",
)

/**
 * Server-side lifetime totals from `my_wallet_totals()`. These are computed
 * over the full immutable ledger, unlike the 50-row activity list.
 */
@Serializable
data class WalletTotals(
    @SerialName("balance_paise") val balancePaise: Long = 0,
    @SerialName("reserved_paise") val reservedPaise: Long = 0,
    val currency: String = "INR",
    @SerialName("deposited_paise") val depositedPaise: Long = 0,
    @SerialName("winning_paise") val winningPaise: Long = 0,
    @SerialName("earnings_paise") val earningsPaise: Long = 0,
    @SerialName("payouts_paise") val payoutsPaise: Long = 0,
    @SerialName("entry_count") val entryCount: Long = 0,
)

@Serializable
data class LedgerEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("entry_type") val entryType: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("balance_after_paise") val balanceAfterPaise: Long,
    @SerialName("reference_type") val referenceType: String? = null,
    @SerialName("reference_id") val referenceId: String? = null,
    val description: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReferralClaim(
    val id: String,
    @SerialName("referrer_id") val referrerId: String,
    @SerialName("referred_id") val referredId: String,
    val code: String,
    val status: String = "pending",
    @SerialName("reward_points") val rewardPoints: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SupportTicket(
    val id: String,
    @SerialName("user_id") val userId: String,
    val category: String,
    val subject: String,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AppFlag(val key: String, val value: String)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUser? = null,
)

@Serializable
data class AuthError(val message: String = "Request failed", val code: String? = null, @SerialName("error_description") val description: String? = null)

@Serializable
data class SupportMessage(
    val id: String,
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Dispute(
    val id: String,
    @SerialName("result_id") val resultId: String,
    @SerialName("raised_by") val raisedBy: String,
    val reason: String,
    val status: String = "open",
    val resolution: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
)
