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
data class Tournament(
    val id: String,
    val slug: String,
    val title: String,
    val game: String = "free_fire",
    val mode: String = "squad",
    @SerialName("entry_type") val entryType: String = "free",
    @SerialName("entry_fee_paise") val entryFeePaise: Long = 0,
    @SerialName("prize_pool_paise") val prizePoolPaise: Long = 0,
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
    val description: String = "",
    val map: String? = null,
    val region: String = "IN",
    @SerialName("is_featured") val isFeatured: Boolean = false,
) {
    val modeLabel: String
        get() = when (mode) {
            "solo" -> "Solo"
            "duo" -> "Duo"
            "squad" -> "Squad"
            "clash_squad" -> "Clash Squad"
            else -> mode.replaceFirstChar { it.uppercase() }
        }

    val isFree: Boolean get() = entryType == "free" || entryFeePaise == 0L
    val slotsRemaining: Int get() = (maxSlots - slotsFilled).coerceAtLeast(0)
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
    @SerialName("tournament_id") val tournamentId: String,
    val round: Int = 1,
    val name: String = "Match",
    val status: String = "scheduled",
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("check_in_opens_at") val checkInOpensAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val map: String? = null,
    val mode: String? = null,
    @SerialName("max_teams") val maxTeams: Int? = null,
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
    @SerialName("published_at") val publishedAt: String? = null,
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
