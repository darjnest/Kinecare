package com.darjnest.kinecare.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "borrador_reserva")
data class BorradorReservaEntity(
    @PrimaryKey val usuarioId: String,
    val profesionalId: String,
    val servicioId: String?,
    val modalidad: String?,
    val fechaHoraEpoch: Long?,
    val direccionJson: String?,
    val pasoActual: Int,
)
