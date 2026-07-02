package com.bragbuddy.app.di

import com.bragbuddy.app.data.usage.DataStoreUsageMeter
import com.bragbuddy.app.data.usage.UsageMeter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the usage-metering hook (PRD P0-12: meter summary generations + cloud minutes). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UsageModule {

    @Binds
    @Singleton
    abstract fun bindUsageMeter(impl: DataStoreUsageMeter): UsageMeter
}
