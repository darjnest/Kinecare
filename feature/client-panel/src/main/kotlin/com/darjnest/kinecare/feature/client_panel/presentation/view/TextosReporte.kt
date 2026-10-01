package com.darjnest.kinecare.feature.client_panel.presentation.view

import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte

internal fun MotivoReporte.aTexto(): String = when (this) {
    MotivoReporte.PROFESIONAL_NO_LLEGO -> "El profesional no llegó"
    MotivoReporte.ATRASO -> "Atraso importante o sesión acortada"
    MotivoReporte.COBRO_INCORRECTO -> "Problema con el cobro"
    MotivoReporte.CONDUCTA_INAPROPIADA -> "Trato o conducta inapropiada"
    MotivoReporte.CALIDAD_ATENCION -> "La atención no fue la esperada"
    MotivoReporte.OTRO -> "Otro"
}

internal fun EstadoReporte.aTexto(): String = when (this) {
    EstadoReporte.ABIERTO -> "Reporte recibido"
    EstadoReporte.EN_REVISION -> "Reporte en revisión"
    EstadoReporte.RESUELTO -> "Reporte resuelto"
}

internal fun EstadoReporte.aTono(): BadgeTono = when (this) {
    EstadoReporte.ABIERTO -> BadgeTono.NEUTRO
    EstadoReporte.EN_REVISION -> BadgeTono.INFO
    EstadoReporte.RESUELTO -> BadgeTono.EXITO
}
