@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
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
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): MisCitasViewModel =
        MisCitasViewModel(reservaRepository, profesionalRepository, firebaseAuth)

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
}
