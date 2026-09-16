package com.law.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LawDao {

    // ===== 缓存查询 =====

    @Query("SELECT * FROM laws WHERE title LIKE '%' || :keyword || '%' OR summary LIKE '%' || :keyword || '%' ORDER BY publishDate DESC")
    fun searchCachedLaws(keyword: String): Flow<List<LawEntity>>

    @Query("SELECT * FROM laws WHERE id = :id")
    suspend fun getLawById(id: String): LawEntity?

    @Query("SELECT * FROM laws WHERE cachedAt > :since ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getRecentCached(since: Long, limit: Int = 50): List<LawEntity>

    // ===== 收藏 =====

    @Query("SELECT * FROM laws WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteLaws(): Flow<List<LawEntity>>

    @Query("UPDATE laws SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT isFavorite FROM laws WHERE id = :id")
    suspend fun isFavorite(id: String): Boolean?

    // ===== 阅读历史 =====

    @Query("SELECT * FROM laws WHERE lastReadTime > 0 ORDER BY lastReadTime DESC LIMIT :limit")
    fun getRecentLaws(limit: Int = 20): Flow<List<LawEntity>>

    @Query("UPDATE laws SET lastReadTime = :timestamp WHERE id = :id")
    suspend fun updateLastReadTime(id: String, timestamp: Long)

    @Query("UPDATE laws SET lastReadTime = 0 WHERE lastReadTime > 0")
    suspend fun clearReadingHistory()

    // ===== 写入 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(law: LawEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(laws: List<LawEntity>)

    @Update
    suspend fun updateLaw(law: LawEntity)

    // ===== 清理 =====

    @Query("DELETE FROM laws WHERE isFavorite = 0 AND lastReadTime = 0 AND cachedAt < :before")
    suspend fun clearOldCache(before: Long)

    @Query("SELECT COUNT(*) FROM laws")
    suspend fun getCount(): Int
}
