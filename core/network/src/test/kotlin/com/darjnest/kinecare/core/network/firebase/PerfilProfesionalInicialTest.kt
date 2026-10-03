package com.darjnest.kinecare.core.network.firebase

import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerfilProfesionalInicialTest {

    @Test
    fun `escribe tiposAtencion por nombre, sin duplicados y en orden estable`() {
        val perfil = perfilProfesionalInicial(
            listOf(TipoAtencion.MASOTERAPIA, TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA),
        )

        assertEquals(listOf("KINESIOLOGIA", "MASOTERAPIA"), perfil["tiposAtencion"])
    }

    @Test
    fun `parte sin reputacion ni verificacion, con calificacionPromedio presente para el orderBy`() {
        val perfil = perfilProfesionalInicial(setOf(TipoAtencion.KINESIOLOGIA))

        assertEquals(0.0, perfil["calificacionPromedio"])
        assertEquals(0L, perfil["totalResenas"])
        assertEquals("NO_SOLICITADO", perfil["estadoVerificacionGeneral"])
        assertEquals(emptyList<Any>(), perfil["insignias"])
        assertEquals(emptyList<Any>(), perfil["especialidades"])
        assertEquals(emptyList<Any>(), perfil["disponibilidad"])
    }

    @Test
    fun `solo escribe los campos que la Security Rule de create permite`() {
        val perfil = perfilProfesionalInicial(setOf(TipoAtencion.KINESIOLOGIA))

        assertEquals(
            setOf(
                "tiposAtencion", "especialidades", "rnpi", "descripcion", "calificacionPromedio",
                "totalResenas", "estadoVerificacionGeneral", "insignias", "disponibilidad",
            ),
            perfil.keys,
        )
    }
}
