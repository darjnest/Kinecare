@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InformacionPersonalClienteViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val usuarioRepository = mockk<UsuarioRepository>()
    private val fotoPerfilRepository = mockk<FotoPerfilRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): InformacionPersonalClienteViewModel =
        InformacionPersonalClienteViewModel(usuarioRepository, fotoPerfilRepository, firebaseAuth)

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
            assertEquals("No pudimos cargar tus datos", estado.error)
            assertTrue(estado.cargaFallida)
            assertFalse(estado.puedeGuardar)
        }
    }

    @Test
    fun `si la carga fallo guardar cambios no escribe en el repositorio`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Error(UsuarioError.SIN_INTERNET)
        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.CambiarNombre("Ana"))

        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        coVerify(exactly = 0) {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        }
    }

    @Test
    fun `Reintentar tras un error de carga vuelve a cargar y limpia el error`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Error(UsuarioError.SIN_INTERNET)
        val viewModel = crearViewModel()

        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        viewModel.onAction(InformacionPersonalClienteAction.Reintentar)

        val estado = viewModel.state.value
        assertNull(estado.error)
        assertFalse(estado.cargaFallida)
        assertEquals("Ana Soto", estado.nombre)
        assertTrue(estado.puedeGuardar)
    }

    @Test
    fun `con el nombre en blanco no se puede guardar ni se llama al repositorio`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        val viewModel = crearViewModel()

        viewModel.onAction(InformacionPersonalClienteAction.CambiarNombre("   "))
        assertFalse(viewModel.state.value.puedeGuardar)
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        coVerify(exactly = 0) {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        }
    }

    @Test
    fun `mientras carga no se puede guardar`() = runTest {
        val gate = CompletableDeferred<Result<Usuario, UsuarioError>>()
        coEvery { usuarioRepository.obtenerPorId(uid) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.cargando)
        assertFalse(viewModel.state.value.puedeGuardar)
        gate.complete(Result.Success(usuarioDePrueba()))
        assertTrue(viewModel.state.value.puedeGuardar)
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
            assertEquals("No pudimos guardar los cambios", trasFalla.error)
        }
    }

    @Test
    fun `un nuevo intento de guardar limpia el error anterior`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        coEvery {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        } returns Result.Error(UsuarioError.DESCONOCIDO)
        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)
        assertEquals("No pudimos guardar los cambios", viewModel.state.value.error)

        coEvery {
            usuarioRepository.actualizarDatosPersonales(any(), any(), any(), any())
        } returns Result.Success(Unit)
        viewModel.onAction(InformacionPersonalClienteAction.GuardarCambios)

        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.guardadoExitoso)
    }

    @Test
    fun `una foto elegida se sube con el rol cliente y su URL se guarda en el usuario`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba(fotoUrl = null))
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, "content://foto/1") } returns
            Result.Success("https://dl/nueva.jpg?v=1")
        coEvery { usuarioRepository.actualizarFotoUrl(uid, "https://dl/nueva.jpg?v=1") } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))

        val estado = viewModel.state.value
        assertEquals("https://dl/nueva.jpg?v=1", estado.fotoUrl)
        assertFalse(estado.subiendoFoto)
        assertNull(estado.errorFoto)
        coVerify { usuarioRepository.actualizarFotoUrl(uid, "https://dl/nueva.jpg?v=1") }
    }

    @Test
    fun `mientras sube la foto el estado marca progreso y no acepta otra subida`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba(fotoUrl = null))
        val gate = CompletableDeferred<Result<String, FotoPerfilError>>()
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, any()) } coAnswers { gate.await() }
        coEvery { usuarioRepository.actualizarFotoUrl(any(), any()) } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))
        assertTrue(viewModel.state.value.subiendoFoto)

        // Mientras hay una subida en curso, otra eleccion se ignora.
        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/2"))
        gate.complete(Result.Success("https://dl/a.jpg"))

        assertFalse(viewModel.state.value.subiendoFoto)
        assertEquals("https://dl/a.jpg", viewModel.state.value.fotoUrl)
        coVerify(exactly = 1) { fotoPerfilRepository.subir(any(), any(), any()) }
    }

    @Test
    fun `cada error de subida muestra su propio mensaje y no cambia la foto`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        val esperados = mapOf(
            FotoPerfilError.SIN_INTERNET to "Sin conexión. Revisa tu internet e intenta de nuevo.",
            FotoPerfilError.IMAGEN_INVALIDA to "No pudimos leer esa imagen. Elige otra foto.",
            FotoPerfilError.IMAGEN_MUY_GRANDE to "La foto es demasiado pesada. Elige otra más liviana.",
            FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE to
                "Cambiar la foto de perfil aún no está disponible. Estamos trabajando en ello.",
            FotoPerfilError.DESCONOCIDO to "No pudimos subir la foto. Intenta de nuevo.",
        )
        esperados.forEach { (error, mensaje) ->
            coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, any()) } returns Result.Error(error)
            val viewModel = crearViewModel()

            viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))

            val estado = viewModel.state.value
            assertEquals(mensaje, estado.errorFoto, error.name)
            assertFalse(estado.subiendoFoto)
            assertEquals("https://foto.test/ana.jpg", estado.fotoUrl)
        }
        coVerify(exactly = 0) { usuarioRepository.actualizarFotoUrl(any(), any()) }
    }

    @Test
    fun `si la foto sube pero no se puede guardar la URL se avisa y la foto no cambia`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, any()) } returns Result.Success("https://dl/a.jpg")
        coEvery { usuarioRepository.actualizarFotoUrl(uid, "https://dl/a.jpg") } returns
            Result.Error(UsuarioError.SIN_INTERNET)
        val viewModel = crearViewModel()

        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))

        val estado = viewModel.state.value
        assertEquals("No pudimos guardar la foto en tu perfil. Intenta de nuevo.", estado.errorFoto)
        assertEquals("https://foto.test/ana.jpg", estado.fotoUrl)
        assertFalse(estado.subiendoFoto)
    }

    @Test
    fun `un nuevo intento limpia el error anterior`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, any()) } returns
            Result.Error(FotoPerfilError.SIN_INTERNET)
        val viewModel = crearViewModel()
        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))
        assertEquals("Sin conexión. Revisa tu internet e intenta de nuevo.", viewModel.state.value.errorFoto)

        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.CLIENTE, any()) } returns Result.Success("https://dl/a.jpg")
        coEvery { usuarioRepository.actualizarFotoUrl(uid, "https://dl/a.jpg") } returns Result.Success(Unit)
        viewModel.onAction(InformacionPersonalClienteAction.FotoElegida("content://foto/1"))

        assertNull(viewModel.state.value.errorFoto)
        assertEquals("https://dl/a.jpg", viewModel.state.value.fotoUrl)
    }
}
