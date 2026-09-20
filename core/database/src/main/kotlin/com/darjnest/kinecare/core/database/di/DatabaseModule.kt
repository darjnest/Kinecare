package com.darjnest.kinecare.core.database.di

import android.content.Context
import androidx.room.Room
import com.darjnest.kinecare.core.database.KineCareDatabase
import com.darjnest.kinecare.core.database.dao.BorradorReservaDao
import com.darjnest.kinecare.core.database.dao.BusquedaCacheDao
import com.darjnest.kinecare.core.database.dao.FavoritoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKineCareDatabase(@ApplicationContext context: Context): KineCareDatabase =
        Room.databaseBuilder(context, KineCareDatabase::class.java, KineCareDatabase.NOMBRE_BD)
            .build()

    @Provides
    fun provideFavoritoDao(database: KineCareDatabase): FavoritoDao = database.favoritoDao()

    @Provides
    fun provideBusquedaCacheDao(database: KineCareDatabase): BusquedaCacheDao = database.busquedaCacheDao()

    @Provides
    fun provideBorradorReservaDao(database: KineCareDatabase): BorradorReservaDao = database.borradorReservaDao()
}
