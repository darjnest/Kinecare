package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

/**
 * Si el profesional conecto su cuenta de Mercado Pago para cobrar sus
 * atenciones. Solo expone el estado de la conexion: los tokens OAuth viven
 * unicamente en Cloud Functions y la app nunca los ve.
 */
data class EstadoMercadoPago(
    val conectado: Boolean,
    /** Cuando se completo la conexion; `null` si no esta conectada o el dato no vino. */
    val conectadoEn: Instant? = null,
) {
    companion object {
        /** Sin documento `mercadoPagoEstados/{uid}`: la cuenta no esta conectada. */
        val NoConectada = EstadoMercadoPago(conectado = false)
    }
}
