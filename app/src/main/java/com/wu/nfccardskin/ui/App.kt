package com.wu.nfccardskin.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.wu.nfccardskin.core.RemoteBridge
import com.wu.nfccardskin.core.Repository
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

/** Pages pushed over the main shell (the tabs themselves are not routes). */
sealed interface Route {
    data object Main : Route
    data class CardEdit(val url: String) : Route
    data class Crop(val uri: Uri, val url: String?) : Route
    data class PickImage(val url: String, val online: Boolean = false) : Route
    data object Hidden : Route
    data object CustomHook : Route
    data object Help : Route
    data object About : Route
    data object Logos : Route
}

@Stable
class Navigator(private val stack: SnapshotStateList<Route>) {
    val current: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Pops until [predicate] matches the top (or only the shell is left). */
    fun popUntil(predicate: (Route) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun App() {
    val repo = remember { Repository.get() }
    val state by repo.state.collectAsState()
    val service by RemoteBridge.state.collectAsState()
    val mode = when (state.settings.themeMode) {
        1 -> ColorSchemeMode.Light
        2 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val controller = remember(mode) { ThemeController(mode) }
    val stack = remember { mutableStateListOf<Route>(Route.Main) }
    val nav = remember(stack) { Navigator(stack) }
    // Kept here so the selected tab survives pushing / popping a page over the shell.
    var tab by rememberSaveable { mutableIntStateOf(0) }

    COUITheme(controller = controller) {
        CompositionLocalProvider(LocalCouiBlurEnabled provides state.settings.blur) {
            BackHandler(enabled = nav.canPop) { nav.pop() }
            when (val route = nav.current) {
                Route.Main -> MainShell(
                    tab = tab,
                    onTabChange = { tab = it },
                    state = state,
                    service = service,
                    repo = repo,
                    nav = nav,
                )
                is Route.CardEdit -> CardEditPage(url = route.url, state = state, repo = repo, nav = nav)
                is Route.Crop -> CropPage(uri = route.uri, url = route.url, state = state, repo = repo, nav = nav)
                is Route.PickImage -> GalleryPage(
                    pickFor = route.url,
                    state = state,
                    repo = repo,
                    nav = nav,
                    initialSection = if (route.online) GALLERY_ONLINE else GALLERY_MINE,
                )
                Route.Hidden -> HiddenPage(state = state, repo = repo, nav = nav)
                Route.CustomHook -> CustomHookPage(state = state, repo = repo, nav = nav)
                Route.Help -> HelpPage(nav = nav)
                Route.About -> AboutPage(state = state, repo = repo, nav = nav)
                Route.Logos -> LogosPage(repo = repo, nav = nav)
            }
        }
    }
}
