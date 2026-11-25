package com.wu.nfccardskin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Navigation icons in two weights, like the ColorOS tab bars: an outline for unselected tabs and a
 * filled glyph for the selected one. COUI's extended set mixes filled (Home, BankCards) and outline
 * (Album, Settings) glyphs, which made two tabs look selected at once. All are drawn black and tinted
 * by the bar.
 */

val TabHome: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabHome",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M3.6,11.2L12,4L20.4,11.2M5.6,9.8V18.9A1.6,1.6 0 0 0 7.2,20.5H16.8A1.6,1.6 0 0 0 18.4,18.9V9.8M10,20.5V15.6A0.8,0.8 0 0 1 10.8,14.8H13.2A0.8,0.8 0 0 1 14,15.6V20.5"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
    }.build()
}

val TabHomeFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabHomeFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M12,3.2L3.3,10.6A0.9,0.9 0 0 0 4.5,11.9L4.9,11.6V19.3A1.9,1.9 0 0 0 6.8,21.2H9.4V15.6A1.4,1.4 0 0 1 10.8,14.2H13.2A1.4,1.4 0 0 1 14.6,15.6V21.2H17.2A1.9,1.9 0 0 0 19.1,19.3V11.6L19.5,11.9A0.9,0.9 0 0 0 20.7,10.6Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
    }.build()
}

val TabCards: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabCards",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M5.2,5.2H18.8A2.6,2.6 0 0 1 21.4,7.8V16.2A2.6,2.6 0 0 1 18.8,18.8H5.2A2.6,2.6 0 0 1 2.6,16.2V7.8A2.6,2.6 0 0 1 5.2,5.2ZM2.8,9.6H21.2M6.2,15H10"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
    }.build()
}

val TabCardsFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabCardsFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M4.8,4.4H19.2A3,3 0 0 1 22.2,7.4V16.6A3,3 0 0 1 19.2,19.6H4.8A3,3 0 0 1 1.8,16.6V7.4A3,3 0 0 1 4.8,4.4ZM1.8,8.6H22.2V10.6H1.8ZM6.3,14.1H9.7A0.9,0.9 0 0 1 10.6,15V15A0.9,0.9 0 0 1 9.7,15.9H6.3A0.9,0.9 0 0 1 5.4,15V15A0.9,0.9 0 0 1 6.3,14.1Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
    }.build()
}

val TabGallery: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabGallery",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M6,4H18A3,3 0 0 1 21,7V17A3,3 0 0 1 18,20H6A3,3 0 0 1 3,17V7A3,3 0 0 1 6,4ZM3.4,17.2L8.6,12L13.2,16.6L15.8,14L20.6,18.8M14,8.9A1.6,1.6 0 1 0 17.2,8.9A1.6,1.6 0 1 0 14,8.9Z"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
    }.build()
}

val TabGalleryFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabGalleryFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M5.6,3.2H18.4A3.4,3.4 0 0 1 21.8,6.6V17.4A3.4,3.4 0 0 1 18.4,20.8H5.6A3.4,3.4 0 0 1 2.2,17.4V6.6A3.4,3.4 0 0 1 5.6,3.2ZM13.7,8.9A1.9,1.9 0 1 0 17.5,8.9A1.9,1.9 0 1 0 13.7,8.9ZM4.6,18.6L8.6,14.4L12.3,18.1L15.8,14.6L19.8,18.6Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
    }.build()
}

val TabSettings: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M10.22,4.82 L10.47,2.83 L13.53,2.83 L13.78,4.82 L15.82,5.66 L17.41,4.43 L19.57,6.59 L18.34,8.18 L19.18,10.22 L21.17,10.47 L21.17,13.53 L19.18,13.78 L18.34,15.82 L19.57,17.41 L17.41,19.57 L15.82,18.34 L13.78,19.18 L13.53,21.17 L10.47,21.17 L10.22,19.18 L8.18,18.34 L6.59,19.57 L4.43,17.41 L5.66,15.82 L4.82,13.78 L2.83,13.53 L2.83,10.47 L4.82,10.22 L5.66,8.18 L4.43,6.59 L6.59,4.43 L8.18,5.66ZM9.1,12A2.9,2.9 0 1 0 14.9,12A2.9,2.9 0 1 0 9.1,12Z"),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
    }.build()
}

val TabSettingsFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "TabSettingsFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
            addPath(
                pathData = addPathNodes("M10.05,4.14 L10.34,2.04 L13.66,2.04 L13.95,4.14 L16.18,5.06 L17.87,3.78 L20.22,6.13 L18.94,7.82 L19.86,10.05 L21.96,10.34 L21.96,13.66 L19.86,13.95 L18.94,16.18 L20.22,17.87 L17.87,20.22 L16.18,18.94 L13.95,19.86 L13.66,21.96 L10.34,21.96 L10.05,19.86 L7.82,18.94 L6.13,20.22 L3.78,17.87 L5.06,16.18 L4.14,13.95 L2.04,13.66 L2.04,10.34 L4.14,10.05 L5.06,7.82 L3.78,6.13 L6.13,3.78 L7.82,5.06ZM8.8,12A3.2,3.2 0 1 0 15.2,12A3.2,3.2 0 1 0 8.8,12Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
    }.build()
}
