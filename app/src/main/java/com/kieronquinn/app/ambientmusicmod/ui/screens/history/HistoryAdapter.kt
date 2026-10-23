package com.kieronquinn.app.ambientmusicmod.ui.screens.history

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import com.kieronquinn.app.ambientmusicmod.R
import com.kieronquinn.app.ambientmusicmod.databinding.ItemHistoryBinding
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryEntry
import com.kieronquinn.app.ambientmusicmod.ui.views.LifecycleAwareRecyclerView
import com.kieronquinn.app.ambientmusicmod.utils.extensions.isDarkMode
import com.kieronquinn.app.ambientmusicmod.utils.extensions.onClicked
import com.kieronquinn.app.ambientmusicmod.utils.extensions.whenResumed
import com.kieronquinn.app.pixelambientmusic.model.RecognitionSource
import com.kieronquinn.monetcompat.core.MonetCompat

class HistoryAdapter(
    recyclerView: LifecycleAwareRecyclerView,
    private val onEntryClicked: (HistoryEntry) -> Unit,
    private val onFavouriteClicked: (HistoryEntry) -> Unit,
    var items: List<HistoryEntry>
): LifecycleAwareRecyclerView.Adapter<HistoryAdapter.ViewHolder>(recyclerView) {

    init {
        setHasStableIds(true)
    }

    private val layoutInflater = LayoutInflater.from(recyclerView.context)

    private val monet by lazy {
        MonetCompat.getInstance()
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int) = items[position].timestamp

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemHistoryBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int): Unit = with(holder.binding) {
        val item = items[position]
        val context = root.context
        val ago = DateUtils.getRelativeTimeSpanString(
            item.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
        itemHistoryTitle.text = item.title
        itemHistoryContent.text = context.getString(R.string.history_item_content, item.artist, ago)
        itemHistoryIcon.setImageResource(
            if(item.source == RecognitionSource.ON_DEMAND) R.drawable.ic_source_picker_on_demand
            else R.drawable.ic_nav_tracklist_tracks
        )
        itemHistoryIcon.backgroundTintList = ColorStateList.valueOf(
            monet.getPrimaryColor(context, !context.isDarkMode)
        )
        itemHistoryIcon.imageTintList = ColorStateList.valueOf(monet.getAccentColor(context))
        itemHistoryFavourite.setImageResource(
            if(item.favourite) R.drawable.ic_history_favourite else R.drawable.ic_history_favourite_border
        )
        itemHistoryFavourite.contentDescription = context.getString(
            if(item.favourite) R.string.history_unfavourite else R.string.history_favourite
        )
        holder.whenResumed {
            root.onClicked().collect {
                onEntryClicked(item)
            }
        }
        holder.whenResumed {
            itemHistoryFavourite.onClicked().collect {
                onFavouriteClicked(item)
            }
        }
        Unit
    }

    data class ViewHolder(val binding: ItemHistoryBinding):
        LifecycleAwareRecyclerView.ViewHolder(binding.root)

}
