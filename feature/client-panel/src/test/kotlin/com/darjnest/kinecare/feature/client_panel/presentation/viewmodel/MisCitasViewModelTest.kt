@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MisCitasViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val reservaRepository = mockk<ReservaRepository>()
    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val resenaRepository = mockk<ResenaRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): MisCitasViewModel =
        MisCitasViewModel(reservaRepository, profesionalRepository, resenaRepository, firebaseAuth)

    private fun profesionalDePrueba(id: String, nombre: String): Profesional = Profesional(
        usuario = Usuario(
            id = id,
            nombre = nombre,
            rut = "9876543-2",
            email = "$id@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        especialidades = listOf("KINESIOLOGIA"),
        rnpi = "RNPI-$id",
        servicios = listOf(
            Servicio(
                id = "serv-1",
                nombre = "Sesion de rehabilitacion",
                descripcion = "desc",
                modalidad = ModalidadServicio.DOMICILIO,
                duracionMinutos = 60,
                precio = 18000L,
            ),
        ),
        insignias = emptyList(),
        disponibilidad = emptyList(),
        calificacionPromedio = 4.8,
        totalResenas = 20,
        descripcion = "desc",
        estadoVerificacionGeneral = EstadoVerificacion.APROBADO,
    )

    private fun reservaDePrueba(
        id: String,
        profesionalId: String,
        estado: EstadoReserva,
        fechaHora: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000),
        estadoPago: EstadoPago = EstadoPago.AUTORIZADO,
    ): Reserva = Reserva(
        id = id,
        clienteId = uid,
        profesionalId = profesionalId,
        servicioId = "serv-1",
        modalidad = ModalidadServicio.DOMICILIO,
        fechaHora = fechaHora,
        direccion = Direccion(
            calle = "Los Aromos",
            numero = "123",
            comuna = "Providencia",
            ciudad = "Santiago",
            lat = null,
            lng = null,
            indicaciones = null,
        ),
        estado = estado,
        pago = Pago(
            id = "pago-$id",
            reservaId = id,
            monto = 18000L,
            metodo = MetodoPago(tipo = TipoMetodoPago.TARJETA, ultimosDigitos = "4242", tokenPasarela = "tok"),
            estado = estadoPago,
            idTransaccionPasarela = "trx-$id",
        ),
        comisionPorcentaje = 0.15,
    )

    @Test
    fun `al inicializar categoriza reservas reales segun su estado y resuelve el nombre del profesional`() = runTest {
        val reservas = listOf(
            reservaDePrueba("r-en-curso", "prof-1", EstadoReserva.EN_CURSO),
            reservaDePrueba("r-proxima-solicitada", "prof-1", EstadoReserva.SOLICITADA),
            reservaDePrueba("r-proxima-confirmada", "prof-1", EstadoReserva.CONFIRMADA),
            reservaDePrueba("r-historial", "prof-1", EstadoReserva.COMPLETADA),
            reservaDePrueba("r-cancelada-cliente", "prof-1", EstadoReserva.CANCELADA_CLIENTE),
            reservaDePrueba("r-cancelada-profesional", "prof-1", EstadoReserva.CANCELADA_PROFESIONAL),
            reservaDePrueba("r-rechazada", "prof-1", EstadoReserva.RECHAZADA),
        )
        coEvery { reservaRepository.obtenerPorCliente(uid) } returns Result.Success(reservas)
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns
            Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))
        coEvery { resenaRepository.obtenerPorReserva("r-historial") } returns Result.Success(null)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)

            requireNotNull(estado.citaEnCurso).let {
                assertEquals("r-en-curso", it.id)
                assertEquals("Bruno Diaz", it.profesionalNombre)
                assertEquals("Sesion de rehabilitacion", it.tratamientoTitulo)
            }

            assertEquals(2, estado.proximasCitas.size)
            assertTrue(estado.proximasCitas.any { it.id == "r-proxima-solicitada" })
            assertTrue(estado.proximasCitas.any { it.id == "r-proxima-confirmada" })
            assertTrue(estado.proximasCitas.all { it.profesionalNombre == "Bruno Diaz" })

            assertEquals(1, estado.historial.size)
            assertEquals("r-historial", estado.historial.first().id)

            assertEquals(3, estado.canceladas.size)
            val canceladaCliente = estado.canceladas.first { it.id == "r-cancelada-cliente" }
            assertEquals("Cancelada por ti", canceladaCliente.motivo)
            val canceladaProfesional = estado.canceladas.first { it.id == "r-cancelada-profesional" }
            assertEquals("Cancelada por el profesional", canceladaProfesional.motivo)
            val rechazada = estado.canceladas.first { it.id == "r-rechazada" }
            assertEquals("Solicitud rechazada", rechazada.motivo)
        }
    }

    @Test
    fun `cuando el repositorio de reservas falla por falta de red el estado no queda colgado en cargando`() = runTest {
        coEvery { reservaRepository.obtenerPorCliente(uid) } returns Result.Error(ReservaError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertNull(estado.citaEnCurso)
            assertTrue(estado.proximasCitas.isEmpty())
            assertTrue(estado.historial.isEmpty())
            assertTrue(estado.canceladas.isEmpty())
        }
    }

    @Test
    fun `cuando el profesional de una reserva no se puede resolver usa un nombre de respaldo sin crashear`() = runTest {
        coEvery { reservaRepository.obtenerPorCliente(uid) } returns
            Result.Success(listOf(reservaDePrueba("r-1", "prof-desconocido", EstadoReserva.CONFIRMADA)))
        coEvery { profesionalRepository.obtenerPorId("prof-desconocido") } returns
            Result.Error(ProfesionalError.NO_ENCONTRADO)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(1, estado.proximasCitas.size)
            assertEquals("Profesional", estado.proximasCitas.first().profesionalNombre)
            assertEquals("Sesión", estado.proximasCitas.first().tipoSesion)
        }
    }

    private fun resenaDePrueba(reservaId: String, calificacion: Int) = Resena(
        id = reservaId,
        reservaId = reservaId,
        clienteId = uid,
        profesionalId = "prof-1",
        calificacion = calificacion,
        comentario = null,
        fecha = Instant.fromEpochMilliseconds(0),
        respuestaProfesional = null,
    )

    private fun stubReservasCompletadas(vararg ids: String) {
        coEvery { reservaRepository.obtenerPorCliente(uid) } returns
            Result.Success(ids.map { reservaDePrueba(it, "prof-1", EstadoReserva.COMPLETADA) })
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns
            Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))
    }

    @Test
    fun `una cita completada sin resena ofrece dejar resena con los ids para navegar`() = runTest {
        stubReservasCompletadas("r-1")
        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Success(null)

        val cita = crearViewModel().state.value.historial.single()

        assertTrue(cita.puedeResenar)
        assertEquals(0, cita.calificacion)
        assertEquals("r-1", cita.id)
        assertEquals("prof-1", cita.profesionalId)
    }

    @Test
    fun `una cita ya resenada oculta el boton y muestra sus estrellas`() = runTest {
        stubReservasCompletadas("r-1")
        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Success(resenaDePrueba("r-1", 4))

        val cita = crearViewModel().state.value.historial.single()

        assertFalse(cita.puedeResenar)
        assertEquals(4, cita.calificacion)
    }

    @Test
    fun `si no se puede comprobar la resena se ofrece dejarla igual y la lista no se rompe`() = runTest {
        stubReservasCompletadas("r-1", "r-2")
        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Error(ResenaError.SIN_INTERNET)
        coEvery { resenaRepository.obtenerPorReserva("r-2") } returns Result.Success(resenaDePrueba("r-2", 5))

        val historial = crearViewModel().state.value.historial

        assertEquals(2, historial.size)
        assertTrue(historial.first { it.id == "r-1" }.puedeResenar)
        assertFalse(historial.first { it.id == "r-2" }.puedeResenar)
    }

    @Test
    fun `solo las reservas completadas consultan resenas`() = runTest {
        coEvery { reservaRepository.obtenerPorCliente(uid) } returns Result.Success(
            listOf(
                reservaDePrueba("r-conf", "prof-1", EstadoReserva.CONFIRMADA),
                reservaDePrueba("r-canc", "prof-1", EstadoReserva.CANCELADA_CLIENTE),
            ),
        )
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns
            Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))

        crearViewModel()

        coVerify(exactly = 0) { resenaRepository.obtenerPorReserva(any()) }
    }

    @Test
    fun `Recargar vuelve a leer las reservas y el boton desaparece tras reseñar`() = runTest {
        stubReservasCompletadas("r-1")
        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Success(null)
        val viewModel = crearViewModel()
        assertTrue(viewModel.state.value.historial.single().puedeResenar)

        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Success(resenaDePrueba("r-1", 5))
        viewModel.onAction(MisCitasAction.Recargar)

        assertFalse(viewModel.state.value.historial.single().puedeResenar)
        coVerify(exactly = 2) { reservaRepository.obtenerPorCliente(uid) }
    }

    @Test
    fun `DejarResena es navegacion y no cambia el estado`() = runTest {
        stubReservasCompletadas("r-1")
        coEvery { resenaRepository.obtenerPorReserva("r-1") } returns Result.Success(null)
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(MisCitasAction.DejarResena("r-1", "prof-1"))

        assertEquals(antes, viewModel.state.value)
        coVerify(exactly = 1) { reservaRepository.obtenerPorCliente(uid) }
    }
}
