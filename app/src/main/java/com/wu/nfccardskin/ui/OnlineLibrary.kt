package com.wu.nfccardskin.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.catalog.CatalogCategory
import com.wu.nfccardskin.core.catalog.CatalogItem
import com.wu.nfccardskin.core.catalog.OnlineCatalog
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.TabRow
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** Categories of the online library, in tab order; null = local favourites. */
private val LIBRARY_TABS: List<CatalogCategory?> = listOf(CatalogCategory.TRANSIT, CatalogCategory.IP, CatalogCategory.BANK, null)

/** UI state of the online library (卡面库) inside the gallery. */
@Stable
class OnlineLibraryState internal constructor(private val catalog: OnlineCatalog) {
    var tab by mutableIntStateOf(0)
        private set
    var group by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val loaded = mutableStateMapOf<CatalogCategory, List<CatalogItem>>()
    private var favorites by mutableStateOf(catalog.favorites())

    val category: CatalogCategory? get() = LIBRARY_TABS[tab]

    /** The whole current category (or the favourites). */
    val items: List<CatalogItem>
        get() = when (val c = category) {
            null -> favorites
            else -> loaded[c].orEmpty()
        }

    fun selectTab(index: Int) {
        if (index == tab) return
        tab = index
        group = null
    }

    fun isFavorite(item: CatalogItem): Boolean = favorites.any { it.key == item.key }

    fun toggleFavorite(item: CatalogItem) {
        catalog.setFavorite(item, !isFavorite(item))
        favorites = catalog.favorites()
    }

