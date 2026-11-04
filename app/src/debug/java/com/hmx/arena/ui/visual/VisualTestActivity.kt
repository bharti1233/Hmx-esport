package com.hmx.arena.ui.visual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmx.arena.core.designsystem.GlassScaffold
import com.hmx.arena.core.designsystem.HMXArenaTheme
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxGlassHost
import com.hmx.arena.core.designsystem.HmxLiquidGlassSurface
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.ui.common.HmxLiquidGlassBottomSheet
import com.hmx.arena.ui.common.HmxLiquidGlassButton
import com.hmx.arena.ui.common.HmxLiquidGlassCard
import com.hmx.arena.ui.common.HmxLiquidGlassChip
import com.hmx.arena.ui.common.HmxLiquidGlassDialog
import com.hmx.arena.ui.common.HmxLiquidGlassIconButton
import com.hmx.arena.ui.common.HmxLiquidGlassOutlinedButton
import com.hmx.arena.ui.common.HmxLiquidGlassSearchBar
import com.hmx.arena.ui.common.HmxLiquidGlassTopBar
import com.hmx.arena.ui.shell.HmxLiquidGlassBottomBar

/**
 * Debug-only visual harness. It renders every HMX Liquid Glass primitive over a
 * deliberately high-contrast monochrome page so real backdrop blur, lensing and
 * highlights can be captured on an emulator without signing in.
 *
 * Launch states (intent extra `state`): `main`, `sheet`, `dialog`.
 * Intent extra `scroll` scrolls the list to expose content under the bar.
 */
class VisualTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.getStringExtra("state") ?: "main"
        val initialScroll = intent.getIntExtra("scroll", 0)
        setContent {
            HMXArenaTheme {
                HmxGlassHost {
                    VisualDemo(initialState = initial, initialScroll = initialScroll)
                }
            }
        }
    }
}

@Composable
private fun VisualDemo(initialState: String, initialScroll: Int) {
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
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Notifications,
                        contentDescription = "Notifications",
                        onClick = {},
                    )
                    Spacer(Modifier.width(8.dp))
                    HmxLiquidGlassIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Settings",
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
                                color = HmxColor.White,
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
                color = HmxColor.White,
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
            level = com.hmx.arena.core.designsystem.HmxGlassLevel.Chrome,
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
