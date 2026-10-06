@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProfessionalPanelViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val uid = "prof-1"

    private fun profesional(conectado: Boolean) = Profesional(
        usuario = Usuario(
            id = uid,
            nombre = "Ana Soto",
            rut = "1-9",
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
        disponibilidad = emptyList(),
        calificacionPromedio = 0.0,
        totalResenas = 0,
        descripcion = "",
        estadoVerificacionGeneral = EstadoVerificacion.NO_SOLICITADO,
        mercadoPagoConectado = conectado,
    )

    private fun iniciarSesion(conSesion: Boolean = true) {
        if (conSesion) {
            val usuario = mockk<FirebaseUser>()
            every { usuario.uid } returns uid
            every { firebaseAuth.currentUser } returns usuario
        } else {
            every { firebaseAuth.currentUser } returns null
        }
    }

    private fun crearViewModel() = ProfessionalPanelViewModel(profesionalRepository, firebaseAuth)

    @Test
    fun `al abrir lee mercadoPagoConectado del profesional`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))

        assertEquals(true, crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `un profesional sin cuenta vinculada se muestra como no conectado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))

        assertEquals(false, crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `si no se puede leer no se afirma ni conectado ni no conectado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)

        assertNull(crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `sin sesion no lee Firestore`() = runTest {
        iniciarSesion(conSesion = false)

        assertNull(crearViewModel().state.value.mercadoPagoConectado)
        coVerify(exactly = 0) { profesionalRepository.obtenerPorId(any()) }
    }

    @Test
    fun `ActualizarCobros al volver de vincular refleja el nuevo estado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        val viewModel = crearViewModel()

        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))
        viewModel.onAction(ProfessionalPanelAction.ActualizarCobros)

        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `un fallo al releer conserva el ultimo estado conocido`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))
        val viewModel = crearViewModel()

        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)
        viewModel.onAction(ProfessionalPanelAction.ActualizarCobros)

        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `una lectura en curso no se duplica con ActualizarCobros`() = runTest {
        iniciarSesion()
        val respuesta = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(uid) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(ProfessionalPanelAction.ActualizarCobros)

        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(uid) }
        respuesta.complete(Result.Success(profesional(conectado = true)))
        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `ConectarMercadoPago es navegacion y no cambia el estado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(ProfessionalPanelAction.ConectarMercadoPago)

        assertEquals(antes, viewModel.state.value)
    }
}
