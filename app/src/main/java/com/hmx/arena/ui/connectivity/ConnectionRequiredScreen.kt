package com.hmx.arena.ui.connectivity

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxBrandMark
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxGlassLevel
import com.hmx.arena.core.designsystem.HmxShapes

private data class ConnectionCopy(
    val title: String,
    val message: String,
    val hint: String,
    val icon: ImageVector,
    val showNetworkSettings: Boolean,
)

private fun copyFor(status: ConnectionStatus): ConnectionCopy = when (status) {
    ConnectionStatus.NoNetwork -> ConnectionCopy(
        title = "No internet connection",
        message = "HMX Arena requires an active internet connection to open.",
        hint = "Turn on Wi-Fi or mobile data, then try again.",
        icon = Icons.Rounded.WifiOff,
        showNetworkSettings = true,
    )
    ConnectionStatus.NoInternet -> ConnectionCopy(
        title = "Connected, no internet",
        message = "Your network is connected but cannot reach the internet.",
        hint = "Check your Wi-Fi or mobile data connection. A captive portal may need sign-in.",
        icon = Icons.Rounded.WifiOff,
        showNetworkSettings = true,
    )
    ConnectionStatus.BackendUnavailable -> ConnectionCopy(
        title = "Arena is unavailable",
        message = "The HMX Arena servers are temporarily unreachable.",
        hint = "Your session is preserved. Try again in a moment.",
        icon = Icons.Rounded.CloudOff,
        showNetworkSettings = false,
    )
    ConnectionStatus.Timeout -> ConnectionCopy(
        title = "Connection timed out",
        message = "The arena servers took too long to respond.",
        hint = "Check your connection and try again.",
        icon = Icons.Rounded.Schedule,
        showNetworkSettings = false,
    )
    ConnectionStatus.Checking -> ConnectionCopy(
        title = "Checking connection",
        message = "Making sure HMX Arena can reach the arena servers.",
        hint = "This should only take a moment.",
        icon = Icons.Rounded.CloudOff,
        showNetworkSettings = false,
    )
    ConnectionStatus.Ready -> ConnectionCopy(
        title = "Connected",
        message = "HMX Arena is ready.",
        hint = "",
        icon = Icons.Rounded.CloudOff,
        showNetworkSettings = false,
    )
}

/**
 * Full-screen connection-required experience. It is rendered over the normal
 * Liquid Glass host so the visual identity is identical in Light and Dark.
 */
@Composable
fun ConnectionRequiredScreen(
    ui: ConnectionUi,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copy = copyFor(ui.status)
    val checking = ui.checking

    Box(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = HmxDimens.lg, vertical = HmxDimens.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HmxBrandMark(size = 56.dp)
            Spacer(Modifier.height(HmxDimens.xl))
            Text(
                text = "HMX ARENA",
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.InkTertiary,
            )
            Spacer(Modifier.height(HmxDimens.md))
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                level = HmxGlassLevel.Chrome,
                fill = HmxColor.Glass,
            ) {
                Column(
                    Modifier.padding(HmxDimens.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(HmxColor.GlassStrong)
                            .border(1.dp, HmxColor.Hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                color = HmxColor.Ink,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Icon(
                                imageVector = copy.icon,
                                contentDescription = null,
                                tint = HmxColor.Ink,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(HmxDimens.lg))
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = HmxColor.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(HmxDimens.xs))
                    Text(
                        text = copy.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HmxColor.InkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(HmxDimens.lg))
                    Text(
                        text = copy.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkTertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(HmxDimens.xl))
                    GlassButton(
                        text = if (checking) "Checking…" else "Try again",
                        onClick = onRetry,
                        enabled = !checking,
                        loading = checking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (copy.showNetworkSettings && !checking) {
                        Spacer(Modifier.height(HmxDimens.sm))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(HmxShapes.pill)
                                .border(1.dp, HmxColor.Hairline, HmxShapes.pill)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Open network settings",
                                style = MaterialTheme.typography.labelLarge,
                                color = HmxColor.Ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
