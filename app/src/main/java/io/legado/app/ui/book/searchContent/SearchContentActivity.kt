package io.legado.app.ui.book.searchContent

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ActivitySearchContentBinding
import io.legado.app.help.IntentData
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarMargin
import io.legado.app.utils.applyTint
import io.legado.app.utils.invisible
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.showSoftInput
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchContentActivity :
    VMBaseActivity<ActivitySearchContentBinding, SearchContentViewModel>(),
    SearchContentAdapter.Callback {

    override val binding by viewBinding(ActivitySearchContentBinding::inflate)
    override val viewModel by viewModels<SearchContentViewModel>()
    private val adapter by lazy { SearchContentAdapter(this, this) }
    private val mLayoutManager by lazy { UpLinearLayoutManager(this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var searchOptions = ContentSearchOptions()
    private var durChapterIndex = 0
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var initJob: Job? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        searchOptions = ContentSearchOptions(
            ContentSearchScope.entries.firstOrNull { it.name == getPrefString("contentSearchScope") }
                ?: ContentSearchScope.ALL,
            ContentSearchDirection.entries.firstOrNull { it.name == getPrefString("contentSearchDirection") }
                ?: ContentSearchDirection.FORWARD
        )
        binding.llSearchBaseInfo.setBackgroundResource(R.drawable.bg_search_glass_panel)
        binding.llSearchBaseInfo.applyNavigationBarMargin()
        binding.tvCurrentSearchInfo.setTextColor(primaryTextColor)
        binding.ivSearchContentTop.setColorFilter(primaryTextColor)
        binding.ivSearchContentBottom.setColorFilter(primaryTextColor)
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList")
        val position = intent.getIntExtra("searchResultIndex", 0)
        val noSearchResult = searchResultList == null
        initSearchView(noSearchResult)
        initRecyclerView()
        initView()
        val bookUrl = intent.getStringExtra("bookUrl") ?: return
        viewModel.initBook(bookUrl) {
            initSearchResultList(searchResultList, position)
            initBook(noSearchResult)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.content_search, menu)
        menu.add(0, MENU_SCOPE, 2, R.string.search_scope_title)
        menu.add(0, MENU_DIRECTION, 3, R.string.search_direction_title)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_enable_replace)?.isChecked = SearchContentViewModel.replaceEnabled
        menu.findItem(R.id.menu_enable_regex)?.isChecked = SearchContentViewModel.regexReplace
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_SCOPE -> selector(getString(R.string.search_scope_title), listOf(
                R.string.search_scope_all, R.string.search_scope_current,
                R.string.search_scope_forward, R.string.search_scope_backward
            ).map { getString(it) }) { _, index ->
                searchOptions = searchOptions.copy(scope = ContentSearchScope.entries[index])
                putPrefString("contentSearchScope", searchOptions.scope.name)
            }
            MENU_DIRECTION -> selector(getString(R.string.search_direction_title), listOf(
                R.string.search_direction_forward, R.string.search_direction_backward
            ).map { getString(it) }) { _, index ->
                searchOptions = searchOptions.copy(direction = ContentSearchDirection.entries[index])
                putPrefString("contentSearchDirection", searchOptions.direction.name)
            }
            R.id.menu_enable_replace -> {
                SearchContentViewModel.replaceEnabled = !SearchContentViewModel.replaceEnabled
                item.isChecked = SearchContentViewModel.replaceEnabled
            }
            R.id.menu_enable_regex -> {
                SearchContentViewModel.regexReplace = !SearchContentViewModel.regexReplace
                item.isChecked = SearchContentViewModel.regexReplace
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initSearchResultList(list: List<SearchResult>?, position: Int) {
        list ?: return
        viewModel.searchResultList.addAll(list)
        viewModel.searchResultCounts = list.size
        adapter.setItems(list)
        binding.recyclerView.scrollToPosition(position)
    }

    private fun initSearchView(requestFocus: Boolean) {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search)
        if (requestFocus) searchView.isIconified = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                startContentSearch(query.trim())
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() {
        binding.ivSearchContentTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        binding.ivSearchContentBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        binding.tvCurrentSearchInfo.setOnClickListener {
            searchView.allViews.forEach { view ->
                if (view is EditText) {
                    view.showSoftInput()
                    return@setOnClickListener
                }
            }
        }
        binding.fbStop.setOnClickListener {
            searchJob?.cancel()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(submit: Boolean = true) {
        binding.tvCurrentSearchInfo.text =
            this.getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
        viewModel.book?.let {
            initCacheFileNames(it)
            durChapterIndex = it.durChapterIndex
            intent.getStringExtra("searchWord")?.let { searchWord ->
                searchView.setQuery(searchWord, submit)
            }
        }
    }

    private fun initCacheFileNames(book: Book) {
        initJob = lifecycleScope.launch {
            withContext(IO) {
                viewModel.cacheChapterNames.addAll(BookHelp.getChapterFiles(book))
            }
            adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.book?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    viewModel.cacheChapterNames.addAll(BookHelp.getChapterCacheFileNames(book, chapter))
                    adapter.notifyItemChanged(chapter.index, true)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun startContentSearch(query: String) {
        // 按章节搜索内容
        if (query.isBlank()) return
        searchJob?.cancel()
        adapter.clearItems()
        viewModel.searchResultList.clear()
        viewModel.searchResultCounts = 0
        viewModel.lastQuery = query
        binding.refreshProgressBar.isAutoLoading = true
        binding.fbStop.visible()
        val options = searchOptions
        val currentChapter = durChapterIndex
        val generation = ++searchGeneration
        val useReplace = SearchContentViewModel.replaceEnabled
        val useRegex = SearchContentViewModel.regexReplace
        searchJob = lifecycleScope.launch(IO) {
            try {
                initJob?.join()
                val book = viewModel.book ?: return@launch
                options.chapters(appDb.bookChapterDao.getChapterList(viewModel.bookUrl), currentChapter)
                    .forEach { chapter ->
                        ensureActive()
                        if (!isLocalBook && BookHelp.getChapterCacheFileNames(book, chapter)
                                .none(viewModel.cacheChapterNames::contains)) return@forEach
                        val found = viewModel.searchChapter(query, chapter, useReplace, useRegex).let {
                            if (options.direction == ContentSearchDirection.BACKWARD) it.reversed() else it
                        }
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            ensureActive()
                            if (generation != searchGeneration || found.isEmpty()) return@withContext
                            viewModel.searchResultList.addAll(found)
                            viewModel.searchResultCounts += found.size
                            binding.tvCurrentSearchInfo.text =
                                getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
                            adapter.addItems(found)
                        }
                    }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (generation == searchGeneration && viewModel.searchResultCounts == 0) {
                        adapter.addItem(SearchResult(resultText = getString(R.string.search_content_empty)))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("Content search failed\n${e.localizedMessage}", e)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                    if (generation == searchGeneration) {
                        binding.fbStop.invisible()
                        binding.refreshProgressBar.isAutoLoading = false
                    }
                }
            }
        }
    }

    private val isLocalBook: Boolean
        get() = viewModel.book?.isLocal == true

    override fun openSearchResult(searchResult: SearchResult, index: Int) {
        searchJob?.cancel()
        postEvent(EventBus.SEARCH_RESULT, viewModel.searchResultList as List<SearchResult>)
        val searchData = Intent()
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", viewModel.searchResultList)
        searchData.putExtra("key", key)
        searchData.putExtra("index", index)
        setResult(RESULT_OK, searchData)
        finish()
    }

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

}

private const val MENU_SCOPE = 0x5410
private const val MENU_DIRECTION = 0x5411
