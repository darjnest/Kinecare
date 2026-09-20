package com.darjnest.kinecare.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darjnest.kinecare.core.database.entity.FavoritoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritoDao {
    @Query("SELECT * FROM favoritos WHERE usuarioId = :usuarioId")
    fun observarFavoritos(usuarioId: String): Flow<List<FavoritoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun agregar(favorito: FavoritoEntity)

    @Delete
    suspend fun quitar(favorito: FavoritoEntity)
}
