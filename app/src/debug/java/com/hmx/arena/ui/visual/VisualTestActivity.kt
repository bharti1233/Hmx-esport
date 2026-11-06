package com.hmx.arena.ui.visual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassScaffold
import com.hmx.arena.core.designsystem.GlassScreenBackground
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassStatCell
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxBrandMark
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxGlassHost
import com.hmx.arena.core.designsystem.HmxGlassLevel
import com.hmx.arena.core.designsystem.HmxLiquidGlassSurface
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.designsystem.HmxTheme
import com.hmx.arena.core.designsystem.HmxThemeMode
import com.hmx.arena.core.designsystem.ThemeViewModel
import com.hmx.arena.core.model.LeaderboardRow
import com.hmx.arena.core.model.Match
import com.hmx.arena.core.model.Profile
import com.hmx.arena.core.model.Tournament
import com.hmx.arena.ui.common.GlassActionRow
import com.hmx.arena.ui.common.GlassPasswordField
import com.hmx.arena.ui.common.GlassTextField
import com.hmx.arena.ui.common.HmxLiquidGlassBottomSheet
import com.hmx.arena.ui.common.HmxLiquidGlassButton
import com.hmx.arena.ui.common.HmxLiquidGlassCard
import com.hmx.arena.ui.common.HmxLiquidGlassChip
import com.hmx.arena.ui.common.HmxLiquidGlassDialog
import com.hmx.arena.ui.common.HmxLiquidGlassIconButton
import com.hmx.arena.ui.common.HmxLiquidGlassLeaderboardItem
import com.hmx.arena.ui.common.HmxLiquidGlassMatchCard
import com.hmx.arena.ui.common.HmxLiquidGlassOutlinedButton
import com.hmx.arena.ui.common.HmxLiquidGlassSearchBar
import com.hmx.arena.ui.common.HmxLiquidGlassSegmentedControl
import com.hmx.arena.ui.common.HmxLiquidGlassStatCard
import com.hmx.arena.ui.common.HmxLiquidGlassThemeSelector
import com.hmx.arena.ui.common.HmxLiquidGlassTopBar
import com.hmx.arena.ui.common.HmxLiquidGlassTournamentCard
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.shell.HmxLiquidGlassBottomBar
import dagger.hilt.android.AndroidEntryPoint

/**
 * Debug-only visual harness for the GitHub Actions emulator workflow.
 *
 * Intent extras:
 *  - `screen`: `components` (default), `signin`, `home`, `arena`, `tournament`,
 *    `matches`, `leaderboard`, `profile`, `settings`.
 *  - `theme`: `light`, `dark` or `system`.
 *  - `state`: `main`, `sheet`, `dialog` (components screen only).
 *  - `scroll`: first visible list index (components screen only).
 *
 * The screen showcase renders the production design-system components with
 * representative HMX content. It is deterministic and needs no backend session,
 * which is what makes the Light/Dark matrix reproducible in CI.
 */
