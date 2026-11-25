package com.wu.nfccardskin.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.RemoteBridge
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.ServiceState
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.WalletOps
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Help
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ModuleState { Working, Paused, NoLsposed, NoScope, NotLoaded }

/**
 * 主页, top to bottom: 工作状态 (the DIY module's big "LSPosed已启用" line, with the reason when
 * not), 概况 (jumps to the other tabs), 模块状态, then the switch and wallet actions.
 */
@Composable
fun HomePage(
    state: UiState,
    service: ServiceState,
    repo: Repository,
    nav: Navigator,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val walletVersion = remember(state.hookStatus) { WalletOps.walletVersion(context) }
    var refreshing by remember { mutableStateOf(false) }
    // The scope prompt shows once per app start, like the original module's.
    var scopePromptDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            withContext(Dispatchers.IO) {
                repo.reload()
                RemoteBridge.refreshState()
            }
            delay(400)
            refreshing = false
        }
    }

    val moduleState = when {
        !service.connected && state.hookStatus == null -> ModuleState.NoLsposed
        service.walletInScope == false -> ModuleState.NoScope
        state.hookStatus == null -> ModuleState.NotLoaded
        !state.settings.enabled -> ModuleState.Paused
        else -> ModuleState.Working
    }
    val replaced = state.cards.count { it.url in state.mappings }

    CouiPage(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        topBarStyle = CouiTopBarStyle.SettingsHome,
        actions = {
            IconButton(onClick = { nav.push(Route.Help) }) {
                Icon(imageVector = COUIIcons.Help, contentDescription = stringResource(R.string.help_title), tint = COUITheme.colorScheme.labelPrimary)
            }
        },
        refreshing = refreshing,
        onRefresh = { refreshing = true },
        refreshTexts = stringArrayResource(R.array.refresh_texts).toList(),
        overlays = {
            InfoDialog(
                show = state.settings.firstRun,
                title = stringResource(R.string.help_usage_title),
                summary = stringResource(R.string.help_usage_text),
                onDismiss = { repo.updateSettings { it.copy(firstRun = false) } },
            )
            ConfirmDialog(
                show = !state.settings.firstRun && service.walletInScope == false && !scopePromptDismissed,
                title = stringResource(R.string.scope_dialog_title),
                summary = stringResource(R.string.scope_dialog_text),
                confirmText = stringResource(R.string.scope_dialog_confirm),
                onConfirm = {
                    scopePromptDismissed = true
                    if (RemoteBridge.requestWalletScope()) context.toast(R.string.toast_scope_requested)
                },
                onDismiss = { scopePromptDismissed = true },
            )
        },
    ) {
        item(key = "hero") {
            val ok = moduleState == ModuleState.Working
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                onClick = when (moduleState) {
                    ModuleState.NoLsposed -> ({ if (!WalletOps.openLsposed(context)) context.toast(R.string.toast_lsposed_missing) })
                    ModuleState.NoScope -> ({ if (RemoteBridge.requestWalletScope()) context.toast(R.string.toast_scope_requested) })
                    ModuleState.NotLoaded -> ({
                        scope.launch {
                            val stopped = withContext(Dispatchers.IO) { WalletOps.forceStopWallet() }
                            if (stopped) WalletOps.openWallet(context) else context.toast(R.string.toast_no_root)
                        }
                    })
                    else -> null
                },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            when (moduleState) {
                                ModuleState.Working -> R.string.home_state_working
                                ModuleState.Paused -> R.string.home_state_paused
                                else -> R.string.home_state_inactive
                            },
                        ),
                        style = COUITheme.textStyles.headlineL,
                        color = if (ok) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(
                            when (moduleState) {
                                ModuleState.Working -> R.string.home_state_working_summary
                                ModuleState.Paused -> R.string.home_state_paused_summary
                                ModuleState.NoLsposed -> R.string.home_state_no_lsposed
                                ModuleState.NoScope -> R.string.home_state_no_scope
                                ModuleState.NotLoaded -> R.string.home_state_not_loaded
                            },
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                        style = COUITheme.textStyles.bodyM,
                        color = COUITheme.colorScheme.labelSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item(key = "overview") {
            PreferenceCategory(title = stringResource(R.string.home_overview))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(value = state.visibleCards.size, label = stringResource(R.string.home_overview_cards), onClick = { onOpenTab(TAB_CARDS) }, modifier = Modifier.weight(1f))
                StatCard(value = replaced, label = stringResource(R.string.home_overview_replaced), onClick = { onOpenTab(TAB_CARDS) }, modifier = Modifier.weight(1f))
                StatCard(value = state.images.size, label = stringResource(R.string.home_overview_images), onClick = { onOpenTab(TAB_GALLERY) }, modifier = Modifier.weight(1f))
            }
        }

        item(key = "status") {
            PreferenceCategory(title = stringResource(R.string.home_status))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.status_lsposed),
                            summary = if (service.connected) service.framework else stringResource(R.string.status_lsposed_off),
                            onClick = if (!service.connected) {
                                { if (!WalletOps.openLsposed(context)) context.toast(R.string.toast_lsposed_missing) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        val inScope = service.walletInScope
                        BasicComponent(
                            title = stringResource(R.string.status_scope),
                            summary = when (inScope) {
                                true -> stringResource(R.string.status_scope_ok)
                                false -> stringResource(R.string.status_scope_missing)
                                null -> stringResource(R.string.status_scope_unknown)
                            },
                            onClick = if (inScope == false) {
                                { if (RemoteBridge.requestWalletScope()) context.toast(R.string.toast_scope_requested) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.status_wallet),
                            summary = walletVersion ?: stringResource(R.string.status_wallet_missing),
                        )
                    }
                    val targets = service.targets
                    if (service.supportsHotReload && targets != null) {
                        item {
                            val stale = targets.count { it.needsReload }
                            val reloading = targets.any { it.state == "RELOADING" }
                            BasicComponent(
                                title = stringResource(R.string.status_targets),
                                summary = when {
                                    targets.isEmpty() -> stringResource(R.string.status_targets_none)
                                    reloading -> stringResource(R.string.status_targets_reloading)
                                    stale > 0 -> stringResource(R.string.status_targets_stale, targets.size, stale)
                                    else -> stringResource(R.string.status_targets_ok, targets.size)
                                },
                                onClick = if (stale > 0 && !reloading) {
                                    {
                                        val started = RemoteBridge.hotReloadStale { ok, failed, message ->
                                            if (failed == 0) {
                                                context.toast(context.getString(R.string.toast_hot_reload_done, ok))
                                            } else {
                                                context.toast(context.getString(R.string.toast_hot_reload_failed, message.orEmpty()))
                                            }
                                        }
                                        if (!started) context.toast(R.string.toast_hot_reload_unavailable)
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    item {
                        val hook = state.hookStatus
                        BasicComponent(
                            title = stringResource(R.string.status_hook),
                            summary = if (hook == null) {
                                stringResource(R.string.status_hook_never)
                            } else {
                                stringResource(
                                    R.string.status_hook_value,
                                    hook.walletVersion,
                                    hook.hooks,
                                    DateUtils.getRelativeTimeSpanString(hook.at).toString(),
                                )
                            },
                        )
                    }
                }
            }
        }

        item(key = "actions") {
            PreferenceCategory(title = stringResource(R.string.home_actions))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        SwitchPreference(
                            checked = state.settings.enabled,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(enabled = on) } },
                            title = stringResource(R.string.home_enable),
                            summary = stringResource(R.string.home_enable_summary),
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.home_open_wallet),
                            onClick = { if (!WalletOps.openWallet(context)) context.toast(R.string.toast_wallet_missing) },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.home_restart_wallet),
                            summary = stringResource(R.string.home_restart_wallet_summary),
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { WalletOps.forceStopWallet() }
                                    context.toast(if (ok) R.string.toast_restarted else R.string.toast_no_root)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, insideMargin = PaddingValues(vertical = 16.dp, horizontal = 8.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value.toString(), style = COUITheme.textStyles.headlineM, color = COUITheme.colorScheme.labelPrimary)
            Text(
                text = label,
                modifier = Modifier.padding(top = 2.dp),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                maxLines = 1,
            )
        }
    }
}
