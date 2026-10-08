package com.darjnest.kinecare.feature.payment.presentation.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UrlsTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=1",
            "https://mercadopago.cl/checkout",
            "https://auth.mercadopago.cl/authorization?client_id=1&state=xyz",
            "https://auth.mercadopago.com/authorization?state=x",
            "https://sandbox.mercadopago.com/checkout/v1/redirect",
            "https://www.mercadolibre.cl/pago",
            "https://www.mercadolibre.com/pago",
            "HTTPS://WWW.MERCADOPAGO.CL/checkout",
            "https://www.mercadopago.cl:443/checkout",
        ],
    )
    fun `se abren URLs https de Mercado Pago`(url: String) {
        assertTrue(esUrlDeMercadoPago(url), url)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://www.mercadopago.cl",
            "intent://scan/#Intent;scheme=zxing;end",
            "kinecare://pago/resultado",
            "javascript:alert(1)",
            "file:///sdcard/mercadopago.cl",
            "",
            "   ",
            "mercadopago.cl",
            "https://",
        ],
    )
    fun `se rechazan otros esquemas y entradas vacias`(url: String) {
        assertFalse(esUrlDeMercadoPago(url), url)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://mp.test/checkout",
            "https://evil.com/checkout",
            "https://mercadopago.cl.evil.com/checkout",
            "https://www.mercadopago.cl.evil.com",
            "https://evil.com/?x=mercadopago.cl",
            "https://evil.com/mercadopago.cl",
            "https://evil.com/#mercadopago.cl",
            "https://mercadopago.cl@evil.com/checkout",
            "https://www.mercadopago.cl:pass@evil.com/",
            "https://evilmercadopago.cl/checkout",
            "https://notmercadopago.com",
            "https://mercadopago.cl%2eevil.com",
            "https://evil.com\\@mercadopago.cl",
            "https://evil.com:8443@www.mercadopago.cl/",
            "https://www.mercadopago.cl:8443/checkout",
        ],
    )
    fun `se rechazan hosts que no son de Mercado Pago`(url: String) {
        assertFalse(esUrlDeMercadoPago(url), url)
    }

    @Test
    fun `una URL mal formada retorna false sin lanzar`() {
        assertFalse(esUrlDeMercadoPago("https://www.mercadopago.cl/con espacio"))
        assertFalse(esUrlDeMercadoPago("https://[::1"))
        assertFalse(esUrlDeMercadoPago("https://%zz.mercadopago.cl"))
    }
}
