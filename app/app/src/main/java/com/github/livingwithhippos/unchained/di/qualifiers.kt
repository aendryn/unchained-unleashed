package com.github.livingwithhippos.unchained.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApiRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TorBoxRetrofit

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TorrentNotification

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TorrentSummaryNotification

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ClassicClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DOHClient

/**
 * SharedPreferences file holding secrets (e.g. the TorBox API key) that must be kept out of cloud
 * backup and device transfer. Backed by its own file so the backup rules can exclude it by name.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SecurePreferences
