package com.wu.nfccardskin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wu.nfccardskin.R
import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.SkinTarget
import com.wu.nfccardskin.core.UiState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.io.File

/** A card face at the wallet's ratio: [model] is a URL / file URI string, or null for a placeholder. */
@Composable
fun CardFace(
    model: Any?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(Constants.CARD_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(COUITheme.colorScheme.fillThin),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            if (placeholder != null) {
                Text(
                    text = placeholder,
                    modifier = Modifier.padding(8.dp),
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** What to show for a mapping value. */
fun SkinTarget?.previewModel(state: UiState): Any? = when (this) {
    is SkinTarget.Image -> {
        val wanted = this.name
        state.images.firstOrNull { it.name == wanted }?.let { Uri.fromFile(File(it.path)).toString() }
    }
    is SkinTarget.Url -> url
    null -> null
}

fun Context.toast(resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("card url", text))
    toast(R.string.toast_copied)
}

/** Two-button confirmation; [destructive] puts the action in the red neutral slot like COUI. */
@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissText: String? = null,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
    ) {
        val cancel = DialogButtonBarAction(dismissText ?: stringResource(R.string.common_cancel), onClick = onDismiss)
        if (destructive) {
            DialogButtonBar(negative = cancel, positive = null, neutral = DialogButtonBarAction(confirmText, onClick = onConfirm))
        } else {
            DialogButtonBar(negative = cancel, positive = DialogButtonBarAction(confirmText, recommended = true, onClick = onConfirm))
        }
    }
}

/** One-button information dialog (usage guide, FAQ). */
@Composable
fun InfoDialog(
    show: Boolean,
    title: String,
    summary: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
    ) {
        DialogButtonBar(
            negative = null,
            positive = DialogButtonBarAction(stringResource(R.string.common_confirm), recommended = true, onClick = onDismiss),
        )
    }
}

/**
 * Single-line input dialog. [validate] returns an error message or null; an accepted value is
 * passed trimmed to [onConfirm].
 */
@Composable
fun InputDialog(
    show: Boolean,
    title: String,
    initial: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    validate: (String) -> String? = { null },
) {
    val field = rememberTextFieldState(initial)
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(show) {
        if (show) {
            field.setTextAndPlaceCursorAtEnd(initial)
            error = null
        }
    }
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = show,
        modifier = modifier,
        title = title,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                state = field,
                modifier = Modifier.fillMaxWidth(),
                label = label,
                isError = error != null,
                showClearButton = true,
            )
            error?.let {
                Text(text = it, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.error)
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(stringResource(R.string.common_cancel), onClick = onDismiss),
                positive = DialogButtonBarAction(
                    text = stringResource(R.string.common_confirm),
                    recommended = true,
                    onClick = {
                        val value = field.text.toString().trim()
                        val e = validate(value)
                        if (e == null) onConfirm(value) else error = e
                    },
                ),
            )
        }
    }
}

/** COUI bottom sheet with the page blur behind it. */
@Composable
fun ActionSheet(
    show: Boolean,
    title: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val blur = panelBlur(MaterialBlurDefaults::bottomSheet)
    OverlayBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismiss,
        content = content,
    )
}
