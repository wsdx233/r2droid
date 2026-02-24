package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "functions",
    indices = [Index(value = ["name"])]
)
data class FunctionEntity(
    @PrimaryKey val addr: Long,
    val name: String,
    val size: Long,
    val nbbs: Int,
    val signature: String
)

@Dao
interface FunctionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FunctionEntity>)

    @Query("SELECT * FROM functions ORDER BY addr ASC")
    fun getPagingSource(): PagingSource<Int, FunctionEntity>

    @Query("SELECT * FROM functions WHERE name LIKE '%' || :query || '%' ORDER BY addr ASC")
    fun search(query: String): PagingSource<Int, FunctionEntity>

    @Query("DELETE FROM functions")
    suspend fun clearAll()
}
