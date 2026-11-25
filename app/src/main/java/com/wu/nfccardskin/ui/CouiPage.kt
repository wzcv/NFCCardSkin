/*
 * Copyright 2025, compose-coui-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Taken from the COUI coui-app skill (references/scaffold.md, https://github.com/suqi8/coui),
 * licensed under the Apache License, Version 2.0 (see NOTICE). Modified for NFCCardSkin:
 * package name, LocalHostBottomPadding and pull-to-refresh support in CouiPage. The modifications
 * are distributed as part of NFCCardSkin under GPL-3.0.
 */
@file:OptIn(ExperimentalScrollBarApi::class)

package com.wu.nfccardskin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.FabPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.PullToRefresh
import io.github.suqi8.coui.kmp.basic.rememberPullToRefreshState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.ToolbarPosition
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.VerticalScrollBar
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.basic.rememberScrollBarAdapter
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.interfaces.ExperimentalScrollBarApi
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.rememberCOUIFlingBehavior
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * Height of a host bar drawn over the page (the main shell's floating navigation bar). Page lists
 * add it to their bottom padding so the last rows can scroll above the bar.
 */
val LocalHostBottomPadding = staticCompositionLocalOf { 0.dp }

/** The top bar layouts a [CouiPageScaffold] can use. */
enum class CouiTopBarStyle {
    /** A collapsing large title; with `wide = true` the pinned small bar of wide windows. */
    Large,

    /** Always the pinned small bar. */
    Small,

    /** The ColorOS Settings home bar: 116dp tall, 40sp title, 24dp / 18dp margins. */
    SettingsHome,
}

/** What page content needs to lay out under the page chrome. */
@Stable
class CouiPageContentScope(
    val contentPadding: PaddingValues,
    val scrollBehavior: ScrollBehavior,
    val backdrop: LayerBackdrop?,
)

/** The scroll modifiers every page list uses: scroll-end haptic, COUI over-scroll, top bar nested scroll. */
fun Modifier.couiPageScroll(scope: CouiPageContentScope): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical()
    .nestedScroll(scope.scrollBehavior.nestedScrollConnection)
    .fillMaxHeight()

/** Whether the window is wide enough for the pinned small top bar: 840dp, or 600dp in landscape. */
@Composable
fun isWideWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        val width = size.width.toDp()
        val height = size.height.toDp()
        width >= 840.dp || (width >= 600.dp && height < width)
    }
}

/**
 * A COUI page: top bar with the COUI 17 glow, the page recorded into [LocalPageBackdrop] (so
 * overlays blur it), and bar / FAB / toolbar slots that get the page as their material backdrop.
 */
@Composable
fun CouiPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    topBarStyle: CouiTopBarStyle = CouiTopBarStyle.Large,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    topBarBottomContent: @Composable () -> Unit = {},
    topBarContainer: @Composable (topBar: @Composable () -> Unit) -> Unit = { topBar -> topBar() },
    topBarSearchState: TopAppBarSearchState? = null,
    snackbarHostState: SnackbarHostState? = null,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    content: @Composable (CouiPageContentScope) -> Unit,
) {
    val backdrop = rememberPageBackdrop()
    val scrollBehavior = COUIScrollBehavior()
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    val popupBlur = rememberPopupMaterialBlur(backdrop)
    Scaffold(
        modifier = modifier,
        topBar = {
            // topBarContainer can wrap the bar, e.g. to anchor something to it.
            topBarContainer {
                CouiPageTopBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    subtitle = subtitle,
                    onBack = onBack,
                    style = topBarStyle,
                    wide = wide,
                    actions = actions,
                    searchState = topBarSearchState,
                    bottomContent = topBarBottomContent,
                )
            }
        },
        // The slots sit outside the page recording, so their material can blur the page.
        bottomBar = {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalMaterialBackdrop provides materialBackdrop,
            ) { bottomBar() }
        },
        floatingActionButton = {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalMaterialBackdrop provides materialBackdrop,
            ) { floatingActionButton() }
        },
        floatingActionButtonPosition = floatingActionButtonPosition,
        floatingToolbar = {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalMaterialBackdrop provides materialBackdrop,
            ) { floatingToolbar() }
        },
        floatingToolbarPosition = floatingToolbarPosition,
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(state = snackbarHostState) },
    ) { innerPadding ->
        val navBottom = max(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            LocalHostBottomPadding.current,
        )
        // Read the scaffold padding while the list lays out, not here: reading it during
        // composition makes the list trail the collapsing top bar by one frame.
        val padding = remember(innerPadding, navBottom) { PageContentPadding(innerPadding, navBottom) }
        val scope = remember(padding, scrollBehavior, backdrop) {
            CouiPageContentScope(padding, scrollBehavior, backdrop)
        }
        Box(Modifier.fillMaxSize().then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalPopupMaterialBlur provides popupBlur,
            ) {
                content(scope)
            }
        }
    }
}

