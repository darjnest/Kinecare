package com.darjnest.kinecare.feature.verification.domain

import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion

/**
 * Estado de la verificacion de identidad segun la Cloud Function
 * `estadoVerificacion`. [solicitudId] es la solicitud a la que se refiere
 * (ausente con [EstadoVerificacion.NO_SOLICITADO]) y [motivo] solo acompana a
 * [EstadoVerificacion.RECHAZADO]; puede faltar aun asi si el backend entrega
 * uno que esta version no conoce.
 */
data class EstadoSolicitudVerificacion(
    val estado: EstadoVerificacion,
    val solicitudId: String? = null,
    val motivo: MotivoRechazoVerificacion? = null,
)
