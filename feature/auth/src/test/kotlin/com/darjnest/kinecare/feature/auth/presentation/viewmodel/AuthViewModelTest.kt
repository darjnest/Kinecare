@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.auth.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.DispositivoPushError
import com.darjnest.kinecare.core.common.data.repository.DispositivoPushRepository
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.data.local.LoginPreferences
import com.darjnest.kinecare.feature.auth.data.repository.AuthRepository
import com.darjnest.kinecare.feature.auth.domain.ResultadoGoogle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val RUT_VALIDO = "12.345.678-5"

class AuthViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val authRepository = mockk<AuthRepository>(relaxed = true) {
        every { observarUsuarioActual() } returns emptyFlow()
    }
    private val dispositivoPushRepository = mockk<DispositivoPushRepository> {
        coEvery { registrarDispositivoActual() } returns Result.Success(Unit)
        coEvery { eliminarDispositivoActual() } returns Result.Success(Unit)
    }
    private val loginPreferences = mockk<LoginPreferences> {
        coEvery { recordarCuenta() } returns false
        coEvery { rutRecordado() } returns ""
    }

    private val usuario = Usuario(
        id = "uid-1",
        nombre = "Ana Soto",
        rut = "12345678-5",
        email = "123456785@rut.kinecare.cl",
        correoContacto = "ana@kinecare.cl",
        telefono = "+56911111111",
        rol = RolUsuario.PROFESIONAL,
        fotoUrl = null,
        fechaRegistro = Instant.fromEpochMilliseconds(0),
    )

    private fun crearViewModel() = AuthViewModel(authRepository, loginPreferences, dispositivoPushRepository)

    private fun AuthViewModel.completarFormularioRegistro(rol: RolUsuario) {
        onAction(AuthAction.CambiarModo(ModoAuth.REGISTRO))
        onAction(AuthAction.SeleccionarRol(rol))
        onAction(AuthAction.CambiarNombre("Ana Soto"))
        onAction(AuthAction.CambiarRut(RUT_VALIDO))
        onAction(AuthAction.CambiarTelefono("911111111"))
        onAction(AuthAction.CambiarCorreo("ana@kinecare.cl"))
        onAction(AuthAction.CambiarPassword("secreta123"))
        onAction(AuthAction.CambiarAceptaTerminos(true))
    }

    @Test
    fun `un profesional no puede enviar el registro sin elegir al menos un tipo de atencion`() {
        val viewModel = crearViewModel()
        viewModel.completarFormularioRegistro(RolUsuario.PROFESIONAL)

        assertFalse(viewModel.state.value.puedeEnviar)

        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        assertTrue(viewModel.state.value.puedeEnviar)

        // Alternar de nuevo lo quita y vuelve a bloquear el envio.
        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        assertFalse(viewModel.state.value.puedeEnviar)
    }

    @Test
    fun `un cliente puede registrarse sin tipos de atencion`() {
        val viewModel = crearViewModel()
        viewModel.completarFormularioRegistro(RolUsuario.CLIENTE)

        assertTrue(viewModel.state.value.puedeEnviar)
    }

    @Test
    fun `registrar un profesional pasa los tipos de atencion elegidos al repositorio`() = runTest {
        coEvery { authRepository.registrar(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(usuario)
        val viewModel = crearViewModel()
        viewModel.completarFormularioRegistro(RolUsuario.PROFESIONAL)
        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))
        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.onAction(AuthAction.Enviar)

        coVerify(exactly = 1) {
            authRepository.registrar(
                "Ana Soto",
                RUT_VALIDO,
                "secreta123",
                RolUsuario.PROFESIONAL,
                "911111111",
                "ana@kinecare.cl",
                setOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA),
            )
        }
    }

    @Test
    fun `un cliente que alterno tipos antes de cambiar de rol se registra sin tipos de atencion`() = runTest {
        coEvery { authRepository.registrar(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(usuario.copy(rol = RolUsuario.CLIENTE))
        val viewModel = crearViewModel()
        viewModel.completarFormularioRegistro(RolUsuario.PROFESIONAL)
        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))
        viewModel.onAction(AuthAction.SeleccionarRol(RolUsuario.CLIENTE))

        viewModel.onAction(AuthAction.Enviar)

        coVerify(exactly = 1) {
            authRepository.registrar(any(), any(), any(), RolUsuario.CLIENTE, any(), any(), emptySet())
        }
    }

    @Test
    fun `completar perfil de Google como profesional exige y envia tipos de atencion`() = runTest {
        coEvery { authRepository.iniciarSesionConGoogle("token") } returns Result.Success(
            ResultadoGoogle.RequiereCompletarPerfil(
                uid = "uid-1",
                nombreSugerido = "Ana Soto",
                correoGoogle = "ana@gmail.com",
                fotoUrl = null,
            ),
        )
        coEvery {
            authRepository.completarRegistroGoogle(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.Success(usuario)
        val viewModel = crearViewModel()
        viewModel.onAction(AuthAction.IniciarSesionConGoogle("token"))
        viewModel.onAction(AuthAction.SeleccionarRol(RolUsuario.PROFESIONAL))
        viewModel.onAction(AuthAction.CambiarRut(RUT_VALIDO))
        viewModel.onAction(AuthAction.CambiarTelefono("911111111"))
        viewModel.onAction(AuthAction.CambiarAceptaTerminos(true))

        assertFalse(viewModel.state.value.puedeConfirmarPerfilGoogle)
        viewModel.onAction(AuthAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))
        assertTrue(viewModel.state.value.puedeConfirmarPerfilGoogle)

        viewModel.onAction(AuthAction.ConfirmarPerfilGoogle)

        coVerify(exactly = 1) {
            authRepository.completarRegistroGoogle(
                uid = "uid-1",
                nombre = "Ana Soto",
                rut = RUT_VALIDO,
                telefono = "911111111",
                correoContacto = "ana@gmail.com",
                rol = RolUsuario.PROFESIONAL,
                fotoUrl = null,
                tiposAtencion = setOf(TipoAtencion.KINESIOLOGIA),
            )
        }
        assertEquals(usuario, viewModel.state.value.usuarioAutenticado)
    }

    @Test
    fun `al haber sesion registra el dispositivo para las notificaciones push`() = runTest {
        val sesion = MutableSharedFlow<Usuario?>()
        every { authRepository.observarUsuarioActual() } returns sesion
        crearViewModel()

        sesion.emit(null)
        coVerify(exactly = 0) { dispositivoPushRepository.registrarDispositivoActual() }

        sesion.emit(usuario)
        coVerify(exactly = 1) { dispositivoPushRepository.registrarDispositivoActual() }
    }

    @Test
    fun `no vuelve a registrar el dispositivo si la misma sesion se emite otra vez`() = runTest {
        val sesion = MutableSharedFlow<Usuario?>()
        every { authRepository.observarUsuarioActual() } returns sesion
        crearViewModel()

        sesion.emit(usuario)
        sesion.emit(usuario.copy(nombre = "Ana Soto Rojas"))

        coVerify(exactly = 1) { dispositivoPushRepository.registrarDispositivoActual() }
    }

    @Test
    fun `un fallo al registrar el dispositivo no afecta a la sesion`() = runTest {
        coEvery { dispositivoPushRepository.registrarDispositivoActual() } returns
            Result.Error(DispositivoPushError.SIN_INTERNET)
        val sesion = MutableSharedFlow<Usuario?>()
        every { authRepository.observarUsuarioActual() } returns sesion
        val viewModel = crearViewModel()

        sesion.emit(usuario)

        assertEquals(usuario, viewModel.state.value.usuarioAutenticado)
    }

    @Test
    fun `cerrar sesion borra primero el token push y despues cierra la sesion`() = runTest {
        val viewModel = crearViewModel()

        viewModel.cerrarSesion()

        coVerifyOrder {
            dispositivoPushRepository.eliminarDispositivoActual()
            authRepository.cerrarSesion()
        }
    }

    @Test
    fun `la accion CerrarSesion tambien borra el token antes de cerrar`() = runTest {
        val viewModel = crearViewModel()

        viewModel.onAction(AuthAction.CerrarSesion)

        coVerifyOrder {
            dispositivoPushRepository.eliminarDispositivoActual()
            authRepository.cerrarSesion()
        }
    }

    @Test
    fun `si borrar el token falla igual se cierra la sesion`() = runTest {
        coEvery { dispositivoPushRepository.eliminarDispositivoActual() } returns
            Result.Error(DispositivoPushError.SIN_INTERNET)
        val viewModel = crearViewModel()

        viewModel.cerrarSesion()

        coVerify(exactly = 1) { authRepository.cerrarSesion() }
    }

    @Test
    fun `si borrar el token se cuelga cerrar sesion no espera mas del limite`() = runTest {
        coEvery { dispositivoPushRepository.eliminarDispositivoActual() } coAnswers {
            delay(60_000)
            Result.Success(Unit)
        }
        val viewModel = crearViewModel()

        viewModel.cerrarSesion()

        assertTrue(currentTime <= TIEMPO_MAXIMO_BORRAR_TOKEN_MS)
        coVerify(exactly = 1) { authRepository.cerrarSesion() }
    }
}
