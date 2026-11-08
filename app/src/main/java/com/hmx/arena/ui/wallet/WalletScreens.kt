package com.hmx.arena.ui.wallet

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassIconButton
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.designsystem.HmxShapes
import com.hmx.arena.core.model.LedgerEntry
import com.hmx.arena.core.model.WalletAccount
import com.hmx.arena.core.model.WalletTotals
import com.hmx.arena.core.util.Format
import com.hmx.arena.core.util.WalletSummary
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxGlassBottomBar
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import com.hmx.arena.ui.navigation.openTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class WalletLoad(
    val wallet: WalletAccount?,
    val totals: WalletTotals?,
    val ledger: List<LedgerEntry>,
    val flags: Map<String, Boolean>,
)

data class WalletUi(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val wallet: WalletAccount? = null,
    val totals: WalletTotals? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val flags: Map<String, Boolean> = emptyMap(),
    val error: String? = null,
)

/**
 * Lifetime totals come from the server aggregate. The 50-row activity list is
 * deliberately not used as a fallback here: showing a partial sum as a lifetime
 * total would misstate the player's money.
 */
private fun WalletTotals.toSummary(): WalletSummary = WalletSummary(
    depositedPaise = depositedPaise,
    winningPaise = winningPaise,
    earningsPaise = earningsPaise,
    payoutsPaise = payoutsPaise,
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(WalletUi())
    val ui: StateFlow<WalletUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update {
                it.copy(loading = it.wallet == null, refreshing = it.wallet != null, error = null)
            }
            runCatching {
                coroutineScope {
                    val wallet = async { player.wallet() }
                    val totals = async { runCatching { player.walletTotals() }.getOrNull() }
                    val ledger = async { runCatching { player.ledger() }.getOrDefault(emptyList()) }
                    val flags = async { runCatching { player.flags() }.getOrDefault(emptyMap()) }
                    WalletLoad(wallet.await(), totals.await(), ledger.await(), flags.await())
                }
            }
                .onSuccess { loaded ->
                    _ui.value = WalletUi(
                        loading = false,
                        refreshing = false,
                        wallet = loaded.wallet,
                        totals = loaded.totals,
                        ledger = loaded.ledger,
                        flags = loaded.flags,
                    )
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, refreshing = false, error = e.hmxMessage()) }
                }
        }
    }
}

