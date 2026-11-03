package com.hmx.arena.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("home", "Home", Icons.Rounded.Home),
    TabItem("tournaments", "Arena", Icons.Rounded.EmojiEvents),
    TabItem("matches", "Matches", Icons.Rounded.SportsEsports),
    TabItem("leaderboard", "Ranks", Icons.Rounded.Star),
    TabItem("profile", "Profile", Icons.Rounded.Person),
)

@Composable
fun GlassBottomBar(selectedRoute: String, onSelect: (String) -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = HmxShapes.pill,
        fill = HmxColor.White.copy(alpha = 0.58f),
        elevation = 18.dp,
        refractive = true,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(HmxShapes.pill)
                        .semantics {
                            this.selected = selected
                            contentDescription = tab.label
                        }
                        .clickable(role = Role.Tab) { onSelect(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 42.dp, height = 28.dp)
                            .clip(CircleShape)
                            .background(if (selected) HmxColor.Ink else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (selected) HmxColor.White else HmxColor.InkTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) HmxColor.Ink else HmxColor.InkTertiary,
                    )
                }
            }
        }
    }
}
