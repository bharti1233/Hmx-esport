package com.hmx.arena.ui.navigation

/**
 * Single source of truth for every navigation destination. Keeping routes in
 * one object prevents the "route drift" that produces dead buttons.
 */
object Routes {
    const val HOME = "home"
    const val TOURNAMENTS = "tournaments"
    const val MATCHES = "matches"
    const val LEADERBOARD = "leaderboard"
    const val PROFILE = "profile"

    const val NOTIFICATIONS = "notifications"
    const val ANNOUNCEMENTS = "announcements"
    const val SEARCH = "search"
    const val HELP = "help"
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "account-settings"
    const val SUPPORT = "support"
    const val ABOUT = "about"
    const val SUPPORT_TICKET = "support/ticket/{ticketId}"
    const val WALLET = "wallet"
    const val TRANSACTIONS = "transactions"
    const val REFERRAL = "referral"
    const val EDIT_PROFILE = "edit-profile"
    const val GAME_ACCOUNTS = "game-accounts"
    const val COMPLETE_PROFILE = "complete-profile"
    const val MY_REGISTRATIONS = "my-registrations"
    const val TEAM = "team"
    const val CREATE_TEAM = "create-team"
    const val MANAGE_TEAM = "manage-team"
    const val INVITATIONS = "invitations"

    const val TOURNAMENT = "tournament/{id}"
    const val TOURNAMENT_RULES = "tournament/{id}/rules"
    const val TOURNAMENT_PARTICIPANTS = "tournament/{id}/participants"
    const val TOURNAMENT_SCHEDULE = "tournament/{id}/schedule"
    const val TOURNAMENT_REGISTER = "tournament/{id}/register"
    const val TOURNAMENT_LEADERBOARD = "tournament/{id}/leaderboard"
    const val ANNOUNCEMENT = "announcement/{id}"
    const val PLAYER = "player/{id}"
    const val MATCH = "match/{id}"
    const val MATCH_CREDENTIALS = "match/{id}/credentials"
    const val MATCH_RESULTS = "match/{id}/results"
    const val MATCH_SUBMIT = "match/{id}/submit"
    const val DISPUTE = "dispute/{resultId}"
    const val INVITE = "team/{teamId}/invite"
    const val LEGAL = "legal/{kind}"

    fun tournament(id: String) = "tournament/$id"
    fun tournamentRules(id: String) = "tournament/$id/rules"
    fun tournamentParticipants(id: String) = "tournament/$id/participants"
    fun tournamentSchedule(id: String) = "tournament/$id/schedule"
    fun tournamentRegister(id: String) = "tournament/$id/register"
    fun tournamentLeaderboard(id: String) = "tournament/$id/leaderboard"
    fun announcement(id: String) = "announcement/$id"
    fun player(id: String) = "player/$id"
    fun match(id: String) = "match/$id"
    fun matchCredentials(id: String) = "match/$id/credentials"
    fun matchResults(id: String) = "match/$id/results"
    fun matchSubmit(id: String) = "match/$id/submit"
    fun dispute(resultId: String) = "dispute/$resultId"
    fun invite(teamId: String) = "team/$teamId/invite"
    fun supportTicket(ticketId: String) = "support/ticket/$ticketId"
    fun legal(kind: String) = "legal/$kind"

    val tabs = setOf(HOME, TOURNAMENTS, MATCHES, LEADERBOARD, PROFILE)
}
