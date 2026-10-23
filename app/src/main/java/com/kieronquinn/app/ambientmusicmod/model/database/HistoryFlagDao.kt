package com.kieronquinn.app.ambientmusicmod.model.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryFlag
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryFlagDao {

    @Query("select * from HistoryFlag")
    fun getAll(): Flow<List<HistoryFlag>>

    @Query("select * from HistoryFlag where timestamp = :timestamp")
    suspend fun get(timestamp: Long): HistoryFlag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flag: HistoryFlag)

}
