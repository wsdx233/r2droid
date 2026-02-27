package top.wsdx233.r2droid.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.wsdx233.r2droid.core.data.db.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "r2droid.db")
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideStringDao(db: AppDatabase): StringDao = db.stringDao()

    @Provides
    fun provideSectionDao(db: AppDatabase): SectionDao = db.sectionDao()

    @Provides
    fun provideSymbolDao(db: AppDatabase): SymbolDao = db.symbolDao()

    @Provides
    fun provideImportDao(db: AppDatabase): ImportDao = db.importDao()

    @Provides
    fun provideRelocationDao(db: AppDatabase): RelocationDao = db.relocationDao()

    @Provides
    fun provideFunctionDao(db: AppDatabase): FunctionDao = db.functionDao()
}
