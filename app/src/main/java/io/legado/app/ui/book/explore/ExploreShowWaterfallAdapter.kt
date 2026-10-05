package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchWaterfallBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.themeCardColorOrDefault
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.widget.WaterfallCardMetrics
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExploreShowWaterfallAdapter(
    context: Context,
    private val callBack: ExploreShowBookCallback,
    private val columns: Int
) : RecyclerAdapter<SearchBook, ItemSearchWaterfallBinding>(context) {

    private val translationJobs = mutableMapOf<ItemViewHolder, Job>()

    override fun getViewBinding(parent: ViewGroup): ItemSearchWaterfallBinding {
        return ItemSearchWaterfallBinding.inflate(inflater, parent, false).apply {
            root.applyUiBodyTypefaceDeep(context.uiTypeface())
            val metrics = WaterfallCardMetrics.resolve(parent, columns)
            root.layoutParams = (root.layoutParams ?: ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )).apply {
                height = metrics.cardHeight
            }
            ivCover.layoutParams = ivCover.layoutParams.apply {
                height = metrics.coverHeight
            }
            root.background = UiCorner.panelRounded(
                root.context,
                root.context.themeCardColorOrDefault(),
                UiCorner.panelRadius(root.context)
            )
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchWaterfallBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        translationJobs.remove(holder)?.cancel()
        if (payloads.isNotEmpty()) {
            payloads.forEach { payload ->
                val bundle = payload as? Bundle ?: return@forEach
                if (bundle.containsKey("isInBookshelf")) {
                    bindKindLabels(binding, item)
                }
            }
            startTranslation(holder, binding, item)
            return
        }
        binding.run {
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
            if (item.latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
                tvLasted.visible()
            }
            tvIntroduce.text = item.exploreListIntro(context)
            bindKindLabels(this, item)
            ivCover.setCoverStyle(CoverImageView.CoverStyle.GRID)
            ivCover.load(item, AppConfig.loadCoverOnlyWifi)
        }
        startTranslation(holder, binding, item)
    }

    private fun startTranslation(holder: ItemViewHolder, binding: ItemSearchWaterfallBinding, item: SearchBook) {
        translationJobs.remove(holder)?.cancel()
        if (!binding.root.isAttachedToWindow) return
        val owner = binding.root.findViewTreeLifecycleOwner() ?: return
        translationJobs[holder] = owner.lifecycleScope.launch {
            TranslateUtils.updates.collectLatest {
                val enabled = TranslateUtils.isTranslateEnabled()
                suspend fun display(raw: String, kind: TranslateUtils.Kind = TranslateUtils.Kind.META) =
                    if (enabled) TranslateUtils.translate(raw, kind) else raw
                fun label(id: Int, value: String) =
                    if (UiTranslation.isEnabled()) UiTranslation.vietnameseString(id, value)
                    else context.getString(id, value)

                val name = display(item.name)
                val author = label(R.string.author_show, display(item.author))
                val latest = label(R.string.lasted_show,
                    display(item.latestChapterTitle.orEmpty(), TranslateUtils.Kind.TITLE))
                val intro = display(item.exploreListIntro(context))
                val labels = kindLabels(item)
                val displayLabels = labels.map { raw ->
                    if (raw == IN_BOOKSHELF_LABEL && UiTranslation.isEnabled()) UiTranslation.translate(raw) else display(raw)
                }
                // A recycled card must never receive the previous book's translation.
                coroutineContext.ensureActive()
                if (enabled != TranslateUtils.isTranslateEnabled()) return@collectLatest
                binding.tvName.text = name
                binding.tvAuthor.text = author
                binding.tvLasted.text = latest
                binding.tvIntroduce.text = intro
                binding.llKind.setLabels(labels, displayLabels = displayLabels)
            }
        }
    }

    private fun kindLabels(item: SearchBook): List<String> = buildList {
        if (callBack.isInBookshelf(item)) add(IN_BOOKSHELF_LABEL)
        addAll(item.getKindList())
    }.take(4)

    private fun bindKindLabels(binding: ItemSearchWaterfallBinding, item: SearchBook) {
        val labels = kindLabels(item)
        if (labels.isEmpty()) {
            binding.llKind.gone()
        } else {
            binding.llKind.visible()
            binding.llKind.setLabels(labels)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchWaterfallBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                callBack.showBookInfo(it)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ItemViewHolder) {
        super.onViewAttachedToWindow(holder)
        val binding = holder.binding as? ItemSearchWaterfallBinding
        if (binding != null) {
            getItem(holder.bindingAdapterPosition - getHeaderCount())?.let {
                startTranslation(holder, binding, it)
            }
        }
        if (holder.itemViewType < 0 || holder.itemViewType >= TYPE_FOOTER_VIEW) {
            (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
        }
    }

    override fun onViewDetachedFromWindow(holder: ItemViewHolder) {
        translationJobs.remove(holder)?.cancel()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: ItemViewHolder) {
        translationJobs.remove(holder)?.cancel()
        super.onViewRecycled(holder)
    }
}

private const val IN_BOOKSHELF_LABEL = "已在书架"
