package com.darjnest.kinecare.feature.verification.presentation.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UrlsTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://verify.didit.me/session/abc123",
            "https://verify.didit.me/",
            "https://didit.me/verify?token=x",
            "https://app.verify.didit.me/s/1",
            "HTTPS://VERIFY.DIDIT.ME/session/abc",
            "https://verify.didit.me:443/session/abc",
        ],
    )
    fun `se abren URLs https de Didit`(url: String) {
        assertTrue(esUrlDeDidit(url), url)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://verify.didit.me/session/abc",
            "javascript:alert(1)",
            "intent://verify.didit.me#Intent;scheme=https;end",
            "kinecare://verificacion/resultado",
            "file:///sdcard/didit.me",
            "",
            "   ",
            "verify.didit.me",
            "https://",
        ],
    )
    fun `se rechazan otros esquemas y entradas vacias`(url: String) {
        assertFalse(esUrlDeDidit(url), url)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://evil.com/session",
            "https://evil-didit.me/session",
            "https://evildidit.me/session",
            "https://didit.me.evil.com/session",
            "https://verify.didit.me.evil.com",
            "https://evil.com/?x=didit.me",
            "https://evil.com/didit.me",
            "https://evil.com/#verify.didit.me",
            "https://didit.me@evil.com/session",
            "https://verify.didit.me:pass@evil.com/",
            "https://didit.com/session",
            "https://verify.didit.me:8443/session",
            "https://evil.com\\@verify.didit.me",
        ],
    )
    fun `se rechazan hosts que no son de Didit`(url: String) {
        assertFalse(esUrlDeDidit(url), url)
    }

    @Test
    fun `una URL mal formada retorna false sin lanzar`() {
        assertFalse(esUrlDeDidit("https://verify.didit.me/con espacio"))
        assertFalse(esUrlDeDidit("https://[::1"))
        assertFalse(esUrlDeDidit("https://%zz.didit.me"))
    }
}
