package com.bragbuddy.app.di

import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.StubAiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the active [AiProvider]. Today that is the no-network [StubAiProvider]; swapping to the
 * OpenRouter provider (Phase 2) or a proxy-backed one later is a one-line change here — nothing
 * else in the app depends on the concrete type.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: StubAiProvider): AiProvider
}
