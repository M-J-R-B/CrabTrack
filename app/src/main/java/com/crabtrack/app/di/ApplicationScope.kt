package com.crabtrack.app.di

import javax.inject.Qualifier

/**
 * Qualifier annotation for application-scoped CoroutineScope.
 * Used for long-running operations that should survive configuration changes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
