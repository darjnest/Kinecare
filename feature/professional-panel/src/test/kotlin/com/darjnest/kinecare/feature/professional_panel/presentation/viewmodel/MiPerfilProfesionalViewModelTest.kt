@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
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
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val uid = "prof-1"

    init {
        val firebaseUser = mockk<FirebaseUser>()
        every { firebaseUser.uid } returns uid
        every { firebaseAuth.currentUser } returns firebaseUser
    }

    private fun crearViewModel() = MiPerfilProfesionalViewModel(profesionalRepository, firebaseAuth)

    private fun profesional(
        descripcion: String = "Kinesiologa deportiva",
        credencialesAprobadas: Boolean = true,
        tiposAtencion: List<TipoAtencion> = listOf(TipoAtencion.KINESIOLOGIA),
    ) = Profesional(
        usuario = Usuario(
            id = uid,
            nombre = "Ana Soto",
            rut = "12345678-5",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
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
    fun `al cargar expone los tiposAtencion reales en el orden del enum`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(
            profesional(tiposAtencion = listOf(TipoAtencion.MASOTERAPIA, TipoAtencion.KINESIOLOGIA)),
        )

        val viewModel = crearViewModel()

        assertEquals(
            listOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA),
            viewModel.state.value.tiposAtencion,
        )
    }

    @Test
    fun `editar tiposAtencion abre el dialogo con la seleccion actual como borrador`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)

        val estado = viewModel.state.value
        assertTrue(estado.editandoTiposAtencion)
        assertEquals(setOf(TipoAtencion.KINESIOLOGIA), estado.borradorTiposAtencion)
    }

    @Test
    fun `alternar agrega y quita tipos del borrador sin tocar lo guardado`() = runTest {
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
        assertEquals(listOf(TipoAtencion.KINESIOLOGIA), viewModel.state.value.tiposAtencion)
    }

    @Test
    fun `cancelar la edicion de tiposAtencion cierra el dialogo y conserva lo guardado`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.onAction(MiPerfilProfesionalAction.CancelarEdicionTiposAtencion)

        val estado = viewModel.state.value
        assertFalse(estado.editandoTiposAtencion)
        assertEquals(listOf(TipoAtencion.KINESIOLOGIA), estado.tiposAtencion)
        coVerify(exactly = 0) { profesionalRepository.actualizarTiposAtencion(any(), any()) }
    }

    @Test
    fun `guardar tiposAtencion persiste en orden del enum, actualiza la tarjeta y cierra el dialogo`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val ambas = listOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA)
        coEvery { profesionalRepository.actualizarTiposAtencion(uid, ambas) } returns Result.Success(Unit)
        val viewModel = crearViewModel()

        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)

        val estado = viewModel.state.value
        assertEquals(ambas, estado.tiposAtencion)
        assertFalse(estado.editandoTiposAtencion)
        assertFalse(estado.guardandoTiposAtencion)
        coVerify(exactly = 1) { profesionalRepository.actualizarTiposAtencion(uid, ambas) }
    }

    @Test
    fun `no se guarda un borrador sin ningun tipo de atencion`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.KINESIOLOGIA))

        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)

        val estado = viewModel.state.value
        assertTrue(estado.borradorTiposAtencion.isEmpty())
        assertTrue(estado.editandoTiposAtencion)
        assertFalse(estado.guardandoTiposAtencion)
        assertEquals(listOf(TipoAtencion.KINESIOLOGIA), estado.tiposAtencion)
        coVerify(exactly = 0) { profesionalRepository.actualizarTiposAtencion(any(), any()) }
    }

    @Test
    fun `si guardar tiposAtencion falla el dialogo sigue abierto con la seleccion y lo guardado no cambia`() = runTest {
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional())
        val ambas = listOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA)
        val gate = CompletableDeferred<Result<Unit, ProfesionalError>>()
        coEvery { profesionalRepository.actualizarTiposAtencion(uid, ambas) } coAnswers { gate.await() }
        val viewModel = crearViewModel()
        viewModel.onAction(MiPerfilProfesionalAction.EditarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))

        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)
        assertTrue(viewModel.state.value.guardandoTiposAtencion)

        // Ni un segundo toque ni cambiar casillas mientras guarda disparan otra escritura o mueven el borrador.
        viewModel.onAction(MiPerfilProfesionalAction.GuardarTiposAtencion)
        viewModel.onAction(MiPerfilProfesionalAction.AlternarTipoAtencion(TipoAtencion.MASOTERAPIA))
        gate.complete(Result.Error(ProfesionalError.SIN_INTERNET))

        val estado = viewModel.state.value
        assertFalse(estado.guardandoTiposAtencion)
        assertTrue(estado.errorGuardarTiposAtencion)
        assertTrue(estado.editandoTiposAtencion)
        assertEquals(ambas.toSet(), estado.borradorTiposAtencion)
        assertEquals(listOf(TipoAtencion.KINESIOLOGIA), estado.tiposAtencion)
        coVerify(exactly = 1) { profesionalRepository.actualizarTiposAtencion(uid, ambas) }
    }
}
