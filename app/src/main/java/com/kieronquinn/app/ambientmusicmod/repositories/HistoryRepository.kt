package com.kieronquinn.app.ambientmusicmod.repositories

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.audio.ambientmusic.HistoryData
import com.kieronquinn.app.ambientmusicmod.model.database.HistoryDatabase
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryEntry
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryFlag
import com.kieronquinn.app.ambientmusicmod.utils.extensions.safeQuery
import com.kieronquinn.app.ambientmusicmod.utils.extensions.safeRegisterContentObserver
import com.kieronquinn.app.pixelambientmusic.model.RecognitionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

interface HistoryRepository {

    /**
     *  Every recognised song in Pixel Ambient Music's history, newest first, excluding entries
     *  the user has removed
     */
    fun getHistory(): Flow<List<HistoryEntry>>

    suspend fun setFavourite(timestamp: Long, favourite: Boolean)
    suspend fun setHidden(timestamp: Long, hidden: Boolean)

}

class HistoryRepositoryImpl(context: Context): HistoryRepository {

    companion object {
        private val URI_HISTORY = Uri.Builder()
            .scheme("content")
            .authority("com.google.android.as.pam.ambientmusic.historyprovider")
            .path("recognizedsongs")
            .build()
        private const val COLUMN_HISTORY_TIMESTAMP = "timestamp"
        private const val COLUMN_HISTORY_HISTORY_ENTRY = "history_entry"
    }

    private val contentResolver = context.contentResolver
    private val dao = HistoryDatabase.getDatabase(context).historyFlagDao()

    private val history = callbackFlow {
        val observer = object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(loadHistory())
            }
        }
        contentResolver.safeRegisterContentObserver(URI_HISTORY, true, observer)
        trySend(loadHistory())
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    override fun getHistory() = combine(history, dao.getAll()) { entries, flags ->
        val flagMap = flags.associateBy { it.timestamp }
        entries.mapNotNull {
            val flag = flagMap[it.timestamp]
            if(flag?.hidden == true) return@mapNotNull null
            it.copy(favourite = flag?.favourite == true)
        }
    }

    override suspend fun setFavourite(timestamp: Long, favourite: Boolean) {
        val current = dao.get(timestamp) ?: HistoryFlag(timestamp)
        dao.insert(current.copy(favourite = favourite))
    }

    override suspend fun setHidden(timestamp: Long, hidden: Boolean) {
        val current = dao.get(timestamp) ?: HistoryFlag(timestamp)
        dao.insert(current.copy(hidden = hidden))
    }

    private fun loadHistory(): List<HistoryEntry> {
        val cursor = contentResolver.safeQuery(
            URI_HISTORY,
            arrayOf(COLUMN_HISTORY_TIMESTAMP, COLUMN_HISTORY_HISTORY_ENTRY),
            null,
            null,
            "$COLUMN_HISTORY_TIMESTAMP DESC"
        ) ?: return emptyList()
        return cursor.use {
            val entries = ArrayList<HistoryEntry>(it.count)
            while(it.moveToNext()) {
                val timestamp = it.getLong(0)
                val blob = it.getBlob(1) ?: continue
                if(timestamp == 0L) continue
                val item = try {
                    HistoryData.Item.parseFrom(blob)
                }catch (e: Exception){
                    continue
                }
                entries.add(HistoryEntry(
                    timestamp,
                    item.track.title,
                    item.track.artist,
                    item.track.album.takeIf { album -> album.isNotBlank() },
                    if(item.source == "ON_DEMAND") RecognitionSource.ON_DEMAND else RecognitionSource.NNFP,
                    item.track.playerList.firstOrNull()?.url?.takeIf { url -> url.isNotBlank() },
                    false
                ))
            }
            entries
        }
    }

}
