package com.hmx.arena.ui.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxGlassLevel
import com.hmx.arena.core.designsystem.HmxGlassTier
import com.hmx.arena.core.designsystem.HmxLiquidGlassSurface
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.designsystem.LocalHmxGlassTier
import com.hmx.arena.ui.navigation.Routes

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.REFERRAL, "Earn", Icons.Rounded.CardGiftcard),
    TabItem(Routes.LEADERBOARD, "Ranking", Icons.Rounded.EmojiEvents),
    TabItem(Routes.HOME, "Play", Icons.Rounded.SportsEsports),
    TabItem(Routes.WALLET, "Wallet", Icons.Rounded.Wallet),
    TabItem(Routes.PROFILE, "Account", Icons.Rounded.Person),
)

/**
 * The reference component: a floating, fully refractive glass capsule.
 *
 * It is placed outside the screen's recorded content layer by `GlassScaffold`,
 * so the blur and lens sample the actual scrolling list behind it. The moving
 * black capsule is the only opaque element; there is no white plate underneath.
 */
@Composable
fun HmxLiquidGlassBottomBar(
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val tier = LocalHmxGlassTier.current

    BoxWithConstraints(modifier) {
        // The adaptive shell places this component in a 96dp-wide container on
        // medium/expanded windows. Measuring the available width (rather than
        // asking the caller) keeps a single navigation component for bottom bar
        // and rail, so route state and semantics stay identical.
        val vertical = maxWidth < 200.dp

        HmxLiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (vertical) Modifier.fillMaxHeight() else Modifier),
            shape = HmxShapes.pill,
            level = HmxGlassLevel.Chrome,
            fill = HmxColor.GlassChrome,
            elevation = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .then(if (vertical) Modifier.fillMaxHeight() else Modifier.height(58.dp)),
            ) {
                val animatedValue by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "navigationSelection",
                )
                // Reduced tier snaps instead of animating a full-height rail.
                val position = if (tier == HmxGlassTier.Full) animatedValue else selectedIndex.toFloat()

                if (vertical) {
                    val tabHeight = maxHeight / tabs.size
                    Box(
                        Modifier
                            .offset(y = tabHeight * position)
                            .height(tabHeight)
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(HmxColor.Ink),
                    )
                    Column(Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { index, tab ->
                            val selected = index == selectedIndex
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .semantics {
                                        this.selected = selected
                                        contentDescription = tab.label
                                    }
                                    .clickable(role = Role.Tab) { onSelect(tab.route) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                                )
                            }
                        }
                    }
                } else {
                    val tabWidth = maxWidth / tabs.size
                    Box(
                        Modifier
                            .offset(x = tabWidth * position)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(HmxColor.Ink),
                    )
                    Row(Modifier.fillMaxSize()) {
                        tabs.forEachIndexed { index, tab ->
                            val selected = index == selectedIndex
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .semantics {
                                        this.selected = selected
                                        contentDescription = tab.label
                                    }
                                    .clickable(role = Role.Tab) { onSelect(tab.route) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compatibility entry point used by older screens. */
@Composable
fun GlassBottomBar(selectedRoute: String, onSelect: (String) -> Unit) {
    HmxLiquidGlassBottomBar(selectedRoute = selectedRoute, onSelect = onSelect)
}
