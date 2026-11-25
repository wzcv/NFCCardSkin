package com.wu.nfccardskin.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.WalletOps
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsPage(
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    var iconHidden by remember { mutableStateOf(WalletOps.isIconHidden(context)) }
    var confirmHideIcon by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

    CouiPage(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        wide = isWideWindow(),
        overlays = {
            ConfirmDialog(
                show = confirmReset,
                title = stringResource(R.string.settings_reset_title),
                summary = stringResource(R.string.settings_reset_summary),
                confirmText = stringResource(R.string.common_confirm),
                destructive = true,
                onConfirm = {
                    repo.resetAllMappings()
                    confirmReset = false
                    context.toast(R.string.toast_done)
                },
                onDismiss = { confirmReset = false },
            )
            ConfirmDialog(
                show = confirmHideIcon,
                title = stringResource(R.string.settings_hide_icon_title),
                summary = stringResource(R.string.settings_hide_icon_text),
                confirmText = stringResource(R.string.common_confirm),
                onConfirm = {
                    WalletOps.setIconHidden(context, true)
                    iconHidden = true
                    confirmHideIcon = false
                },
                onDismiss = { confirmHideIcon = false },
            )
            ConfirmDialog(
                show = confirmClearCache,
                title = stringResource(R.string.settings_clear_cache_title),
                summary = stringResource(R.string.settings_clear_cache_text),
                confirmText = stringResource(R.string.common_confirm),
                onConfirm = {
                    confirmClearCache = false
                    // Root shell off the main thread (the original crashed / could ANR here).
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { WalletOps.clearWalletImageCache() }
                        context.toast(if (ok) R.string.toast_cache_cleared else R.string.toast_no_root_cache)
                    }
                },
                onDismiss = { confirmClearCache = false },
            )
        },
    ) {
        item(key = "hook") {
            PreferenceCategory(title = stringResource(R.string.settings_hook))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        SwitchPreference(
                            checked = settings.testMode,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(testMode = on) } },
                            title = stringResource(R.string.settings_test_mode),
                            summary = stringResource(R.string.settings_test_mode_summary),
                        )
                    }
                    item {
                        SwitchPreference(
                            checked = settings.roundCorners,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(roundCorners = on) } },
                            title = stringResource(R.string.crop_round),
                            summary = stringResource(R.string.crop_round_summary),
                        )
                    }
                    item {
                        SwitchPreference(
                            checked = settings.catalogProxy,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(catalogProxy = on) } },
                            title = stringResource(R.string.settings_catalog_proxy),
                            summary = stringResource(R.string.settings_catalog_proxy_summary),
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.settings_clear_cache),
                            summary = stringResource(R.string.settings_clear_cache_summary),
                            onClick = { confirmClearCache = true },
                        )
                    }
                }
            }
        }

        item(key = "appearance") {
            PreferenceCategory(title = stringResource(R.string.settings_appearance))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        OverlayDropdownPreference(
                            items = listOf(
                                stringResource(R.string.settings_theme_system),
                                stringResource(R.string.settings_theme_light),
                                stringResource(R.string.settings_theme_dark),
                            ),
                            selectedIndex = settings.themeMode.coerceIn(0, 2),
                            title = stringResource(R.string.settings_theme),
                            onSelectedIndexChange = { index -> repo.updateSettings { it.copy(themeMode = index) } },
                        )
                    }
                    item {
                        SwitchPreference(
                            checked = settings.blur,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(blur = on) } },
                            title = stringResource(R.string.settings_blur),
                        )
                    }
                    item {
                        SwitchPreference(
                            checked = iconHidden,
                            onCheckedChange = { on ->
                                if (on) {
                                    confirmHideIcon = true
                                } else {
                                    WalletOps.setIconHidden(context, false)
                                    iconHidden = false
                                }
                            },
                            title = stringResource(R.string.settings_hide_icon),
                            summary = stringResource(R.string.settings_hide_icon_summary),
                        )
                    }
                }
            }
        }

        item(key = "data") {
            PreferenceCategory(title = stringResource(R.string.settings_data))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.settings_hidden),
                            summary = stringResource(R.string.settings_hidden_summary),
                            endActions = {
                                Text(
                                    text = state.hiddenCards.size.toString(),
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                            onClick = { nav.push(Route.Hidden) },
                        )
                    }
                    item {
                        ArrowPreference(title = stringResource(R.string.logos_title), onClick = { nav.push(Route.Logos) })
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.settings_reset),
                            summary = stringResource(R.string.settings_reset_summary),
                            titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                            onClick = { confirmReset = true },
                        )
                    }
                }
            }
        }

        if (settings.devMode) {
            item(key = "dev") {
                PreferenceCategory(title = stringResource(R.string.settings_dev))
                Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            SwitchPreference(
                                checked = settings.recordAll,
                                onCheckedChange = { on -> repo.updateSettings { it.copy(recordAll = on) } },
                                title = stringResource(R.string.settings_record_all),
                                summary = stringResource(R.string.settings_record_all_summary),
                            )
                        }
                        item {
                            SwitchPreference(
                                checked = settings.glideFallback,
                                onCheckedChange = { on -> repo.updateSettings { it.copy(glideFallback = on) } },
                                title = stringResource(R.string.settings_glide),
                                summary = stringResource(R.string.settings_glide_summary),
                            )
                        }
                        item {
                            SwitchPreference(
                                checked = settings.debug,
                                onCheckedChange = { on -> repo.updateSettings { it.copy(debug = on) } },
                                title = stringResource(R.string.settings_debug),
                                summary = stringResource(R.string.settings_debug_summary),
                            )
                        }
                        item {
                            ArrowPreference(
                                title = stringResource(R.string.custom_hook_title),
                                summary = if (settings.customHook.isSet) {
                                    "${settings.customHook.className.substringAfterLast('.')}#${settings.customHook.methodName}"
                                } else {
                                    stringResource(R.string.custom_hook_unset)
                                },
                                onClick = { nav.push(Route.CustomHook) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "about") {
            PreferenceCategory(title = stringResource(R.string.settings_about))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item { ArrowPreference(title = stringResource(R.string.help_title), onClick = { nav.push(Route.Help) }) }
                    item { ArrowPreference(title = stringResource(R.string.about_title), onClick = { nav.push(Route.About) }) }
                }
            }
        }
    }
}

/** The original module's "主界面卡面黑名单": hidden cards, tap to show again. */
@Composable
fun HiddenPage(
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val hidden = state.hiddenCards
    CouiPage(
        title = stringResource(R.string.hidden_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
    ) {
        if (hidden.isEmpty()) {
            item(key = "empty") {
                Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    BasicComponent(title = stringResource(R.string.hidden_empty))
                }
            }
        } else {
            item(key = "list") {
                Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        for (card in hidden) {
                            item(key = card.url) {
                                BasicComponent(
                                    title = state.nameOf(card) ?: stringResource(R.string.card_untitled),
                                    summary = stringResource(R.string.hidden_restore),
                                    startAction = { CardFace(model = card.url, modifier = Modifier.width(72.dp)) },
                                    onClick = { repo.setHidden(card.url, false) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
