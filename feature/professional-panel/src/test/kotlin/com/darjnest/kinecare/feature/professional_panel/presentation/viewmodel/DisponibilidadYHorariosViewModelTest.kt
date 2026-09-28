@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DisponibilidadYHorariosViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel() = DisponibilidadYHorariosViewModel(profesionalRepository, firebaseAuth)

    private fun horario(dia: DayOfWeek, inicio: Int, fin: Int, activo: Boolean = true) =
        Disponibilidad(dia, LocalTime(inicio, 0), LocalTime(fin, 0), activo)

    private fun profesional(disponibilidad: List<Disponibilidad>) = Profesional(
        usuario = Usuario(
            id = uid,
            nombre = "Ana Soto",
            rut = "12345678-5",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        especialidades = emptyList(),
        rnpi = "",
        servicios = emptyList(),
        insignias = emptyList(),
        disponibilidad = disponibilidad,
        calificacionPromedio = 0.0,
        totalResenas = 0,
        descripcion = "",
        estadoVerificacionGeneral = EstadoVerificacion.NO_SOLICITADO,
    )

    private val semana = listOf(
        horario(DayOfWeek.MONDAY, 9, 13),
        horario(DayOfWeek.MONDAY, 15, 19),
        horario(DayOfWeek.WEDNESDAY, 10, 12),
        horario(DayOfWeek.FRIDAY, 8, 12, activo = false),
    )

    @Test
    fun `numeroDiaDeLaSemana ubica cada dia en la semana lunes a domingo de hoy`() {
        val jueves = LocalDate(2026, 4, 30)

        assertEquals(27, numeroDiaDeLaSemana(jueves, DayOfWeek.MONDAY))
        assertEquals(30, numeroDiaDeLaSemana(jueves, DayOfWeek.THURSDAY))
        // Cruza de mes: el domingo de esa semana es 3 de mayo.
        assertEquals(3, numeroDiaDeLaSemana(jueves, DayOfWeek.SUNDAY))
    }

    @Test
    fun `al inicializar deriva dias y turnos del horario real y selecciona el primer dia activo`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(semana))

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(7, estado.diasLaborales.size)
            assertEquals(
                listOf("MONDAY", "WEDNESDAY"),
                estado.diasLaborales.filter { it.habilitado }.map { it.id },
            )
            assertEquals("MONDAY", estado.diasLaborales.single { it.seleccionado }.id)
            assertEquals(listOf("09:00" to "13:00", "15:00" to "19:00"), estado.turnos.map { it.horaInicio to it.horaFin })
            assertEquals(listOf(TonoTurno.MATUTINO, TonoTurno.VESPERTINO), estado.turnos.map { it.tono })
        }
    }

    @Test
    fun `seleccionar un dia muestra sus turnos incluso si esta inactivo`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(semana))

        val viewModel = crearViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.onAction(DisponibilidadYHorariosAction.SeleccionarDia("FRIDAY"))

            val estado = awaitItem()
            assertEquals("FRIDAY", estado.diasLaborales.single { it.seleccionado }.id)
            assertEquals(listOf(false), estado.turnos.map { it.activo })
        }
    }

    @Test
    fun `un error de carga avisa y bloquea el guardado para no borrar el horario real`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals("No pudimos cargar tu disponibilidad", estado.mensaje)

            viewModel.onAction(DisponibilidadYHorariosAction.GuardarDisponibilidad)
            expectNoEvents()
        }

        coVerify(exactly = 0) { profesionalRepository.actualizarDisponibilidad(any(), any()) }
    }

    @Test
    fun `replicar copia los turnos del dia seleccionado a los demas dias habilitados`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(semana))
        val guardado = slot<List<Disponibilidad>>()
        coEvery { profesionalRepository.actualizarDisponibilidad(uid, capture(guardado)) } returns Result.Success(Unit)

        val viewModel = crearViewModel()
        viewModel.onAction(DisponibilidadYHorariosAction.Replicar)
        viewModel.onAction(DisponibilidadYHorariosAction.GuardarDisponibilidad)

        val miercoles = guardado.captured.filter { it.diaSemana == DayOfWeek.WEDNESDAY }
        assertEquals(listOf(9 to 13, 15 to 19), miercoles.map { it.horaInicio.hour to it.horaFin.hour })
        // El viernes estaba inactivo: no se habilita ni se toca.
        assertEquals(listOf(horario(DayOfWeek.FRIDAY, 8, 12, activo = false)), guardado.captured.filter { it.diaSemana == DayOfWeek.FRIDAY })
        assertTrue(guardado.captured.none { it.diaSemana == DayOfWeek.SUNDAY })
    }

    @Test
    fun `guardar persiste el horario y avisa el resultado`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(semana))
        coEvery { profesionalRepository.actualizarDisponibilidad(uid, semana) } returns Result.Success(Unit)

        val viewModel = crearViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.onAction(DisponibilidadYHorariosAction.GuardarDisponibilidad)

            // Con dispatcher inmediato `guardando` puede colapsar; lo relevante es el estado final.
            var estado = awaitItem()
            if (estado.mensaje == null) estado = awaitItem()
            assertFalse(estado.guardando)
            assertEquals("Horarios guardados", estado.mensaje)

            viewModel.onAction(DisponibilidadYHorariosAction.MensajeMostrado)
            assertNull(awaitItem().mensaje)
        }
    }

    @Test
    fun `guardar avisa cuando el repositorio falla`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(semana))
        coEvery { profesionalRepository.actualizarDisponibilidad(uid, semana) } returns
            Result.Error(ProfesionalError.SIN_INTERNET)

        val viewModel = crearViewModel()
        viewModel.onAction(DisponibilidadYHorariosAction.GuardarDisponibilidad)

        assertEquals("No pudimos guardar tus horarios", viewModel.state.value.mensaje)
        assertFalse(viewModel.state.value.guardando)
    }
}
