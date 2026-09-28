@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.reviews.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_PROFESIONAL_ID
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_RESERVA_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CrearResenaViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val resenaRepository = mockk<ResenaRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val reservaId = "res-1"
    private val profesionalId = "prof-1"
    private val uid = "cli-1"

    private fun iniciarSesion(uid: String? = this.uid) {
        if (uid == null) {
            every { firebaseAuth.currentUser } returns null
        } else {
            val usuario = mockk<FirebaseUser>()
            every { usuario.uid } returns uid
            every { firebaseAuth.currentUser } returns usuario
        }
    }

    private fun crearViewModel(
        reserva: String? = reservaId,
        profesional: String? = profesionalId,
    ): CrearResenaViewModel {
        val args = buildMap {
            if (reserva != null) put(ARG_RESERVA_ID, reserva)
            if (profesional != null) put(ARG_PROFESIONAL_ID, profesional)
        }
        return CrearResenaViewModel(SavedStateHandle(args), resenaRepository, firebaseAuth)
    }

    private fun sinResenaPrevia() {
        coEvery { resenaRepository.obtenerPorReserva(reservaId) } returns Result.Success(null)
    }

    private fun resenaExistente(calificacion: Int = 4, comentario: String? = "Bien") = Resena(
        id = reservaId,
        reservaId = reservaId,
        clienteId = uid,
        profesionalId = profesionalId,
        calificacion = calificacion,
        comentario = comentario,
        fecha = Instant.fromEpochMilliseconds(0),
        respuestaProfesional = null,
    )

    @Test
    fun `al abrir comprueba si la reserva ya tiene resena y muestra el formulario si no`() = runTest {
        iniciarSesion()
        sinResenaPrevia()

        val state = crearViewModel().state.value

        assertFalse(state.verificando)
        assertNull(state.errorVerificacion)
        assertNull(state.resenaExistente)
        assertEquals(0, state.calificacion)
        coVerify(exactly = 1) { resenaRepository.obtenerPorReserva(reservaId) }
    }

    @Test
    fun `mientras comprueba no se puede enviar`() = runTest {
        iniciarSesion()
        val gate = CompletableDeferred<Result<Resena?, ResenaError>>()
        coEvery { resenaRepository.obtenerPorReserva(reservaId) } coAnswers { gate.await() }

        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(5))

        assertTrue(viewModel.state.value.verificando)
        assertFalse(viewModel.state.value.puedeEnviar)
        viewModel.onAction(CrearResenaAction.Enviar)
        coVerify(exactly = 0) { resenaRepository.crear(any()) }

        gate.complete(Result.Success(null))
        assertTrue(viewModel.state.value.puedeEnviar)
    }

    @Test
    fun `si la resena ya existe muestra el estado de solo lectura y no permite enviar`() = runTest {
        iniciarSesion()
        coEvery { resenaRepository.obtenerPorReserva(reservaId) } returns
            Result.Success(resenaExistente(calificacion = 4, comentario = "  Bien  "))

        val viewModel = crearViewModel()

        val existente = viewModel.state.value.resenaExistente
        assertNotNull(existente)
        assertEquals(4, existente!!.calificacion)
        assertEquals("Bien", existente.comentario)

        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(5))
        assertFalse(viewModel.state.value.puedeEnviar)
        viewModel.onAction(CrearResenaAction.Enviar)
        coVerify(exactly = 0) { resenaRepository.crear(any()) }
    }

    @Test
    fun `si falla la comprobacion inicial se muestra el error y Reintentar la repite`() = runTest {
        iniciarSesion()
        coEvery { resenaRepository.obtenerPorReserva(reservaId) } returns Result.Error(ResenaError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertEquals(ResenaError.SIN_INTERNET, viewModel.state.value.errorVerificacion)
        assertFalse(viewModel.state.value.verificando)

        sinResenaPrevia()
        viewModel.onAction(CrearResenaAction.Reintentar)

        assertNull(viewModel.state.value.errorVerificacion)
        assertFalse(viewModel.state.value.verificando)
        coVerify(exactly = 2) { resenaRepository.obtenerPorReserva(reservaId) }
    }

    @Test
    fun `sin argumentos de reserva queda en error sin llamar al repositorio`() = runTest {
        iniciarSesion()

        val state = crearViewModel(reserva = null).state.value

        assertEquals(ResenaError.DESCONOCIDO, state.errorVerificacion)
        assertFalse(state.verificando)
        coVerify(exactly = 0) { resenaRepository.obtenerPorReserva(any()) }
    }

    @Test
    fun `enviar requiere elegir una calificacion`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val viewModel = crearViewModel()

        assertFalse(viewModel.state.value.puedeEnviar)
        viewModel.onAction(CrearResenaAction.Enviar)

        coVerify(exactly = 0) { resenaRepository.crear(any()) }
        assertFalse(viewModel.state.value.enviando)
    }

    @Test
    fun `la calificacion fuera de 1 a 5 se ignora`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val viewModel = crearViewModel()

        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(0))
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(6))
        assertEquals(0, viewModel.state.value.calificacion)

        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(3))
        assertEquals(3, viewModel.state.value.calificacion)
        assertTrue(viewModel.state.value.puedeEnviar)
    }

    @Test
    fun `el comentario se recorta a 500 caracteres`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val viewModel = crearViewModel()

        viewModel.onAction(CrearResenaAction.CambiarComentario("x".repeat(LARGO_MAXIMO_COMENTARIO + 50)))

        assertEquals(LARGO_MAXIMO_COMENTARIO, viewModel.state.value.comentario.length)
    }

    @Test
    fun `enviar crea la resena con id de reserva, comentario recortado y marca enviada`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val capturada = slot<Resena>()
        coEvery { resenaRepository.crear(capture(capturada)) } returns Result.Success(Unit)
        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(4))
        viewModel.onAction(CrearResenaAction.CambiarComentario("  Excelente  "))

        viewModel.onAction(CrearResenaAction.Enviar)

        val resena = capturada.captured
        assertEquals(reservaId, resena.id)
        assertEquals(reservaId, resena.reservaId)
        assertEquals(uid, resena.clienteId)
        assertEquals(profesionalId, resena.profesionalId)
        assertEquals(4, resena.calificacion)
        assertEquals("Excelente", resena.comentario)
        assertNull(resena.respuestaProfesional)
        val state = viewModel.state.value
        assertTrue(state.enviada)
        assertFalse(state.enviando)
        assertNull(state.error)
    }

    @Test
    fun `un comentario vacio o en blanco se envia como null`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val capturada = slot<Resena>()
        coEvery { resenaRepository.crear(capture(capturada)) } returns Result.Success(Unit)
        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(5))
        viewModel.onAction(CrearResenaAction.CambiarComentario("    "))

        viewModel.onAction(CrearResenaAction.Enviar)

        assertNull(capturada.captured.comentario)
    }

    @Test
    fun `cada ResenaError al enviar produce su mensaje y conserva el formulario`() = runTest {
        val esperado = mapOf(
            ResenaError.SIN_INTERNET to ErrorEnvioResena.SIN_INTERNET,
            ResenaError.SIN_PERMISO to ErrorEnvioResena.SIN_PERMISO,
            ResenaError.DESCONOCIDO to ErrorEnvioResena.DESCONOCIDO,
        )
        ResenaError.entries.forEach { error ->
            iniciarSesion()
            sinResenaPrevia()
            coEvery { resenaRepository.crear(any()) } returns Result.Error(error)
            val viewModel = crearViewModel()
            viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(2))
            viewModel.onAction(CrearResenaAction.CambiarComentario("Regular"))

            viewModel.onAction(CrearResenaAction.Enviar)

            val state = viewModel.state.value
            assertEquals(esperado.getValue(error), state.error)
            assertFalse(state.enviando, "enviando colgado para $error")
            assertFalse(state.enviada)
            assertEquals(2, state.calificacion)
            assertEquals("Regular", state.comentario)
            assertTrue(state.puedeEnviar, "no se puede reintentar tras $error")
        }
    }

    @Test
    fun `editar el formulario despues de un error limpia el mensaje`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        coEvery { resenaRepository.crear(any()) } returns Result.Error(ResenaError.SIN_INTERNET)
        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(2))
        viewModel.onAction(CrearResenaAction.Enviar)
        assertNotNull(viewModel.state.value.error)

        viewModel.onAction(CrearResenaAction.CambiarComentario("hola"))

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `sin sesion muestra el error de inicio de sesion y no llama al repositorio`() = runTest {
        iniciarSesion(uid = null)
        sinResenaPrevia()
        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(5))

        viewModel.onAction(CrearResenaAction.Enviar)

        assertEquals(ErrorEnvioResena.SIN_SESION, viewModel.state.value.error)
        assertFalse(viewModel.state.value.enviando)
        coVerify(exactly = 0) { resenaRepository.crear(any()) }
    }

    @Test
    fun `no permite un segundo envio mientras se esta enviando`() = runTest {
        iniciarSesion()
        sinResenaPrevia()
        val gate = CompletableDeferred<Result<Unit, ResenaError>>()
        coEvery { resenaRepository.crear(any()) } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(CrearResenaAction.SeleccionarCalificacion(5))

        viewModel.onAction(CrearResenaAction.Enviar)
        assertTrue(viewModel.state.value.enviando)
        assertFalse(viewModel.state.value.puedeEnviar)
        viewModel.onAction(CrearResenaAction.Enviar)

        coVerify(exactly = 1) { resenaRepository.crear(any()) }
        gate.complete(Result.Success(Unit))
        assertTrue(viewModel.state.value.enviada)
        assertFalse(viewModel.state.value.puedeEnviar)
        viewModel.onAction(CrearResenaAction.Enviar)
        coVerify(exactly = 1) { resenaRepository.crear(any()) }
    }
}
