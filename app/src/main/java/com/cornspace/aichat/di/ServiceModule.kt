package com.cornspace.aichat.di

import android.content.Context
import com.cornspace.aichat.util.NotificationUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideNotificationUtils(
        @ApplicationContext context: Context
    ): NotificationUtils {
        return NotificationUtils(context)
    }
}
