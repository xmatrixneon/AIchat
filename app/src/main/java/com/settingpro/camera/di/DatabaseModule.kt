package com.settingpro.camera.di

import android.content.Context
import com.settingpro.camera.data.local.SettingsDataStore
import com.google.gson.Gson
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
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        gson: Gson
    ): SettingsDataStore {
        return SettingsDataStore(context, gson)
    }
}
