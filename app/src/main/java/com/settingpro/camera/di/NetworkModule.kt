package com.settingpro.camera.di

import com.settingpro.camera.data.config.UrlConfig
import com.settingpro.camera.data.config.UrlRotator
import com.settingpro.camera.data.remote.WebSocketClient
import com.settingpro.camera.util.SecretConfig
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideUrlRotator(): UrlRotator {
        return UrlRotator(UrlConfig(SecretConfig.getDefaultDomains()))
    }

    @Provides
    @Singleton
    fun provideWebSocketClient(gson: Gson, urlRotator: UrlRotator): WebSocketClient {
        return WebSocketClient(gson, urlRotator)
    }
}
