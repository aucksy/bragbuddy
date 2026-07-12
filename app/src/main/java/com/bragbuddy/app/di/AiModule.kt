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
 * refine + summary). As of Phase M1 its transport — direct-Groq with the user's own key, or
 * BragBuddy's managed relay — is chosen per call by [com.bragbuddy.app.data.ai.AiEndpoint], so the
 * managed-proxy switch is a config change, not a provider swap. Swapping the provider itself (a
 * different vendor) is still a one-line change here. The old no-network `StubAiProvider` stays in the
 * tree as a fail-safe reference implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: GroqAiProvider): AiProvider
}
