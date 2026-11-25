package com.wu.nfccardskin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.BuildConfig
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.CardLogo
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.CustomHook
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.UiState
import com.wu.nfccardskin.core.WalletOps
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 使用说明 + 常见问题. */
@Composable
fun HelpPage(nav: Navigator, modifier: Modifier = Modifier) {
    val steps = stringArrayResource(R.array.help_steps)
    val faqQ = stringArrayResource(R.array.faq_questions)
    val faqA = stringArrayResource(R.array.faq_answers)
    CouiPage(
        title = stringResource(R.string.help_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
    ) {
        item(key = "usage") {
            PreferenceCategory(title = stringResource(R.string.help_usage_title))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    steps.forEachIndexed { i, s ->
                        item { BasicComponent(title = "${i + 1}. " + s.substringBefore('|'), summary = s.substringAfter('|', "").ifEmpty { null }) }
                    }
                }
            }
        }
        item(key = "faq") {
            PreferenceCategory(title = stringResource(R.string.help_faq_title))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    faqQ.forEachIndexed { i, q ->
                        item { BasicComponent(title = q, summary = faqA.getOrNull(i)) }
                    }
                }
            }
        }
    }
}

/** 关于: version (tap 7 times for developer options), target, references. */
@Composable
fun AboutPage(
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val dev = state.settings.devMode
    CouiPage(
        title = stringResource(R.string.about_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
    ) {
        item(key = "app") {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.app_name),
                            summary = stringResource(R.string.xposed_description),
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.settings_version),
                            summary = if (dev) stringResource(R.string.about_dev_on) else null,
                            endActions = {
                                Text(
                                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelTertiary,
                                )
                            },
                            onClick = {
                                val now = System.currentTimeMillis()
                                taps = if (now - lastTap > 2_000L) 1 else taps + 1
                                lastTap = now
                                if (taps >= 7) {
                                    taps = 0
                                    repo.updateSettings { it.copy(devMode = !it.devMode) }
                                    context.toast(if (dev) R.string.about_dev_disabled else R.string.about_dev_enabled)
                                }
                            },
                        )
                    }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.settings_target),
                            summary = stringResource(R.string.settings_target_value),
                        )
                    }
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.about_open_lsposed),
                            summary = stringResource(R.string.about_open_lsposed_summary),
                            onClick = { if (!WalletOps.openLsposed(context)) context.toast(R.string.toast_lsposed_missing) },
                        )
                    }
                }
            }
        }
        item(key = "refs") {
            PreferenceCategory(title = stringResource(R.string.about_refs))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    for ((title, url) in REFERENCES) {
                        item {
                            ArrowPreference(title = title, summary = url, onClick = { WalletOps.openUrl(context, url) })
                        }
                    }
                }
            }
        }
    }
}

private val REFERENCES = listOf(
    "COUI" to "https://github.com/suqi8/coui",
    "DIY NFC卡面图片" to "https://github.com/Xposed-Modules-Repo/com.zhizi42.diymiuicard",
    "cardface-miuix" to "https://github.com/MiamBao/cardface-miuix",
    "libxposed" to "https://github.com/libxposed/api",
)

