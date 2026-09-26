@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InformacionPersonalClienteViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val usuarioRepository = mockk<UsuarioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): InformacionPersonalClienteViewModel =
        InformacionPersonalClienteViewModel(usuarioRepository, firebaseAuth)

    private fun usuarioDePrueba(
        nombre: String = "Ana Soto",
        rut: String = "12345678-5",
        telefono: String? = "+56911111111",
        correoContacto: String? = null,
        fotoUrl: String? = "https://foto.test/ana.jpg",
    ): Usuario = Usuario(
        id = uid,
        nombre = nombre,
        rut = rut,
        email = "ana@kinecare.cl",
        correoContacto = correoContacto,
        telefono = telefono,
        rol = RolUsuario.CLIENTE,
        fotoUrl = fotoUrl,
        fechaRegistro = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `al inicializar queda cargando hasta que el repositorio responde y luego refleja los datos del usuario`() = runTest {
        val gate = CompletableDeferred<Result<Usuario, UsuarioError>>()
        coEvery { usuarioRepository.obtenerPorId(uid) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        viewModel.state.test {
            val cargando = awaitItem()
            assertEquals(true, cargando.cargando)

            gate.complete(Result.Success(usuarioDePrueba()))

            val cargado = awaitItem()
            assertEquals(false, cargado.cargando)
            assertEquals("Ana Soto", cargado.nombre)
            assertEquals("12345678-5", cargado.rut)
            assertEquals("+56911111111", cargado.telefono)
            assertEquals("", cargado.correoContacto)
            assertEquals("https://foto.test/ana.jpg", cargado.fotoUrl)
        }
    }

    @Test
    fun `cuando el repositorio retorna error al cargar el estado no queda colgado en cargando`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Error(UsuarioError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            // Sin datos: el estado se queda en los valores por defecto, no crashea.
            assertEquals("", estado.nombre)
            assertEquals("", estado.rut)
        }
    }

    @Test
    fun `guardar cambios envia al repositorio los valores editados y marca guardado exitoso`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns
            Result.Success(usuarioDePrueba(telefono = "+56911111111", correoContacto = null))
        coEvery {
            usuarioRepository.actualizarDatosPersonales(uid, "Ana Maria Soto", "+56911111111", null)
        } returns Result.Success(Unit)

        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.CambiarNombre("Ana Maria Soto"))
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        viewModel.state.test {
            val guardado = awaitItem()
            assertEquals("Ana Maria Soto", guardado.nombre)
            assertFalse(guardado.guardando)
            assertEquals(true, guardado.guardadoExitoso)
        }

        coVerify(exactly = 1) {
            usuarioRepository.actualizarDatosPersonales(uid, "Ana Maria Soto", "+56911111111", null)
        }
    }

    @Test
    fun `guardar cambios blanquea el correo de contacto vacio a null antes de persistir`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns
            Result.Success(usuarioDePrueba(correoContacto = null))
        coEvery {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        } returns Result.Success(Unit)

        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        coVerify(exactly = 1) {
            usuarioRepository.actualizarDatosPersonales(uid, "Ana Soto", "+56911111111", null)
        }
    }

    @Test
    fun `cuando guardar cambios falla no queda colgado en guardando ni marca guardado exitoso`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        coEvery {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        } returns Result.Error(UsuarioError.DESCONOCIDO)

        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        viewModel.state.test {
            val trasFalla = awaitItem()
            assertFalse(trasFalla.guardando)
            assertFalse(trasFalla.guardadoExitoso)
        }
    }
}
