package com.bragbuddy.app.di

import android.content.Context
import androidx.room.Room
import com.bragbuddy.app.data.local.BragBuddyDatabase
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BragBuddyDatabase =
        Room.databaseBuilder(context, BragBuddyDatabase::class.java, BragBuddyDatabase.NAME)
            .addMigrations(
                BragBuddyDatabase.MIGRATION_1_2,
                BragBuddyDatabase.MIGRATION_2_3,
                BragBuddyDatabase.MIGRATION_3_4,
                BragBuddyDatabase.MIGRATION_4_5,
                BragBuddyDatabase.MIGRATION_5_6,
                BragBuddyDatabase.MIGRATION_6_7,
            )
            .build()

    @Provides
    fun provideEntryDao(db: BragBuddyDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideProjectDao(db: BragBuddyDatabase): ProjectDao = db.projectDao()
}
