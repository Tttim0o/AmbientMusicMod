package com.kieronquinn.app.ambientmusicmod.model.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 *  Local state for a Now Playing history entry, keyed by the entry's recognition timestamp.
 *  The history itself lives in Pixel Ambient Music and is never modified.
 */
@Entity
data class HistoryFlag(
    @PrimaryKey
    val timestamp: Long,
    val favourite: Boolean = false,
    val hidden: Boolean = false
)
