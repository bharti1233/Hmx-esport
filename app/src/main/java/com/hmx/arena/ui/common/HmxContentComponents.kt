package com.hmx.arena.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hmx.arena.core.designsystem.GlassButton
import com.hmx.arena.core.designsystem.GlassButtonVariant
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.Announcement
import com.hmx.arena.core.model.LookupOption
import com.hmx.arena.core.model.PromotionalBanner

/**
 * One-line glass announcement strip. It shows the newest admin-published
 * announcement and opens the full list. Nothing is invented when the list is
 * empty: the strip says so instead of displaying placeholder copy.
 */
@Composable
fun HmxAnnouncementTicker(
    announcements: List<Announcement>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = announcements.firstOrNull()
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clip(HmxShapes.pill)
            .clickable(enabled = latest != null, role = Role.Button, onClick = onOpen),
        shape = HmxShapes.pill,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(HmxColor.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Campaign,
                    contentDescription = null,
                    tint = HmxColor.Accent,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    latest?.title ?: "No announcements right now",
                    style = MaterialTheme.typography.labelLarge,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (latest != null && latest.body.isNotBlank()) {
                    Text(
                        latest.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (announcements.size > 1) {
                Text(
                    "+${announcements.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                )
            }
            if (latest != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = HmxColor.InkTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Horizontally paged promotional carousel. Images are admin-uploaded; the
 * fallback surface is deliberately neutral so a missing image never looks like
 * a fake campaign.
 */
@Composable
fun HmxPromoCarousel(
    banners: List<PromotionalBanner>,
    onBannerClick: (PromotionalBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) {
        GlassSurface(modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(HmxDimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Campaign,
                    contentDescription = null,
                    tint = HmxColor.InkTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "No active promotions",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
            }
        }
        return
    }

    val state = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, banners.lastIndex) }
    }

    Column(modifier) {
        BoxWithMaxWidth { cardWidth ->
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                items(banners, key = { it.id }) { banner ->
                    PromoBannerCard(
                        banner = banner,
                        modifier = Modifier.width(cardWidth),
                        onClick = { onBannerClick(banner) },
                    )
                }
            }
        }
        if (banners.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                banners.indices.forEach { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == currentPage) 16.dp else 6.dp, 6.dp)
                            .clip(HmxShapes.pill)
                            .background(
                                if (index == currentPage) HmxColor.Accent else HmxColor.HairlineStrong,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxWithMaxWidth(content: @Composable (androidx.compose.ui.unit.Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val cardWidth = (maxWidth * 0.92f).coerceAtLeast(240.dp)
        content(cardWidth)
    }
}

@Composable
private fun PromoBannerCard(
    banner: PromotionalBanner,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .clip(HmxShapes.large)
            .clickable(
                enabled = !banner.destination.isNullOrBlank(),
                role = Role.Button,
                onClick = onClick,
            ),
        shape = HmxShapes.large,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.35f)) {
            if (!banner.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = banner.imageUrl,
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(HmxColor.CanvasTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = HmxColor.InkTertiary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            ) {
                Text(
                    banner.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (banner.subtitle.isNotBlank()) {
                    Text(
                        banner.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Compact count card used for the three My Matches shortcuts. */
@Composable
fun HmxMatchShortcutCard(
    label: String,
    count: Int?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .heightIn(min = 112.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (count == null) label else "$label, $count matches"
            },
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(HmxColor.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = HmxColor.Accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                count?.toString() ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                color = HmxColor.Ink,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = HmxColor.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Image-first category tile. Artwork falls back to the square logo and then to
 * a neutral letter tile; the category name is always visible.
 */
@Composable
fun HmxCategoryArtCard(
    category: LookupOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    GlassSurface(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, HmxColor.Accent, HmxShapes.card)
                } else {
                    Modifier
                },
            )
            .clip(HmxShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${category.name} category" },
        shape = HmxShapes.card,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.12f)) {
                val artwork = category.artworkUrl
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(HmxColor.AccentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = HmxColor.Accent,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                if (!category.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = category.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HmxColor.CanvasTint),
                    )
                }
            }
            Text(
                category.name,
                style = MaterialTheme.typography.labelLarge,
                color = HmxColor.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Referral invitation card. Copy is deliberately factual: referrals are
 * reviewed by staff and no cash reward is promised by the app.
 */
@Composable
fun HmxReferralCard(
    code: String,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier.fillMaxWidth(), shape = HmxShapes.large) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(HmxColor.AccentSoft, Color.Transparent),
                    ),
                )
                .padding(HmxDimens.cardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(HmxColor.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        tint = HmxColor.Accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Refer and earn",
                        style = MaterialTheme.typography.titleMedium,
                        color = HmxColor.Ink,
                    )
                    Text(
                        "Invite a player with your code. Referrals are reviewed by staff.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkSecondary,
                    )
                }
            }
            if (code.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    color = HmxColor.Ink,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = "Share",
                    onClick = onShare,
                    variant = GlassButtonVariant.Secondary,
                    leadingIcon = Icons.Rounded.Share,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "WhatsApp",
                    onClick = onWhatsApp,
                    variant = GlassButtonVariant.Accent,
                    leadingIcon = Icons.Rounded.Chat,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

data class HmxFilterOption(
    val key: String,
    val label: String,
    val count: Int? = null,
)

/** Equal-width selected-state tabs used by Matches and My Matches. */
@Composable
fun HmxFilterTabs(
    options: List<HmxFilterOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = HmxShapes.pill,
        contentPadding = PaddingValues(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(HmxShapes.pill)
                        .background(if (selected) HmxColor.Ink else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelect(option.key) }
                        .semantics {
                            this.selected = selected
                            contentDescription = option.label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) HmxColor.OnInk else HmxColor.InkSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (option.count != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                option.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) HmxColor.OnInk.copy(alpha = 0.72f) else HmxColor.InkTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