/** Developer option: hook a method by name, with the original module's two switches. */
@Composable
fun CustomHookPage(
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val h = state.settings.customHook
    var editing by remember { mutableStateOf<Int?>(null) } // 0 class, 1 method, 2 arg
    val update: (CustomHook.() -> CustomHook) -> Unit = { f -> repo.updateSettings { it.copy(customHook = it.customHook.f()) } }
    CouiPage(
        title = stringResource(R.string.custom_hook_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
        overlays = {
            val field = editing
            InputDialog(
                show = field != null,
                title = when (field) {
                    0 -> stringResource(R.string.custom_hook_class)
                    1 -> stringResource(R.string.custom_hook_method)
                    else -> stringResource(R.string.custom_hook_arg)
                },
                initial = when (field) {
                    0 -> h.className
                    1 -> h.methodName
                    else -> h.argClass
                },
                label = when (field) {
                    0 -> "com.finshell.finui.widget.imageview.CircleNetworkImageView"
                    1 -> "setImageUrl"
                    else -> "String"
                },
                onConfirm = { v ->
                    when (field) {
                        0 -> update { copy(className = v) }
                        1 -> update { copy(methodName = v) }
                        else -> update { copy(argClass = v) }
                    }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        },
    ) {
        item(key = "tip") {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                BasicComponent(title = stringResource(R.string.custom_hook_warning), summary = stringResource(R.string.custom_hook_tip))
            }
        }
        item(key = "fields") {
            PreferenceCategory(title = stringResource(R.string.custom_hook_point))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item { BasicComponent(title = stringResource(R.string.custom_hook_class), summary = h.className.ifEmpty { "—" }, onClick = { editing = 0 }) }
                    item { BasicComponent(title = stringResource(R.string.custom_hook_method), summary = h.methodName.ifEmpty { "—" }, onClick = { editing = 1 }) }
                    item {
                        BasicComponent(
                            title = stringResource(R.string.custom_hook_arg),
                            summary = h.argClass.ifEmpty { stringResource(R.string.custom_hook_no_arg) },
                            onClick = { editing = 2 },
                        )
                    }
                }
            }
        }
        item(key = "mode") {
            PreferenceCategory(title = stringResource(R.string.custom_hook_mode))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        SwitchPreference(
                            checked = h.loadUrlFromArg,
                            onCheckedChange = { on -> update { copy(loadUrlFromArg = on) } },
                            title = stringResource(R.string.custom_hook_from_arg),
                            summary = stringResource(R.string.custom_hook_from_arg_summary),
                        )
                    }
                    item {
                        SwitchPreference(
                            checked = h.replaceUrlToArg,
                            onCheckedChange = { on -> update { copy(replaceUrlToArg = on) } },
                            title = stringResource(R.string.custom_hook_to_arg),
                            summary = stringResource(R.string.custom_hook_to_arg_summary),
                            enabled = h.loadUrlFromArg,
                        )
                    }
                }
            }
        }
        item(key = "clear") {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                BasicComponent(
                    title = stringResource(R.string.custom_hook_clear),
                    titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                    onClick = { repo.updateSettings { it.copy(customHook = CustomHook()) } },
                )
            }
        }
    }
}

/** Card marks: built-in ones plus imported transparent PNGs. */
@Composable
fun LogosPage(repo: Repository, nav: Navigator, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logos by remember { mutableStateOf<List<CardLogo>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { logos = withContext(Dispatchers.IO) { repo.logos.list() } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { repo.logos.import(uri) }.isSuccess }
                context.toast(if (ok) R.string.logos_imported else R.string.crop_failed)
                reload++
            }
        }
    }
    CouiPage(
        title = stringResource(R.string.logos_title),
        modifier = modifier,
        onBack = { nav.pop() },
        wide = isWideWindow(),
    ) {
        item(key = "import") {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                ArrowPreference(
                    title = stringResource(R.string.logos_import),
                    summary = stringResource(R.string.logos_import_summary),
                    onClick = { importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
        }
        item(key = "list") {
            PreferenceCategory(title = stringResource(R.string.logos_list))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    for (l in logos) {
                        item(key = l.id) {
                            BasicComponent(
                                title = l.label,
                                summary = stringResource(if (l.builtIn) R.string.logos_builtin else R.string.logos_user),
                                startAction = { LogoThumb(repo, l, Modifier.width(72.dp)) },
                                endActions = if (l.builtIn) {
                                    null
                                } else {
                                    {
                                        Text(
                                            text = stringResource(R.string.common_delete),
                                            style = COUITheme.textStyles.bodyM,
                                            color = COUITheme.colorScheme.error,
                                        )
                                    }
                                },
                                onClick = if (l.builtIn) {
                                    null
                                } else {
                                    {
                                        repo.logos.delete(l.id)
                                        reload++
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

@Composable
private fun LogoThumb(repo: Repository, logo: CardLogo, modifier: Modifier = Modifier) {
    var bmp by remember(logo.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(logo.id) {
        bmp = withContext(Dispatchers.IO) { repo.logos.load(logo.id) }?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .aspectRatio(Constants.CARD_RATIO)
            .clip(RoundedCornerShape(8.dp))
            // Dark tile so white marks stay visible.
            .background(Color(0xFF3A3A3C)),
    ) {
        bmp?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}
