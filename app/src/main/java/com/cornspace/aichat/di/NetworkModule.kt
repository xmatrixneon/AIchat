package com.cornspace.aichat.di

import com.cornspace.aichat.data.remote.WebSocketClient
import com.cornspace.aichat.util.NotificationUtils
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
    fun provideWebSocketClient(gson: Gson): WebSocketClient {
        return WebSocketClient(gson)
    }
}
