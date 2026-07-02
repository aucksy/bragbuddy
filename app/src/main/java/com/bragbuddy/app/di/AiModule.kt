package com.bragbuddy.app.di

import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.OpenRouterAiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the active [AiProvider]. As of Phase 2 that is the [OpenRouterAiProvider] (categorizer +
 * framework refine + summary, keyed on-device). Swapping to a proxy-backed provider for public
 * release is a one-line change here — nothing else in the app depends on the concrete type. The
 * old no-network `StubAiProvider` stays in the tree as a fail-safe reference implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: OpenRouterAiProvider): AiProvider
}
