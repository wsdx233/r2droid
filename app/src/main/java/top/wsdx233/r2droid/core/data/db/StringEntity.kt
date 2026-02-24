package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "strings",
    indices = [Index(value = ["string"])]
)
data class StringEntity(
    @PrimaryKey val vAddr: Long,
    val string: String,
    val type: String,
    val section: String
)

@Dao
interface StringDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strings: List<StringEntity>)

    @Query("SELECT * FROM strings ORDER BY vAddr ASC")
    fun getPagingSource(): PagingSource<Int, StringEntity>

    @Query("SELECT * FROM strings WHERE string LIKE '%' || :query || '%' ORDER BY vAddr ASC")
    fun searchStrings(query: String): PagingSource<Int, StringEntity>

    @Query("DELETE FROM strings")
    suspend fun clearAll()
}
