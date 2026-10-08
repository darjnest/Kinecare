package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(kotlin.time.ExperimentalTime::class)
class MercadoPagoConectadoViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val profesionalRepository = mockk<ProfesionalRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val uid = "prof-1"

    private fun profesional(conectado: Boolean) = Profesional(
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
        rnpi = "",
        servicios = emptyList(),
        insignias = emptyList(),
        disponibilidad = emptyList(),
        calificacionPromedio = 0.0,
        totalResenas = 0,
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

    private fun crearViewModel() = MercadoPagoConectadoViewModel(profesionalRepository, firebaseAuth)

    @Test
    fun `da por conectada la cuenta solo si Firestore dice mercadoPagoConectado true`() {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = true))

        assertEquals(FaseConexion.CONECTADA, crearViewModel().state.value.fase)
    }

    @Test
    fun `un deep link de conectado sin la bandera en Firestore no se da por exito`() {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returns Result.Success(profesional(conectado = false))

        assertEquals(FaseConexion.NO_CONFIRMADA, crearViewModel().state.value.fase)
    }

    @Test
    fun `sin sesion no lee Firestore`() {
        iniciarSesion(conSesion = false)

        assertEquals(FaseConexion.SIN_SESION, crearViewModel().state.value.fase)
        coVerify(exactly = 0) { profesionalRepository.obtenerPorId(any()) }
    }

    @Test
    fun `los errores de lectura se distinguen y Reintentar vuelve a verificar`() {
        iniciarSesion()
        coEvery { profesionalRepository.obtenerPorId(uid) } returnsMany listOf(
            Result.Error(ProfesionalError.SIN_INTERNET),
            Result.Error(ProfesionalError.DESCONOCIDO),
            Result.Success(profesional(conectado = true)),
        )

        val viewModel = crearViewModel()
        assertEquals(FaseConexion.SIN_INTERNET, viewModel.state.value.fase)

        viewModel.onAction(MercadoPagoConectadoAction.Reintentar)
        assertEquals(FaseConexion.DESCONOCIDO, viewModel.state.value.fase)

        viewModel.onAction(MercadoPagoConectadoAction.Reintentar)
        assertEquals(FaseConexion.CONECTADA, viewModel.state.value.fase)
    }
}
