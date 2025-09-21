package io.legato.kazusa.ui.main.bookshelf.books

//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseFragment
import io.legato.kazusa.constant.AppLog
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.data.AppDatabase
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookGroup
import io.legato.kazusa.databinding.FragmentBooksBinding
import io.legato.kazusa.help.book.isAudio
import io.legato.kazusa.help.book.isImage
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.ui.book.audio.AudioPlayActivity
import io.legato.kazusa.ui.book.info.BookInfoActivity
import io.legato.kazusa.ui.book.manga.ReadMangaActivity
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.ui.main.MainViewModel
import io.legato.kazusa.ui.main.bookshelf.books.styleDefalut.BaseBooksAdapter
import io.legato.kazusa.ui.main.bookshelf.books.styleDefalut.BooksAdapterGrid
import io.legato.kazusa.ui.main.bookshelf.books.styleDefalut.BooksAdapterGridCompact
import io.legato.kazusa.ui.main.bookshelf.books.styleDefalut.BooksAdapterGridCover
import io.legato.kazusa.ui.main.bookshelf.books.styleDefalut.BooksAdapterList
import io.legato.kazusa.utils.bookshelfLayoutGrid
import io.legato.kazusa.utils.bookshelfLayoutMode
import io.legato.kazusa.utils.cnCompare
import io.legato.kazusa.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面
 */
class BooksFragment() : BaseFragment(R.layout.fragment_books),
    BaseBooksAdapter.CallBack {

    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()

    private val bookshelfLayoutMode by lazy { requireContext().bookshelfLayoutMode }

    private val bookshelfLayoutGrid by lazy { requireContext().bookshelfLayoutGrid }

    private val booksAdapter: BaseBooksAdapter<*> by lazy {
        when (bookshelfLayoutMode) {
            0 -> {
                BooksAdapterList(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            }

            1 -> {
                BooksAdapterGrid(requireContext(), this)
            }

            2 -> {
                BooksAdapterGridCompact(requireContext(), this)
            }

            else -> {
                BooksAdapterGridCover(requireContext(), this)
            }
        }
    }
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var enableRefresh = true

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            enableRefresh = it.getBoolean("enableRefresh", true)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        initRecyclerView()
        upRecyclerData()
    }

    private fun initRecyclerView() {
        //binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        //binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(booksAdapter.getItems())
        }
        if (bookshelfLayoutMode == 0) {
            binding.rvBookshelf.layoutManager = LinearLayoutManager(context)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksListRecycledViewPool)
        } else {
            binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayoutGrid)
            binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksGridRecycledViewPool)
        }
        booksAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvBookshelf.adapter = booksAdapter
        booksAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (positionStart == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (toPosition == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }
        })
        startLastUpdateTimeJob()
    }

    private fun upFastScrollerBar() {
        val showBookshelfFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showBookshelfFastScroller)
        if (showBookshelfFastScroller) {
            binding.rvBookshelf.scrollBarSize = 0
        } else {
            binding.rvBookshelf.scrollBarSize =
                ViewConfiguration.get(requireContext()).scaledScrollBarSize
        }
    }

    fun upBookSort() {
        bookSort = AppConfig.bookshelfSort
        upRecyclerData()
    }

    fun setEnableRefresh(enable: Boolean) {
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    /**
     * 更新书籍列表信息
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序

                val isDescending = AppConfig.bookshelfSortOrder == 1

                when (bookSort) {
                    1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
                    else list.sortedBy { it.latestChapterTime }

                    2 -> if (isDescending)
                        list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    else
                        list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

                    3 -> if (isDescending) list.sortedByDescending { it.order }
                    else list.sortedBy { it.order }

                    4 -> if (isDescending) list.sortedByDescending {
                        max(
                            it.latestChapterTime,
                            it.durChapterTime
                        )
                    }
                    else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

                    5 -> if (isDescending)
                        list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
                    else
                        list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }

                    else -> if (isDescending) list.sortedByDescending { it.durChapterTime }
                    else list.sortedBy { it.durChapterTime }
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                binding.emptyView.isGone = list.isNotEmpty()
                binding.refreshLayout.isEnabled = enableRefresh && list.isNotEmpty()
                booksAdapter.setItems(list)
            }
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!AppConfig.showLastUpdateTime || bookshelfLayoutMode != 0) {
            return
        }
        upLastUpdateTimeJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    booksAdapter.upLastUpdateTime()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> {
        return booksAdapter.getItems()
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    fun getBooksCount(): Int {
        return booksAdapter.itemCount
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /**
         * 将 RecyclerView 中的视图全部回收到 RecycledViewPool 中
         */
        binding.rvBookshelf.setItemViewCacheSize(0)
        binding.rvBookshelf.adapter = null
    }

    override fun open(book: Book, sharedView: View) {
        val transitionName = "book_${book.bookUrl}"
        sharedView.transitionName = transitionName

        val cls = when {
            book.isAudio -> AudioPlayActivity::class.java
            book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
            else -> ReadBookActivity::class.java
        }

        val intent = Intent(requireContext(), cls).apply {
            putExtra("bookUrl", book.bookUrl)
            putExtra("transitionName", transitionName)
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            sharedView,
            transitionName
        )

        startActivity(intent, options.toBundle())
    }

    override fun openBookInfo(book: Book, sharedView: View) {
        val intent = Intent(requireContext(), BookInfoActivity::class.java).apply {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("transitionName", "book_${book.bookUrl}") // 给共享元素唯一标识
        }

        sharedView.transitionName = "book_${book.bookUrl}"

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            sharedView,
            sharedView.transitionName
        )

        startActivity(intent, options.toBundle())
    }


    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
            startLastUpdateTimeJob()
            upFastScrollerBar()
        }
    }
}
