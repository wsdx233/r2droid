package top.wsdx233.r2droid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        StringEntity::class,
        SectionEntity::class,
        SymbolEntity::class,
        ImportEntity::class,
        RelocationEntity::class,
        FunctionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stringDao(): StringDao
    abstract fun sectionDao(): SectionDao
    abstract fun symbolDao(): SymbolDao
    abstract fun importDao(): ImportDao
    abstract fun relocationDao(): RelocationDao
    abstract fun functionDao(): FunctionDao
}
