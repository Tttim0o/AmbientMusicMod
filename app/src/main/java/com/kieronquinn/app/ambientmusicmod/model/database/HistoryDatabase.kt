package com.kieronquinn.app.ambientmusicmod.model.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryFlag

/**
 *  Favourites and hidden entries for the history screen. Unlike the shards cache this is in the
 *  app's database directory, so it is not cleared with the cache.
 */
@Database(entities = [
    HistoryFlag::class
], version = 1, exportSchema = false)
abstract class HistoryDatabase: RoomDatabase() {

    companion object {
        fun getDatabase(context: Context): HistoryDatabase {
            return Room.databaseBuilder(
                context,
                HistoryDatabase::class.java,
                "history.db"
            ).build()
        }
    }

    abstract fun historyFlagDao(): HistoryFlagDao

}
