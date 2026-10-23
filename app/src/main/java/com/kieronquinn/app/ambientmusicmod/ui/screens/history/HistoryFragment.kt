package com.kieronquinn.app.ambientmusicmod.ui.screens.history

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.kieronquinn.app.ambientmusicmod.R
import com.kieronquinn.app.ambientmusicmod.databinding.FragmentHistoryBinding
import com.kieronquinn.app.ambientmusicmod.ui.base.BackAvailable
import com.kieronquinn.app.ambientmusicmod.ui.base.BoundFragment
import com.kieronquinn.app.ambientmusicmod.ui.screens.history.HistoryViewModel.Filter
import com.kieronquinn.app.ambientmusicmod.ui.screens.history.HistoryViewModel.State
import com.kieronquinn.app.ambientmusicmod.utils.extensions.applyBottomNavigationInset
import com.kieronquinn.app.ambientmusicmod.utils.extensions.applyBottomNavigationMargin
import com.kieronquinn.app.ambientmusicmod.utils.extensions.hideIme
import com.kieronquinn.app.ambientmusicmod.utils.extensions.onChanged
import com.kieronquinn.app.ambientmusicmod.utils.extensions.onClicked
import com.kieronquinn.app.ambientmusicmod.utils.extensions.onEditorActionSent
import com.kieronquinn.app.ambientmusicmod.utils.extensions.setTypeface
import com.kieronquinn.app.ambientmusicmod.utils.extensions.whenResumed
import com.kieronquinn.monetcompat.extensions.applyMonet
import com.kieronquinn.monetcompat.extensions.views.applyMonet
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HistoryFragment: BoundFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate), BackAvailable {

    private val viewModel by viewModel<HistoryViewModel>()

    private val adapter by lazy {
        HistoryAdapter(
            binding.historyRecyclerview,
            viewModel::onEntryClicked,
            viewModel::onFavouriteClicked,
            emptyList()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeToRemove()
        setupSearch()
        setupSearchClear()
        setupFilter()
        setupState()
        setupMonet()
    }

    private fun setupMonet() {
        binding.historyLoadingProgress.applyMonet()
        binding.historySearch.searchBox.applyMonet()
        binding.historySearch.searchBox.backgroundTintList = ColorStateList.valueOf(
            monet.getBackgroundColorSecondary(requireContext()) ?: monet.getBackgroundColor(
                requireContext()
            )
        )
    }

    private fun setupRecyclerView() = with(binding.historyRecyclerview) {
        layoutManager = LinearLayoutManager(context)
        adapter = this@HistoryFragment.adapter
        applyBottomNavigationInset(resources.getDimension(R.dimen.margin_16))
    }

    private fun setupSwipeToRemove() {
        val callback = object: ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val entry = adapter.items.getOrNull(viewHolder.bindingAdapterPosition) ?: return
                viewModel.onRemoved(entry)
                Snackbar.make(requireView(), R.string.history_removed, Snackbar.LENGTH_LONG)
                    .applyMonet()
                    .setTypeface(ResourcesCompat.getFont(requireContext(), R.font.google_sans_text_medium))
                    .setAction(R.string.history_undo) { viewModel.onUndoRemove(entry) }
                    .apply {
                        view.applyBottomNavigationMargin()
                    }
                    .show()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.historyRecyclerview)
    }

    private fun setupState() {
        handleState(viewModel.state.value)
        whenResumed {
            viewModel.state.collect {
                handleState(it)
            }
        }
    }

    private fun handleState(state: State) {
        when(state){
            is State.Loading -> {
                binding.historyLoading.isVisible = true
                binding.historyRecyclerview.isVisible = false
                binding.historyEmpty.isVisible = false
            }
            is State.Loaded -> {
                binding.historyLoading.isVisible = false
                binding.historyRecyclerview.isVisible = true
                binding.historyEmpty.isVisible = state.list.isEmpty()
                adapter.items = state.list
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupFilter() {
        binding.historyFilter.check(when(viewModel.filter.value) {
            Filter.ALL -> R.id.history_filter_all
            Filter.FAVOURITES -> R.id.history_filter_favourites
        })
        binding.historyFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setFilter(
                if(checkedIds.firstOrNull() == R.id.history_filter_favourites) Filter.FAVOURITES
                else Filter.ALL
            )
        }
    }

    private fun setupSearch() {
        setSearchText(viewModel.searchText.value)
        whenResumed {
            binding.historySearch.searchBox.onChanged().debounce(250L).collect {
                viewModel.setSearchText(it ?: "")
            }
        }
        whenResumed {
            binding.historySearch.searchBox.onEditorActionSent(EditorInfo.IME_ACTION_SEARCH).collect {
                binding.historySearch.searchBox.hideIme()
            }
        }
    }

    private fun setupSearchClear() = whenResumed {
        launch {
            viewModel.searchShowClear.collect {
                binding.historySearch.searchClear.isVisible = it
            }
        }
        launch {
            binding.historySearch.searchClear.onClicked().collect {
                setSearchText("")
            }
        }
    }

    private fun setSearchText(text: CharSequence) {
        binding.historySearch.searchBox.run {
            this.text?.let {
                it.clear()
                it.append(text)
            } ?: setText(text)
        }
    }

}
