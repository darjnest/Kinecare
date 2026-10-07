package com.darjnest.kinecare.feature.payment.data.repository

import com.darjnest.kinecare.feature.payment.domain.ConectarMercadoPagoError
import com.darjnest.kinecare.feature.payment.domain.EstadoPagoError
import com.darjnest.kinecare.feature.payment.domain.IniciarPagoError
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.feature.payment.domain.IntentoPago
import com.darjnest.kinecare.core.common.result.Result

/**
 * Pagos con Mercado Pago (Marketplace + Checkout Pro). Todo pasa por Cloud
 * Functions (`iniciarPago`, `estadoPago`, `conectarMercadoPago`): el cliente
 * nunca resuelve montos, comisiones ni estados de pago localmente
 * (docs/ARCHITECTURE.md#seguridad). El detalle del pago se lee de la reserva
 * (`ReservaRepository`).
 */
interface PagoRepository {
    /**
     * El cliente autenticado paga una reserva `CONFIRMADA`. Retorna la URL de
     * Mercado Pago que debe abrirse en un Custom Tab. Reintentar mientras el
     * pago sigue `PENDIENTE` devuelve el mismo intento.
     */
    suspend fun iniciar(reservaId: String): Result<IntentoPago, IniciarPagoError>

    /**
     * Estado del pago segun el backend (que relee Mercado Pago si sigue
     * `PENDIENTE`). Se usa al volver del Custom Tab: el resultado nunca sale
     * de los parametros del deep link.
     */
    suspend fun consultarEstado(pagoId: String): Result<EstadoPago, EstadoPagoError>

    /**
     * El profesional autenticado pide la URL de autorizacion OAuth para
     * vincular su cuenta de Mercado Pago y poder cobrar.
     */
    suspend fun obtenerUrlConexion(): Result<String, ConectarMercadoPagoError>
}
