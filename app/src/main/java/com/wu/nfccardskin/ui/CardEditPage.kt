package com.wu.nfccardskin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.SkinTarget
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.UrlKey
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun CardEditPage(
    url: String,
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val card = state.cards.firstOrNull { it.url == url }
    val name = card?.let { state.nameOf(it) }
    val target = state.targetOf(url)
    val hidden = url in state.hidden
    var showUrlDialog by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val invalidUrl = stringResource(R.string.edit_url_invalid)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) nav.push(Route.Crop(uri, url))
    }

    CouiPage(
        title = name ?: stringResource(R.string.edit_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
        overlays = {
            InputDialog(
                show = showUrlDialog,
                title = stringResource(R.string.edit_url_dialog_title),
                initial = (target as? SkinTarget.Url)?.url.orEmpty(),
                label = stringResource(R.string.edit_url_dialog_hint),
                validate = { if (UrlKey.isHttp(it)) null else invalidUrl },
                onConfirm = { value ->
                    showUrlDialog = false
                    repo.setTarget(url, SkinTarget.Url(value))
                    context.toast(R.string.toast_applied)
                },
                onDismiss = { showUrlDialog = false },
            )
            InputDialog(
                show = showRename,
                title = stringResource(R.string.edit_rename),
                initial = name.orEmpty(),
                label = card?.title ?: stringResource(R.string.card_untitled),
                onConfirm = { value ->
                    showRename = false
                    // Empty or the wallet's own title: drop the alias.
                    repo.setAlias(url, value.takeIf { it.isNotEmpty() && it != card?.title })
                },
                onDismiss = { showRename = false },
            )
            ConfirmDialog(
                show = confirmForget,
                title = stringResource(R.string.edit_forget),
                summary = stringResource(R.string.edit_forget_summary),
                confirmText = stringResource(R.string.common_delete),
                destructive = true,
                onConfirm = {
                    confirmForget = false
                    repo.forget(url)
                    nav.pop()
                },
                onDismiss = { confirmForget = false },
            )
        },
    ) {
        item(key = "preview") {
            PreferenceCategory(title = stringResource(R.string.edit_preview))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.card_original),
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
                CardFace(model = url, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.edit_current),
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
                CardFace(model = target.previewModel(state) ?: url, modifier = Modifier.fillMaxWidth())
            }
        }

        item(key = "source") {
            PreferenceCategory(title = stringResource(R.string.edit_source))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.edit_pick_photo),
                            summary = stringResource(R.string.edit_pick_photo_summary),
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                    }
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.edit_pick_gallery),
                            endActions = {
                                Text(
                                    text = state.images.size.toString(),
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                            onClick = { nav.push(Route.PickImage(url)) },
                        )
                    }
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.edit_pick_online),
                            summary = stringResource(R.string.edit_pick_online_summary),
                            onClick = { nav.push(Route.PickImage(url, online = true)) },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.edit_use_url),
                            summary = (target as? SkinTarget.Url)?.url ?: stringResource(R.string.edit_use_url_summary),
                            onClick = { showUrlDialog = true },
                        )
                    }
                    if (target != null) {
                        item {
                            BasicComponent(
                                title = stringResource(R.string.edit_restore),
                                titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                                onClick = {
                                    repo.setTarget(url, null)
                                    context.toast(R.string.toast_restored)
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "manage") {
            PreferenceCategory(title = stringResource(R.string.edit_manage))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.edit_rename),
                            summary = name ?: stringResource(R.string.card_untitled),
                            onClick = { showRename = true },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.edit_copy_url),
                            summary = url,
                            onClick = { context.copyText(url) },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(if (hidden) R.string.edit_unhide else R.string.edit_hide),
                            onClick = { repo.setHidden(url, !hidden) },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.edit_forget),
                            titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                            onClick = { confirmForget = true },
                        )
                    }
                }
            }
        }
    }
}
