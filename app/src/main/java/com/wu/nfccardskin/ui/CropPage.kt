package com.wu.nfccardskin.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.CardLogo
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.ImageStore
import com.wu.nfccardskin.core.Repository
import com.wu.nfccardskin.core.UiState
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the bitmap is drawn inside the crop frame for a given zoom / pan. */
private class CropGeometry(frame: IntSize, bw: Int, bh: Int, zoom: Float, pan: Offset) {
    val scale = max(frame.width.toFloat() / bw, frame.height.toFloat() / bh) * zoom
    val drawW = bw * scale
    val drawH = bh * scale
    private val maxX = ((drawW - frame.width) / 2f).coerceAtLeast(0f)
    private val maxY = ((drawH - frame.height) / 2f).coerceAtLeast(0f)
    val pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    val left = (frame.width - drawW) / 2f + this.pan.x
    val top = (frame.height - drawH) / 2f + this.pan.y
    val cropRect = RectF(-left / scale, -top / scale, (-left + frame.width) / scale, (-top + frame.height) / scale)
}

/** Corner radius of the preview frame as a share of its height (1296 x 800 output, 54 px radius). */
private const val ROUND_PERCENT = 7

@Composable
fun CropPage(
    uri: Uri,
    url: String?,
    state: UiState,
    repo: Repository,
    nav: Navigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var image by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    var logos by remember { mutableStateOf<List<CardLogo>>(emptyList()) }
    val logoBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    var logoId by remember { mutableStateOf<String?>(null) }
    val round = state.settings.roundCorners

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { runCatching { ImageStore.decode(context, uri) }.getOrNull() }
        if (bmp == null) failed = true else {
            source = bmp
            image = bmp.asImageBitmap()
        }
    }
    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { repo.logos.list() }
        logos = list
        for ((id) in list) {
            val b = withContext(Dispatchers.IO) { repo.logos.load(id) } ?: continue
            logoBitmaps[id] = b.asImageBitmap()
        }
    }

    CouiPageScaffold(
        title = stringResource(R.string.crop_title),
        modifier = modifier,
        onBack = { nav.pop() },
        topBarStyle = CouiTopBarStyle.Small,
    ) { page ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(page.contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(Constants.CARD_RATIO)
                    .clip(if (round) RoundedCornerShape(ROUND_PERCENT) else RoundedCornerShape(0.dp))
                    // Crop backdrop: black like a photo viewer, independent of the theme.
                    .background(Color.Black)
                    .onSizeChanged { frame = it }
                    .pointerInput(image) {
                        val bmp = image ?: return@pointerInput
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                            pan = CropGeometry(frame, bmp.width, bmp.height, zoom, pan + panChange).pan
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = image
                when {
                    bmp != null && frame != IntSize.Zero -> Canvas(Modifier.fillMaxSize()) {
                        val g = CropGeometry(frame, bmp.width, bmp.height, zoom, pan)
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(g.left.roundToInt(), g.top.roundToInt()),
                            dstSize = IntSize(g.drawW.roundToInt(), g.drawH.roundToInt()),
                            filterQuality = FilterQuality.High,
                        )
                    }
                    failed -> Text(stringResource(R.string.crop_failed), color = Color.White)
                    else -> Text(stringResource(R.string.crop_loading), color = Color.White)
                }
                val overlay = logoId?.let { logoBitmaps[it] }
                if (overlay != null) {
                    Image(
                        bitmap = overlay,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
            Text(
                text = stringResource(R.string.crop_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
            )

            Column {
                PreferenceCategory(title = stringResource(R.string.crop_logo))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "none") {
                        LogoChip(label = stringResource(R.string.crop_logo_none), bitmap = null, selected = logoId == null, onClick = { logoId = null })
                    }
                    items(logos, key = { it.id }) { l ->
                        LogoChip(label = l.label, bitmap = logoBitmaps[l.id], selected = logoId == l.id, onClick = { logoId = l.id })
                    }
                }
            }

            Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                CardListGroup {
                    item {
                        SwitchPreference(
                            checked = round,
                            onCheckedChange = { on -> repo.updateSettings { it.copy(roundCorners = on) } },
                            title = stringResource(R.string.crop_round),
                            summary = stringResource(R.string.crop_round_summary),
                        )
                    }
                    item {
                        ArrowPreference(title = stringResource(R.string.logos_title), onClick = { nav.push(Route.Logos) })
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.crop_reset),
                    onClick = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(if (url != null) R.string.crop_save else R.string.crop_save_only),
                    enabled = source != null && !saving,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        val bmp = source ?: return@TextButton
                        saving = true
                        val rect = CropGeometry(frame, bmp.width, bmp.height, zoom, pan).cropRect
                        val selectedLogo = logoId
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                runCatching {
                                    val scaled = ImageStore.cropAndScale(bmp, rect, Constants.OUTPUT_WIDTH, Constants.OUTPUT_HEIGHT)
                                    val logo = selectedLogo?.let { repo.logos.load(it) }
                                    val out = ImageStore.finish(scaled, logo, round)
                                    logo?.recycle()
                                    repo.saveImageFor(url, out)
                                    out.recycle()
                                }.isSuccess
                            }
                            saving = false
                            if (ok) {
                                context.toast(if (url != null) R.string.toast_applied else R.string.toast_saved)
                                // Back to the card being edited (the picker may have been opened from the gallery).
                                if (url != null) nav.popUntil { it is Route.CardEdit } else nav.pop()
                            } else {
                                context.toast(R.string.crop_failed)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LogoChip(
    label: String,
    bitmap: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    Column(modifier = modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Constants.CARD_RATIO)
                .clip(RoundedCornerShape(10.dp))
                // Dark tile so white marks stay visible, like the original module's black thumbnails.
                .background(Color(0xFF3A3A3C))
                .border(2.dp, if (selected) colors.primary else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            style = COUITheme.textStyles.bodyXS,
            color = if (selected) colors.primary else colors.labelSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
