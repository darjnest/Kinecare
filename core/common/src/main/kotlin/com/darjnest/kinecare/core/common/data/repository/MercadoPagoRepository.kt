package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.MercadoPagoError
import com.darjnest.kinecare.core.common.domain.model.EstadoMercadoPago
import com.darjnest.kinecare.core.common.result.Result

/**
 * Conexion de la cuenta Mercado Pago del profesional (OAuth). El flujo y los
 * tokens viven solo en Cloud Functions; la app lee el estado de
 * `mercadoPagoEstados/{uid}` (solo lectura, nadie escribe ahi desde el
 * cliente) y pide a las funciones iniciar o desconectar la conexion.
 */
interface MercadoPagoRepository {
    /** Estado de la conexion; documento ausente = [EstadoMercadoPago.NoConectada]. */
    suspend fun obtenerEstado(profesionalId: String): Result<EstadoMercadoPago, MercadoPagoError>

    /**
     * `iniciarConexionMercadoPago`: retorna la URL de autorizacion de Mercado
     * Pago, que la UI debe abrir en el navegador.
     */
    suspend fun iniciarConexion(): Result<String, MercadoPagoError>

    /** `desconectarMercadoPago`: el profesional deja de poder cobrar con su cuenta. */
    suspend fun desconectar(): Result<Unit, MercadoPagoError>
}
