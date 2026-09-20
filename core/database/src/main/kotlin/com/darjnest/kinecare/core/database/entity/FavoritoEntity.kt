package com.darjnest.kinecare.core.database.entity

import androidx.room.Entity

@Entity(tableName = "favoritos", primaryKeys = ["usuarioId", "profesionalId"])
data class FavoritoEntity(
    val usuarioId: String,
    val profesionalId: String,
)
