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
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun cargado(vararg servicios: Servicio): ServiciosYTarifasViewModel {
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(servicios.toList())
        return crearViewModel()
    }

    private fun ServiciosYTarifasViewModel.completarFormulario(precio: String = "35000", duracion: String = "60") {
        onAction(ServiciosYTarifasAction.CambiarCampoServicio(CampoServicio.NOMBRE, "  Kinesiologia motora "))
        onAction(ServiciosYTarifasAction.CambiarCampoServicio(CampoServicio.DURACION, duracion))
        onAction(ServiciosYTarifasAction.CambiarCampoServicio(CampoServicio.PRECIO, precio))
    }

    @Test
    fun `agregar abre un formulario que no se puede guardar hasta tener nombre, duracion y precio validos`() = runTest {
        val viewModel = cargado()

        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)
        assertFalse(requireNotNull(viewModel.state.value.formulario).puedeGuardar)

        viewModel.completarFormulario()
        assertTrue(requireNotNull(viewModel.state.value.formulario).puedeGuardar)
    }

    @Test
    fun `un precio o duracion fuera de rango deja el formulario sin poder guardarse`() = runTest {
        val viewModel = cargado()
        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)

        viewModel.completarFormulario(precio = "500")
        assertFalse(requireNotNull(viewModel.state.value.formulario).puedeGuardar)

        viewModel.completarFormulario(duracion = "5")
        assertFalse(requireNotNull(viewModel.state.value.formulario).puedeGuardar)
    }

    @Test
    fun `los campos numericos descartan lo que no son digitos`() = runTest {
        val viewModel = cargado()
        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)

        viewModel.onAction(ServiciosYTarifasAction.CambiarCampoServicio(CampoServicio.PRECIO, "$35.000"))

        assertEquals("35000", requireNotNull(viewModel.state.value.formulario).precioTexto)
    }

    @Test
    fun `guardar un servicio nuevo lo agrega a la lista, recalcula el resumen y avisa`() = runTest {
        val enviado = slot<Servicio>()
        coEvery { servicioRepository.guardar(uid, capture(enviado)) } answers {
            Result.Success(enviado.captured.copy(id = "nuevo"))
        }
        val viewModel = cargado(servicio("s1", 30_000L))
        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)
        viewModel.completarFormulario(precio = "50000")

        viewModel.onAction(ServiciosYTarifasAction.GuardarServicio)

        assertEquals("", enviado.captured.id)
        assertEquals("Kinesiologia motora", enviado.captured.nombre)
        assertTrue(enviado.captured.activo)
        val estado = viewModel.state.value
        assertNull(estado.formulario)
        assertEquals(listOf("s1", "nuevo"), estado.servicios.map { it.id })
        assertEquals(40_000L, estado.resumenCatalogo?.tarifaPromedio)
        assertEquals("Servicio agregado.", estado.mensaje)
    }

    @Test
    fun `editar precarga el servicio y guardar conserva su estado activo`() = runTest {
        val enviado = slot<Servicio>()
        coEvery { servicioRepository.guardar(uid, capture(enviado)) } answers { Result.Success(enviado.captured) }
        val viewModel = cargado(servicio("s1", 30_000L, activo = false))

        viewModel.onAction(ServiciosYTarifasAction.EditarServicio("s1"))
        val formulario = requireNotNull(viewModel.state.value.formulario)
        assertEquals("s1", formulario.servicioId)
        assertEquals("30000", formulario.precioTexto)

        viewModel.onAction(ServiciosYTarifasAction.CambiarCampoServicio(CampoServicio.PRECIO, "45000"))
        viewModel.onAction(ServiciosYTarifasAction.GuardarServicio)

        assertFalse(enviado.captured.activo)
        assertEquals(45_000L, viewModel.state.value.servicios.single().precio)
        assertEquals("Servicio actualizado.", viewModel.state.value.mensaje)
    }

    @Test
    fun `si guardar falla el formulario sigue abierto con el borrador y un mensaje`() = runTest {
        coEvery { servicioRepository.guardar(uid, any()) } returns Result.Error(ServicioError.SIN_INTERNET)
        val viewModel = cargado()
        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)
        viewModel.completarFormulario()

        viewModel.onAction(ServiciosYTarifasAction.GuardarServicio)

        val formulario = requireNotNull(viewModel.state.value.formulario)
        assertFalse(formulario.guardando)
        assertTrue(formulario.error != null)
        assertTrue(viewModel.state.value.servicios.isEmpty())
    }

    @Test
    fun `cancelar cierra el formulario sin llamar al repositorio`() = runTest {
        val viewModel = cargado()
        viewModel.onAction(ServiciosYTarifasAction.AgregarNuevoServicio)

        viewModel.onAction(ServiciosYTarifasAction.CancelarServicio)

        assertNull(viewModel.state.value.formulario)
        coVerify(exactly = 0) { servicioRepository.guardar(any(), any()) }
    }
}
