package com.wu.nfccardskin.core

/** Values shared by the module app and the hook running inside the wallet process. */
object Constants {
    /** ColorOS / OPPO / OnePlus / realme wallet (钱包). */
    const val WALLET_PACKAGE = "com.finshell.wallet"

    const val MODULE_PACKAGE = "com.wu.nfccardskin"

    /** [SkinProvider] authority: images for the wallet, config pull and card reports from the hook. */
    const val AUTHORITY = "com.wu.nfccardskin.provider"
    const val IMAGE_URI_PREFIX = "content://$AUTHORITY/images/"
    const val TEST_IMAGE_URI = "content://$AUTHORITY/test/card.png"

    /** LSPosed remote preferences group / key that carries the hook config JSON. */
    const val REMOTE_GROUP = "config"
    const val REMOTE_KEY_JSON = "json"

    // ContentProvider.call methods / extras.
    const val CALL_GET_CONFIG = "get_config"
    const val CALL_REPORT = "report"
    const val CALL_HELLO = "hello"
    const val EXTRA_JSON = "json"
    const val EXTRA_URL = "url"
    const val EXTRA_TITLE = "title"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_VERSION = "version"
    const val EXTRA_PROCESS = "process"
    const val EXTRA_HOOKS = "hooks"

    /** Value prefix of a mapping to an image in the module gallery. */
    const val MAPPING_IMAGE_PREFIX = "img:"

    /** ColorOS wallet card face ratio (FinUINfcCardFaceView uses 1.62). */
    const val CARD_RATIO = 1.62f
    const val OUTPUT_WIDTH = 1296
    const val OUTPUT_HEIGHT = 800
}
