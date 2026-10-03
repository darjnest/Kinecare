package com.darjnest.kinecare.core.common.domain.model

/**
 * Disciplina que ofrece un [Profesional] y que el Cliente elige en el
 * selector de busqueda. Se persiste en `profesionales/{id}.tiposAtencion`
 * (ver docs/DATA_MODEL.md) por su `name`; es el unico campo por el que se
 * filtra la busqueda — `especialidades` queda como texto libre para mostrar.
 * Movido desde `:feature:search`: `:feature:auth` lo necesita al crear el
 * perfil profesional y las features nunca se dependen entre si.
 */
enum class TipoAtencion { KINESIOLOGIA, MASOTERAPIA }
