package io.legato.kazusa.ui.main.bookshelf

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.databinding.DialogBookshelfConfigBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.ui.main.MainViewModel
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class BookshelfConfigBottomSheet : BaseBottomSheetDialogFragment(R.layout.dialog_bookshelf_config) {

    private val binding by viewBinding(DialogBookshelfConfigBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        val orientation = requireContext().resources.configuration.orientation

        val bookshelfLayout = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            AppConfig.bookshelfLayoutLandscape
        } else {
            AppConfig.bookshelfLayoutPortrait
        }

        val isGrid = bookshelfLayout > 0
        val columnCount = bookshelfLayout.takeIf { it > 0 } ?: 1

        val bookshelfSort = AppConfig.bookshelfSort

        binding.apply {

            resources.getStringArray(R.array.group_style).forEachIndexed { index, label ->
                val chip = Chip(context).apply {
                    text = label
                    isCheckable = true
                    isClickable = true
                    id = View.generateViewId()
                }
                chipGroupStyle.addView(chip)
                if (index == AppConfig.bookGroupStyle) {
                    chipGroupStyle.check(chip.id)
                }
            }

            resources.getStringArray(R.array.bookshelf_px_array).forEachIndexed { index, label ->
                val chip = Chip(context).apply {
                    text = label
                    isCheckable = true
                    isClickable = true
                    id = index
                }
                chipGroupSort.addView(chip)
                if (index == AppConfig.bookshelfSort) {
                    chipGroupSort.check(chip.id)
                }
            }

            swShowUnread.isChecked = AppConfig.showUnread
            swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
            swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
            swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller

            chipList.isChecked = !isGrid
            chipGrid.isChecked = isGrid
            sliderText.isVisible = isGrid
            sliderGridCount.isVisible = isGrid
            sliderGridCount.value = columnCount.toFloat()

            when (AppConfig.bookshelfSortOrder) {
                0 -> binding.chipGroupOrder.check(binding.chipAsc.id)
                1 -> binding.chipGroupOrder.check(binding.chipDesc.id)
            }

            chipGroupLayout.setOnCheckedStateChangeListener { group, checkedIds ->
                sliderText.isVisible = checkedIds.firstOrNull() == R.id.chip_grid
                TransitionManager.beginDelayedTransition(root)
                sliderGridCount.isVisible = checkedIds.firstOrNull() == R.id.chip_grid
            }

            btnOk.setOnClickListener {
                var notifyMain = false
                var recreate = false

                if (AppConfig.bookGroupStyle != chipGroupStyle.checkedChipId) {
                    AppConfig.bookGroupStyle = chipGroupStyle.indexOfChild(chipGroupStyle.findViewById(chipGroupStyle.checkedChipId))
                    notifyMain = true
                }

                if (AppConfig.showUnread != swShowUnread.isChecked) {
                    AppConfig.showUnread = swShowUnread.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
                if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                    AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
                if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                    AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                    activityViewModel.postUpBooksLiveData(true)
                }

                if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                    AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }

                if (bookshelfSort != chipGroupSort.checkedChipId) {
                    AppConfig.bookshelfSort = chipGroupSort.checkedChipId
                    (requireParentFragment() as? BaseBookshelfFragment)?.upSort()
                }

                val isNowGrid = chipGrid.isChecked
                val selectedColumn = sliderGridCount.value.toInt()
                val newLayout = if (isNowGrid) selectedColumn else 0

                if (bookshelfLayout != newLayout) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        AppConfig.bookshelfLayoutLandscape = newLayout
                        if (newLayout == 0) {
                            activityViewModel.booksGridRecycledViewPool.clear()
                        } else {
                            activityViewModel.booksListRecycledViewPool.clear()
                        }
                    } else {
                        AppConfig.bookshelfLayoutPortrait = newLayout
                        if (newLayout == 0) {
                            activityViewModel.booksGridRecycledViewPool.clear()
                        } else {
                            activityViewModel.booksListRecycledViewPool.clear()
                        }
                    }
                    recreate = true
                }

                val newOrder = when (binding.chipGroupOrder.checkedChipId) {
                    binding.chipAsc.id -> 0
                    binding.chipDesc.id -> 1
                    else -> 1
                }

                if (AppConfig.bookshelfSortOrder != newOrder) {
                    AppConfig.bookshelfSortOrder = newOrder
                    (requireParentFragment() as? BaseBookshelfFragment)?.upSort()
                }

                if (recreate) {
                    dismiss()
                    postEvent(EventBus.RECREATE, "")
                } else if (notifyMain) {
                    dismiss()
                    postEvent(EventBus.NOTIFY_MAIN, false)
                }
                dismiss()
            }

            btnCancel.setOnClickListener {
                dismiss()
            }
        }
    }
}
