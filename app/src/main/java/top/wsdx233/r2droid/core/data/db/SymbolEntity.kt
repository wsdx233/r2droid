package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "symbols",
    indices = [Index(value = ["name"]), Index(value = ["realname"])]
)
data class SymbolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val vAddr: Long,
    val pAddr: Long,
    val isImported: Boolean,
    val realname: String?
)

@Dao
interface SymbolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SymbolEntity>)

    @Query("SELECT * FROM symbols ORDER BY id ASC")
    fun getPagingSource(): PagingSource<Int, SymbolEntity>

    @Query("SELECT * FROM symbols WHERE name LIKE '%' || :query || '%' OR realname LIKE '%' || :query || '%' ORDER BY id ASC")
    fun search(query: String): PagingSource<Int, SymbolEntity>

    @Query("DELETE FROM symbols")
    suspend fun clearAll()
}
