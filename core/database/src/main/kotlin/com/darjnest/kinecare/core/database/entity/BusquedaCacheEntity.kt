package com.darjnest.kinecare.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "busqueda_cache")
data class BusquedaCacheEntity(
    @PrimaryKey val id: String,
    val resultadosJson: String,
    val timestamp: Long,
)
