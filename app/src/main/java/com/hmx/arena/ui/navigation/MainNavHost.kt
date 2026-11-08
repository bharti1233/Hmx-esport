package com.hmx.arena.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hmx.arena.ui.admin.AdminAuditScreen
import com.hmx.arena.ui.admin.AdminDashboardScreen
import com.hmx.arena.ui.admin.AdminLookupScreen
import com.hmx.arena.ui.admin.AdminMatchEditScreen
import com.hmx.arena.ui.admin.AdminMatchesScreen
import com.hmx.arena.ui.admin.AdminTournamentEditScreen
import com.hmx.arena.ui.admin.AdminTournamentsScreen
import com.hmx.arena.ui.home.AnnouncementScreen
import com.hmx.arena.ui.home.AnnouncementsScreen
import com.hmx.arena.ui.home.HomeScreen
import com.hmx.arena.ui.home.NotificationsScreen
import com.hmx.arena.ui.home.PlayerProfileScreen
import com.hmx.arena.ui.home.SearchScreen
import com.hmx.arena.ui.leaderboard.LeaderboardScreen
import com.hmx.arena.ui.match.DisputeScreen
import com.hmx.arena.ui.match.MatchCredentialsScreen
import com.hmx.arena.ui.match.MatchDetailScreen
import com.hmx.arena.ui.match.MatchResultsScreen
import com.hmx.arena.ui.match.MatchesScreen
import com.hmx.arena.ui.match.MyMatchesScreen
import com.hmx.arena.ui.match.SubmitResultScreen
import com.hmx.arena.ui.onboarding.CompleteProfileScreen
import com.hmx.arena.ui.profile.EditProfileScreen
import com.hmx.arena.ui.profile.GameAccountsScreen
import com.hmx.arena.ui.profile.ProfileScreen
import com.hmx.arena.ui.referral.ReferralScreen
import com.hmx.arena.ui.settings.AboutScreen
import com.hmx.arena.ui.settings.AccountSettingsScreen
import com.hmx.arena.ui.settings.HelpScreen
import com.hmx.arena.ui.settings.LegalScreen
import com.hmx.arena.ui.settings.SettingsScreen
import com.hmx.arena.ui.settings.SupportScreen
import com.hmx.arena.ui.settings.TicketScreen
import com.hmx.arena.ui.team.CreateTeamScreen
import com.hmx.arena.ui.team.InvitationsScreen
import com.hmx.arena.ui.team.InvitePlayerScreen
import com.hmx.arena.ui.team.ManageTeamScreen
import com.hmx.arena.ui.team.TeamScreen
import com.hmx.arena.ui.tournament.MyRegistrationsScreen
import com.hmx.arena.ui.tournament.RegisterTournamentScreen
import com.hmx.arena.ui.tournament.TournamentDetailScreen
import com.hmx.arena.ui.tournament.TournamentLeaderboardScreen
import com.hmx.arena.ui.tournament.TournamentParticipantsScreen
import com.hmx.arena.ui.tournament.TournamentRulesScreen
import com.hmx.arena.ui.tournament.TournamentScheduleScreen
import com.hmx.arena.ui.tournament.TournamentsScreen
import com.hmx.arena.ui.wallet.TransactionsScreen
import com.hmx.arena.ui.wallet.WalletScreen

@Composable
fun MainNavHost(startDestination: String) {
    val nav = rememberNavController()
    MainGraph(nav = nav, startDestination = startDestination)
}

@Composable
private fun MainGraph(nav: NavHostController, startDestination: String) {
    NavHost(navController = nav, startDestination = startDestination) {
        tabDestinations(nav)
        accountDestinations(nav)
        tournamentDestinations(nav)
        matchDestinations(nav)
        teamDestinations(nav)
        adminDestinations(nav)
    }
}

private fun androidx.navigation.NavGraphBuilder.tabDestinations(nav: NavHostController) {
    composable(Routes.HOME) { HomeScreen(nav) }
    composable(Routes.TOURNAMENTS) { TournamentsScreen(nav) }
    composable(Routes.MATCHES) { MatchesScreen(nav) }
    composable(Routes.LEADERBOARD) { LeaderboardScreen(nav) }
    composable(Routes.PROFILE) { ProfileScreen(nav) }
}

