package com.example.animewiki.di

import android.content.Context
import androidx.room.Room
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.dao.RemoteKeyDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "animewiki.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideAnimeDao(db: AppDatabase): AnimeDao = db.animeDao()

    @Provides
    fun provideRemoteKeyDao(db: AppDatabase): RemoteKeyDao = db.remoteKeyDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
}