@AndroidEntryPoint
class VisualTestActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        when (intent.getStringExtra("theme")?.lowercase()) {
            "light" -> themeViewModel.setMode(HmxThemeMode.LIGHT)
            "dark" -> themeViewModel.setMode(HmxThemeMode.DARK)
            "system" -> themeViewModel.setMode(HmxThemeMode.SYSTEM)
        }
        val screen = intent.getStringExtra("screen") ?: "components"
        val initialState = intent.getStringExtra("state") ?: "main"
        val initialScroll = intent.getIntExtra("scroll", 0)

        setContent {
            val mode by themeViewModel.mode.collectAsStateWithLifecycle()
            val resolvedMode = mode ?: HmxThemeMode.SYSTEM
            val systemDark = isSystemInDarkTheme()
            val dark = when (resolvedMode) {
                HmxThemeMode.LIGHT -> false
                HmxThemeMode.DARK -> true
                HmxThemeMode.SYSTEM -> systemDark
            }
            // The software-rendered emulator needs several seconds for the first
            // blurred frame. Drawing a cheap placeholder first lets the window
            // take focus before that long frame, otherwise Android reports an
            // input-dispatch ANR and kills the process mid-capture.
            var contentReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                contentReady = true
            }
            if (!contentReady) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (dark) Color(0xFF0B0B0C) else Color(0xFFFDFDFE)),
                )
            } else {
                HmxTheme(
                    mode = resolvedMode,
                    onModeChange = themeViewModel::setMode,
                ) {
                    HmxGlassHost {
                        val isDark = HmxColor.Ink.luminance() < 0.5f
                        val toggle = {
                            themeViewModel.setMode(
                                if (isDark) HmxThemeMode.LIGHT else HmxThemeMode.DARK,
                            )
                        }
                        when (screen) {
                            "signin" -> SignInShowcase(onToggleTheme = toggle)
                            "home" -> HomeShowcase(onToggleTheme = toggle)
                            "arena" -> ArenaShowcase(onToggleTheme = toggle)
                            "tournament" -> TournamentDetailShowcase(onToggleTheme = toggle)
                            "matches" -> MatchesShowcase(onToggleTheme = toggle)
                            "leaderboard" -> LeaderboardShowcase(onToggleTheme = toggle)
                            "profile" -> ProfileShowcase(onToggleTheme = toggle)
                            "settings" -> SettingsShowcase(
                                mode = mode ?: HmxThemeMode.SYSTEM,
                                onModeChange = themeViewModel::setMode,
                            )
                            else -> VisualDemo(
                                initialState = initialState,
                                initialScroll = initialScroll,
                                onToggleTheme = toggle,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

// ---------------------------------------------------------------------------
// Demo data
// ---------------------------------------------------------------------------

private val demoTournament = Tournament(
    id = "t-weekend",
    slug = "weekend-clash",
    title = "Weekend Clash · Squad Series",
    mode = "squad",
    prizePoolPaise = 2500000,
    maxSlots = 100,
    slotsFilled = 74,
    status = "registration_open",
    startsAt = "2026-11-07T18:30:00Z",
    map = "Bermuda",
    description = "Four rounds, verified results and a paid practice lobby.",
)

private val liveTournament = Tournament(
    id = "t-live",
    slug = "inferno-cup",
    title = "Inferno Cup · Duo",
    mode = "duo",
    prizePoolPaise = 1200000,
    maxSlots = 64,
    slotsFilled = 64,
    status = "live",
    startsAt = "2026-11-05T14:00:00Z",
    map = "Purgatory",
)

private val nextTournament = Tournament(
    id = "t-clash",
    slug = "clash-open",
    title = "Clash Squad Open",
    mode = "clash_squad",
    maxSlots = 48,
    slotsFilled = 21,
    status = "registration_open",
    startsAt = "2026-11-09T17:00:00Z",
    map = "Bermuda Remastered",
)

private val demoMatch = Match(
    id = "m-1",
    tournamentId = "t-weekend",
    round = 2,
    name = "Round 2 · Bermuda",
    status = "scheduled",
    scheduledAt = "2026-11-05T18:30:00Z",
    map = "Bermuda",
    mode = "squad",
)

private val demoRows = listOf(
    LeaderboardRow(1, "p-1", "Shadowstrike", points = 4820, matches = 24, wins = 11),
    LeaderboardRow(2, "p-2", "NovaRush", points = 4510, matches = 23, wins = 9),
    LeaderboardRow(3, "p-3", "IronVeil", points = 4275, matches = 25, wins = 8),
    LeaderboardRow(4, "p-4", "KiteRunner", points = 3980, matches = 22, wins = 7),
    LeaderboardRow(5, "p-5", "GhostPine", points = 3640, matches = 21, wins = 6),
)

private val demoProfile = Profile(
    id = "p-1",
    username = "shadowstrike",
    displayName = "Shadowstrike",
    region = "IN",
    points = 4820,
    matchesPlayed = 24,
    wins = 11,
    onboardingCompleted = true,
)

// ---------------------------------------------------------------------------
// Reusable showcase chrome
// ---------------------------------------------------------------------------

@Composable
private fun ThemeToggle(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    GlassIconButton(
        icon = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
        // The state is part of the description so the screenshot workflow can
        // prove a switch actually happened from the UI dump, not just a colour.
        contentDescription = if (isDark) "Switch theme (currently dark)" else "Switch theme (currently light)",
        onClick = onToggleTheme,
    )
}

@Composable
private fun ShowcaseTopBar(
    title: String,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: (() -> Unit)? = null,
    showBrandMark: Boolean = false,
) {
    HmxLiquidGlassTopBar(
        title = title,
        onBack = onBack,
        leading = if (showBrandMark) {
            { HmxBrandMark() }
        } else {
            null
        },
        actions = {
            ThemeToggle(isDark = isDark, onToggleTheme = onToggleTheme)
        },
    )
}

@Composable
private fun ShowcaseBottomBar(route: String) {
    HmxLiquidGlassBottomBar(selectedRoute = route, onSelect = {})
}

@Composable
private fun BrandRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(HmxShapes.small)
                .background(HmxColor.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "H",
                color = HmxColor.OnInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "HMX ARENA",
            style = MaterialTheme.typography.labelMedium,
            color = HmxColor.Ink,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun Greeting(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = HmxColor.Ink)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
    }
}

// ---------------------------------------------------------------------------
// Sign in
// ---------------------------------------------------------------------------

@Composable
private fun SignInShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    var email by remember { mutableStateOf("shadowstrike@hmx.gg") }
    var password by remember { mutableStateOf("hmxarena") }
    Box(Modifier.fillMaxSize()) {
        GlassScreenBackground(force = true)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandRow()
                Spacer(Modifier.weight(1f))
                ThemeToggle(isDark = isDark, onToggleTheme = onToggleTheme)
            }
            Spacer(Modifier.height(44.dp))
            Text("Welcome back.", style = MaterialTheme.typography.displayMedium, color = HmxColor.Ink)
            Spacer(Modifier.height(10.dp))
            Text(
                "Sign in to register for tournaments, submit results and track your rank.",
                style = MaterialTheme.typography.bodyMedium,
                color = HmxColor.InkSecondary,
            )
            Spacer(Modifier.height(26.dp))
            HmxLiquidGlassCard(refractive = true) {
                GlassTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(12.dp))
                GlassPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Forgot password?",
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.InkSecondary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(HmxShapes.pill)
                        .clickable(role = Role.Button, onClick = {})
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                HmxLiquidGlassButton(
                    text = "Sign in",
                    onClick = {},
                    variant = GlassButtonVariant.Ink,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "New to HMX Arena? ",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Text(
                        "Create account",
                        style = MaterialTheme.typography.labelMedium,
                        color = HmxColor.Ink,
                        modifier = Modifier
                            .clip(HmxShapes.pill)
                            .clickable(role = Role.Button, onClick = {})
                            .padding(horizontal = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Fair play first")
                StatusPill("Free to play")
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "By continuing you accept the Terms and Privacy Policy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

@Composable
private fun HomeShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(
                title = "HMX Arena",
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                showBrandMark = true,
            )
        },
        bottomBar = { ShowcaseBottomBar("home") },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Greeting(
                    title = "Good evening, Shadowstrike.",
                    subtitle = "Two registrations are open and your next match starts in 3 hours.",
                )
            }
            item {
                HmxLiquidGlassCard(refractive = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill("Featured", filled = true)
                        Spacer(Modifier.width(8.dp))
                        StatusPill("Squad")
                        Spacer(Modifier.weight(1f))
                        Text("LIVE", style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Weekend Clash",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HmxColor.Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "₹25,000 prize pool · Bermuda · 4 rounds",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    HmxLiquidGlassButton(
                        text = "View tournament",
                        onClick = {},
                        variant = GlassButtonVariant.Ink,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmxLiquidGlassStatCard(
                        label = "Points",
                        value = "4,820",
                        caption = "Season",
                        modifier = Modifier.weight(1f),
                    )
                    HmxLiquidGlassStatCard(
                        label = "Wins",
                        value = "11",
                        caption = "24 matches",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { GlassSectionHeader("Live now") }
            item {
                HmxLiquidGlassTournamentCard(liveTournament, onClick = {})
            }
            item { GlassSectionHeader("Your next match") }
            item {
                HmxLiquidGlassMatchCard(
                    match = demoMatch,
                    tournamentTitle = demoTournament.title,
                    onClick = {},
                )
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("You moved up 6 places", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Text("Rank #42 in the weekly solo ladder.", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = HmxColor.InkTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Arena
// ---------------------------------------------------------------------------

@Composable
private fun ArenaShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Live", "Upcoming", "Free entry")
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(title = "Arena", isDark = isDark, onToggleTheme = onToggleTheme)
        },
        bottomBar = { ShowcaseBottomBar("tournaments") },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Greeting(
                    title = "Find your next lobby.",
                    subtitle = "Verified Free Fire tournaments with transparent slots and room release.",
                )
            }
            item {
                HmxLiquidGlassSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search tournaments, maps or modes",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEachIndexed { index, label ->
                        HmxLiquidGlassChip(
                            text = label,
                            selected = chip == index,
                            onClick = { chip = index },
                        )
                    }
                }
            }
            item { GlassSectionHeader("Registration open") }
            item { HmxLiquidGlassTournamentCard(demoTournament, onClick = {}) }
            item { HmxLiquidGlassTournamentCard(nextTournament, onClick = {}) }
            item { GlassSectionHeader("Live") }
            item { HmxLiquidGlassTournamentCard(liveTournament, onClick = {}) }
        }
    }
}

// ---------------------------------------------------------------------------
// Tournament details
// ---------------------------------------------------------------------------

@Composable
private fun TournamentDetailShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(
                title = "Tournament",
                isDark = isDark,
                onToggleTheme = onToggleTheme,
                onBack = {},
            )
        },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HmxLiquidGlassCard(refractive = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill("Registration open", filled = true)
                        Spacer(Modifier.width(8.dp))
                        StatusPill("Squad")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        demoTournament.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = HmxColor.Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Four rounds · Bermuda · 7 Nov, 6:30 PM IST",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row {
                        GlassStatCell("Prize pool", "₹25,000", Modifier.weight(1f))
                        GlassStatCell("Slots", "74/100", Modifier.weight(1f))
                        GlassStatCell("Entry", "Free", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    HmxLiquidGlassButton(
                        text = "Register now",
                        onClick = {},
                        variant = GlassButtonVariant.Ink,
                    )
                    Spacer(Modifier.height(10.dp))
                    HmxLiquidGlassOutlinedButton(text = "Share tournament", onClick = {})
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Format", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                        Spacer(Modifier.height(10.dp))
                        DetailRow("Mode", "Squad · 4 players")
                        DetailRow("Rounds", "4")
                        DetailRow("Map pool", "Bermuda, Purgatory")
                        DetailRow("Check-in", "30 minutes before round 1")
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Schedule", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                        Spacer(Modifier.height(10.dp))
                        DetailRow("Registration closes", "7 Nov, 5:00 PM")
                        DetailRow("Round 1", "7 Nov, 6:30 PM")
                        DetailRow("Room release", "15 minutes before each round")
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Rules", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "One account per player. Emulators are not permitted. Submit a full-result screenshot within 10 minutes of each round. Staff decisions are final.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.InkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = HmxColor.Ink)
    }
}

// ---------------------------------------------------------------------------
// Matches
// ---------------------------------------------------------------------------

@Composable
private fun MatchesShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    var chip by remember { mutableIntStateOf(0) }
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(title = "Matches", isDark = isDark, onToggleTheme = onToggleTheme)
        },
        bottomBar = { ShowcaseBottomBar("matches") },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Greeting(
                    title = "Your matches.",
                    subtitle = "Room credentials unlock for registered players 15 minutes before each round.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Upcoming", "Live", "Completed").forEachIndexed { index, label ->
                        HmxLiquidGlassChip(
                            text = label,
                            selected = chip == index,
                            onClick = { chip = index },
                        )
                    }
                }
            }
            item {
                HmxLiquidGlassMatchCard(
                    match = demoMatch,
                    tournamentTitle = demoTournament.title,
                    onClick = {},
                )
            }
            item {
                HmxLiquidGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Room credentials", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Text("Unlock at 6:15 PM · 15 minutes before round 2", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                        }
                        StatusPill("Locked")
                    }
                    Spacer(Modifier.height(14.dp))
                    HmxLiquidGlassOutlinedButton(text = "View match details", onClick = {})
                }
            }
            item { GlassSectionHeader("Recent result") }
            item {
                HmxLiquidGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Round 1 · Purgatory", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                            Text("3rd place · 11 kills · 27 points", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                        }
                        StatusPill("Verified", filled = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HmxLiquidGlassChip(text = "Evidence", selected = false, onClick = {})
                        HmxLiquidGlassChip(text = "Dispute", selected = false, onClick = {})
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Leaderboard
// ---------------------------------------------------------------------------

@Composable
private fun LeaderboardShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    var entity by remember { mutableIntStateOf(0) }
    var period by remember { mutableIntStateOf(1) }
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(title = "Leaderboard", isDark = isDark, onToggleTheme = onToggleTheme)
        },
        bottomBar = { ShowcaseBottomBar("leaderboard") },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Greeting(
                    title = "The grind, ranked.",
                    subtitle = "Points come from verified placements and kills. Disputes are excluded until resolved.",
                )
            }
            item {
                HmxLiquidGlassSegmentedControl(
                    options = listOf("Players", "Teams"),
                    selectedIndex = entity,
                    onSelect = { entity = it },
                    icons = listOf(Icons.Rounded.Person, Icons.Rounded.Group),
                    contentDescription = "Leaderboard entity",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("This week", "Season", "All time").forEachIndexed { index, label ->
                        HmxLiquidGlassChip(
                            text = label,
                            selected = period == index,
                            onClick = { period = index },
                        )
                    }
                }
            }
            item {
                HmxLiquidGlassCard(refractive = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(HmxColor.Ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("1", style = MaterialTheme.typography.titleMedium, color = HmxColor.OnInk)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Shadowstrike", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                            Text("24 matches · 11 wins · 46% win rate", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("4,820", style = MaterialTheme.typography.titleLarge, color = HmxColor.Ink)
                            Text("points", style = MaterialTheme.typography.labelSmall, color = HmxColor.InkTertiary)
                        }
                    }
                }
            }
            items(demoRows.size) { index ->
                HmxLiquidGlassLeaderboardItem(row = demoRows[index], onClick = {})
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

@Composable
private fun ProfileShowcase(onToggleTheme: () -> Unit) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    GlassScaffold(
        topBar = {
            ShowcaseTopBar(title = "Profile", isDark = isDark, onToggleTheme = onToggleTheme)
        },
        bottomBar = { ShowcaseBottomBar("profile") },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HmxLiquidGlassCard(refractive = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(HmxColor.Ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "SS",
                                style = MaterialTheme.typography.titleLarge,
                                color = HmxColor.OnInk,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(demoProfile.display, style = MaterialTheme.typography.headlineMedium, color = HmxColor.Ink)
                            Text("@${demoProfile.username} · ${demoProfile.region}", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill("Rank #42", filled = true)
                                StatusPill("Verified")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmxLiquidGlassStatCard(label = "Matches", value = "24", modifier = Modifier.weight(1f))
                    HmxLiquidGlassStatCard(label = "Wins", value = "11", modifier = Modifier.weight(1f))
                    HmxLiquidGlassStatCard(label = "Points", value = "4,820", modifier = Modifier.weight(1f))
                }
            }
            item { GlassSectionHeader("Game account") }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = HmxColor.Ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Free Fire · SHADOW", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                                Text("UID 1842 5521 · Level 61", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkSecondary)
                            }
                            StatusPill("Linked", filled = true)
                        }
                        Spacer(Modifier.height(14.dp))
                        HmxLiquidGlassOutlinedButton(
                            text = "Add game account",
                            onClick = {},
                            leadingIcon = Icons.Rounded.AddCircle,
                        )
                    }
                }
            }
            item { GlassSectionHeader("Team") }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(HmxShapes.small)
                                    .background(HmxColor.Ink),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("NV", style = MaterialTheme.typography.labelLarge, color = HmxColor.OnInk)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Nova Vanguard", style = MaterialTheme.typography.titleMedium, color = HmxColor.Ink)
                                Text("NOVA · 4 members · IN", style = MaterialTheme.typography.bodySmall, color = HmxColor.InkTertiary)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = HmxColor.InkTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Wallet",
                            subtitle = "Balance and transaction history",
                            icon = Icons.Rounded.AccountBalanceWallet,
                            onClick = {},
                        )
                        GlassActionRow(
                            title = "Referrals",
                            subtitle = "Invite players and earn points",
                            icon = Icons.Rounded.Group,
                            onClick = {},
                        )
                        GlassActionRow(
                            title = "Support",
                            subtitle = "Tickets and help centre",
                            icon = Icons.Rounded.SupportAgent,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

@Composable
private fun SettingsShowcase(
    mode: HmxThemeMode,
    onModeChange: (HmxThemeMode) -> Unit,
) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    GlassScaffold(
        topBar = {
            HmxLiquidGlassTopBar(
                title = "Settings",
                onBack = {},
                actions = {
                    ThemeToggle(
                        isDark = isDark,
                        onToggleTheme = {
                            onModeChange(if (isDark) HmxThemeMode.LIGHT else HmxThemeMode.DARK)
                        },
                    )
                },
            )
        },
    ) { _ ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HmxLiquidGlassThemeSelector(
                    mode = mode,
                    onModeChange = onModeChange,
                )
            }
            item {
                GlassSectionHeader("Account")
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Account settings",
                            subtitle = "Signed in as shadowstrike@hmx.gg",
                            icon = Icons.Rounded.Person,
                            onClick = {},
                        )
                        GlassActionRow(
                            title = "In-app notifications",
                            subtitle = "Match and result alerts",
                            icon = Icons.Rounded.Notifications,
                            onClick = {},
                        )
                    }
                }
            }
            item {
                GlassSectionHeader("Help")
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        GlassActionRow(
                            title = "Support tickets",
                            subtitle = "Contact the HMX team",
                            icon = Icons.Rounded.SupportAgent,
                            onClick = {},
                        )
                        GlassActionRow(
                            title = "About HMX Arena",
                            subtitle = "Version 1.0.0",
                            icon = Icons.Rounded.Info,
                            onClick = {},
                        )
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = {})
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null, tint = HmxColor.Danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Sign out", style = MaterialTheme.typography.bodyLarge, color = HmxColor.Danger)
                    }
                }
            }
            item {
                Text(
                    "HMX Arena 1.0.0 · Liquid Glass",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Component demo (existing)
// ---------------------------------------------------------------------------

@Composable
private fun VisualDemo(
    initialState: String,
    initialScroll: Int,
    onToggleTheme: () -> Unit,
) {
    val isDark = HmxColor.Ink.luminance() < 0.5f
    var selectedTab by remember { mutableStateOf("home") }
    var sheetVisible by remember { mutableStateOf(initialState == "sheet") }
    var dialogVisible by remember { mutableStateOf(initialState == "dialog") }
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScroll)

    GlassScaffold(
        topBar = {
            HmxLiquidGlassTopBar(
                title = "Liquid Glass",
                actions = {
                    ThemeToggle(isDark = isDark, onToggleTheme = onToggleTheme)
                    Spacer(Modifier.width(8.dp))
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Notifications,
                        contentDescription = "Notifications",
                        onClick = {},
                    )
                },
            )
        },
        bottomBar = {
            HmxLiquidGlassBottomBar(
                selectedRoute = selectedTab,
                onSelect = { selectedTab = it },
            )
        },
    ) { _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Hero() }
            item { RefractionTarget() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmxLiquidGlassButton(
                        text = "Primary",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    HmxLiquidGlassOutlinedButton(
                        text = "Secondary",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search",
                        onClick = {},
                    )
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Star,
                        contentDescription = "Favourite",
                        onClick = {},
                    )
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.EmojiEvents,
                        contentDescription = "Trophy",
                        onClick = {},
                    )
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Person,
                        contentDescription = "Profile",
                        onClick = {},
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HmxLiquidGlassButton(
                        text = "Open sheet",
                        onClick = { sheetVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                    HmxLiquidGlassOutlinedButton(
                        text = "Open dialog",
                        onClick = { dialogVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Live", "Upcoming", "Finished").forEachIndexed { index, label ->
                        HmxLiquidGlassChip(
                            text = label,
                            selected = chip == index,
                            onClick = { chip = index },
                        )
                    }
                }
            }
            item {
                HmxLiquidGlassSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search tournaments",
                )
            }
            items(6) { index ->
                HmxLiquidGlassCard(onClick = { dialogVisible = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(HmxColor.Ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = HmxColor.OnInk,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Glass card ${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = HmxColor.Ink,
                            )
                            Text(
                                "Backdrop sampled through the surface",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                        HmxLiquidGlassChip(
                            text = "Open",
                            selected = false,
                            onClick = { dialogVisible = true },
                        )
                    }
                }
            }
        }
    }

    HmxLiquidGlassBottomSheet(
        visible = sheetVisible,
        onDismiss = { sheetVisible = false },
        title = "Glass bottom sheet",
    ) {
        Text(
            "The sheet is a real backdrop surface. Its blur and lens sample the " +
                "decorative layer behind it, while the highlight traces the top edge.",
            style = MaterialTheme.typography.bodyMedium,
            color = HmxColor.InkSecondary,
        )
        Spacer(Modifier.height(16.dp))
        HmxLiquidGlassButton(text = "Continue", onClick = { sheetVisible = false })
    }

    HmxLiquidGlassDialog(
        visible = dialogVisible,
        onDismiss = { dialogVisible = false },
        title = "Glass dialog",
        message = "Dialogs use the Chrome glass level: stronger blur, a specular " +
            "top lip, an outer drop shadow and a soft inner shadow.",
        confirmLabel = "Done",
        onConfirm = { dialogVisible = false },
    )
}

@Composable
private fun Hero() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF111113), Color(0xFF333338), Color(0xFF0B0B0C)),
                ),
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                "HMX",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
            )
            Text(
                "LIQUID GLASS",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 3.sp,
            )
        }
        HmxLiquidGlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = HmxShapes.pill,
            level = HmxGlassLevel.Chrome,
            fill = HmxColor.GlassChrome,
            elevation = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.Ink,
            )
        }
    }
}

/**
 * Horizontal black/grey stripes are the best possible proof of refraction: if
 * blur and lens are not running, the stripes stay razor sharp through the
 * floating bar; if they are, the bar bends and softens them.
 */
@Composable
private fun RefractionTarget() {
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0B0B0C)),
    ) {
        repeat(10) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (index % 2 == 0) Color(0xFF0B0B0C) else Color(0xFFD8D8DE)),
            )
        }
    }
}
