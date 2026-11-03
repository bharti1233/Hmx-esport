package com.hmx.arena.di

import android.content.Context
import androidx.room.Room
import com.hmx.arena.data.local.HmxDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): HmxDatabase =
        Room.databaseBuilder(context, HmxDatabase::class.java, "hmx-arena.db")
            .fallbackToDestructiveMigration()
            .build()
}
