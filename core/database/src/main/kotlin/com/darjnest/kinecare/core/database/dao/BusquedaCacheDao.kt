package com.darjnest.kinecare.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darjnest.kinecare.core.database.entity.BusquedaCacheEntity

@Dao
interface BusquedaCacheDao {
    @Query("SELECT * FROM busqueda_cache WHERE id = :id")
    suspend fun obtener(id: String): BusquedaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(cache: BusquedaCacheEntity)

    @Query("DELETE FROM busqueda_cache WHERE timestamp < :antesDe")
    suspend fun limpiarExpirados(antesDe: Long)
}
