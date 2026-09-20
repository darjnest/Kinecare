package com.darjnest.kinecare.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darjnest.kinecare.core.database.entity.BorradorReservaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BorradorReservaDao {
    @Query("SELECT * FROM borrador_reserva WHERE usuarioId = :usuarioId")
    fun observar(usuarioId: String): Flow<BorradorReservaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(borrador: BorradorReservaEntity)

    @Query("DELETE FROM borrador_reserva WHERE usuarioId = :usuarioId")
    suspend fun limpiar(usuarioId: String)
}
