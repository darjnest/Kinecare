package com.darjnest.kinecare.feature.payment.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FaseConexion {
    /** Comprobando en Firestore que la cuenta quedo vinculada. */
    VERIFICANDO,
    CONECTADA,

    /** Llego el deep link `conectado` pero `mercadoPagoConectado` no es `true`: no se da por conectada. */
    NO_CONFIRMADA,
    SIN_INTERNET,
    SIN_SESION,
    DESCONOCIDO,
}

data class MercadoPagoConectadoState(
    val fase: FaseConexion = FaseConexion.VERIFICANDO,
)

sealed interface MercadoPagoConectadoAction {
    /** Vuelve a leer `mercadoPagoConectado`. */
    data object Reintentar : MercadoPagoConectadoAction

    /** Navegacion: las resuelve el `Root` contra el NavGraph, no el ViewModel. */
    data object IrAlPanel : MercadoPagoConectadoAction
    data object VolverAConectar : MercadoPagoConectadoAction
    data object IniciarSesion : MercadoPagoConectadoAction
}

/**
 * El deep link `kinecare://mp/conectado` lo puede disparar cualquier app o
 * pagina web, asi que llegar aqui no prueba nada: el exito se confirma leyendo
 * `profesionales/{uid}.mercadoPagoConectado`, que solo escribe el backend.
 */
@HiltViewModel
class MercadoPagoConectadoViewModel @Inject constructor(
    private val profesionalRepository: ProfesionalRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _state = MutableStateFlow(MercadoPagoConectadoState())
    val state: StateFlow<MercadoPagoConectadoState> = _state.asStateFlow()

    init {
        verificar()
    }

    fun onAction(action: MercadoPagoConectadoAction) {
        when (action) {
            MercadoPagoConectadoAction.Reintentar -> verificar()
            MercadoPagoConectadoAction.IrAlPanel,
            MercadoPagoConectadoAction.VolverAConectar,
            MercadoPagoConectadoAction.IniciarSesion,
            -> Unit
        }
    }

    private fun verificar() {
        _state.value = MercadoPagoConectadoState(FaseConexion.VERIFICANDO)
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _state.value = MercadoPagoConectadoState(FaseConexion.SIN_SESION)
            return
        }
        viewModelScope.launch {
            val fase = when (val resultado = profesionalRepository.obtenerPorId(uid)) {
                is Result.Success ->
                    if (resultado.data.mercadoPagoConectado) FaseConexion.CONECTADA else FaseConexion.NO_CONFIRMADA
                is Result.Error -> when (resultado.error) {
                    ProfesionalError.SIN_INTERNET -> FaseConexion.SIN_INTERNET
                    ProfesionalError.NO_ENCONTRADO,
                    ProfesionalError.DESCONOCIDO,
                    -> FaseConexion.DESCONOCIDO
                }
            }
            _state.value = MercadoPagoConectadoState(fase)
        }
    }
}
