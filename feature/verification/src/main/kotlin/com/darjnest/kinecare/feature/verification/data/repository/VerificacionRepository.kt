package com.darjnest.kinecare.feature.verification.data.repository

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.IntentoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError

/**
 * Verificacion de identidad del Profesional con un proveedor externo (Didit).
 * Todo pasa por Cloud Functions (`solicitarVerificacion`, `estadoVerificacion`):
 * el cliente nunca resuelve ni persiste el resultado, ni guarda biometria ni
 * documentos (docs/ARCHITECTURE.md#seguridad). Solo muestra el estado que
 * informa la funcion.
 */
interface VerificacionRepository {
    /**
     * El profesional autenticado abre (o continua) su verificacion de
     * identidad. Retorna la URL hospedada por el proveedor, que debe abrirse en
     * un Custom Tab. Es idempotente: con una verificacion abierta devuelve la misma.
     */
    suspend fun solicitar(): Result<IntentoVerificacion, SolicitarVerificacionError>

    /**
     * Estado de la verificacion segun el backend (que relee al proveedor si
     * sigue `PENDIENTE`). Con [solicitudId] `null` es la verificacion de
     * identidad mas reciente de quien llama. Al volver del Custom Tab el
     * resultado nunca sale de los parametros del deep link.
     */
    suspend fun consultarEstado(solicitudId: String?): Result<EstadoSolicitudVerificacion, EstadoVerificacionError>
}
