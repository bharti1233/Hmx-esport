package com.hmx.arena.ui.wallet

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hmx.arena.core.designsystem.GlassSectionHeader
import com.hmx.arena.core.designsystem.GlassSurface
import com.hmx.arena.core.designsystem.HmxColor
import com.hmx.arena.core.designsystem.HmxDimens
import com.hmx.arena.core.model.LedgerEntry
import com.hmx.arena.core.model.WalletAccount
import com.hmx.arena.core.util.Format
import com.hmx.arena.data.repository.PlayerRepository
import com.hmx.arena.ui.common.EmptyBlock
import com.hmx.arena.ui.common.HmxPage
import com.hmx.arena.ui.common.InlineError
import com.hmx.arena.ui.common.InlineNotice
import com.hmx.arena.ui.common.LoadingBlock
import com.hmx.arena.ui.common.StatusPill
import com.hmx.arena.ui.common.hmxMessage
import com.hmx.arena.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUi(
    val loading: Boolean = true,
    val wallet: WalletAccount? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val flags: Map<String, Boolean> = emptyMap(),
    val error: String? = null,
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
            _ui.value = _ui.value.copy(loading = _ui.value.wallet == null, error = null)
            runCatching {
                Triple(
                    player.wallet(),
                    runCatching { player.ledger() }.getOrDefault(emptyList()),
                    runCatching { player.flags() }.getOrDefault(emptyMap()),
                )
            }
                .onSuccess { (wallet, ledger, flags) ->
                    _ui.value = WalletUi(false, wallet, ledger, flags)
                }
                .onFailure { e -> _ui.value = _ui.value.copy(loading = false, error = e.hmxMessage()) }
        }
    }
}

@Composable
fun WalletScreen(nav: NavHostController, viewModel: WalletViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    HmxPage(title = "Wallet", onBack = { nav.popBackStack() }) {
        when {
            ui.loading -> LoadingBlock("Loading wallet")
            ui.error != null && ui.wallet == null -> InlineError(ui.error!!, onRetry = viewModel::load)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Wallet, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("HMX balance", style = MaterialTheme.typography.labelMedium, color = HmxColor.InkSecondary)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                Format.money(ui.wallet?.balancePaise ?: 0),
                                style = MaterialTheme.typography.displayMedium,
                                color = HmxColor.Ink,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Real ledger balance, not a promotional estimate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                            val reserved = ui.wallet?.reservedPaise ?: 0
                            if (reserved > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Reserved for pending entries: ${Format.money(reserved)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HmxColor.InkSecondary,
                                )
                            }
                        }
                    }
                }
                item {
                    InlineNotice(
                        "Paid tournaments, wallet funding, withdrawals, refunds and payouts are disabled while compliance approval is pending. The app cannot accept a payment today.",
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
                            message = "Free tournaments do not move money. Any future verified credit or debit will be listed here.",
                            icon = Icons.Rounded.ReceiptLong,
                        )
                    }
                } else {
                    items(ui.ledger.take(4), key = { it.id }) { entry ->
                        LedgerRow(entry)
                    }
                }
                item {
                    GlassSectionHeader("Feature status")
                }
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
                                "These switches are controlled on the server and cannot be changed from the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HmxColor.InkTertiary,
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(Routes.SUPPORT) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = HmxColor.InkSecondary, modifier = Modifier.size(18.dp))
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
private fun FlagRow(label: String, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HmxColor.Ink, modifier = Modifier.weight(1f))
        StatusPill(if (enabled) "Enabled" else "Disabled", filled = enabled)
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.description.ifBlank { entry.entryType.replace("_", " ").replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.titleMedium,
                    color = HmxColor.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Format.dateTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = HmxColor.InkTertiary,
                )
            }
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
    val ui by viewModel.ui.collectAsState()
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Balance ${Format.money(ui.wallet?.balancePaise ?: 0)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = HmxColor.Ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ledger entries are immutable and server-written.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HmxColor.InkTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(ui.entries, key = { it.id }) { entry -> LedgerRow(entry) }
            }
        }
    }
}
