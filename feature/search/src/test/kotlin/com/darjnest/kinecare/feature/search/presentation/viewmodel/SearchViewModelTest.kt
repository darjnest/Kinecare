@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.search.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.Cliente
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
    private val clienteRepository = mockk<ClienteRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    init {
        every { firebaseAuth.currentUser } returns null
    }

    private fun conSesion(uid: String = "cli-1", favoritos: List<String> = emptyList()) {
        val usuario = mockk<FirebaseUser>()
        every { usuario.uid } returns uid
        every { firebaseAuth.currentUser } returns usuario
        coEvery { clienteRepository.obtenerPorId(uid) } returns Result.Success(
            Cliente(
                usuario = Usuario(uid, "Cata", "11111111-1", "c@k.cl", null, null, RolUsuario.CLIENTE, null, Instant.fromEpochMilliseconds(0)),
                direcciones = emptyList(),
                metodosPago = emptyList(),
                favoritos = favoritos,
            ),
        )
    }

    private fun crearViewModel(): SearchViewModel =
        SearchViewModel(ubicacionRepository, profesionalRepository, clienteRepository, firebaseAuth)

    private fun profesionalDePrueba(
        id: String = "prof-1",
        nombre: String = "Ana Soto",
        especialidades: List<String> = listOf("KINESIOLOGIA"),
        descripcion: String = "desc",
        modalidad: ModalidadServicio = ModalidadServicio.DOMICILIO,
        precio: Long = 18000L,
        verificado: Boolean = true,
        calificacion: Double = 4.5,
        totalResenas: Int = 10,
    ): Profesional = Profesional(
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
        especialidades = especialidades,
        rnpi = "RNPI-1",
        servicios = listOf(
            Servicio(
                id = "serv-1",
                nombre = "Sesion",
                descripcion = "desc",
                modalidad = modalidad,
                duracionMinutos = 60,
                precio = precio,
            ),
        ),
        insignias = emptyList(),
        disponibilidad = emptyList(),
        calificacionPromedio = calificacion,
        totalResenas = totalResenas,
        descripcion = descripcion,
        estadoVerificacionGeneral = if (verificado) EstadoVerificacion.APROBADO else EstadoVerificacion.NO_SOLICITADO,
        tiposAtencion = listOf(TipoAtencion.KINESIOLOGIA),
    )

    @Test
    fun `al inicializar carga profesionales destacados de la especialidad por defecto`() = runTest {
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) } returns
            Result.Success(listOf(profesionalDePrueba()))

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertEquals(false, estado.cargandoProfesionales)
            assertEquals(1, estado.profesionalesDestacados.size)
            assertEquals("Ana Soto", estado.profesionalesDestacados.first().nombre)
        }
        coVerify(exactly = 1) { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) }
    }

    @Test
    fun `cambiar tipo de atencion actualiza categorias sincronicamente y recarga profesionales de la nueva especialidad`() = runTest {
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) } returns
            Result.Success(emptyList())
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.MASOTERAPIA) } returns
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
        coVerify(exactly = 1) { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.MASOTERAPIA) }
    }

    @Test
    fun `cuando el repositorio retorna error el estado queda sin profesionales y sin cargando`() = runTest {
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) } returns
            Result.Success(listOf(profesionalDePrueba()))
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.MASOTERAPIA) } returns
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

    @Test
    fun `la especialidad de la tarjeta ignora los tags de tipo y usa la categoria conocida`() = runTest {
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) } returns Result.Success(
            listOf(
                profesionalDePrueba(id = "prof-1", especialidades = listOf("KINESIOLOGIA", "kine-respiratoria")),
                profesionalDePrueba(id = "prof-2", especialidades = listOf("MASOTERAPIA"), descripcion = "Bio libre"),
            ),
        )

        val viewModel = crearViewModel()

        viewModel.state.test {
            val destacados = awaitItem().profesionalesDestacados
            assertEquals("Kinesiología respiratoria", destacados[0].especialidad)
            assertEquals("Bio libre", destacados[1].especialidad)
        }
    }

    private fun cargar(vararg profesionales: Profesional): SearchViewModel {
        coEvery { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) } returns
            Result.Success(profesionales.toList())
        return crearViewModel()
    }

    @Test
    fun `la modalidad elegida filtra por los servicios activos de cada profesional`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "domi", modalidad = ModalidadServicio.DOMICILIO),
            profesionalDePrueba(id = "consulta", modalidad = ModalidadServicio.CONSULTA),
        )

        assertEquals(listOf("domi"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarModalidad(ModalidadAtencion.EN_CONSULTA))

        assertEquals(listOf("consulta"), viewModel.state.value.profesionalesDestacados.map { it.id })
        assertEquals(2, viewModel.state.value.totalProfesionalesCargados)
    }

    @Test
    fun `solo verificados oculta a los no verificados y se puede apagar`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "ok", verificado = true),
            profesionalDePrueba(id = "sin", verificado = false),
        )
        assertEquals(listOf("ok", "sin"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarSoloVerificados(true))
        assertEquals(listOf("ok"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarSoloVerificados(false))
        assertEquals(2, viewModel.state.value.profesionalesDestacados.size)
    }

    @Test
    fun `el texto libre busca por nombre, sin tildes ni mayusculas, y se puede borrar`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", nombre = "Ana Pérez"),
            profesionalDePrueba(id = "b", nombre = "Bruno Díaz"),
        )

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("PEREZ"))
        assertEquals(listOf("a"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("diaz"))
        assertEquals(listOf("b"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarTextoBusqueda(""))
        assertEquals(2, viewModel.state.value.profesionalesDestacados.size)
    }

    @Test
    fun `el texto libre busca en la especialidad y en la descripcion`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "dep", especialidades = listOf("KINESIOLOGIA", "kine-deportiva")),
            profesionalDePrueba(id = "resp", especialidades = listOf("KINESIOLOGIA", "kine-respiratoria")),
            profesionalDePrueba(id = "desc", descripcion = "Atiendo en Ñuñoa y La Reina"),
        )

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("deportiva"))
        assertEquals(listOf("dep"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("nunoa"))
        assertEquals(listOf("desc"), viewModel.state.value.profesionalesDestacados.map { it.id })
    }

    @Test
    fun `todas las palabras del texto deben calzar y un texto sin coincidencias deja la lista vacia`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", nombre = "Ana Pérez", especialidades = listOf("KINESIOLOGIA", "kine-deportiva")),
            profesionalDePrueba(id = "b", nombre = "Ana Díaz"),
        )

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("ana deportiva"))
        assertEquals(listOf("a"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("zzz"))
        assertTrue(viewModel.state.value.profesionalesDestacados.isEmpty())
        assertEquals(2, viewModel.state.value.totalProfesionalesCargados)
    }

    @Test
    fun `el texto libre se combina con los demas filtros y con los conteos de categorias`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", nombre = "Ana", especialidades = listOf("KINESIOLOGIA", "kine-deportiva"), verificado = true),
            profesionalDePrueba(id = "b", nombre = "Ana", especialidades = listOf("KINESIOLOGIA", "kine-deportiva"), verificado = false),
            profesionalDePrueba(id = "c", nombre = "Carlos", especialidades = listOf("KINESIOLOGIA", "kine-deportiva")),
        )

        viewModel.onAction(SearchAction.CambiarTextoBusqueda("ana"))
        viewModel.onAction(SearchAction.CambiarSoloVerificados(true))

        assertEquals(listOf("a"), viewModel.state.value.profesionalesDestacados.map { it.id })
        assertEquals(1, viewModel.state.value.categorias.first { it.id == "kine-deportiva" }.profesionalesActivos)
    }

    @Test
    fun `el orden por defecto es mejor calificados, con mas resenas como desempate`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "media", nombre = "Media", calificacion = 4.0, totalResenas = 50),
            profesionalDePrueba(id = "top-poca", nombre = "Top poca", calificacion = 5.0, totalResenas = 2),
            profesionalDePrueba(id = "top-mucha", nombre = "Top mucha", calificacion = 5.0, totalResenas = 20),
        )

        assertEquals(OrdenBusqueda.MEJOR_CALIFICADOS, viewModel.state.value.orden)
        assertEquals(listOf("top-mucha", "top-poca", "media"), viewModel.state.value.profesionalesDestacados.map { it.id })
    }

    @Test
    fun `ordenar por menor precio pone primero el desde mas barato y al final al que no tiene precio`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "caro", nombre = "Caro", precio = 40_000),
            profesionalDePrueba(id = "gratis", nombre = "Sin precio", precio = 0),
            profesionalDePrueba(id = "barato", nombre = "Barato", precio = 15_000),
        )

        viewModel.onAction(SearchAction.CambiarOrden(OrdenBusqueda.MENOR_PRECIO))

        assertEquals(listOf("barato", "caro", "gratis"), viewModel.state.value.profesionalesDestacados.map { it.id })
    }

    @Test
    fun `ordenar por mas resenas y volver a mejor calificados`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "estrella", nombre = "Estrella", calificacion = 5.0, totalResenas = 3),
            profesionalDePrueba(id = "popular", nombre = "Popular", calificacion = 4.2, totalResenas = 90),
        )

        viewModel.onAction(SearchAction.CambiarOrden(OrdenBusqueda.MAS_RESENAS))
        assertEquals(listOf("popular", "estrella"), viewModel.state.value.profesionalesDestacados.map { it.id })

        viewModel.onAction(SearchAction.CambiarOrden(OrdenBusqueda.MEJOR_CALIFICADOS))
        assertEquals(listOf("estrella", "popular"), viewModel.state.value.profesionalesDestacados.map { it.id })
    }

    @Test
    fun `el texto y el orden se conservan al recargar con Buscar`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", nombre = "Ana", precio = 30_000),
            profesionalDePrueba(id = "b", nombre = "Ana Maria", precio = 10_000),
        )
        viewModel.onAction(SearchAction.CambiarTextoBusqueda("ana"))
        viewModel.onAction(SearchAction.CambiarOrden(OrdenBusqueda.MENOR_PRECIO))

        viewModel.onAction(SearchAction.Buscar)

        assertEquals("ana", viewModel.state.value.textoBusqueda)
        assertEquals(listOf("b", "a"), viewModel.state.value.profesionalesDestacados.map { it.id })
    }

    @Test
    fun `los conteos y precios de las categorias salen de los profesionales cargados`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", especialidades = listOf("KINESIOLOGIA", "kine-deportiva"), precio = 25000L),
            profesionalDePrueba(id = "b", especialidades = listOf("KINESIOLOGIA", "kine-deportiva"), precio = 20000L),
            profesionalDePrueba(id = "c", especialidades = listOf("KINESIOLOGIA", "kine-respiratoria"), precio = 30000L),
        )

        val categorias = viewModel.state.value.categorias.associateBy { it.id }

        assertEquals(2, categorias.getValue("kine-deportiva").profesionalesActivos)
        assertEquals(20000L, categorias.getValue("kine-deportiva").precioDesde)
        assertEquals(1, categorias.getValue("kine-respiratoria").profesionalesActivos)
        assertEquals(0, categorias.getValue("kine-neurologica").profesionalesActivos)
        assertEquals(0L, categorias.getValue("kine-neurologica").precioDesde)
    }

    @Test
    fun `seleccionar una categoria filtra la lista, tocarla de nuevo o Ver todos la quita`() = runTest {
        val viewModel = cargar(
            profesionalDePrueba(id = "a", especialidades = listOf("KINESIOLOGIA", "kine-deportiva")),
            profesionalDePrueba(id = "b", especialidades = listOf("KINESIOLOGIA", "kine-respiratoria")),
        )

        viewModel.onAction(SearchAction.SeleccionarCategoria("kine-deportiva"))
        assertEquals(listOf("a"), viewModel.state.value.profesionalesDestacados.map { it.id })
        assertEquals("kine-deportiva", viewModel.state.value.categoriaSeleccionadaId)
        // Los conteos no se achican al elegir una categoria: siguen contando las demas.
        assertEquals(1, viewModel.state.value.categorias.first { it.id == "kine-respiratoria" }.profesionalesActivos)

        viewModel.onAction(SearchAction.SeleccionarCategoria("kine-deportiva"))
        assertEquals(2, viewModel.state.value.profesionalesDestacados.size)

        viewModel.onAction(SearchAction.SeleccionarCategoria("kine-respiratoria"))
        viewModel.onAction(SearchAction.VerTodasLasCategorias)
        assertEquals(null, viewModel.state.value.categoriaSeleccionadaId)
        assertEquals(2, viewModel.state.value.profesionalesDestacados.size)
    }

    @Test
    fun `cambiar de tipo de atencion limpia la categoria elegida`() = runTest {
        coEvery { profesionalRepository.buscarPorTipoAtencion(any()) } returns Result.Success(emptyList())
        val viewModel = crearViewModel()
        viewModel.onAction(SearchAction.SeleccionarCategoria("kine-deportiva"))

        viewModel.onAction(SearchAction.CambiarTipoAtencion(TipoAtencion.MASOTERAPIA))

        assertEquals(null, viewModel.state.value.categoriaSeleccionadaId)
    }

    @Test
    fun `Buscar vuelve a consultar el tipo de atencion elegido`() = runTest {
        val viewModel = cargar(profesionalDePrueba())

        viewModel.onAction(SearchAction.Buscar)

        coVerify(exactly = 2) { profesionalRepository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) }
    }

    @Test
    fun `los favoritos del cliente se cargan al iniciar`() = runTest {
        conSesion(favoritos = listOf("a", "b"))

        val viewModel = cargar(profesionalDePrueba(id = "a"))

        assertEquals(setOf("a", "b"), viewModel.state.value.favoritos)
    }

    @Test
    fun `alternar agrega y quita un favorito`() = runTest {
        conSesion()
        coEvery { clienteRepository.agregarFavorito("cli-1", "a") } returns Result.Success(Unit)
        coEvery { clienteRepository.quitarFavorito("cli-1", "a") } returns Result.Success(Unit)
        val viewModel = cargar(profesionalDePrueba(id = "a"))

        viewModel.onAction(SearchAction.AlternarFavorito("a"))
        assertEquals(setOf("a"), viewModel.state.value.favoritos)

        viewModel.onAction(SearchAction.AlternarFavorito("a"))
        assertEquals(emptySet<String>(), viewModel.state.value.favoritos)
        coVerify { clienteRepository.agregarFavorito("cli-1", "a") }
        coVerify { clienteRepository.quitarFavorito("cli-1", "a") }
    }

    @Test
    fun `si guardar el favorito falla se revierte y se avisa`() = runTest {
        conSesion()
        coEvery { clienteRepository.agregarFavorito("cli-1", "a") } returns Result.Error(ClienteError.SIN_INTERNET)
        val viewModel = cargar(profesionalDePrueba(id = "a"))

        viewModel.onAction(SearchAction.AlternarFavorito("a"))

        assertEquals(emptySet<String>(), viewModel.state.value.favoritos)
        assertTrue(viewModel.state.value.mensaje != null)
        viewModel.onAction(SearchAction.MensajeMostrado)
        assertEquals(null, viewModel.state.value.mensaje)
    }

    /** Espejo minimo del catalogo privado de `SearchViewModel` solo para comparar ids de categoria en el test. */
    private fun categoriasParaTest(tipo: TipoAtencion): List<String> = when (tipo) {
        TipoAtencion.KINESIOLOGIA -> listOf("kine-deportiva", "kine-traumatologica", "kine-respiratoria", "kine-neurologica")
        TipoAtencion.MASOTERAPIA -> listOf("maso-descontracturante", "maso-drenaje", "maso-deportivo", "maso-reflexologia")
    }
}
