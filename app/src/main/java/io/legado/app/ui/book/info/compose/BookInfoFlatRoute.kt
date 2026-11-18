package io.legado.app.ui.book.info.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.widget.compose.BookCoverImage
import io.legado.app.ui.widget.compose.LegadoMiuixSwitch
import io.legado.app.ui.widget.compose.rememberAppManagementPalette
import io.legado.app.ui.widget.compose.translatedText
import io.legado.app.ui.widget.compose.translatedUiString
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.TranslateUtils

/** A compact detail page sharing the existing activity actions and rich-intro renderer. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BookInfoFlatRoute(state: BookInfoUiState, actions: BookInfoActions) {
    val palette = rememberAppManagementPalette()
    val colors = palette.settings
    val baseStyle = bookInfoComposeStyle(LocalContext.current)
    val style = baseStyle.copy(colors = baseStyle.colors.copy(
        background = colors.page, contentBackground = colors.page, contentTop = colors.page,
        surface = Color(colors.row), surfaceVariant = palette.miuix.surfaceVariant,
        primaryText = colors.primaryText, secondaryText = colors.secondaryText,
        accent = colors.accent, accentContainer = colors.accent.copy(alpha = 0.12f),
        actionText = colors.onAccent
    ))
    val scroll = rememberScrollState()
    val atTop by remember { derivedStateOf { scroll.value == 0 } }
    LaunchedEffect(atTop) { actions.onRefreshEnabledChanged(atTop) }
    var webPages by remember(state.bookUrl, state.intro) { mutableStateOf(2) }
    val expandWeb by remember(state.intro) { derivedStateOf {
        state.intro.startsWith("<useweb>", true) && scroll.maxValue > 0 &&
            scroll.value >= scroll.maxValue - 96
    } }
    LaunchedEffect(expandWeb) { if (expandWeb && webPages < 36) webPages += 2 }
    var more by remember { mutableStateOf(false) }
    var cloud by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.page).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlatIcon(R.drawable.ic_back, translatedUiString(R.string.back), colors.primaryText, actions.onBack)
            Spacer(Modifier.weight(1f))
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            FlatIcon(R.drawable.ic_more_vert, translatedUiString(R.string.more), colors.primaryText) { more = true }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookCoverImage(
                    path = state.coverPath, name = state.name, author = state.author, sourceOrigin = state.sourceUrl,
                    style = CoverImageView.CoverStyle.DETAIL, fillBounds = true,
                    modifier = Modifier.width(78.dp).aspectRatio(0.68f).clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = actions.onChangeCover, onLongClick = actions.onPreviewCover)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(translatedText(state.name).ifBlank { translatedUiString(R.string.book_name) },
                        color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.combinedClickable(onClick = actions.onNameClick, onLongClick = actions.onNameLongClick))
                    Text(translatedText(state.author).ifBlank { translatedUiString(R.string.author) },
                        color = colors.secondaryText, fontSize = 13.sp,
                        modifier = Modifier.combinedClickable(onClick = actions.onAuthorClick, onLongClick = actions.onAuthorLongClick))
                    Text(translatedText(state.originName), color = colors.secondaryText, fontSize = 12.sp)
                    if (state.readTimeText.isNotBlank()) Text(state.readTimeText, color = colors.secondaryText, fontSize = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (state.kinds + state.customTags).filter { it.isNotBlank() }.distinct().forEach { tag ->
                            Text(if (tag in state.kinds) translatedText(tag) else tag, fontSize = 11.sp,
                                color = colors.secondaryText, modifier = Modifier.background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            Text(translatedUiString(R.string.book_info_flat_quick_actions), color = colors.secondaryText, fontSize = 12.sp)
            val buttons = buildList {
                add(FlatAction(R.drawable.ic_toc, R.string.view_toc, actions.onOpenToc))
                add(FlatAction(R.drawable.ic_groups, R.string.group_select, actions.onChangeGroup))
                add(FlatAction(R.drawable.ic_outline_cloud_24, R.string.book_cloud_entry_mode) { cloud = true })
                add(FlatAction(R.drawable.ic_edit, R.string.edit, actions.onEditBookInfo))
                if (state.hasBookSource) add(FlatAction(R.drawable.ic_cfg_source, R.string.change_origin, actions.onChangeSource))
                add(FlatAction(R.drawable.ic_refresh_black_24dp, R.string.refresh, actions.onRefresh))
                if (state.hasCustomButton) add(FlatAction(R.drawable.ic_menu, R.string.custom_button, actions.onCustomButton))
                add(FlatAction(R.drawable.ic_outline_delete, R.string.clear_cache, actions.onClearCache))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { button ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = button.onClick)
                                .heightIn(min = 72.dp).padding(horizontal = 2.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(button.icon), null, tint = colors.primaryText, modifier = Modifier.size(22.dp))
                                Text(translatedUiString(button.label), color = colors.primaryText, fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            FlatDetailRow(R.drawable.ic_image, translatedUiString(R.string.book_info_component_ai_images),
                state.aiImageCount.toString(), style, actions.onOpenAiGallery)
            if (state.hasBookSource) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(translatedUiString(R.string.allow_update), color = colors.primaryText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    LegadoMiuixSwitch(checked = state.canUpdate, onCheckedChange = actions.onAllowUpdateChanged, palette = palette.miuix)
                }
            }
            if (state.canScheduleUpdate) FlatDetailRow(R.drawable.ic_refresh_black_24dp,
                translatedUiString(R.string.auto_task_book_update), "", style, actions.onBookAutoTask)
            if (state.latestChapterTitle.isNotBlank()) FlatDetailRow(R.drawable.ic_toc,
                translatedText(state.latestChapterTitle, TranslateUtils.Kind.TITLE), "", style, actions.onOpenToc)
            FlatDetailRow(R.drawable.ic_book_last,
                translatedText(state.currentChapterTitle, TranslateUtils.Kind.TITLE).ifBlank { translatedUiString(R.string.view_toc) },
                state.chapterCount.takeIf { it > 0 }?.toString().orEmpty(), style, actions.onOpenToc)
            BookInfoIntroContent(
                rawIntro = translatedText(state.intro).ifBlank { translatedUiString(R.string.intro_show_null) },
                state = state, actions = actions, style = style, webIntroExpandPages = webPages
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlatFooterButton(translatedUiString(if (state.inBookshelf) R.string.remove_from_bookshelf else R.string.add_to_bookshelf),
                colors.accent.copy(alpha = 0.12f), colors.primaryText, Modifier.weight(1f), actions.onShelf)
            FlatFooterButton(translatedUiString(R.string.reading), colors.accent, colors.onAccent, Modifier.weight(1f), actions.onRead)
        }
    }
    if (more) BookInfoMoreActionSheet(state, style, actions,
        onEditQuickActions = {}, onDismiss = { more = false }, allowEditQuickActions = false)
    if (cloud) BookInfoCloudEntrySelectorDialog(state, style, actions, onDismiss = { cloud = false })
}

private data class FlatAction(val icon: Int, val label: Int, val onClick: () -> Unit)

@Composable
private fun FlatIcon(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FlatDetailRow(icon: Int, title: String, value: String, style: BookInfoComposeStyle, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), null, tint = style.colors.accent, modifier = Modifier.size(20.dp))
        Text(title, color = style.colors.primaryText, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (value.isNotBlank()) Text(value, color = style.colors.secondaryText, fontSize = 12.sp)
        Icon(painterResource(R.drawable.ic_arrow_right), null, tint = style.colors.secondaryText, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FlatFooterButton(text: String, background: Color, foreground: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(background).clickable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