    suspend fun load(force: Boolean) {
        val c = category
        if (c == null) {
            favorites = catalog.favorites()
            return
        }
        if (!force && loaded.containsKey(c)) return
        loading = true
        error = null
        runCatching { catalog.load(c, force) }
            .onSuccess { loaded[c] = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }
}

/** The library state; loads the current category whenever the library is [active]. */
@Composable
fun rememberOnlineLibraryState(catalog: OnlineCatalog, active: Boolean): OnlineLibraryState {
    val s = remember(catalog) { OnlineLibraryState(catalog) }
    val tab = s.tab
    LaunchedEffect(s, active, tab) { if (active) s.load(force = false) }
    return s
}

/** The grid used by the gallery: card faces in columns of at least 150dp. */
@Composable
fun CardGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** Section title inside a [CardGrid] (the grid already has the 16dp page margin). */
@Composable
fun GridTitle(title: String, modifier: Modifier = Modifier) {
    PreferenceCategory(title = title, modifier = modifier, insideMargin = PaddingValues(start = 8.dp, top = 8.dp, bottom = 4.dp))
}

/** Category tabs, group chips and tiles of the online library. */
fun LazyGridScope.onlineLibraryItems(
    lib: OnlineLibraryState,
    groups: List<Pair<String, Int>>,
    shown: List<CatalogItem>,
    total: Int,
    proxy: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    fullSpan("lib_tabs") {
        TabRow(
            tabs = listOf(
                stringResource(R.string.online_transit),
                stringResource(R.string.online_ip),
                stringResource(R.string.online_bank),
                stringResource(R.string.online_favorites),
            ),
            selectedTabIndex = lib.tab,
            onTabSelected = { lib.selectTab(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (groups.size > 1) {
        fullSpan("lib_groups") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    Chip(selected = lib.group == null, onClick = { lib.group = null }, label = stringResource(R.string.online_group_all), count = total.toString())
                }
                items(groups, key = { it.first }) { (g, n) ->
                    Chip(selected = lib.group == g, onClick = { lib.group = if (lib.group == g) null else g }, label = g, count = n.toString())
                }
            }
        }
    }
    fullSpan("lib_status") {
        GridTitle(
            title = when {
                lib.loading -> stringResource(R.string.online_loading)
                lib.error != null -> stringResource(R.string.online_error, lib.error.orEmpty())
                lib.category == null && total == 0 -> stringResource(R.string.online_favorites_empty)
                else -> stringResource(R.string.online_count, shown.size)
            },
        )
    }
    items(shown, key = { "lib:" + it.key }) { item ->
        OnlineTile(item = item, proxy = proxy, onClick = { onOpen(item) })
    }
}

@Composable
fun OnlineTile(
    item: CatalogItem,
    proxy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, insideMargin = PaddingValues(8.dp), onClick = onClick) {
        OnlineImage(item = item, proxy = proxy, width = 360, modifier = Modifier.fillMaxWidth())
        Text(
            text = item.title.ifBlank { stringResource(R.string.card_untitled) },
            modifier = Modifier.padding(top = 6.dp),
            style = COUITheme.textStyles.bodyM,
            color = COUITheme.colorScheme.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.group.isNotBlank()) {
            Text(
                text = item.group,
                style = COUITheme.textStyles.bodyXS,
                color = COUITheme.colorScheme.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Library image: proxied thumbnail first, the original when the proxy fails or is off. */
@Composable
fun OnlineImage(
    item: CatalogItem,
    proxy: Boolean,
    width: Int,
    modifier: Modifier = Modifier,
) {
    var original by remember(item.key, proxy) { mutableStateOf(!proxy) }
    Box(
        modifier = modifier
            .aspectRatio(Constants.CARD_RATIO)
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.fillThin),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (original) item.imageUrl else OnlineCatalog.thumbnailUrl(item.imageUrl, width),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onError = { if (!original) original = true },
        )
    }
}

/**
 * Bottom sheet of an online card face: apply (to [pickFor], or to a card picked from the list),
 * crop first, save to the gallery, favourite, copy the original URL.
 */
@Composable
fun OnlineItemSheet(
    item: CatalogItem?,
    onDismiss: () -> Unit,
    pickFor: String?,
    lib: OnlineLibraryState,
    state: UiState,
    repo: Repository,
    nav: Navigator,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // Keeps the item while the sheet animates out.
    var last by remember { mutableStateOf<CatalogItem?>(null) }
    SideEffect { if (item != null) last = item }
    val shown = item ?: last
    val proxy = state.settings.catalogProxy

    fun launchBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { block() }.onFailure { context.toast(R.string.online_download_failed) }
            busy = false
        }
    }

    ActionSheet(
        show = item != null,
        title = shown?.title,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        val it = shown ?: return@ActionSheet
        val isFav = lib.isFavorite(it)
        val pickName = pickFor?.let { url -> state.cards.firstOrNull { c -> c.url == url }?.let { c -> state.nameOf(c) } }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OnlineImage(item = it, proxy = proxy, width = 900, modifier = Modifier.fillMaxWidth())
            Text(
                text = listOf(it.group, it.source).filter { s -> s.isNotBlank() }.joinToString(" · "),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
            )
            Card(modifier = Modifier.padding(bottom = 12.dp)) {
                CardListGroup {
                    if (pickFor != null) {
                        item {
                            BasicComponent(
                                title = stringResource(R.string.online_apply_this, pickName ?: stringResource(R.string.card_untitled)),
                                summary = if (busy) stringResource(R.string.online_downloading) else stringResource(R.string.online_apply_summary),
                                enabled = !busy,
                                onClick = {
                                    launchBusy {
                                        repo.saveOnline(it, pickFor)
                                        context.toast(R.string.toast_applied)
                                        onDismiss()
                                        nav.popUntil { r -> r is Route.CardEdit }
                                    }
                                },
                            )
                        }
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.online_crop),
                            summary = stringResource(R.string.online_crop_summary),
                            enabled = !busy,
                            onClick = {
                                launchBusy {
                                    val file = repo.catalog.download(it)
                                    onDismiss()
                                    nav.push(Route.Crop(Uri.fromFile(file), pickFor))
                                }
                            },
                        )
                    }
                    if (pickFor == null) {
                        item {
                            BasicComponent(
                                title = stringResource(R.string.online_save),
                                summary = if (busy) stringResource(R.string.online_downloading) else null,
                                enabled = !busy,
                                onClick = {
                                    launchBusy {
                                        repo.saveOnline(it, null)
                                        context.toast(R.string.toast_saved)
                                    }
                                },
                            )
                        }
                    }
                    item {
                        BasicComponent(
                            title = stringResource(if (isFav) R.string.online_unfavorite else R.string.online_favorite),
                            onClick = { lib.toggleFavorite(it) },
                        )
                    }
                    item {
                        BasicComponent(title = stringResource(R.string.online_copy_url), onClick = { context.copyText(it.imageUrl) })
                    }
                }
            }
            if (pickFor == null) {
                val cards = state.visibleCards
                PreferenceCategory(title = stringResource(R.string.gallery_apply_title))
                Card(modifier = Modifier.padding(bottom = 16.dp)) {
                    CardListGroup {
                        if (cards.isEmpty()) {
                            item { BasicComponent(title = stringResource(R.string.home_cards_empty_title)) }
                        }
                        for (c in cards) {
                            item(key = c.url) {
                                BasicComponent(
                                    title = state.nameOf(c) ?: stringResource(R.string.card_untitled),
                                    startAction = { CardFace(model = c.url, modifier = Modifier.width(64.dp)) },
                                    enabled = !busy,
                                    onClick = {
                                        launchBusy {
                                            repo.saveOnline(it, c.url)
                                            context.toast(R.string.toast_applied)
                                            onDismiss()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
