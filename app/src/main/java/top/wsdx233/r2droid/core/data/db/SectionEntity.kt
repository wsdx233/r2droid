package top.wsdx233.r2droid.core.data.db

import androidx.paging.PagingSource
import androidx.room.*

@Entity(
    tableName = "sections",
    indices = [Index(value = ["name"])]
)
data class SectionEntity(
    @PrimaryKey val vAddr: Long,
    val name: String,
    val size: Long,
    val vSize: Long,
    val perm: String,
    val pAddr: Long
)

@Dao
interface SectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SectionEntity>)

    @Query("SELECT * FROM sections ORDER BY vAddr ASC")
    fun getPagingSource(): PagingSource<Int, SectionEntity>

    @Query("SELECT * FROM sections WHERE name LIKE '%' || :query || '%' ORDER BY vAddr ASC")
    fun search(query: String): PagingSource<Int, SectionEntity>

    @Query("DELETE FROM sections")
    suspend fun clearAll()
}
