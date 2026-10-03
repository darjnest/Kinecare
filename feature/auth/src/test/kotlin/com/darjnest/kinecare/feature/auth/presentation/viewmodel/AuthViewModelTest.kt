@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.auth.presentation.viewmodel

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
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
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

    private fun crearViewModel() = AuthViewModel(authRepository, loginPreferences)

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
}
