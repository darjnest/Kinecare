@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.search.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SearchViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val ubicacionRepository = mockk<UbicacionRepository>()
    private val profesionalRepository = mockk<ProfesionalRepository>()

    private fun crearViewModel(): SearchViewModel =
        SearchViewModel(ubicacionRepository, profesionalRepository)

    private fun profesionalDePrueba(id: String = "prof-1", nombre: String = "Ana Soto"): Profesional = Profesional(
        usuario = Usuario(
            id = id,
            nombre = nombre,
            rut = "12345678-5",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        especialidades = listOf("KINESIOLOGIA"),
        rnpi = "RNPI-1",
        servicios = listOf(
            Servicio(
                id = "serv-1",
                nombre = "Sesion",
                descripcion = "desc",
                modalidad = ModalidadServicio.CONSULTA,
                duracionMinutos = 60,
                precio = 18000L,
            ),
        ),
        insignias = emptyList(),
        disponibilidad = emptyList(),
        calificacionPromedio = 4.5,
        totalResenas = 10,
        descripcion = "desc",
        estadoVerificacionGeneral = EstadoVerificacion.APROBADO,
    )

    @Test
    fun `al inicializar carga profesionales destacados de la especialidad por defecto`() = runTest {
        coEvery { profesionalRepository.buscarPorEspecialidad("KINESIOLOGIA") } returns
            Result.Success(listOf(profesionalDePrueba()))

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertEquals(false, estado.cargandoProfesionales)
            assertEquals(1, estado.profesionalesDestacados.size)
            assertEquals("Ana Soto", estado.profesionalesDestacados.first().nombre)
        }
        coVerify(exactly = 1) { profesionalRepository.buscarPorEspecialidad("KINESIOLOGIA") }
    }

    @Test
    fun `cambiar tipo de atencion actualiza categorias sincronicamente y recarga profesionales de la nueva especialidad`() = runTest {
        coEvery { profesionalRepository.buscarPorEspecialidad("KINESIOLOGIA") } returns
            Result.Success(emptyList())
        coEvery { profesionalRepository.buscarPorEspecialidad("MASOTERAPIA") } returns
            Result.Success(listOf(profesionalDePrueba(id = "prof-2", nombre = "Bruno Diaz")))

        val viewModel = crearViewModel()

        viewModel.onAction(SearchAction.CambiarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.state.test {
            val estado = awaitItem()
            assertEquals(TipoAtencion.MASOTERAPIA, estado.tipoAtencion)
            assertEquals(categoriasParaTest(TipoAtencion.MASOTERAPIA), estado.categorias.map { it.id })
            assertEquals(false, estado.cargandoProfesionales)
            assertEquals(1, estado.profesionalesDestacados.size)
            assertEquals("Bruno Diaz", estado.profesionalesDestacados.first().nombre)
        }
        coVerify(exactly = 1) { profesionalRepository.buscarPorEspecialidad("MASOTERAPIA") }
    }

    @Test
    fun `cuando el repositorio retorna error el estado queda sin profesionales y sin cargando`() = runTest {
        coEvery { profesionalRepository.buscarPorEspecialidad("KINESIOLOGIA") } returns
            Result.Success(listOf(profesionalDePrueba()))
        coEvery { profesionalRepository.buscarPorEspecialidad("MASOTERAPIA") } returns
            Result.Error(ProfesionalError.SIN_INTERNET)

        val viewModel = crearViewModel()

        // Precondicion: la carga inicial (KINESIOLOGIA) si trajo datos.
        viewModel.state.test {
            val inicial = awaitItem()
            assertTrue(inicial.profesionalesDestacados.isNotEmpty())
        }

        viewModel.onAction(SearchAction.CambiarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.state.test {
            val estado = awaitItem()
            assertEquals(false, estado.cargandoProfesionales)
            assertTrue(estado.profesionalesDestacados.isEmpty())
        }
    }

    /** Espejo minimo del catalogo privado de `SearchViewModel` solo para comparar ids de categoria en el test. */
    private fun categoriasParaTest(tipo: TipoAtencion): List<String> = when (tipo) {
        TipoAtencion.KINESIOLOGIA -> listOf("kine-deportiva", "kine-traumatologica", "kine-respiratoria", "kine-neurologica")
        TipoAtencion.MASOTERAPIA -> listOf("maso-descontracturante", "maso-drenaje", "maso-deportivo", "maso-reflexologia")
    }
}
