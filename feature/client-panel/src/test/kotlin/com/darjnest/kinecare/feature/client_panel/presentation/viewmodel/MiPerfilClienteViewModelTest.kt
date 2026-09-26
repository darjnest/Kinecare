@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
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

class MiPerfilClienteViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val usuarioRepository = mockk<UsuarioRepository>()
    private val clienteRepository = mockk<ClienteRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): MiPerfilClienteViewModel =
        MiPerfilClienteViewModel(usuarioRepository, clienteRepository, firebaseAuth)

    private fun usuarioDePrueba(nombre: String = "Ana Maria Soto", rut: String = "12345678-5"): Usuario = Usuario(
        id = uid,
        nombre = nombre,
        rut = rut,
        email = "ana@kinecare.cl",
        correoContacto = null,
        telefono = null,
        rol = RolUsuario.CLIENTE,
        fotoUrl = null,
        fechaRegistro = Instant.fromEpochMilliseconds(0),
    )

    private fun direccionDePrueba(
        calle: String = "Los Aromos",
        numero: String = "123",
        comuna: String = "Providencia",
        ciudad: String = "Santiago",
    ): Direccion = Direccion(
        calle = calle,
        numero = numero,
        comuna = comuna,
        ciudad = ciudad,
        lat = null,
        lng = null,
        indicaciones = null,
    )

    private fun clienteDePrueba(direcciones: List<Direccion> = emptyList()): Cliente = Cliente(
        usuario = usuarioDePrueba(),
        direcciones = direcciones,
        metodosPago = emptyList(),
        favoritos = emptyList(),
    )

    @Test
    fun `al inicializar queda cargando y luego refleja el resumen y las direcciones reales`() = runTest {
        val gate = CompletableDeferred<Result<Usuario, UsuarioError>>()
        coEvery { usuarioRepository.obtenerPorId(uid) } coAnswers { gate.await() }
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(
            clienteDePrueba(
                direcciones = listOf(
                    direccionDePrueba(comuna = "Providencia", ciudad = "Santiago"),
                    direccionDePrueba(calle = "Av. Siempre Viva", numero = "742", comuna = "Ñuñoa", ciudad = "Santiago"),
                ),
            ),
        )

        val viewModel = crearViewModel()

        viewModel.state.test {
            val cargando = awaitItem()
            assertTrue(cargando.cargando)

            gate.complete(Result.Success(usuarioDePrueba(nombre = "Ana Maria Soto")))

            val cargado = awaitItem()
            assertFalse(cargado.cargando)
            requireNotNull(cargado.resumen).let { resumen ->
                assertEquals("Ana Maria Soto", resumen.nombreCompleto)
                assertEquals("AM", resumen.iniciales)
                assertEquals("12345678-5", resumen.rut)
                assertEquals("Santiago", resumen.ciudad)
                assertFalse(resumen.verificadoClaveUnica)
            }
            assertEquals(2, cargado.direcciones.size)
            val principal = cargado.direcciones[0]
            assertEquals("Dirección principal", principal.etiqueta)
            assertTrue(principal.predeterminada)
            assertEquals("Los Aromos 123", principal.calle)
            assertEquals("Providencia, Santiago", principal.comunaRegion)
            val segunda = cargado.direcciones[1]
            assertEquals("Dirección 2", segunda.etiqueta)
            assertFalse(segunda.predeterminada)
            assertEquals("Av. Siempre Viva 742", segunda.calle)
        }
    }

    @Test
    fun `cuando el usuario no se puede cargar el estado no queda colgado en cargando y no expone resumen`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Error(UsuarioError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertNull(estado.resumen)
            assertTrue(estado.direcciones.isEmpty())
        }
    }

    @Test
    fun `cuando las direcciones fallan al cargar el resumen igual se muestra sin direcciones`() = runTest {
        coEvery { usuarioRepository.obtenerPorId(uid) } returns Result.Success(usuarioDePrueba())
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Error(ClienteError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertTrue(estado.direcciones.isEmpty())
            requireNotNull(estado.resumen).let { resumen ->
                assertEquals("Ana Maria Soto", resumen.nombreCompleto)
                assertEquals("", resumen.ciudad)
            }
        }
    }
}
