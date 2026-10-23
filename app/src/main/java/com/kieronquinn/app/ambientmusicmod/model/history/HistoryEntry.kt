package com.kieronquinn.app.ambientmusicmod.model.history

import com.kieronquinn.app.pixelambientmusic.model.RecognitionSource

data class HistoryEntry(
    val timestamp: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val source: RecognitionSource,
    val playerUrl: String?,
    val favourite: Boolean
)
