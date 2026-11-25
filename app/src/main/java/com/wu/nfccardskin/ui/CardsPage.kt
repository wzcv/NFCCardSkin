package com.wu.nfccardskin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.RemoteBridge
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.SeenCard
import com.wu.nfccardskin.core.SkinTarget
import com.wu.nfccardskin.core.UiState
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 卡面: the wallet cards the hook has seen, original → replacement (the DIY module's main list). */
@Composable
fun CardsPage(
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val cards = state.visibleCards
    var refreshing by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<SeenCard?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

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

    CouiPage(
        title = stringResource(R.string.tab_cards),
        modifier = modifier,
        wide = isWideWindow(),
        refreshing = refreshing,
        onRefresh = { refreshing = true },
        refreshTexts = stringArrayResource(R.array.refresh_texts).toList(),
        overlays = {
            ConfirmDialog(
                show = confirmClear,
                title = stringResource(R.string.home_clear_title),
                summary = stringResource(R.string.home_clear_text),
                confirmText = stringResource(R.string.home_clear_confirm),
                destructive = true,
                onConfirm = {
                    confirmClear = false
                    repo.clearUnmappedCards()
                },
                onDismiss = { confirmClear = false },
            )
            CardMenuSheet(card = menuFor, state = state, repo = repo, nav = nav, onDismiss = { menuFor = null })
        },
    ) {
        item(key = "cards_title") {
            PreferenceCategory(
                title = stringResource(R.string.home_cards) + if (cards.isNotEmpty()) " · ${cards.size}" else "",
            )
        }
        if (cards.isEmpty()) {
            item(key = "cards_empty") {
                Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.home_cards_empty_title),
                        summary = stringResource(R.string.home_cards_empty_summary),
                    )
                }
            }
        } else {
            items(cards, key = { "card:" + it.url }) { card ->
                CardRow(
                    card = card,
                    state = state,
                    onClick = { nav.push(Route.CardEdit(card.url)) },
                    onLongPress = { menuFor = card },
                )
            }
            if (cards.any { it.url !in state.mappings }) {
                item(key = "cards_clear") {
                    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.home_clear),
                            summary = stringResource(R.string.home_clear_summary),
                            onClick = { confirmClear = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    card: SeenCard,
    state: UiState,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.targetOf(card.url)
    Card(
        modifier = modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardFace(model = card.url, modifier = Modifier.weight(1f))
            Icon(
                imageVector = COUIIcons.Back,
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(180f),
                tint = COUITheme.colorScheme.labelTertiary,
            )
            CardFace(
                model = target.previewModel(state),
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.card_original),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.nameOf(card) ?: stringResource(R.string.card_untitled),
            style = COUITheme.textStyles.headlineXS,
            color = COUITheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = when (target) {
                is SkinTarget.Image -> stringResource(R.string.card_replaced_image)
                is SkinTarget.Url -> stringResource(R.string.card_replaced_url)
                null -> stringResource(R.string.card_not_replaced)
            },
            style = COUITheme.textStyles.bodyS,
            color = if (target != null) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelSecondary,
        )
    }
}

/** Long-press menu of a card (the original module: long press = URL + copy, swipe = blacklist). */
@Composable
private fun CardMenuSheet(
    card: SeenCard?,
    state: UiState,
    repo: Repository,
    nav: Navigator,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var last by remember { mutableStateOf<SeenCard?>(null) }
    SideEffect { if (card != null) last = card }
    val c = card ?: last
    ActionSheet(
        show = card != null,
        title = c?.let { state.nameOf(it) } ?: stringResource(R.string.card_untitled),
        onDismiss = onDismiss,
    ) {
        if (c == null) return@ActionSheet
        Card(modifier = Modifier.padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(title = stringResource(R.string.edit_title), onClick = {
                        onDismiss()
                        nav.push(Route.CardEdit(c.url))
                    })
                }
                item {
                    BasicComponent(title = stringResource(R.string.edit_copy_url), summary = c.url, onClick = {
                        context.copyText(c.url)
                        onDismiss()
                    })
                }
                item {
                    BasicComponent(title = stringResource(R.string.edit_hide), onClick = {
                        repo.setHidden(c.url, true)
                        context.toast(R.string.toast_hidden)
                        onDismiss()
                    })
                }
                if (c.url in state.mappings) {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.edit_restore),
                            titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                            onClick = {
                                repo.setTarget(c.url, null)
                                context.toast(R.string.toast_restored)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}
