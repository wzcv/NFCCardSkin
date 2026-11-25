package com.wu.nfccardskin.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.GalleryImage
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.SkinTarget
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.catalog.CatalogItem
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.topAppBarSearchContent
import io.github.suqi8.coui.kmp.basic.topAppBarSearchResults
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val GALLERY_MINE = 0
const val GALLERY_ONLINE = 1

/**
 * 图库, laid out like the DIY module's image dialog: 我的图片 (saved card faces + "add from album")
 * and, as the second section, the online library 卡面库. Pushed from a card ([pickFor] = its URL)
 * a tap applies straight to that card; as a main tab a tap opens a sheet to choose the card.
 */
@Composable
fun GalleryPage(
    pickFor: String?,
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
    initialSection: Int = GALLERY_MINE,
) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableIntStateOf(initialSection) }
    val online = section == GALLERY_ONLINE
    val lib = rememberOnlineLibraryState(repo.catalog, active = online)
    val proxy = state.settings.catalogProxy

    // The top-bar search belongs to the online library.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchState = rememberTopAppBarSearchState(active = searching)
    LaunchedEffect(online) {
        if (!online) {
            searching = false
            query = ""
        }
    }
    val gridState = rememberLazyGridState()

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            if (online) lib.load(force = true) else withContext(Dispatchers.IO) { repo.refreshImages() }
            refreshing = false
        }
    }

    var imageSheet by remember { mutableStateOf<GalleryImage?>(null) }
    var deleting by remember { mutableStateOf<GalleryImage?>(null) }
    var onlineSheet by remember { mutableStateOf<CatalogItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) nav.push(Route.Crop(uri, pickFor))
    }

    val all = lib.items
    val groups = remember(all) { repo.catalog.groups(all) }
    val group = lib.group
    val shown = remember(all, group) { if (group == null) all else all.filter { it.group == group } }
    val results = remember(all, query) { repo.catalog.search(all, query) }

    val tapImage: (GalleryImage) -> Unit = { img ->
        if (pickFor != null) {
            repo.setTarget(pickFor, SkinTarget.Image(img.name))
            context.toast(R.string.toast_applied)
            nav.popUntil { it is Route.CardEdit }
        } else {
            imageSheet = img
        }
    }

    CouiPageScaffold(
        title = stringResource(if (pickFor != null) R.string.gallery_pick_title else R.string.gallery_title),
        modifier = modifier,
        onBack = if (pickFor != null) ({ nav.pop() }) else null,
        wide = isWideWindow(),
        topBarSearchState = if (online) searchState else null,
        topBarBottomContent = {
            if (online) {
                TopAppBarSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    expanded = searching,
                    onExpandedChange = {
                        searching = it
                        if (!it) query = ""
                    },
                    cancelText = stringResource(R.string.common_cancel),
                    label = stringResource(R.string.online_search_hint),
                )
            }
        },
    ) { page ->
        Box(Modifier.fillMaxSize()) {
            if (searchState.isContentVisible) {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true },
                    modifier = Modifier.topAppBarSearchContent(searchState),
                    pullToRefreshState = rememberPullToRefreshState(),
                    contentPadding = page.contentPadding,
                    topAppBarScrollBehavior = page.scrollBehavior,
                    refreshTexts = stringArrayResource(R.array.refresh_texts).toList(),
                ) {
                    CardGrid(
                        modifier = Modifier.couiPageScroll(page).padding(horizontal = 16.dp),
                        contentPadding = page.contentPadding,
                        state = gridState,
                    ) {
                        fullSpan("section") {
                            TabRowWithContour(
                                tabs = listOf(
                                    stringResource(R.string.gallery_section_mine, state.images.size),
                                    stringResource(R.string.gallery_section_online),
                                ),
                                selectedTabIndex = section,
                                onTabSelected = { section = it },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            )
                        }
                        if (online) {
                            onlineLibraryItems(lib, groups, shown, all.size, proxy, onOpen = { onlineSheet = it })
                        } else {
                            myImagesItems(
                                state = state,
                                pickFor = pickFor,
                                onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onLogos = { nav.push(Route.Logos) },
                                onOnline = { section = GALLERY_ONLINE },
                                onTap = tapImage,
                                onLongPress = { if (pickFor == null) deleting = it },
                            )
                        }
                    }
                }
            }
            if (online && searchState.isResultsVisible) {
                CardGrid(
                    modifier = Modifier.couiPageScroll(page).topAppBarSearchResults(searchState).padding(horizontal = 16.dp),
                    contentPadding = page.contentPadding,
                ) {
                    fullSpan("results") {
                        GridTitle(
                            title = if (query.isBlank()) stringResource(R.string.online_search_tip) else stringResource(R.string.online_count, results.size),
                        )
                    }
                    items(results, key = { "r:" + it.key }) { item ->
                        OnlineTile(item = item, proxy = proxy, onClick = { onlineSheet = item })
                    }
                }
            }

            ConfirmDialog(
                show = deleting != null,
                title = stringResource(R.string.gallery_delete_title),
                summary = deleting?.let { img ->
                    val n = state.usages(img.name)
                    if (n > 0) stringResource(R.string.gallery_delete_used, n) else stringResource(R.string.gallery_delete_summary)
                },
                confirmText = stringResource(R.string.common_delete),
                destructive = true,
                onConfirm = {
                    deleting?.let { repo.deleteImage(it.name) }
                    deleting = null
                },
                onDismiss = { deleting = null },
            )
            ImageSheet(
                image = imageSheet,
                state = state,
                repo = repo,
                onDelete = { img ->
                    imageSheet = null
                    deleting = img
                },
                onDismiss = { imageSheet = null },
            )
            OnlineItemSheet(
                item = onlineSheet,
                onDismiss = { onlineSheet = null },
                pickFor = pickFor,
                lib = lib,
                state = state,
                repo = repo,
                nav = nav,
            )
        }
    }
}

