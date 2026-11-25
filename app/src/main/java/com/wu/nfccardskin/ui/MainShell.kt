package com.wu.nfccardskin.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.ServiceState
import com.wu.nfccardskin.core.UiState
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.FloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.NavigationRail
import io.github.suqi8.coui.kmp.basic.NavigationRailItem
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import kotlinx.coroutines.launch

const val TAB_HOME = 0
const val TAB_CARDS = 1
const val TAB_GALLERY = 2
const val TAB_SETTINGS = 3

private enum class MainTab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(R.string.tab_home, TabHome, TabHomeFilled),
    Cards(R.string.tab_cards, TabCards, TabCardsFilled),
    Gallery(R.string.tab_gallery, TabGallery, TabGalleryFilled),
    Settings(R.string.tab_settings, TabSettings, TabSettingsFilled),
}

/**
 * The four tabs (主页 / 卡面 / 图库 / 设置): a COUI 17 floating navigation bar on narrow windows (blurring the pages under it),
 * a navigation rail on wide ones, and the tab pages in a pager.
 */
@Composable
fun MainShell(
    tab: Int,
    onTabChange: (Int) -> Unit,
    state: UiState,
    service: ServiceState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = tab.coerceIn(0, tabs.lastIndex)) { tabs.size }
    val scope = rememberCoroutineScope()
    val select: (Int) -> Unit = { index ->
        onTabChange(index)
        scope.launch { pagerState.animateScrollToPage(index, animationSpec = tween(300)) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onTabChange(it) }
    }
    // System back on another tab returns to the first one before leaving the app.
    BackHandler(enabled = pagerState.currentPage != 0) { select(0) }

    val pages: @Composable (Modifier) -> Unit = { pagerModifier ->
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 1,
        ) { page ->
            when (tabs[page]) {
                MainTab.Home -> HomePage(state = state, service = service, repo = repo, nav = nav, onOpenTab = select)
                MainTab.Cards -> CardsPage(state = state, repo = repo, nav = nav)
                MainTab.Gallery -> GalleryPage(pickFor = null, state = state, repo = repo, nav = nav)
                MainTab.Settings -> SettingsPage(state = state, repo = repo, nav = nav)
            }
        }
    }

    if (isWideWindow()) {
        Row(modifier.fillMaxSize()) {
            NavigationRail {
                tabs.forEachIndexed { index, t ->
                    val selected = pagerState.targetPage == index
                    NavigationRailItem(
                        selected = selected,
                        onClick = { select(index) },
                        icon = if (selected) t.selectedIcon else t.icon,
                        label = stringResource(t.label),
                    )
                }
            }
            pages(Modifier.weight(1f))
        }
    } else {
        // The shell records the pager so the floating bar's material can blur the pages under it.
        val backdrop = rememberPageBackdrop()
        val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
                    FloatingNavigationBar {
                        tabs.forEachIndexed { index, t ->
                            val selected = pagerState.targetPage == index
                            FloatingNavigationBarItem(
                                selected = selected,
                                onClick = { select(index) },
                                icon = if (selected) t.selectedIcon else t.icon,
                                label = stringResource(t.label),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                CompositionLocalProvider(LocalHostBottomPadding provides innerPadding.calculateBottomPadding()) {
                    pages(Modifier.fillMaxSize())
                }
            }
        }
    }
}
