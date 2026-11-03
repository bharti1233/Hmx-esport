package com.hmx.arena.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "cached_tournaments")
data class CachedTournament(
    @PrimaryKey val id: String,
    val payload: String,
    val cachedAt: Long,
)

@Dao
interface TournamentCacheDao {
    @Query("SELECT * FROM cached_tournaments ORDER BY cachedAt DESC")
    suspend fun all(): List<CachedTournament>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedTournament>)

    @Query("DELETE FROM cached_tournaments")
    suspend fun clear()
}

/**
 * Read-through cache for the tournament catalogue. The app is online-first:
 * Room only serves the last known catalogue when the device is offline.
 */
@Database(entities = [CachedTournament::class], version = 1, exportSchema = true)
abstract class HmxDatabase : RoomDatabase() {
    abstract fun tournaments(): TournamentCacheDao
}
