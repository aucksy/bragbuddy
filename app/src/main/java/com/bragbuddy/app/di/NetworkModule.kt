package com.bragbuddy.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // The chat completions here are NON-streaming: the server sends nothing at all until the
            // whole reply is generated, then delivers it in one go. The read timeout must therefore
            // cover the model's full generation time — left unset it defaults to 10s, which hung up
            // mid-generation on every long reply (a Detailed summary) and surfaced as a bogus
            // "check your connection" failure while short replies (One page) squeaked under.
            .readTimeout(90, TimeUnit.SECONDS)
            // Uploads (voice clips, scanned images up to 4 MB) on a slow link; default is also 10s.
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
}
