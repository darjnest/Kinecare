@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Jueves 2026-10-01, 09:00 en Chile (UTC-3, horario de verano). */
private val AHORA_PANEL = Instant.parse("2026-10-01T12:00:00Z")

class ProfessionalPanelViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val reservaRepository = mockk<ReservaRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val servicioRepository = mockk<ServicioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val clock = object : Clock {
        override fun now() = AHORA_PANEL
    }
    private val uid = "prof-1"

    init {
        // Por defecto, sin reservas: los tests de Mercado Pago no dependen de la agenda.
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Success(emptyList())
        coEvery { usuarioRepository.obtenerPorId(any()) } answers {
            Result.Success(usuarioPanel(firstArg(), "Nombre ${firstArg<String>()}"))
        }
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Success(
            listOf(servicioPanel("srv-1", "Kinesiología deportiva")),
        )
    }

    private fun conReservas(vararg reservas: Reserva) {
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Success(reservas.toList())
    }

    private fun profesional(
        conectado: Boolean,
        rnpi: String = "",
        insignias: List<Insignia> = emptyList(),
        calificacion: Double = 0.0,
        totalResenas: Int = 0,
    ) = Profesional(
        usuario = Usuario(
            id = uid,
            nombre = "Ana Soto",
            rut = "1-9",
            email = "ana@kinecare.cl",
            correoContacto = null,
            telefono = null,
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            fechaRegistro = Instant.fromEpochMilliseconds(0),
        ),
        especialidades = emptyList(),
        rnpi = rnpi,
        servicios = emptyList(),
        insignias = insignias,
        disponibilidad = emptyList(),
        calificacionPromedio = calificacion,
        totalResenas = totalResenas,
        descripcion = "",
        estadoVerificacionGeneral = EstadoVerificacion.NO_SOLICITADO,
        mercadoPagoConectado = conectado,
    )

    private fun iniciarSesion(conSesion: Boolean = true) {
        if (conSesion) {
            val usuario = mockk<FirebaseUser>()
            every { usuario.uid } returns uid
            every { firebaseAuth.currentUser } returns usuario
        } else {
            every { firebaseAuth.currentUser } returns null
        }
    }

    private fun crearViewModel() = ProfessionalPanelViewModel(
        profesionalRepository,
        reservaRepository,
        usuarioRepository,
        servicioRepository,
        firebaseAuth,
        clock,
    )

    @Test
    fun `al abrir lee mercadoPagoConectado del profesional`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))

        assertEquals(true, crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `un profesional sin cuenta vinculada se muestra como no conectado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))

        assertEquals(false, crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `si no se puede leer no se afirma ni conectado ni no conectado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)

        assertNull(crearViewModel().state.value.mercadoPagoConectado)
    }

    @Test
    fun `sin sesion no lee Firestore`() = runTest {
        iniciarSesion(conSesion = false)

        assertNull(crearViewModel().state.value.mercadoPagoConectado)
        coVerify(exactly = 0) { profesionalRepository.obtenerPorId(any()) }
    }

    @Test
    fun `Actualizar al volver de vincular refleja el nuevo estado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        val viewModel = crearViewModel()

        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))
        viewModel.onAction(ProfessionalPanelAction.Actualizar)

        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `un fallo al releer conserva el ultimo estado conocido`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))
        val viewModel = crearViewModel()

        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Error(ProfesionalError.SIN_INTERNET)
        viewModel.onAction(ProfessionalPanelAction.Actualizar)

        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `una lectura en curso no se duplica con Actualizar`() = runTest {
        iniciarSesion()
        val respuesta = CompletableDeferred<Result<Profesional, ProfesionalError>>()
        coEvery { profesionalRepository.obtenerPorId(uid) } coAnswers { respuesta.await() }
        val viewModel = crearViewModel()

        viewModel.onAction(ProfessionalPanelAction.Actualizar)

        coVerify(exactly = 1) { profesionalRepository.obtenerPorId(uid) }
        respuesta.complete(Result.Success(profesional(conectado = true)))
        assertEquals(true, viewModel.state.value.mercadoPagoConectado)
    }

    @Test
    fun `ConectarMercadoPago es navegacion y no cambia el estado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        val viewModel = crearViewModel()
        val antes = viewModel.state.value

        viewModel.onAction(ProfessionalPanelAction.ConectarMercadoPago)

        assertEquals(antes, viewModel.state.value)
    }

    // --- Dashboard conectado a reservas y perfil ---

    private val credencialesAprobadas = Insignia(
        tipo = TipoInsignia.CREDENCIALES,
        estado = EstadoVerificacion.APROBADO,
        detalle = null,
        fechaActualizacion = AHORA_PANEL,
    )

    @Test
    fun `saluda con el primer nombre y muestra calificacion y resenas del perfil`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(conectado = false, calificacion = 4.8, totalResenas = 12))

        val state = crearViewModel().state.value

        assertEquals("Ana", state.nombreProfesional)
        assertEquals(4.8, state.resumenHoy?.calificacion)
        assertEquals(12, state.resumenHoy?.totalResenas)
        assertEquals("Actualizado 09:00", state.resumenHoy?.actualizadoHaceTexto)
    }

    @Test
    fun `el banner de verificacion solo aparece con credenciales aprobadas y RNPI`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(
            profesional(conectado = false, rnpi = "123456", insignias = listOf(credencialesAprobadas)),
        )
        assertEquals(
            VerificacionPanel(numeroSis = "123456", credencialesAlDia = true),
            crearViewModel().state.value.verificacion,
        )

        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(conectado = false, rnpi = "123456"))
        assertNull(crearViewModel().state.value.verificacion)

        coEvery { profesionalRepository.obtenerPorId(uid) } returns
            Result.Success(profesional(conectado = false, rnpi = " ", insignias = listOf(credencialesAprobadas)))
        assertNull(crearViewModel().state.value.verificacion)
    }

    @Test
    fun `cuenta las proximas confirmadas y las de hoy, sin contar solicitudes ni vencidas`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(
            reservaPanel("hoy-tarde", AHORA_PANEL + 5.hours, EstadoReserva.CONFIRMADA),
            reservaPanel("hoy-en-curso", AHORA_PANEL - 10.minutes, EstadoReserva.EN_CURSO),
            reservaPanel("manana", AHORA_PANEL + 1.days, EstadoReserva.CONFIRMADA),
            reservaPanel("solicitada", AHORA_PANEL + 2.hours, EstadoReserva.SOLICITADA),
            reservaPanel("rechazada", AHORA_PANEL + 3.hours, EstadoReserva.RECHAZADA),
        )

        val resumen = crearViewModel().state.value.resumenHoy

        assertEquals(2, resumen?.proximasCitas)
        assertEquals(2, resumen?.citasHoyEnAgenda)
    }

    @Test
    fun `por liquidar suma el honorario neto de lo completado y cobrado`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(
            reservaPanel("c1", AHORA_PANEL - 2.days, EstadoReserva.COMPLETADA, monto = 30_000, estadoPago = EstadoPago.AUTORIZADO),
            reservaPanel("c2", AHORA_PANEL - 1.days, EstadoReserva.COMPLETADA, monto = 20_000, estadoPago = EstadoPago.AUTORIZADO),
            reservaPanel("sin-pago", AHORA_PANEL - 1.days, EstadoReserva.COMPLETADA, monto = 50_000, estadoPago = EstadoPago.PENDIENTE),
            reservaPanel("confirmada", AHORA_PANEL + 1.days, EstadoReserva.CONFIRMADA, monto = 40_000, estadoPago = EstadoPago.AUTORIZADO),
        )

        // 27.000 + 18.000 (10 % de comision).
        assertEquals(45_000L, crearViewModel().state.value.resumenHoy?.porLiquidar)
    }

    @Test
    fun `la proxima cita es la confirmada mas cercana con nombres reales`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(
            reservaPanel("lejana", AHORA_PANEL + 2.days, EstadoReserva.CONFIRMADA),
            reservaPanel(
                "cercana",
                AHORA_PANEL + 45.minutes,
                EstadoReserva.CONFIRMADA,
                clienteId = "cli-9",
                modalidad = ModalidadServicio.DOMICILIO,
                direccion = Direccion("Av. Pocuro", "2150", "Providencia", "Santiago", null, null, "Depto 502"),
            ),
            reservaPanel("solicitada-mas-cerca", AHORA_PANEL + 10.minutes, EstadoReserva.SOLICITADA),
        )

        val cita = crearViewModel().state.value.proximaCita

        assertEquals(
            ProximaCita(
                pacienteNombre = "Nombre cli-9",
                servicio = "Kinesiología deportiva",
                tiempoRestanteTexto = "En 45 min",
                modalidad = "A Domicilio",
                horaTexto = "Hoy, 09:45 hrs",
                comuna = "Providencia",
                direccion = "Av. Pocuro 2150, Providencia",
                complemento = "Depto 502",
                direccionRuta = "Av. Pocuro 2150, Providencia, Santiago",
            ),
            cita,
        )
    }

    @Test
    fun `una cita en consulta no ofrece ruta ni comuna`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.CONFIRMADA))

        val cita = crearViewModel().state.value.proximaCita

        assertEquals("En 3 h", cita?.tiempoRestanteTexto)
        assertEquals("En Consulta", cita?.modalidad)
        assertNull(cita?.direccionRuta)
        assertNull(cita?.comuna)
        assertNull(cita?.complemento)
    }

    @Test
    fun `si fallan los nombres la proxima cita usa textos genericos`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        coEvery { usuarioRepository.obtenerPorId(any()) } returns Result.Error(UsuarioError.SIN_INTERNET)
        coEvery { servicioRepository.obtenerPorProfesional(uid) } returns Result.Error(ServicioError.SIN_INTERNET)
        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.CONFIRMADA))

        val cita = crearViewModel().state.value.proximaCita

        assertEquals("Paciente", cita?.pacienteNombre)
        assertEquals("Sesión", cita?.servicio)
    }

    @Test
    fun `sin citas confirmadas no hay proxima cita y no se piden nombres`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.SOLICITADA))

        assertNull(crearViewModel().state.value.proximaCita)
        coVerify(exactly = 0) { usuarioRepository.obtenerPorId(any()) }
    }

    @Test
    fun `el acceso a Solicitudes muestra cuantas esperan respuesta`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(
            reservaPanel("a", AHORA_PANEL + 3.hours, EstadoReserva.SOLICITADA),
            reservaPanel("b", AHORA_PANEL + 1.days, EstadoReserva.SOLICITADA),
            reservaPanel("vencida", AHORA_PANEL - 1.hours, EstadoReserva.SOLICITADA),
            reservaPanel("confirmada", AHORA_PANEL + 1.days, EstadoReserva.CONFIRMADA),
        )

        val accesos = crearViewModel().state.value.accesosGestion

        assertEquals(2, accesos.single { it.id == AccesoGestionId.SOLICITUDES }.badgeNumero)
        assertTrue(accesos.filter { it.id != AccesoGestionId.SOLICITUDES }.all { it.badgeNumero == null })
    }

    @Test
    fun `sin solicitudes pendientes el acceso no muestra contador`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))

        val accesos = crearViewModel().state.value.accesosGestion

        assertNull(accesos.single { it.id == AccesoGestionId.SOLICITUDES }.badgeNumero)
        assertEquals(6, accesos.size)
    }

    @Test
    fun `si fallan las reservas se oculta el resumen pero el perfil sigue visible`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))
        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Error(ReservaError.SIN_INTERNET)

        val state = crearViewModel().state.value

        assertNull(state.resumenHoy)
        assertNull(state.proximaCita)
        assertEquals("Ana", state.nombreProfesional)
        assertEquals(true, state.mercadoPagoConectado)
        assertFalse(state.cargando)
    }

    @Test
    fun `un fallo al releer las reservas conserva la agenda anterior`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.CONFIRMADA))
        val viewModel = crearViewModel()

        coEvery { reservaRepository.obtenerPorProfesional(uid) } returns Result.Error(ReservaError.SIN_INTERNET)
        viewModel.onAction(ProfessionalPanelAction.Actualizar)

        assertEquals(1, viewModel.state.value.resumenHoy?.proximasCitas)
        assertEquals("En 3 h", viewModel.state.value.proximaCita?.tiempoRestanteTexto)
    }

    @Test
    fun `al actualizar aparece una solicitud aceptada desde la otra pantalla`() = runTest {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))
        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.SOLICITADA))
        val viewModel = crearViewModel()
        assertNull(viewModel.state.value.proximaCita)

        conReservas(reservaPanel("r1", AHORA_PANEL + 3.hours, EstadoReserva.CONFIRMADA))
        viewModel.onAction(ProfessionalPanelAction.Actualizar)

        assertEquals("En 3 h", viewModel.state.value.proximaCita?.tiempoRestanteTexto)
        assertNull(viewModel.state.value.accesosGestion.single { it.id == AccesoGestionId.SOLICITUDES }.badgeNumero)
    }

    @Test
    fun `sin sesion no lee reservas`() = runTest {
        iniciarSesion(conSesion = false)

        crearViewModel()

        coVerify(exactly = 0) { reservaRepository.obtenerPorProfesional(any()) }
    }
}

