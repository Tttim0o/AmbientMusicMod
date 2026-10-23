package com.kieronquinn.app.ambientmusicmod.ui.screens.history

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.ambientmusicmod.components.navigation.ContainerNavigation
import com.kieronquinn.app.ambientmusicmod.model.history.HistoryEntry
import com.kieronquinn.app.ambientmusicmod.repositories.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class HistoryViewModel: ViewModel() {

    enum class Filter {
        ALL, FAVOURITES
    }

    sealed class State {
        object Loading: State()
        data class Loaded(val list: List<HistoryEntry>): State()
    }

    abstract val state: StateFlow<State>
    abstract val searchText: StateFlow<CharSequence>
    abstract val searchShowClear: StateFlow<Boolean>
    abstract val filter: StateFlow<Filter>

    abstract fun setSearchText(text: CharSequence)
    abstract fun setFilter(filter: Filter)
    abstract fun onEntryClicked(entry: HistoryEntry)
    abstract fun onFavouriteClicked(entry: HistoryEntry)
    abstract fun onRemoved(entry: HistoryEntry)
    abstract fun onUndoRemove(entry: HistoryEntry)

}

class HistoryViewModelImpl(
    private val historyRepository: HistoryRepository,
    private val navigation: ContainerNavigation
): HistoryViewModel() {

    override val searchText = MutableStateFlow<CharSequence>("")
    override val filter = MutableStateFlow(Filter.ALL)

    override val searchShowClear = searchText.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override val state = combine(
        historyRepository.getHistory(),
        searchText,
        filter
    ) { history, search, filter ->
        val query = search.trim()
        val list = history.filter {
            (filter == Filter.ALL || it.favourite) && (query.isEmpty() ||
                    it.title.contains(query, true) ||
                    it.artist.contains(query, true) ||
                    it.album?.contains(query, true) == true)
        }
        State.Loaded(list)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun setSearchText(text: CharSequence) {
        viewModelScope.launch {
            searchText.emit(text)
        }
    }

    override fun setFilter(filter: Filter) {
        viewModelScope.launch {
            this@HistoryViewModelImpl.filter.emit(filter)
        }
    }

    override fun onEntryClicked(entry: HistoryEntry) {
        val uri = entry.playerUrl?.let { Uri.parse(it) }
            ?: Uri.parse("https://www.google.com/search").buildUpon()
                .appendQueryParameter("q", "${entry.title} ${entry.artist}")
                .build()
        viewModelScope.launch {
            navigation.navigate(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun onFavouriteClicked(entry: HistoryEntry) {
        viewModelScope.launch {
            historyRepository.setFavourite(entry.timestamp, !entry.favourite)
        }
    }

    override fun onRemoved(entry: HistoryEntry) {
        viewModelScope.launch {
            historyRepository.setHidden(entry.timestamp, true)
        }
    }

    override fun onUndoRemove(entry: HistoryEntry) {
        viewModelScope.launch {
            historyRepository.setHidden(entry.timestamp, false)
        }
    }

}