@Composable
fun WalletScreen(nav: NavHostController, viewModel: WalletViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Null means the server aggregate could not be read. The tiles then show
    // an explicit dash instead of a misleading partial sum.
    val summary = ui.totals?.toSummary()
    HmxPage(
        title = "Wallet",
        actions = {
            GlassIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh wallet",
                onClick = viewModel::load,
                enabled = !ui.refreshing,
            )
        },
        bottomBar = { HmxGlassBottomBar(Routes.WALLET) { nav.openTab(it) } },
    ) {
        when {
            ui.loading && ui.wallet == null -> LoadingBlock("Loading wallet")
            ui.error != null && ui.wallet == null -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BalanceHero(ui.wallet, summary) }
                item { SummaryGrid(summary) }
                if (summary == null) {
                    item {
                        InlineNotice(
                            "Lifetime totals are temporarily unavailable. The balance above is " +
                                "read directly from your wallet account.",
                        )
                    }
                }
                if (ui.error != null) {
                    item { InlineError(ui.error!!, onRetry = viewModel::load) }
                }
                item {
                    InlineNotice(
                        "Paid tournaments, wallet funding, withdrawals, refunds and payouts are " +
                            "disabled while compliance approval is pending. The app cannot accept a " +
                            "payment today.",
                    )
                }
                item {
                    GlassSectionHeader(
                        "Recent activity",
                        action = "See all",
                        onAction = { nav.navigate(Routes.TRANSACTIONS) },
                    )
                }
                if (ui.ledger.isEmpty()) {
                    item {
                        EmptyBlock(
                            title = "No wallet activity",
                            message = "Free tournaments do not move money. Any future verified credit " +
                                "or debit will be listed here.",
                            icon = Icons.Rounded.ReceiptLong,
                        )
                    }
                } else {
                    items(ui.ledger.take(6), key = { it.id }) { entry -> LedgerRow(entry) }
                }
                item { GlassSectionHeader("Feature status") }
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(HmxDimens.cardPadding)) {
                            FlagRow("Free tournaments", ui.flags["free_tournaments_enabled"] == true)
                            FlagRow("Paid tournaments", ui.flags["paid_tournaments_enabled"] == true)
                            FlagRow("Payment orders", ui.flags["payment_orders_enabled"] == true)
                            FlagRow("Refunds", ui.flags["refunds_enabled"] == true)
                            FlagRow("Payouts", ui.flags["payouts_enabled"] == true)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "These switches are controlled on the server and cannot be changed " +
                                    "from the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { nav.navigate(Routes.SUPPORT) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = HmxColor.InkSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Questions about a balance? Contact support",
                            style = MaterialTheme.typography.bodySmall,
                            color = HmxColor.Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(wallet: WalletAccount?, summary: WalletSummary?) {
    GlassSurface(Modifier.fillMaxWidth(), shape = HmxShapes.large, refractive = true) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Wallet,
                    contentDescription = null,
                    tint = HmxColor.Accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Total balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = HmxColor.InkSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    wallet?.currency ?: "INR",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                Format.money(wallet?.balancePaise ?: 0),
                style = MaterialTheme.typography.displayMedium,
                color = HmxColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Real ledger balance, not a promotional estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
            )
            val reserved = wallet?.reservedPaise ?: 0
            if (reserved > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Reserved for pending entries: ${Format.money(reserved)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat("Deposited", summary?.depositedPaise, Modifier.weight(1f))
                HeroStat("Winning", summary?.winningPaise, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, paise: Long?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = HmxColor.InkTertiary,
        )
        Text(
            paise?.let { Format.money(it) } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = HmxColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryGrid(summary: WalletSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(
                label = "Deposited",
                value = summary?.depositedPaise,
                icon = Icons.Rounded.AddCircle,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = "Winning",
                value = summary?.winningPaise,
                icon = Icons.Rounded.EmojiEvents,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(
                label = "Earnings",
                value = summary?.earningsPaise,
                icon = Icons.Rounded.Savings,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = "Payouts",
                value = summary?.payoutsPaise,
                icon = Icons.Rounded.CallMade,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: Long?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = HmxColor.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value?.let { Format.money(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = HmxColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun FlagRow(label: String, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = HmxColor.Ink,
            modifier = Modifier.weight(1f),
        )
        StatusPill(if (enabled) "Enabled" else "Disabled", filled = enabled)
    }
}

private fun entryTypeLabel(entryType: String): String = when (entryType.lowercase()) {
    "credit" -> "Credit"
    "debit" -> "Debit"
    "hold" -> "Hold"
    "release" -> "Release"
    "adjustment" -> "Adjustment"
    else -> entryType.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun entryStatus(entryType: String): String = when (entryType.lowercase()) {
    "hold" -> "Reserved"
    "release" -> "Released"
    "credit", "debit" -> "Settled"
    else -> "Recorded"
}

private fun entryReference(entry: LedgerEntry): String {
    val type = entry.referenceType?.trim()?.replace("_", " ").orEmpty()
    return type.replaceFirstChar { it.uppercase() }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.description.ifBlank { entryTypeLabel(entry.entryType) },
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(entryTypeLabel(entry.entryType))
                    Spacer(Modifier.width(6.dp))
                    StatusPill(entryStatus(entry.entryType))
                    val reference = entryReference(entry)
                    if (reference.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            reference,
                            style = MaterialTheme.typography.labelSmall,
                            color = HmxColor.InkTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    Format.dateTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                val positive = entry.amountPaise >= 0
                Text(
                    (if (positive) "+" else "") + Format.money(entry.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (positive) HmxColor.Ink else HmxColor.Danger,
                )
                Text(
                    "Bal ${Format.money(entry.balanceAfterPaise)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HmxColor.InkTertiary,
                )
            }
        }
    }
}

data class TransactionsUi(
    val loading: Boolean = true,
    val wallet: WalletAccount? = null,
    val entries: List<LedgerEntry> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val player: PlayerRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(TransactionsUi())
    val ui: StateFlow<TransactionsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { player.wallet() to player.ledger() }
                .onSuccess { (wallet, entries) -> _ui.value = TransactionsUi(false, wallet, entries) }
                .onFailure { e -> _ui.value = TransactionsUi(false, null, emptyList(), e.hmxMessage()) }
        }
    }
}

@Composable
fun TransactionsScreen(nav: NavHostController, viewModel: TransactionsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HmxPage(title = "Transactions", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading transactions")
            ui.error != null -> InlineError(ui.error!!)
            ui.entries.isEmpty() -> EmptyBlock(
                title = "No transactions",
                message = "Your immutable financial ledger is empty. Paid features are currently disabled.",
                icon = Icons.Rounded.ReceiptLong,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    BalanceSummaryHeader(ui.wallet)
                    Spacer(Modifier.height(8.dp))
                }
                items(ui.entries, key = { it.id }) { entry -> LedgerRow(entry) }
            }
        }
    }
}

@Composable
private fun BalanceSummaryHeader(wallet: WalletAccount?) {
    GlassSurface(Modifier.fillMaxWidth(), shape = HmxShapes.large) {
        Column(Modifier.padding(HmxDimens.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = HmxColor.Accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Balance ${Format.money(wallet?.balancePaise ?: 0)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Ledger entries are immutable and server-written.",
                style = MaterialTheme.typography.bodySmall,
                color = HmxColor.InkTertiary,
            )
        }
    }
}
