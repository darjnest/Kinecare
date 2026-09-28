package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ServiciosYTarifasViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val servicioRepository = mockk<ServicioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel() = ServiciosYTarifasViewModel(servicioRepository, firebaseAuth)

    private fun servicio(id: String, precio: Long, activo: Boolean = true) = Servicio(
        id = id,
        nombre = "Sesion $id",
        descripcion = "desc",
        modalidad = ModalidadServicio.DOMICILIO,
        duracionMinutos = 60,
        precio = precio,
        activo = activo,
    )

    @Test
    fun `al inicializar carga los servicios reales y calcula el resumen solo con los activos`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(
            listOf(servicio("s1", 30_000L), servicio("s2", 40_000L), servicio("s3", 100_000L, activo = false)),
        )

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(listOf("s1", "s2", "s3"), estado.servicios.map { it.id })
            assertEquals(listOf(ModalidadServicio.DOMICILIO), estado.servicios.first().modalidades)
            assertEquals(ResumenCatalogoServicios(serviciosActivos = 2, tarifaPromedio = 35_000L), estado.resumenCatalogo)
        }
    }

    @Test
    fun `sin servicios el resumen es nulo y no deja cargando colgado`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(emptyList())

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertNull(estado.resumenCatalogo)
        }
    }

    @Test
    fun `un error del repositorio deja la lista vacia y sin cargando`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Error(ServicioError.SIN_INTERNET)

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            assertEquals(emptyList<ServicioProfesional>(), estado.servicios)
        }
    }

    @Test
    fun `pausar un servicio lo persiste y actualiza el resumen`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns
            Result.Success(listOf(servicio("s1", 30_000L), servicio("s2", 50_000L)))
        coEvery { servicioRepository.actualizarActivo(uid, "s1", false) } returns Result.Success(Unit)

        val viewModel = crearViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.onAction(ServiciosYTarifasAction.CambiarActivoServicio("s1", false))

            val estado = awaitItem()
            assertEquals(listOf(false, true), estado.servicios.map { it.activo })
            assertEquals(ResumenCatalogoServicios(serviciosActivos = 1, tarifaPromedio = 50_000L), estado.resumenCatalogo)
        }

        coVerify(exactly = 1) { servicioRepository.actualizarActivo(uid, "s1", false) }
    }

    @Test
    fun `pausar un servicio revierte la actualizacion optimista si el repositorio falla`() = runTest {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(listOf(servicio("s1", 30_000L)))
        val gate = CompletableDeferred<Result<Unit, ServicioError>>()
        coEvery { servicioRepository.actualizarActivo(uid, "s1", false) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        viewModel.state.test {
            assertEquals(true, awaitItem().servicios.single().activo)

            viewModel.onAction(ServiciosYTarifasAction.CambiarActivoServicio("s1", false))
            assertEquals(false, awaitItem().servicios.single().activo)

            gate.complete(Result.Error(ServicioError.SIN_INTERNET))

            val revertido = awaitItem()
            assertEquals(true, revertido.servicios.single().activo)
            assertEquals(ResumenCatalogoServicios(serviciosActivos = 1, tarifaPromedio = 30_000L), revertido.resumenCatalogo)
        }
    }
}
