package com.wu.nfccardskin

import com.wu.nfccardskin.core.Constants
import com.wu.nfccardskin.core.SkinConfig
import com.wu.nfccardskin.core.UrlKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkinConfigTest {
    private val face = "https://cdn.example.com/nfc/card/w720h444/abc.png?sign=1&t=2"
    private val target = Constants.IMAGE_URI_PREFIX + "card-1.png?v=1"

    @Test
    fun exactAndNormalizedMatch() {
        val cfg = SkinConfig(mappings = mapOf(face to target))
        assertEquals(target, cfg.replacementFor(face))
        // New signature / size segment / http scheme from a later wallet session still matches.
        assertEquals(target, cfg.replacementFor("http://cdn.example.com/nfc/card/abc.png?sign=9"))
        assertNull(cfg.replacementFor("https://cdn.example.com/nfc/card/other.png"))
    }

    @Test
    fun disabledTestModeAndOwnUrls() {
        assertNull(SkinConfig(enabled = false, mappings = mapOf(face to target)).replacementFor(face))
        assertEquals(Constants.TEST_IMAGE_URI, SkinConfig(testMode = true).replacementFor(face))
        assertNull(SkinConfig(testMode = true).replacementFor(target))
        assertEquals(false, UrlKey.isCardCandidate(target))
    }

    @Test
    fun jsonRoundTrip() {
        val cfg = SkinConfig(enabled = true, testMode = false, debug = true, glideFallback = true, mappings = mapOf(face to target), version = 42)
        val back = SkinConfig.fromJson(cfg.toJson())!!
        assertEquals(cfg.mappings, back.mappings)
        assertEquals(42L, back.version)
        assertEquals(true, back.glideFallback)
        assertNull(SkinConfig.fromJson("not json"))
    }
}