private fun reservaPanel(
    id: String,
    fechaHora: Instant,
    estado: EstadoReserva,
    clienteId: String = "cli-1",
    modalidad: ModalidadServicio = ModalidadServicio.CONSULTA,
    direccion: Direccion? = null,
    monto: Long = 30_000,
    estadoPago: EstadoPago = EstadoPago.PENDIENTE,
) = Reserva(
    id = id,
    clienteId = clienteId,
    profesionalId = "prof-1",
    servicioId = "srv-1",
    modalidad = modalidad,
    fechaHora = fechaHora,
    direccion = direccion,
    estado = estado,
    pago = Pago(
        id = "",
        reservaId = id,
        monto = monto,
        metodo = MetodoPago(TipoMetodoPago.TARJETA, null, ""),
        estado = estadoPago,
        idTransaccionPasarela = null,
    ),
    comisionPorcentaje = 0.1,
)

private fun usuarioPanel(id: String, nombre: String) = Usuario(
    id = id,
    nombre = nombre,
    rut = "11.111.111-1",
    email = "$id@test.cl",
    correoContacto = null,
    telefono = null,
    rol = RolUsuario.CLIENTE,
    fotoUrl = null,
    fechaRegistro = AHORA_PANEL,
)

private fun servicioPanel(id: String, nombre: String) = Servicio(
    id = id,
    nombre = nombre,
    descripcion = "",
    modalidad = ModalidadServicio.CONSULTA,
    duracionMinutos = 60,
    precio = 30_000,
)
