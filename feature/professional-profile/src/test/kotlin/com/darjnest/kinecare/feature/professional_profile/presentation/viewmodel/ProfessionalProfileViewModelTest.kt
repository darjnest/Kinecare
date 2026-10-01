@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.ProfessionalProfileRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProfessionalProfileViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()

    private val id = "prof-1"

    private fun crearViewModel(profesionalId: String? = id): ProfessionalProfileViewModel {
        val handle = if (profesionalId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf(ARG_PROFESIONAL_ID to profesionalId))
        }
        return ProfessionalProfileViewModel(handle, profesionalRepository)
    }

    private fun profesional(
        servicios: List<Servicio> = emptyList(),
        insignias: List<Insignia> = emptyList(),
        disponibilidad: List<Disponibilidad> = emptyList(),
        totalResenas: Int = 12,
        descripcion: String = "  Kinesiologa deportiva  ",
    ) = Profesional(
        usuario = Usuario(
            id = id,
            nombre = "Ana Soto Rojas",
            rut = "12345678-5",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        especialidades = listOf("Kinesiología"),
        rnpi = "123456",
        servicios = servicios,
        insignias = insignias,
        disponibilidad = disponibilidad,
        calificacionPromedio = 4.8,
        totalResenas = totalResenas,
        descripcion = descripcion,
        estadoVerificacionGeneral = EstadoVerificacion.APROBADO,
    )

    private fun servicio(id: String, activo: Boolean) = Servicio(
        id = id,
        nombre = "Servicio $id",
        descripcion = "",
        modalidad = ModalidadServicio.DOMICILIO,
        duracionMinutos = 60,
        precio = 25_000L,
        activo = activo,
    )

    private fun <T : Any> requerir(valor: T?): T {
        assertNotNull(valor)
        return valor!!
    }

    @Test
    fun `carga inicial exitosa mapea el profesional al estado`() = runTest {
        val insignia = Insignia(
            TipoInsignia.IDENTIDAD,
            EstadoVerificacion.APROBADO,
            "Cedula verificada",
            Instant.fromEpochMilliseconds(1_000L),
        )
        coEvery { profesionalRepository.obtenerPorId(id) } returns
            Result.Success(profesional(insignias = listOf(insignia)))

        val viewModel = crearViewModel()

        val state = viewModel.state.value
        assertFalse(state.cargando)
        assertNull(state.error)
        val perfil = requerir(state.perfil)
        assertEquals("Ana Soto Rojas", perfil.nombre)
        assertEquals("AS", perfil.iniciales)
        assertEquals(listOf("Kinesiología"), perfil.especialidades)
        assertEquals("123456", perfil.rnpi)
        assertEquals(4.8, perfil.calificacionPromedio)
        assertEquals(12, perfil.totalResenas)
        assertEquals("Kinesiologa deportiva", perfil.descripcion)
        val identidad = perfil.insignias.first { it.tipo == TipoInsignia.IDENTIDAD }
        assertEquals(EstadoVerificacion.APROBADO, identidad.estado)
        assertEquals("Cedula verificada", identidad.detalle)
        assertEquals(1_000L, identidad.fechaActualizacionMillis)
        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(id) }
    }

    @Test
    fun `mientras carga el estado esta cargando y sin perfil`() = runTest {
        val gate = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(id) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.cargando)
        assertNull(viewModel.state.value.perfil)

        gate.complete(Result.Success(profesional()))
        assertFalse(viewModel.state.value.cargando)
        assertNotNull(viewModel.state.value.perfil)
    }

    @Test
    fun `cada ProfesionalError produce su estado de error`() = runTest {
        ProfesionalError.entries.forEach { error ->
            coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Error(error)

            val viewModel = crearViewModel()

            val state = viewModel.state.value
            assertFalse(state.cargando, "cargando colgado para $error")
            assertEquals(error, state.error)
            assertNull(state.perfil)
        }
    }

    @Test
    fun `sin id en los argumentos queda en error NO_ENCONTRADO sin llamar al repositorio`() = runTest {
        val viewModel = crearViewModel(profesionalId = null)

        assertEquals(ProfesionalError.NO_ENCONTRADO, viewModel.state.value.error)
        assertFalse(viewModel.state.value.cargando)
        coVerify(exactly = 0) { profesionalRepository.obtenerPorId(any()) }
    }

    @Test
    fun `Reintentar recarga y sale del estado de error`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Error(ProfesionalError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertEquals(ProfesionalError.SIN_INTERNET, viewModel.state.value.error)

        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(profesional())
        viewModel.state.test {
            assertEquals(ProfesionalError.SIN_INTERNET, awaitItem().error)
            viewModel.onAction(ProfessionalProfileAction.Reintentar)
            val final = expectMostRecentItem()
            assertNull(final.error)
            assertFalse(final.cargando)
            assertNotNull(final.perfil)
        }
        coVerify(exactly = 2) { profesionalRepository.obtenerPorId(id) }
    }

    @Test
    fun `solo se exponen los servicios activos`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(
            profesional(servicios = listOf(servicio("a", true), servicio("b", false), servicio("c", true))),
        )

        val viewModel = crearViewModel()

        assertEquals(listOf("a", "c"), viewModel.state.value.perfil?.servicios?.map { it.id })
    }

    @Test
    fun `los tipos de insignia faltantes se muestran como NO_SOLICITADO`() = runTest {
        val credenciales = Insignia(
            TipoInsignia.CREDENCIALES,
            EstadoVerificacion.PENDIENTE,
            null,
            Instant.fromEpochMilliseconds(5L),
        )
        coEvery { profesionalRepository.obtenerPorId(id) } returns
            Result.Success(profesional(insignias = listOf(credenciales)))

        val viewModel = crearViewModel()

        val insignias = requerir(viewModel.state.value.perfil).insignias
        assertEquals(TipoInsignia.entries, insignias.map { it.tipo })
        assertEquals(EstadoVerificacion.PENDIENTE, insignias.first { it.tipo == TipoInsignia.CREDENCIALES }.estado)
        val faltantes = insignias.filter { it.tipo != TipoInsignia.CREDENCIALES }
        assertEquals(3, faltantes.size)
        faltantes.forEach {
            assertEquals(EstadoVerificacion.NO_SOLICITADO, it.estado)
            assertNull(it.detalle)
            assertNull(it.fechaActualizacionMillis)
        }
    }

    @Test
    fun `sin resenas la calificacion se oculta`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns
            Result.Success(profesional(totalResenas = 0))

        val viewModel = crearViewModel()

        assertNull(viewModel.state.value.perfil?.calificacionPromedio)
    }

    @Test
    fun `el horario agrupa turnos activos por dia en orden semanal`() = runTest {
        fun turno(dia: DayOfWeek, inicio: Int, fin: Int, activo: Boolean = true) =
            Disponibilidad(dia, LocalTime(inicio, 0), LocalTime(fin, 0), activo)
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(
            profesional(
                disponibilidad = listOf(
                    turno(DayOfWeek.WEDNESDAY, 15, 18),
                    turno(DayOfWeek.MONDAY, 9, 13),
                    turno(DayOfWeek.WEDNESDAY, 9, 12),
                    turno(DayOfWeek.FRIDAY, 9, 12, activo = false),
                ),
            ),
        )

        val viewModel = crearViewModel()

        val horario = requerir(viewModel.state.value.perfil).horario
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), horario.map { it.dia })
        assertEquals(
            listOf(LocalTime(9, 0), LocalTime(15, 0)),
            horario.last().turnos.map { it.inicio },
        )
    }

    @Test
    fun `AlternarInsignia expande y vuelve a contraer`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        assertTrue(viewModel.state.value.insigniasExpandidas.isEmpty())

        viewModel.onAction(ProfessionalProfileAction.AlternarInsignia(TipoInsignia.IDENTIDAD))
        assertEquals(setOf(TipoInsignia.IDENTIDAD), viewModel.state.value.insigniasExpandidas)

        viewModel.onAction(ProfessionalProfileAction.AlternarInsignia(TipoInsignia.HISTORIAL))
        assertEquals(
            setOf(TipoInsignia.IDENTIDAD, TipoInsignia.HISTORIAL),
            viewModel.state.value.insigniasExpandidas,
        )

        viewModel.onAction(ProfessionalProfileAction.AlternarInsignia(TipoInsignia.IDENTIDAD))
        assertEquals(setOf(TipoInsignia.HISTORIAL), viewModel.state.value.insigniasExpandidas)
    }

    @Test
    fun `VerResenas es navegacion y no cambia el estado ni recarga`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(ProfessionalProfileAction.VerResenas(id))

        assertEquals(antes, viewModel.state.value)
        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(id) }
    }

    @Test
    fun `Reservar es navegacion y no cambia el estado ni recarga`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(ProfessionalProfileAction.Reservar(id))
        viewModel.onAction(ProfessionalProfileAction.Reservar(id, servicioId = "serv-1"))

        assertEquals(antes, viewModel.state.value)
        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(id) }
    }

    @Test
    fun `el perfil expone el id que se pasa al listado de resenas y el total guardado`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(id) } returns
            Result.Success(profesional(totalResenas = 7))

        val perfil = requerir(crearViewModel().state.value.perfil)

        assertEquals(id, perfil.id)
        assertEquals(7, perfil.totalResenas)
    }

    @Test
    fun `la clave del argumento coincide con la propiedad de la ruta`() {
        assertEquals(
            ARG_PROFESIONAL_ID,
            ProfessionalProfileRoute.serializer().descriptor.getElementName(0),
        )
    }
}
