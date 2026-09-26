@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class FavoritosViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val clienteRepository = mockk<ClienteRepository>()
    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "cliente-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel(): FavoritosViewModel =
        FavoritosViewModel(clienteRepository, profesionalRepository, firebaseAuth)

    private fun clienteDePrueba(favoritos: List<String>): Cliente = Cliente(
        usuario = Usuario(
            id = uid,
            nombre = "Ana Soto",
            rut = "12345678-5",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.CLIENTE,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        direcciones = emptyList(),
        metodosPago = emptyList(),
        favoritos = favoritos,
    )

    private fun profesionalDePrueba(
        id: String,
        nombre: String,
        identidadVerificada: Boolean = true,
        precios: List<Long> = listOf(18000L),
    ): Profesional = Profesional(
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
        servicios = precios.mapIndexed { index, precio ->
            Servicio(
                id = "serv-$index",
                nombre = "Sesion",
                descripcion = "desc",
                modalidad = ModalidadServicio.DOMICILIO,
                duracionMinutos = 60,
                precio = precio,
            )
        },
        insignias = listOf(
            Insignia(
                tipo = TipoInsignia.IDENTIDAD,
                estado = if (identidadVerificada) EstadoVerificacion.APROBADO else EstadoVerificacion.PENDIENTE,
                detalle = null,
                fechaActualizacion = Instant.fromEpochMilliseconds(0),
            ),
        ),
        disponibilidad = emptyList(),
        calificacionPromedio = 4.8,
        totalResenas = 20,
        descripcion = "desc",
        estadoVerificacionGeneral = EstadoVerificacion.APROBADO,
    )

    @Test
    fun `al inicializar resuelve cada favorito real y marca el primero como terapeuta principal`() = runTest {
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(clienteDePrueba(listOf("prof-1", "prof-2")))
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns
            Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz", precios = listOf(18000L, 12000L)))
        coEvery { profesionalRepository.obtenerPorId("prof-2") } returns
            Result.Success(profesionalDePrueba("prof-2", "Carla Reyes", identidadVerificada = false))

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(2, estado.favoritos.size)

            val primero = estado.favoritos[0]
            assertEquals("prof-1", primero.id)
            assertEquals("Bruno Diaz", primero.nombre)
            assertTrue(primero.esTerapeutaPrincipal)
            assertTrue(primero.identidadVerificada)
            assertEquals(12000L, primero.precioSesion)

            val segundo = estado.favoritos[1]
            assertEquals("prof-2", segundo.id)
            assertFalse(segundo.esTerapeutaPrincipal)
            assertFalse(segundo.identidadVerificada)
        }
    }

    @Test
    fun `cuando el cliente no se puede cargar el estado no queda colgado en cargando y no crashea`() = runTest {
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Error(ClienteError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertTrue(estado.favoritos.isEmpty())
        }
    }

    @Test
    fun `cuando un profesional favorito individual falla al resolverse se omite sin crashear la lista`() = runTest {
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(clienteDePrueba(listOf("prof-1", "prof-2")))
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns
            Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))
        coEvery { profesionalRepository.obtenerPorId("prof-2") } returns
            Result.Error(ProfesionalError.NO_ENCONTRADO)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(1, estado.favoritos.size)
            assertEquals("prof-1", estado.favoritos.first().id)
            assertTrue(estado.favoritos.first().esTerapeutaPrincipal)
        }
    }

    @Test
    fun `quitar de favoritos exitoso conserva la actualizacion optimista y llama al repositorio`() = runTest {
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(clienteDePrueba(listOf("prof-1", "prof-2")))
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))
        coEvery { profesionalRepository.obtenerPorId("prof-2") } returns Result.Success(profesionalDePrueba("prof-2", "Carla Reyes"))
        coEvery { clienteRepository.quitarFavorito(uid, "prof-1") } returns Result.Success(Unit)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val cargado = awaitItem()
            assertEquals(2, cargado.favoritos.size)

            viewModel.onAction(FavoritosAction.QuitarDeFavoritos("prof-1"))

            val trasQuitar = awaitItem()
            assertEquals(listOf("prof-2"), trasQuitar.favoritos.map { it.id })
        }

        coVerify(exactly = 1) { clienteRepository.quitarFavorito(uid, "prof-1") }
    }

    @Test
    fun `quitar de favoritos revierte la actualizacion optimista si el repositorio falla`() = runTest {
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(clienteDePrueba(listOf("prof-1", "prof-2")))
        coEvery { profesionalRepository.obtenerPorId("prof-1") } returns Result.Success(profesionalDePrueba("prof-1", "Bruno Diaz"))
        coEvery { profesionalRepository.obtenerPorId("prof-2") } returns Result.Success(profesionalDePrueba("prof-2", "Carla Reyes"))

        val gate = CompletableDeferred<Result<Unit, ClienteError>>()
        coEvery { clienteRepository.quitarFavorito(uid, "prof-1") } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        viewModel.state.test {
            val cargado = awaitItem()
            assertEquals(listOf("prof-1", "prof-2"), cargado.favoritos.map { it.id })

            viewModel.onAction(FavoritosAction.QuitarDeFavoritos("prof-1"))

            val optimista = awaitItem()
            assertEquals(listOf("prof-2"), optimista.favoritos.map { it.id })

            gate.complete(Result.Error(ClienteError.SIN_INTERNET))

            val revertido = awaitItem()
            assertEquals(listOf("prof-1", "prof-2"), revertido.favoritos.map { it.id })
        }
    }
}
