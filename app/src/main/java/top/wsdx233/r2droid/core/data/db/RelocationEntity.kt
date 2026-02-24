package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "relocations",
    indices = [Index(value = ["name"])]
)
data class RelocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val vAddr: Long,
    val pAddr: Long
)

@Dao
interface RelocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RelocationEntity>)

    @Query("SELECT * FROM relocations ORDER BY id ASC")
    fun getPagingSource(): PagingSource<Int, RelocationEntity>

    @Query("SELECT * FROM relocations WHERE name LIKE '%' || :query || '%' ORDER BY id ASC")
    fun search(query: String): PagingSource<Int, RelocationEntity>

    @Query("DELETE FROM relocations")
    suspend fun clearAll()
}