/** The page content padding: the scaffold padding plus a 12dp top gap, read lazily. */
@Stable
private class PageContentPadding(
    private val innerPadding: PaddingValues,
    private val navBottom: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = innerPadding.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = innerPadding.calculateTopPadding() + 12.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = innerPadding.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = max(innerPadding.calculateBottomPadding(), navBottom)
}

/**
 * A [CouiPageScaffold] whose content is a lazy list with the page scroll behaviour and a scroll bar.
 *
 * @param wide Use the pinned small bar of wide windows, e.g. `wide = isWideWindow()`.
 * @param overlays Dialogs and bottom sheets of the page. They live here, beside the list, so they
 *   keep the page backdrop and are not disposed when a lazy item scrolls out of view.
 */
@Composable
fun CouiPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    topBarStyle: CouiTopBarStyle = CouiTopBarStyle.Large,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    topBarBottomContent: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    listState: LazyListState = rememberLazyListState(),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    refreshTexts: List<String> = emptyList(),
    overlays: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    CouiPageScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        topBarStyle = topBarStyle,
        wide = wide,
        actions = actions,
        topBarBottomContent = topBarBottomContent,
        snackbarHostState = snackbarHostState,
    ) { scope ->
        Box(Modifier.fillMaxSize()) {
            val list: @Composable () -> Unit = {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.couiPageScroll(scope),
                    contentPadding = scope.contentPadding,
                    // COUI lists fling through SpringOverScroller, longer than Compose's default spline.
                    flingBehavior = rememberCOUIFlingBehavior(),
                ) {
                    content()
                    item(key = "coui_page_tail") { Spacer(Modifier.height(32.dp)) }
                }
            }
            if (onRefresh != null) {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    pullToRefreshState = rememberPullToRefreshState(),
                    contentPadding = scope.contentPadding,
                    topAppBarScrollBehavior = scope.scrollBehavior,
                    refreshTexts = refreshTexts.ifEmpty { io.github.suqi8.coui.kmp.basic.PullToRefreshDefaults.refreshTexts },
                    content = list,
                )
            } else {
                list()
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = scope.contentPadding,
            )
            overlays()
        }
    }
}

/** The page top bar, with the COUI 17 condensed-light glow when the page is recorded into [backdrop]. */
@Composable
fun CouiPageTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    style: CouiTopBarStyle = CouiTopBarStyle.Large,
    wide: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    searchState: TopAppBarSearchState? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val colors = COUITheme.colorScheme
    // A transparent bar with a glow suppresses the divider like COUI 17; a null glow keeps a solid bar.
    val barColor = if (backdrop != null) Color.Transparent else colors.backgroundWithCard
    val glow: (@Composable BoxScope.() -> Unit)? = if (backdrop != null) {
        {
            TopAppBarGlow(
                backdrop = backdrop,
                scrollOffsetPx = scrollBehavior.glowScrollOffsetPx(),
                isDark = colors.isDark,
                baseColor = colors.backgroundWithCard,
                enabled = true,
            )
        }
    } else {
        null
    }
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = COUIIcons.Back,
                    contentDescription = null,
                    tint = colors.labelPrimary,
                )
            }
        }
    }
    if (style == CouiTopBarStyle.Small || (wide && style == CouiTopBarStyle.Large)) {
        SmallTopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            color = barColor,
            scrollBehavior = scrollBehavior,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        // The settings home bar keeps the default title morph, so the small title stays pinned.
        val home = style == CouiTopBarStyle.SettingsHome
        TopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            color = barColor,
            scrollBehavior = scrollBehavior,
            expandedHeight = if (home) 116.dp else TopAppBarDefaults.ExpandedHeight,
            largeTitleFontSize = if (home) 40.sp else TopAppBarDefaults.largeTitleFontSize(),
            titlePadding = if (home) 24.dp else Dp.Unspecified,
            largeTitleBottomMargin = if (home) 18.dp else Dp.Unspecified,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
            searchState = searchState,
            bottomContent = bottomContent,
        )
    }
}
