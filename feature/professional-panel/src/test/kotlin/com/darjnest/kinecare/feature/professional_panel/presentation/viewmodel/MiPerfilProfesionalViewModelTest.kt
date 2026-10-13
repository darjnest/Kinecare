@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.PerfilPublico
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
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

class MiPerfilProfesionalViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val fotoPerfilRepository = mockk<FotoPerfilRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel() = MiPerfilProfesionalViewModel(profesionalRepository, usuarioRepository, fotoPerfilRepository, firebaseAuth)

    private fun profesional(
        descripcion: String = "Kinesiologa deportiva",
        credencialesAprobadas: Boolean = true,
        tiposAtencion: List<TipoAtencion> = listOf(TipoAtencion.KINESIOLOGIA),
        fotoUrl: String? = null,
    ) = Profesional(
        perfil = PerfilPublico(id = uid, nombre = "Ana Soto", fotoUrl = fotoUrl),
        especialidades = listOf("Kinesiología", "Masoterapia"),
        rnpi = "123456",
        servicios = emptyList(),
        insignias = listOf(
            Insignia(
                tipo = TipoInsignia.CREDENCIALES,
                estado = if (credencialesAprobadas) EstadoVerificacion.APROBADO else EstadoVerificacion.PENDIENTE,
                detalle = null,
                fechaActualizacion = Instant.fromEpochMilliseconds(0),
            ),
            // Una insignia de otro tipo aprobada no debe contar como credenciales al dia.
            Insignia(TipoInsignia.IDENTIDAD, EstadoVerificacion.APROBADO, null, Instant.fromEpochMilliseconds(0)),
        ),
        disponibilidad = emptyList(),
        calificacionPromedio = 4.7,
        totalResenas = 12,
        descripcion = descripcion,
        estadoVerificacionGeneral = EstadoVerificacion.APROBADO,
        tiposAtencion = tiposAtencion,
    )

    @Test
    fun `al inicializar mapea identidad, metricas, biografia y especialidades reales`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())

        val viewModel = crearViewModel()

        viewModel.state.test {
            val estado = awaitItem()
            assertFalse(estado.cargando)
            val identidad = estado.identidad!!
            assertEquals("Ana Soto", identidad.nombre)
            assertEquals("Kinesiología", identidad.especialidadPrincipal)
            assertEquals("123456", identidad.numeroRegistroSis)
            assertTrue(identidad.credencialesAlDia)
            // Datos que Firestore no tiene quedan nulos en vez de inventarse.
            assertNull(identidad.universidad)
            assertNull(identidad.habilitadoIsapreFonasa)
            assertEquals(4.7, estado.metricas!!.calificacion, 0.0)
            assertEquals(12, estado.metricas!!.totalResenas)
            assertNull(estado.metricas!!.atencionesCompletadas)
            assertNull(estado.metricas!!.porcentajePuntualidad)
            assertEquals("Kinesiologa deportiva", estado.biografia)
            assertEquals(listOf("Kinesiología", "Masoterapia"), estado.especialidades.map { it.nombre })
        }
    }

    @Test
    fun `al cargar expone el uid propio como profesionalId para abrir sus resenas`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())

        val viewModel = crearViewModel()

        assertEquals(uid, viewModel.state.value.profesionalId)
    }

    @Test
    fun `si el perfil no carga no hay profesionalId y VerTodasLasResenas no cambia el estado`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(MiPerfilProfesionalAction.VerTodasLasResenas)

        assertNull(antes.profesionalId)
        assertEquals(antes, viewModel.state.value)
    }

    @Test
    fun `si el perfil no carga se marca errorCarga y no queda cargando`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)

        val estado = crearViewModel().state.value

        assertTrue(estado.errorCarga)
        assertFalse(estado.cargando)
        assertNull(estado.identidad)
    }

    @Test
    fun `Reintentar tras un fallo carga el perfil y limpia el error`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertTrue(viewModel.state.value.errorCarga)

        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        viewModel.onAction(MiPerfilProfesionalAction.Reintentar)

        val estado = viewModel.state.value
        assertFalse(estado.errorCarga)
        assertFalse(estado.cargando)
        assertEquals("Ana Soto", estado.identidad?.nombre)
    }

    @Test
    fun `una carga en curso no se duplica con Reintentar`() = runTest {
        val respuesta = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(uid) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()
        assertTrue(viewModel.state.value.cargando)

        viewModel.onAction(MiPerfilProfesionalAction.Reintentar)

        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(uid) }
        respuesta.complete(Result.Success(profesional()))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `credenciales no aprobadas no se marcan al dia`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(credencialesAprobadas = false))

        val viewModel = crearViewModel()

        assertFalse(viewModel.state.value.identidad!!.credencialesAlDia)
    }

    @Test
    fun `un error de carga deja el perfil vacio y sin cargando`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)

        val viewModel = crearViewModel()

        val estado = viewModel.state.value
        assertFalse(estado.cargando)
        assertNull(estado.identidad)
        assertNull(estado.metricas)
    }

    @Test
    fun `editar biografia abre el dialogo con la biografia actual como borrador`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarBiografia)

        assertTrue(viewModel.state.value.editandoBiografia)
        assertEquals("Kinesiologa deportiva", viewModel.state.value.borradorBiografia)
    }

    @Test
    fun `el borrador se recorta al largo maximo`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.CambiarBorradorBiografia("x".repeat(LARGO_MAXIMO_BIOGRAFIA + 50)))

        assertEquals(LARGO_MAXIMO_BIOGRAFIA, viewModel.state.value.borradorBiografia.length)
    }

    @Test
    fun `guardar biografia persiste el texto recortado, actualiza la tarjeta y cierra el dialogo`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        coEvery { profesionalRepository.actualizarDescripcion(uid, "Nueva bio") } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarBiografia)
        viewModel.onAction(MiPerfilProfesionalAction.CambiarBorradorBiografia("  Nueva bio  "))
        viewModel.onAction(MiPerfilProfesionalAction.GuardarBiografia)

        val estado = viewModel.state.value
        assertEquals("Nueva bio", estado.biografia)
        assertFalse(estado.editandoBiografia)
        assertFalse(estado.guardandoBiografia)
        coVerify(exactly = 1) { profesionalRepository.actualizarDescripcion(uid, "Nueva bio") }
    }

    @Test
    fun `si guardar falla el dialogo sigue abierto con el borrador y la biografia no cambia`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val gate = CompletableDeferred<Result<Unit, ProfesionalError>>()
        coEvery { profesionalRepository.actualizarDescripcion(uid, "Nueva bio") } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarBiografia)
        viewModel.onAction(MiPerfilProfesionalAction.CambiarBorradorBiografia("Nueva bio"))

        viewModel.onAction(MiPerfilProfesionalAction.GuardarBiografia)
        assertTrue(viewModel.state.value.guardandoBiografia)

        // Un segundo toque mientras guarda no dispara otra escritura.
        viewModel.onAction(MiPerfilProfesionalAction.GuardarBiografia)
        gate.complete(Result.Error(ProfesionalError.SIN_INTERNET))

        val estado = viewModel.state.value
        assertFalse(estado.guardandoBiografia)
        assertTrue(estado.errorGuardarBiografia)
        assertTrue(estado.editandoBiografia)
        assertEquals("Nueva bio", estado.borradorBiografia)
        assertEquals("Kinesiologa deportiva", estado.biografia)
        coVerify(exactly = 1) { profesionalRepository.actualizarDescripcion(uid, "Nueva bio") }
    }

    @Test
    fun `al cargar expone los tipos de atencion del profesional`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(tiposAtencion = listOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA)))

        val viewModel = crearViewModel()

        assertEquals(setOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA), viewModel.state.value.tiposAtencion)
    }

    @Test
    fun `editar tipos de atencion abre el dialogo con los actuales como borrador`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)

        assertTrue(viewModel.state.value.editandoTiposAtencion)
        assertEquals(setOf(TipoAtencion.KINESIOLOGIA), viewModel.state.value.borradorTiposAtencion)
    }

    @Test
    fun `alternar agrega y quita un tipo del borrador sin tocar los guardados`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)

        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        assertEquals(
            setOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA),
            viewModel.state.value.borradorTiposAtencion,
        )

        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))
        assertEquals(setOf(TipoAtencion.MASOTERAPIA), viewModel.state.value.borradorTiposAtencion)
        assertEquals(setOf(TipoAtencion.KINESIOLOGIA), viewModel.state.value.tiposAtencion)
    }

    @Test
    fun `guardar tipos de atencion persiste el borrador, actualiza la tarjeta y cierra el dialogo`() = runTest {
        val nuevos = setOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA)
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        coEvery { profesionalRepository.actualizarTiposAtencion(uid, nuevos) } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)

        val estado = viewModel.state.value
        assertEquals(nuevos, estado.tiposAtencion)
        assertFalse(estado.editandoTiposAtencion)
        assertFalse(estado.guardandoTiposAtencion)
        coVerify(exactly = 1) { profesionalRepository.actualizarTiposAtencion(uid, nuevos) }
    }

    @Test
    fun `no se puede guardar sin ningun tipo de atencion`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))

        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)

        assertTrue(viewModel.state.value.editandoTiposAtencion)
        coVerify(exactly = 0) { profesionalRepository.actualizarTiposAtencion(any(), any()) }
    }

    @Test
    fun `si guardar tipos de atencion falla el dialogo sigue abierto y lo guardado no cambia`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val gate = CompletableDeferred<Result<Unit, ProfesionalError>>()
        coEvery { profesionalRepository.actualizarTiposAtencion(uid, any()) } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)
        assertTrue(viewModel.state.value.guardandoTiposAtencion)

        // Un segundo toque mientras guarda no dispara otra escritura.
        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)
        gate.complete(Result.Error(ProfesionalError.SIN_INTERNET))

        val estado = viewModel.state.value
        assertFalse(estado.guardandoTiposAtencion)
        assertTrue(estado.errorGuardarTiposAtencion)
        assertTrue(estado.editandoTiposAtencion)
        assertEquals(setOf(TipoAtencion.KINESIOLOGIA), estado.tiposAtencion)
        coVerify(exactly = 1) { profesionalRepository.actualizarTiposAtencion(uid, any()) }
    }

    @Test
    fun `el estado trae la foto de perfil del profesional`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(fotoUrl = "https://dl/prof.jpg"))

        val viewModel = crearViewModel()

        assertEquals("https://dl/prof.jpg", viewModel.state.value.fotoUrl)
    }

    @Test
    fun `una foto elegida se sube con el rol profesional y su URL se guarda en el usuario`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, "content://foto/1") } returns
            Result.Success("https://dl/nueva.jpg?v=1")
        coEvery { usuarioRepository.actualizarFotoUrl(uid, "https://dl/nueva.jpg?v=1") } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/1"))

        val estado = viewModel.state.value
        assertEquals("https://dl/nueva.jpg?v=1", estado.fotoUrl)
        assertFalse(estado.subiendoFoto)
        assertNull(estado.errorFoto)
        coVerify { usuarioRepository.actualizarFotoUrl(uid, "https://dl/nueva.jpg?v=1") }
    }

    @Test
    fun `mientras sube la foto marca progreso y no acepta otra subida`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val gate = CompletableDeferred<Result<String, FotoPerfilError>>()
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, any()) } coAnswers { gate.await() }
        coEvery { usuarioRepository.actualizarFotoUrl(any(), any()) } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/1"))
        assertTrue(viewModel.state.value.subiendoFoto)
        viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/2"))
        gate.complete(Result.Success("https://dl/a.jpg"))

        assertFalse(viewModel.state.value.subiendoFoto)
        coVerify(exactly = 1) { fotoPerfilRepository.subir(any(), any(), any()) }
    }

    @Test
    fun `si Storage no esta disponible avisa con su mensaje y conserva la foto`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(fotoUrl = "https://dl/prof.jpg"))
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, any()) } returns
            Result.Error(FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/1"))

        val estado = viewModel.state.value
        assertEquals("Cambiar la foto de perfil aún no está disponible. Estamos trabajando en ello.", estado.errorFoto)
        assertEquals("https://dl/prof.jpg", estado.fotoUrl)
        assertFalse(estado.subiendoFoto)
        coVerify(exactly = 0) { usuarioRepository.actualizarFotoUrl(any(), any()) }
    }

    @Test
    fun `sin conexion o con imagen invalida cada error tiene su mensaje`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val esperados = mapOf(
            FotoPerfilError.SIN_INTERNET to "Sin conexión. Revisa tu internet e intenta de nuevo.",
            FotoPerfilError.IMAGEN_INVALIDA to "No pudimos leer esa imagen. Elige otra foto.",
            FotoPerfilError.IMAGEN_MUY_GRANDE to "La foto es demasiado pesada. Elige otra más liviana.",
            FotoPerfilError.DESCONOCIDO to "No pudimos subir la foto. Intenta de nuevo.",
        )
        esperados.forEach { (error, mensaje) ->
            coEvery { fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, any()) } returns Result.Error(error)
            val viewModel = crearViewModel()

            viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/1"))

            assertEquals(mensaje, viewModel.state.value.errorFoto, error.name)
        }
    }

    @Test
    fun `si la foto sube pero la URL no se guarda se avisa y la foto no cambia`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        coEvery { fotoPerfilRepository.subir(uid, RolUsuario.PROFESIONAL, any()) } returns Result.Success("https://dl/a.jpg")
        coEvery { usuarioRepository.actualizarFotoUrl(uid, "https://dl/a.jpg") } returns
            Result.Error(UsuarioError.SIN_INTERNET)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.FotoElegida("content://foto/1"))

        val estado = viewModel.state.value
        assertEquals("No pudimos guardar la foto en tu perfil. Intenta de nuevo.", estado.errorFoto)
        assertNull(estado.fotoUrl)
        assertFalse(estado.subiendoFoto)
    }
}
