package com.darjnest.kinecare.core.network.firebase

import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion

/**
 * Contenido de `profesionales/{uid}` recien creado al registrarse un
 * Profesional (ver docs/DATA_MODEL.md). Vive en `:core:network` y no en
 * `:feature:auth` porque es el formato del documento que
 * `ProfesionalRepositoryImpl` lee y la busqueda filtra: si falta
 * `tiposAtencion` el perfil nunca matchea el array-contains, y si falta
 * `calificacionPromedio` Firestore lo excluye del `orderBy`.
 *
 * Los campos de reputacion y verificacion parten en cero/`NO_SOLICITADO`:
 * la Security Rule de `create` exige exactamente esos valores, asi un
 * profesional no puede autoasignarse insignias ni calificacion.
 */
fun perfilProfesionalInicial(tiposAtencion: Collection<TipoAtencion>): Map<String, Any> = mapOf(
    "tiposAtencion" to tiposAtencion.distinct().sortedBy { it.ordinal }.map { it.name },
    "especialidades" to emptyList<String>(),
    "rnpi" to "",
    "descripcion" to "",
    "calificacionPromedio" to 0.0,
    "totalResenas" to 0L,
    "estadoVerificacionGeneral" to EstadoVerificacion.NO_SOLICITADO.name,
    "insignias" to emptyList<Any>(),
    "disponibilidad" to emptyList<Any>(),
)