private fun androidx.navigation.NavGraphBuilder.accountDestinations(nav: NavHostController) {
    composable(Routes.COMPLETE_PROFILE) {
        CompleteProfileScreen(
            onCompleted = {
                nav.navigate(Routes.HOME) {
                    popUpTo(nav.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
    composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
    composable(Routes.ANNOUNCEMENTS) { AnnouncementsScreen(nav) }
    composable(Routes.ANNOUNCEMENT) { entry ->
        AnnouncementScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.SEARCH) { SearchScreen(nav) }
    composable(Routes.PLAYER) { entry ->
        PlayerProfileScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.WALLET) { WalletScreen(nav) }
    composable(Routes.TRANSACTIONS) { TransactionsScreen(nav) }
    composable(Routes.REFERRAL) { ReferralScreen(nav) }
    composable(Routes.EDIT_PROFILE) { EditProfileScreen(nav) }
    composable(Routes.GAME_ACCOUNTS) { GameAccountsScreen(nav) }
    composable(Routes.MY_REGISTRATIONS) { MyRegistrationsScreen(nav) }
    composable(Routes.SETTINGS) { SettingsScreen(nav) }
    composable(Routes.ACCOUNT_SETTINGS) { AccountSettingsScreen(nav) }
    composable(Routes.SUPPORT) { SupportScreen(nav) }
    composable(Routes.SUPPORT_TICKET) { entry ->
        TicketScreen(nav, entry.arguments?.getString("ticketId").orEmpty())
    }
    composable(Routes.HELP) { HelpScreen(nav) }
    composable(Routes.ABOUT) { AboutScreen(nav) }
    composable(Routes.LEGAL) { entry ->
        LegalScreen(nav, entry.arguments?.getString("kind").orEmpty())
    }
}

private fun androidx.navigation.NavGraphBuilder.tournamentDestinations(nav: NavHostController) {
    composable(Routes.TOURNAMENT) { entry ->
        TournamentDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.TOURNAMENT_RULES) { entry ->
        TournamentRulesScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.TOURNAMENT_PARTICIPANTS) { entry ->
        TournamentParticipantsScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.TOURNAMENT_SCHEDULE) { entry ->
        TournamentScheduleScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.TOURNAMENT_REGISTER) { entry ->
        RegisterTournamentScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.TOURNAMENT_LEADERBOARD) { entry ->
        TournamentLeaderboardScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
}

private fun androidx.navigation.NavGraphBuilder.matchDestinations(nav: NavHostController) {
    composable(Routes.MY_MATCHES) { MyMatchesScreen(nav) }
    composable(Routes.MATCH) { entry ->
        MatchDetailScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.MATCH_CREDENTIALS) { entry ->
        MatchCredentialsScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.MATCH_RESULTS) { entry ->
        MatchResultsScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.MATCH_SUBMIT) { entry ->
        SubmitResultScreen(nav, entry.arguments?.getString("id").orEmpty())
    }
    composable(Routes.DISPUTE) { entry ->
        DisputeScreen(nav, entry.arguments?.getString("resultId").orEmpty())
    }
}

private fun androidx.navigation.NavGraphBuilder.adminDestinations(nav: NavHostController) {
    composable(Routes.ADMIN) { AdminDashboardScreen(nav) }
    composable(Routes.ADMIN_LOOKUP) { AdminLookupScreen(nav) }
    composable(Routes.ADMIN_TOURNAMENTS) { AdminTournamentsScreen(nav) }
    composable(Routes.ADMIN_TOURNAMENT_EDIT) { AdminTournamentEditScreen(nav) }
    composable(Routes.ADMIN_MATCHES) { AdminMatchesScreen(nav) }
    composable(Routes.ADMIN_MATCH_EDIT) { AdminMatchEditScreen(nav) }
    composable(Routes.ADMIN_AUDIT) { AdminAuditScreen(nav) }
}

private fun androidx.navigation.NavGraphBuilder.teamDestinations(nav: NavHostController) {
    composable(Routes.TEAM) { TeamScreen(nav) }
    composable(Routes.CREATE_TEAM) { CreateTeamScreen(nav) }
    composable(Routes.MANAGE_TEAM) { ManageTeamScreen(nav) }
    composable(Routes.INVITATIONS) { InvitationsScreen(nav) }
    composable(Routes.INVITE) { entry ->
        InvitePlayerScreen(nav, entry.arguments?.getString("teamId").orEmpty())
    }
}
