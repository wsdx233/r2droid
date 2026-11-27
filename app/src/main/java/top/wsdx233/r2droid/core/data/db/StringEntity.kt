package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "strings",
    indices = [Index(value = ["string"]), Index(value = ["vAddr"])]
)
data class StringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vAddr: Long,
    val string: String,
    val type: String,
    val section: String
)

data class StringOverviewRow(
    val vAddr: Long,
    val length: Int
)

@Dao
interface StringDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strings: List<StringEntity>)

    @Query("SELECT * FROM strings ORDER BY (vAddr < 0) ASC, vAddr ASC, id ASC")
    fun getPagingSource(): PagingSource<Int, StringEntity>

    @Query("SELECT * FROM strings WHERE string LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY (vAddr < 0) ASC, vAddr ASC, id ASC")
    fun searchStrings(query: String): PagingSource<Int, StringEntity>

    @Query("SELECT vAddr, LENGTH(string) AS length FROM strings ORDER BY (vAddr < 0) ASC, vAddr ASC, id ASC")
    suspend fun getOverviewRows(): List<StringOverviewRow>

    @Query("DELETE FROM strings")
    suspend fun clearAll()
}
