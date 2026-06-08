package com.github.livingwithhippos.unchained.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the shared preferences injected with Dagger Hilt */
@InstallIn(SingletonComponent::class)
@Module
object SharedPreferencesModule {

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext appContext: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
    }

    /**
     * Dedicated preferences file for secrets. Kept separate from the default (backed-up) settings
     * file so the backup rules can exclude it by name ([SECURE_PREFS_FILE].xml).
     */
    @Provides
    @Singleton
    @SecurePreferences
    fun provideSecurePreferences(@ApplicationContext appContext: Context): SharedPreferences {
        return appContext.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
    }

    const val SECURE_PREFS_FILE = "credentials_secure"
}
