package com.bragbuddy.app.di

import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.GroqAiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the active [AiProvider]. As of Phase 2 that is the [GroqAiProvider] (categorizer + framework
 * refine + summary, reusing the on-device Groq key). Swapping to OpenRouter/a proxy-backed provider
 * for public release is a one-line change here — nothing else in the app depends on the concrete
 * type. The old no-network `StubAiProvider` stays in the tree as a fail-safe reference implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: GroqAiProvider): AiProvider
}
