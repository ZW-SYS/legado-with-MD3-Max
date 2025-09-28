package io.legato.kazusa.ui.main.bookshelf.books.styleDefalut

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legato.kazusa.base.adapter.ItemViewHolder
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.databinding.ItemBookshelfListBinding
import io.legato.kazusa.help.book.isLocal
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.toTimeAgo
import splitties.views.onLongClick

class BooksAdapterList(
    context: Context,
    private val fragment: Fragment,
    private val callBack: CallBack,
    private val lifecycle: Lifecycle
) : BaseBooksAdapter<ItemBookshelfListBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookshelfListBinding {
        return ItemBookshelfListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookshelfListBinding,
        item: Book,
        payloads: MutableList<Any>
    ) = binding.run {
        binding.cdCover.transitionName = "book_${item.bookUrl}"
        if (payloads.isEmpty()) {
            tvName.text = item.name
            tvAuthor.text = item.author
            tvRead.text = item.durChapterTitle
            tvLast.text = item.latestChapterTitle
            ivCover.load(item.getDisplayCover(), item.name, item.author, false, item.origin)
            upRefresh(binding, item)
            upLastUpdateTime(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bundle.keySet().forEach {
                    when (it) {
                        "name" -> tvName.text = item.name
                        "author" -> tvAuthor.text = item.author
                        "dur" -> tvRead.text = item.durChapterTitle
                        "last" -> tvLast.text = item.latestChapterTitle
                        "cover" -> ivCover.load(
                            item.getDisplayCover(),
                            item.name,
                            item.author,
                            false,
                            item.origin,
                            fragment,
                            lifecycle
                        )

                        "refresh" -> upRefresh(binding, item)
                        "lastUpdateTime" -> upLastUpdateTime(binding, item)
                    }
                }
            }
        }
    }

    private fun upRefresh(binding: ItemBookshelfListBinding, item: Book) {
        if (!item.isLocal && callBack.isUpdate(item.bookUrl)) {
            binding.tvUnread.isVisible = false
            binding.rlLoading.isVisible = true
        } else {
            binding.rlLoading.isVisible = false
            if (AppConfig.showUnread) {
                //binding.tvUnread.setHighlight(item.lastCheckCount > 0)
                binding.tvUnread.isVisible = true
                binding.tvUnread.text = item.getUnreadChapterNum().toString()
            } else {
                binding.tvUnread.isVisible = false
            }
        }
    }

    private fun upLastUpdateTime(binding: ItemBookshelfListBinding, item: Book) {
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (binding.tvLastUpdateTime.text != time) {
                binding.tvLastUpdateTime.text = time
            }
        } else {
            binding.tvLastUpdateTime.text = ""
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookshelfListBinding) {
        holder.itemView.apply {
            binding.cvContent.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it, binding.cdCover)
                }
            }

            binding.cvContent.onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it, binding.cdCover)
                }
            }
        }
    }
}
