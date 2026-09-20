package com.darjnest.kinecare.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.darjnest.kinecare.core.database.dao.BorradorReservaDao
import com.darjnest.kinecare.core.database.dao.BusquedaCacheDao
import com.darjnest.kinecare.core.database.dao.FavoritoDao
import com.darjnest.kinecare.core.database.entity.BorradorReservaEntity
import com.darjnest.kinecare.core.database.entity.BusquedaCacheEntity
import com.darjnest.kinecare.core.database.entity.FavoritoEntity

/**
 * Solo cache offline (busqueda, favoritos, borrador de reserva). Firestore
 * es la fuente de verdad - ver docs/ARCHITECTURE.md#persistencia-local.
 */
@Database(
    entities = [
        FavoritoEntity::class,
        BusquedaCacheEntity::class,
        BorradorReservaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KineCareDatabase : RoomDatabase() {
    abstract fun favoritoDao(): FavoritoDao
    abstract fun busquedaCacheDao(): BusquedaCacheDao
    abstract fun borradorReservaDao(): BorradorReservaDao

    companion object {
        const val NOMBRE_BD = "kinecare.db"
    }
}
