package com.darjnest.kinecare.feature.verification.presentation.viewmodel

import app.cash.turbine.test
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.IntentoVerificacion
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@OptIn(ExperimentalCoroutinesApi::class)
class VerificationViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val repository = mockk<VerificacionRepository>()

    private val urlDidit = "https://verify.didit.me/session/abc"

    private fun estado(
        estado: EstadoVerificacion,
        motivo: MotivoRechazoVerificacion? = null,
    ): Result<EstadoSolicitudVerificacion, EstadoVerificacionError> =
        Result.Success(EstadoSolicitudVerificacion(estado, "sol-1", motivo))

    private fun respondeEstado(vararg resultados: Result<EstadoSolicitudVerificacion, EstadoVerificacionError>) {
        coEvery { repository.consultarEstado(null) } returnsMany resultados.toList()
    }

    private fun respondeSolicitar(resultado: Result<IntentoVerificacion, SolicitarVerificacionError>) {
        coEvery { repository.solicitar() } returns resultado
    }

    private fun crearViewModel() = VerificationViewModel(repository)

    // ── estado inicial ───────────────────────────────────────────────────────────────────

    @Test
    fun `al abrir consulta el estado de la verificacion mas reciente (sin solicitudId)`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))

        crearViewModel()

        coVerify(exactly = 1) { repository.consultarEstado(null) }
    }

    @Test
    fun `NO_SOLICITADO muestra la explicacion con el boton de verificar`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))

        assertEquals(VerificationState(fase = FaseVerificacion.NO_SOLICITADO), crearViewModel().state.value)
    }

    @Test
    fun `PENDIENTE muestra verificacion en curso`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE))

        assertEquals(VerificationState(fase = FaseVerificacion.PENDIENTE), crearViewModel().state.value)
    }

    @Test
    fun `APROBADO muestra identidad verificada`() = runTest {
        respondeEstado(estado(EstadoVerificacion.APROBADO))

        assertEquals(VerificationState(fase = FaseVerificacion.APROBADO), crearViewModel().state.value)
    }

    @ParameterizedTest
    @EnumSource(MotivoRechazoVerificacion::class)
    fun `RECHAZADO conserva el motivo para elegir el mensaje`(motivo: MotivoRechazoVerificacion) = runTest {
        respondeEstado(estado(EstadoVerificacion.RECHAZADO, motivo))

        val state = crearViewModel().state.value

        assertEquals(FaseVerificacion.RECHAZADO, state.fase)
        assertEquals(motivo, state.motivoRechazo)
    }

    @Test
    fun `RECHAZADO sin motivo conocido queda sin motivo`() = runTest {
        respondeEstado(estado(EstadoVerificacion.RECHAZADO, motivo = null))

        val state = crearViewModel().state.value

        assertEquals(FaseVerificacion.RECHAZADO, state.fase)
        assertNull(state.motivoRechazo)
    }

    @Test
    fun `mientras consulta por primera vez esta CARGANDO`() = runTest {
        val respuesta = CompletableDeferred<Result<EstadoSolicitudVerificacion, EstadoVerificacionError>>()
        coEvery { repository.consultarEstado(null) } coAnswers { respuesta.await() }

        val viewModel = crearViewModel()
        assertEquals(FaseVerificacion.CARGANDO, viewModel.state.value.fase)

        respuesta.complete(estado(EstadoVerificacion.NO_SOLICITADO))
        assertEquals(FaseVerificacion.NO_SOLICITADO, viewModel.state.value.fase)
    }

    // ── errores de la consulta ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(EstadoVerificacionError::class)
    fun `un error de la consulta inicial muestra la pantalla de error con el motivo`(error: EstadoVerificacionError) =
        runTest {
            respondeEstado(Result.Error(error))

            assertEquals(
                VerificationState(fase = FaseVerificacion.ERROR, errorEstado = error),
                crearViewModel().state.value,
            )
        }

    @Test
    fun `Reintentar tras un error vuelve a consultar y muestra el estado`() = runTest {
        respondeEstado(Result.Error(EstadoVerificacionError.SIN_INTERNET), estado(EstadoVerificacion.NO_SOLICITADO))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Reintentar)

        assertEquals(VerificationState(fase = FaseVerificacion.NO_SOLICITADO), viewModel.state.value)
        coVerify(exactly = 2) { repository.consultarEstado(null) }
    }

    @Test
    fun `una consulta en curso no se duplica con Reintentar ni con Actualizar`() = runTest {
        val respuesta = CompletableDeferred<Result<EstadoSolicitudVerificacion, EstadoVerificacionError>>()
        coEvery { repository.consultarEstado(null) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Reintentar)
        viewModel.onAction(VerificationAction.Actualizar)

        coVerify(exactly = 1) { repository.consultarEstado(null) }
        respuesta.complete(estado(EstadoVerificacion.APROBADO))
    }

    // ── Verificar / Continuar / Intentar de nuevo ────────────────────────────────────────

    @Test
    fun `Verificar con una URL de Didit emite el evento para abrir el Custom Tab y deja la verificacion en curso`() =
        runTest {
            respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
            respondeSolicitar(Result.Success(IntentoVerificacion("sol-1", urlDidit)))
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(VerificationAction.Verificar)

                val evento = awaitItem() as VerificationEvent.AbrirVerificacion
                assertEquals(urlDidit, evento.url)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(VerificationState(fase = FaseVerificacion.PENDIENTE), viewModel.state.value)
        }

    @Test
    fun `Continuar verificacion y Intentar de nuevo vuelven a llamar solicitar y abren la URL`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE))
        respondeSolicitar(Result.Success(IntentoVerificacion("sol-1", urlDidit)))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(VerificationAction.Verificar)
            assertEquals(urlDidit, (awaitItem() as VerificationEvent.AbrirVerificacion).url)
            viewModel.onAction(VerificationAction.Verificar)
            assertEquals(urlDidit, (awaitItem() as VerificationEvent.AbrirVerificacion).url)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { repository.solicitar() }
    }

    @Test
    fun `tras un rechazo Verificar abre la URL y limpia el motivo`() = runTest {
        respondeEstado(estado(EstadoVerificacion.RECHAZADO, MotivoRechazoVerificacion.EXPIRADA))
        respondeSolicitar(Result.Success(IntentoVerificacion("sol-2", urlDidit)))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(VerificationAction.Verificar)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(FaseVerificacion.PENDIENTE, viewModel.state.value.fase)
        assertNull(viewModel.state.value.motivoRechazo)
    }

    @Test
    fun `una URL que no es de Didit nunca se abre y se informa como error generico`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
        val urlsInvalidas = listOf(
            "http://verify.didit.me/session/abc",
            "https://evil.com/session",
            "https://didit.me.evil.com/session",
            "https://evil-didit.me/session",
            "javascript:alert(1)",
            "intent://verify.didit.me#Intent;end",
            "",
        )
        for (url in urlsInvalidas) {
            respondeSolicitar(Result.Success(IntentoVerificacion("sol-1", url)))
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(VerificationAction.Verificar)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SolicitarVerificacionError.DESCONOCIDO, viewModel.state.value.errorSolicitud, url)
            assertEquals(FaseVerificacion.NO_SOLICITADO, viewModel.state.value.fase, url)
            assertFalse(viewModel.state.value.solicitando, url)
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = SolicitarVerificacionError::class,
        names = ["YA_VERIFICADO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `cada error de solicitar llega al estado sin abrir nada ni perder la pantalla`(error: SolicitarVerificacionError) =
        runTest {
            respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
            respondeSolicitar(Result.Error(error))
            val viewModel = crearViewModel()

            viewModel.events.test {
                viewModel.onAction(VerificationAction.Verificar)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                VerificationState(fase = FaseVerificacion.NO_SOLICITADO, errorSolicitud = error),
                viewModel.state.value,
            )
        }

    @Test
    fun `YA_VERIFICADO se trata como exito y refresca el estado en vez de abrir el navegador`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO), estado(EstadoVerificacion.APROBADO))
        respondeSolicitar(Result.Error(SolicitarVerificacionError.YA_VERIFICADO))
        val viewModel = crearViewModel()

        viewModel.events.test {
            viewModel.onAction(VerificationAction.Verificar)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(VerificationState(fase = FaseVerificacion.APROBADO), viewModel.state.value)
        coVerify(exactly = 2) { repository.consultarEstado(null) }
    }

    @Test
    fun `un nuevo intento de Verificar limpia el error anterior`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
        coEvery { repository.solicitar() } returnsMany listOf(
            Result.Error(SolicitarVerificacionError.SIN_INTERNET),
            Result.Success(IntentoVerificacion("sol-1", urlDidit)),
        )
        val viewModel = crearViewModel()
        viewModel.onAction(VerificationAction.Verificar)
        assertEquals(SolicitarVerificacionError.SIN_INTERNET, viewModel.state.value.errorSolicitud)

        viewModel.events.test {
            viewModel.onAction(VerificationAction.Verificar)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.errorSolicitud)
    }

    @Test
    fun `Verificar mientras solicita o mientras carga no duplica la llamada`() = runTest {
        val respuestaEstado = CompletableDeferred<Result<EstadoSolicitudVerificacion, EstadoVerificacionError>>()
        coEvery { repository.consultarEstado(null) } coAnswers { respuestaEstado.await() }
        val viewModel = crearViewModel()

        // Aun CARGANDO: no hay boton, pero una accion perdida no debe solicitar.
        viewModel.onAction(VerificationAction.Verificar)
        coVerify(exactly = 0) { repository.solicitar() }

        respuestaEstado.complete(estado(EstadoVerificacion.NO_SOLICITADO))
        val respuestaSolicitar = CompletableDeferred<Result<IntentoVerificacion, SolicitarVerificacionError>>()
        coEvery { repository.solicitar() } coAnswers { respuestaSolicitar.await() }

        viewModel.onAction(VerificationAction.Verificar)
        assertTrue(viewModel.state.value.solicitando)
        viewModel.onAction(VerificationAction.Verificar)

        coVerify(exactly = 1) { repository.solicitar() }
        respuestaSolicitar.complete(Result.Error(SolicitarVerificacionError.DESCONOCIDO))
        assertFalse(viewModel.state.value.solicitando)
    }

    @Test
    fun `NavegadorNoDisponible se refleja en el estado y Verificar lo limpia`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
        respondeSolicitar(Result.Error(SolicitarVerificacionError.DESCONOCIDO))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.NavegadorNoDisponible)
        assertTrue(viewModel.state.value.navegadorNoDisponible)

        viewModel.onAction(VerificationAction.Verificar)
        assertFalse(viewModel.state.value.navegadorNoDisponible)
    }

    // ── Actualizar / Reanudar ────────────────────────────────────────────────────────────

    @Test
    fun `Actualizar estado conserva la pantalla y pasa a aprobado cuando el backend lo informa`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE), estado(EstadoVerificacion.APROBADO))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Actualizar)

        assertEquals(VerificationState(fase = FaseVerificacion.APROBADO), viewModel.state.value)
    }

    @Test
    fun `mientras Actualizar espera, la pantalla sigue en curso con los botones deshabilitados`() = runTest {
        val respuesta = CompletableDeferred<Result<EstadoSolicitudVerificacion, EstadoVerificacionError>>()
        var llamadas = 0
        coEvery { repository.consultarEstado(null) } coAnswers {
            if (llamadas++ == 0) estado(EstadoVerificacion.PENDIENTE) else respuesta.await()
        }
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Actualizar)

        assertEquals(FaseVerificacion.PENDIENTE, viewModel.state.value.fase)
        assertTrue(viewModel.state.value.actualizando)
        respuesta.complete(estado(EstadoVerificacion.PENDIENTE))
        assertFalse(viewModel.state.value.actualizando)
    }

    @Test
    fun `un error de Actualizar se muestra en linea y conserva la verificacion en curso`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE), Result.Error(EstadoVerificacionError.SIN_INTERNET))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Actualizar)

        assertEquals(
            VerificationState(fase = FaseVerificacion.PENDIENTE, errorEstado = EstadoVerificacionError.SIN_INTERNET),
            viewModel.state.value,
        )
    }

    @Test
    fun `Reanudar refresca una verificacion en curso sin tapar el contenido`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE), estado(EstadoVerificacion.APROBADO))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Reanudar)

        assertEquals(VerificationState(fase = FaseVerificacion.APROBADO), viewModel.state.value)
        coVerify(exactly = 2) { repository.consultarEstado(null) }
    }

    @Test
    fun `Reanudar ignora un error de red y deja la pantalla como estaba`() = runTest {
        respondeEstado(estado(EstadoVerificacion.PENDIENTE), Result.Error(EstadoVerificacionError.SIN_INTERNET))
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Reanudar)

        assertEquals(VerificationState(fase = FaseVerificacion.PENDIENTE), viewModel.state.value)
    }

    @Test
    fun `Reanudar no consulta si la verificacion no esta en curso`() = runTest {
        for (fase in listOf(EstadoVerificacion.NO_SOLICITADO, EstadoVerificacion.APROBADO, EstadoVerificacion.RECHAZADO)) {
            respondeEstado(estado(fase))
            val viewModel = crearViewModel()

            viewModel.onAction(VerificationAction.Reanudar)
        }

        // Una consulta por cada ViewModel (la de init): Reanudar no sumo ninguna.
        coVerify(exactly = 3) { repository.consultarEstado(null) }
    }

    @Test
    fun `Reanudar durante la primera carga no duplica la consulta`() = runTest {
        val respuesta = CompletableDeferred<Result<EstadoSolicitudVerificacion, EstadoVerificacionError>>()
        coEvery { repository.consultarEstado(null) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(VerificationAction.Reanudar)

        coVerify(exactly = 1) { repository.consultarEstado(null) }
        respuesta.complete(estado(EstadoVerificacion.NO_SOLICITADO))
    }

    @Test
    fun `las acciones de navegacion no cambian el estado ni llaman al repositorio`() = runTest {
        respondeEstado(estado(EstadoVerificacion.NO_SOLICITADO))
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(VerificationAction.VolverAtras)
        viewModel.onAction(VerificationAction.IniciarSesion)

        assertEquals(antes, viewModel.state.value)
        coVerify(exactly = 0) { repository.solicitar() }
        coVerify(exactly = 1) { repository.consultarEstado(null) }
    }
}
