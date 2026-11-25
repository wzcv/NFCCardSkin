package com.wu.nfccardskin.core

import androidx.compose.runtime.Immutable

/** A card face URL the hook saw in the wallet. */
@Immutable
data class SeenCard(
    val url: String,
    val title: String?,
    val source: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

/** An image in the module gallery (files/images). */
@Immutable
data class GalleryImage(
    val name: String,
    val path: String,
    val modified: Long,
    val size: Long,
)

@Immutable
data class HookStatus(
    val walletVersion: String,
    val process: String,
    val hooks: Int,
    val at: Long,
)

@Immutable
data class AppSettings(
    val enabled: Boolean = true,
    val testMode: Boolean = false,
    val debug: Boolean = false,
    val glideFallback: Boolean = false,
    val recordAll: Boolean = false,
    val devMode: Boolean = false,
    val customHook: CustomHook = CustomHook(),
    /** 0 = follow system, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
    val blur: Boolean = true,
    /** Round the corners of new card faces (the wallet clips them anyway). */
    val roundCorners: Boolean = false,
    val firstRun: Boolean = true,
    /** Load online library thumbnails through the wsrv.nl image proxy (small WebP). */
    val catalogProxy: Boolean = true,
)

@Immutable
data class ServiceState(
    val connected: Boolean = false,
    val framework: String = "",
    /** Xposed service API of the framework (101, 102…); 0 when not connected. */
    val apiVersion: Int = 0,
    /** null = unknown (service not connected). */
    val walletInScope: Boolean? = null,
    /** Wallet processes running the module (API 102 only; null = not available). */
    val targets: List<WalletProcess>? = null,
) {
    val supportsHotReload: Boolean get() = apiVersion >= 102
}

/** A hooked wallet process as LSPosed reports it (API 102 `HookedTarget`). */
@Immutable
data class WalletProcess(
    val processName: String,
    val pid: Int,
    /** UP_TO_DATE, STALE (runs an older module build), RELOADING or FAILED. */
    val state: String,
) {
    val needsReload: Boolean get() = state == "STALE" || state == "FAILED"
}

/** A mapping value as the app stores it: a gallery image or a network URL. */
sealed interface SkinTarget {
    data class Image(val name: String) : SkinTarget
    data class Url(val url: String) : SkinTarget

    fun encode(): String = when (this) {
        is Image -> Constants.MAPPING_IMAGE_PREFIX + name
        is Url -> url
    }

    companion object {
        fun decode(value: String?): SkinTarget? = when {
            value.isNullOrBlank() -> null
            value.startsWith(Constants.MAPPING_IMAGE_PREFIX) -> Image(value.removePrefix(Constants.MAPPING_IMAGE_PREFIX))
            UrlKey.isHttp(value) -> Url(value)
            else -> null
        }
    }
}

@Immutable
data class UiState(
    val cards: List<SeenCard> = emptyList(),
    val mappings: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** User-given card names (url -> name), shown instead of the wallet's title. */
    val aliases: Map<String, String> = emptyMap(),
    val images: List<GalleryImage> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val hookStatus: HookStatus? = null,
) {
    /** Same order as the original module: replaced cards first, then most recently seen. */
    val visibleCards: List<SeenCard>
        get() = cards.filter { it.url !in hidden }
            .sortedWith(compareByDescending<SeenCard> { it.url in mappings }.thenByDescending { it.lastSeen })

    val hiddenCards: List<SeenCard> get() = cards.filter { it.url in hidden }

    fun targetOf(url: String): SkinTarget? = SkinTarget.decode(mappings[url])

    fun nameOf(card: SeenCard): String? = aliases[card.url] ?: card.title

    fun usages(imageName: String): Int {
        val encoded = SkinTarget.Image(imageName).encode()
        return mappings.values.count { it == encoded }
    }
}