private fun LazyGridScope.myImagesItems(
    state: UiState,
    pickFor: String?,
    onAdd: () -> Unit,
    onLogos: () -> Unit,
    onOnline: () -> Unit,
    onTap: (GalleryImage) -> Unit,
    onLongPress: (GalleryImage) -> Unit,
) {
    fullSpan("mine_add") {
        Card {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.gallery_add),
                        summary = stringResource(R.string.edit_pick_photo_summary),
                        onClick = onAdd,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.gallery_browse_online),
                        summary = stringResource(R.string.edit_pick_online_summary),
                        onClick = onOnline,
                    )
                }
                if (pickFor == null) {
                    item { ArrowPreference(title = stringResource(R.string.logos_title), onClick = onLogos) }
                }
            }
        }
    }
    fullSpan("mine_title") {
        GridTitle(
            title = stringResource(R.string.gallery_count, state.images.size) +
                if (pickFor == null && state.images.isNotEmpty()) stringResource(R.string.gallery_tap_hint) else "",
        )
    }
    if (state.images.isEmpty()) {
        fullSpan("mine_empty") {
            Card {
                BasicComponent(title = stringResource(R.string.gallery_empty), summary = stringResource(R.string.gallery_empty_summary))
            }
        }
    }
    items(state.images, key = { "img:" + it.name }) { img ->
        ImageTile(image = img, used = state.usages(img.name), onClick = { onTap(img) }, onLongPress = { onLongPress(img) })
    }
}

@Composable
private fun ImageTile(
    image: GalleryImage,
    used: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(modifier = modifier, insideMargin = PaddingValues(8.dp), onClick = onClick, onLongPress = onLongPress) {
        CardFace(model = Uri.fromFile(java.io.File(image.path)).toString(), modifier = Modifier.fillMaxWidth())
        Text(
            text = if (used > 0) stringResource(R.string.gallery_used, used) else stringResource(R.string.gallery_unused),
            modifier = Modifier.padding(top = 6.dp),
            style = COUITheme.textStyles.bodyM,
            color = if (used > 0) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Formatter.formatShortFileSize(context, image.size),
            style = COUITheme.textStyles.bodyXS,
            color = COUITheme.colorScheme.labelSecondary,
        )
    }
}

/** Apply a gallery image to one of the wallet cards, or delete it. */
@Composable
private fun ImageSheet(
    image: GalleryImage?,
    state: UiState,
    repo: Repository,
    onDelete: (GalleryImage) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var last by remember { mutableStateOf<GalleryImage?>(null) }
    SideEffect { if (image != null) last = image }
    val img = image ?: last
    ActionSheet(
        show = image != null,
        title = stringResource(R.string.gallery_apply_title),
        onDismiss = onDismiss,
    ) {
        if (img == null) return@ActionSheet
        val cards = state.visibleCards
        Card(modifier = Modifier.padding(bottom = 12.dp)) {
            CardListGroup {
                if (cards.isEmpty()) {
                    item { BasicComponent(title = stringResource(R.string.home_cards_empty_title)) }
                }
                for (c in cards) {
                    item(key = c.url) {
                        val current = state.targetOf(c.url) == SkinTarget.Image(img.name)
                        BasicComponent(
                            title = state.nameOf(c) ?: stringResource(R.string.card_untitled),
                            summary = if (current) stringResource(R.string.gallery_apply_current) else null,
                            startAction = { CardFace(model = c.url, modifier = Modifier.width(64.dp)) },
                            selected = current,
                            onClick = {
                                repo.setTarget(c.url, SkinTarget.Image(img.name))
                                context.toast(R.string.toast_applied)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
        Card(modifier = Modifier.padding(bottom = 16.dp)) {
            BasicComponent(
                title = stringResource(R.string.gallery_delete),
                titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                onClick = { onDelete(img) },
            )
        }
    }
}
