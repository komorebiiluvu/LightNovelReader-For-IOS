@file:Suppress("AssignedValueIsNeverRead")

package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.bookmanager

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.ui.components.Cover
import indi.dmzz_yyhyy.lightnovelreader.ui.components.EmptyPage
import indi.dmzz_yyhyy.lightnovelreader.utils.withHaptic
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.lightnovelreader.api.book.WordCount
import java.time.LocalDateTime
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookManagerScreen(
    onClickBack: () -> Unit,
    onImportLocalFile: (ImportType, Uri) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    val isSelectMode = selectedIds.isNotEmpty()

    val offlineBookList = remember {
        mutableStateListOf<BookInformation>().apply {
            addAll(buildMockBooks())
        }
    }
    val localBookList = remember {
        mutableStateListOf<BookInformation>().apply {
            addAll(buildMockBooks())
        }
    }
    val tabCounts = listOf(5, 8)

    fun handleBack() {
        if (showDiscardConfirmDialog) {
            showDiscardConfirmDialog = false
            return
        }
        if (selectedIds.isNotEmpty()) {
            selectedIds.clear()
            return
        }
        onClickBack()
    }

    BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            TopBar(
                onClickTab = { index ->
                    if (showDiscardConfirmDialog) showDiscardConfirmDialog = false
                    selectedTabIndex = index
                },
                selectedIds = selectedIds,
                onClickBack = ::handleBack,
                selectedTabIndex = selectedTabIndex,
                tabCounts = tabCounts,
                onClickDeleteSelected = {
                    showDiscardConfirmDialog = true
                },
                onClickExitSelectMode = {
                    selectedIds.clear()
                }
            )
        },
        floatingActionButton = {
            FAB(
                show = selectedTabIndex == 1 && !isSelectMode,
                onImportLocalFile = onImportLocalFile
            )
        }
    ) { innerPadding ->
        val currentList = if (selectedTabIndex == 0) offlineBookList else localBookList
        BookManagerList(
            modifier = Modifier.padding(innerPadding),
            items = currentList,
            selectedIds = selectedIds,
            isSelectMode = isSelectMode,
            onToggleSelect = { id ->
                if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
            },
            onDeleteSingle = { id ->
                currentList.removeAll { it.id == id }
            }
        )
    }

    if (showDiscardConfirmDialog && selectedIds.isNotEmpty()) {
        val count = selectedIds.size
        val isLocal = selectedTabIndex == 1
        val title = if (isLocal) "删除内容" else "清除缓存"
        val body = if (isLocal) {
            "确定要删除选中的 $count 本书吗？这将使它们从本地书本中移除，且无法撤销该操作。"
        } else {
            "确定要清除选中的 $count 本书缓存吗？这将使它们不再离线可用，且无法撤销该操作。"
        }
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text(text = title) },
            text = { Text(text = body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetList = if (isLocal) localBookList else offlineBookList
                        targetList.removeAll { selectedIds.contains(it.id) }
                        showDiscardConfirmDialog = false
                        selectedIds.clear()
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BookManagerList(
    items: List<BookInformation>,
    selectedIds: List<String>,
    isSelectMode: Boolean,
    onToggleSelect: (String) -> Unit,
    onDeleteSingle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyPage(
            modifier = modifier.fillMaxSize(),
            icon = painterResource(R.drawable.empty_90dp),
            title = "空列表",
            description = "还没有书本…"
        )
    } else {
        val listModifier = if (isSelectMode) {
            modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else {
            modifier.fillMaxWidth().wrapContentHeight().verticalScroll(rememberScrollState())
        }
        Column(
            modifier = listModifier
        ) {
            items.forEachIndexed { index, book ->
                val selected = selectedIds.contains(book.id)
                BookCardItem(
                    book = book,
                    selected = selected,
                    isSelectMode = isSelectMode,
                    isCached = Random.nextBoolean(),
                    onLongPress = { onToggleSelect(book.id) },
                    onClick = {
                        if (isSelectMode) {
                            onToggleSelect(book.id)
                        }
                    },
                    onDeleteSingle = { onDeleteSingle(book.id) }
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCardItem(
    book: BookInformation,
    selected: Boolean,
    isSelectMode: Boolean,
    isCached: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDeleteSingle: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected && isSelectMode) colorScheme.primaryContainer.copy(alpha = 0.35f)
        else Color.Transparent
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = withHaptic { onLongPress() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isSelectMode,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            Row {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onLongPress() }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Cover(
            uri = Uri.EMPTY,
            width = 55.dp,
            height = 74.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = book.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.titleMedium
            )
            Text(
                text = book.author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.bodyMedium.copy(
                    fontWeight = FontWeight.W600,
                    color = colorScheme.primary
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cachedBg = if (isCached) colorScheme.primaryContainer
                else colorScheme.surfaceContainerHigh
                val cachedTextColor = if (isCached) colorScheme.onPrimaryContainer
                else colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(cachedBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isCached) stringResource(R.string.cached)
                        else stringResource(R.string.cached_false),
                        style = typography.labelSmall.copy(
                            color = cachedTextColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                /* 这里用书本更新时间或上次阅读时间均可 */
                val statusIcon = painterResource(R.drawable.update_24px)
                // val statusIcon = if (book.isComplete) painterResource(R.drawable.done_all_24px)
                // else painterResource(R.drawable.hourglass_top_24px)
                Icon(
                    modifier = Modifier.size(14.dp),
                    painter = statusIcon,
                    contentDescription = null,
                    tint = colorScheme.outline
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = book.lastUpdated.toString(),
                    style = typography.labelMedium.copy(color = colorScheme.outline),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        AnimatedVisibility(
            visible = !isSelectMode,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
        ) {
            ManagementActions(onDelete = onDeleteSingle)
        }
    }
}

@Composable
private fun ManagementActions(
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert_24px),
                contentDescription = "more",
                tint = colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("详情") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.info_24px),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("导出") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.file_export_24px),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showMenu = false }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        text = "删除",
                        color = colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.delete_forever_24px),
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "删除内容",
                    style = typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "确定要删除这本书吗？此操作无法撤销。",
                    style = typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 用于生成随机的示例书本列表。
 * 另外，UI 的设计中提供了 已缓存 和 上次阅读日期 的位置。如果不易实现可以先使用 已完结 和 更新日期 替代
 */
private fun buildMockBooks(): List<BookInformation> {
    val titles = listOf("神秘书本", "不神秘书本", "未知")
    val random = Random(1)
    val authors = listOf("yukonisen", "NightFish", "unknown")
    return titles.mapIndexed { index, title ->
        MutableBookInformation(
            id = "mock${index}",
            title = title,
            author = authors.random(),
            description = "略",
            isComplete = random.nextBoolean(),
            subtitle = "",
            coverUrl = Uri.EMPTY,
            tags = listOf("N/A"),
            publishingHouse = "N/A",
            wordCount = WordCount(100),
            lastUpdated = LocalDateTime.now(),
        )
    }
}

enum class ImportType {
    TXT,
    EPUB
}

data class ImportSource(
    val type: ImportType,
    val icon: Painter,
    val label: String
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FAB(
    show: Boolean,
    onImportLocalFile: (ImportType, Uri) -> Unit
) {
    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollForward
        }
    }
    val focusRequester = remember { FocusRequester() }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val items = listOf(
        ImportSource(
            type = ImportType.TXT,
            icon = painterResource(R.drawable.folder_24px),
            label = ".txt"
        ),
        ImportSource(
            type = ImportType.EPUB,
            icon = painterResource(R.drawable.folder_24px),
            label = ".epub"
        )
    )

    if (show) {
        FloatingActionButtonMenu(
            expanded = fabMenuExpanded,
            button = {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Start
                        ),
                    tooltip = { PlainTooltip { Text("导入本地内容") } },
                    state = rememberTooltipState(),
                ) {
                    ToggleFloatingActionButton(
                        modifier =
                            Modifier
                                .animateFloatingActionButton(
                                    visible = fabVisible || fabMenuExpanded,
                                    alignment = Alignment.BottomEnd,
                                )
                                .focusRequester(focusRequester),
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded },
                    ) {
                        val painter =
                            if (checkedProgress > 0.5f)
                                painterResource(R.drawable.close_24px)
                            else
                                painterResource(R.drawable.input_24px)

                        Icon(
                            modifier = Modifier.animateIcon({ checkedProgress }),
                            painter = painter,
                            contentDescription = null,
                        )
                    }
                }
            },
        ) {
            items.forEach { item ->
                FloatingActionButtonMenuItem(
                    onClick = {
                        onImportLocalFile(item.type, Uri.EMPTY) // FIXME: Uri.EMPTY
                        fabMenuExpanded = false
                    },
                    icon = { Icon(item.icon, contentDescription = null) },
                    text = { Text(text = item.label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    selectedTabIndex: Int,
    selectedIds: List<String>,
    tabCounts: List<Int>,
    onClickBack: () -> Unit,
    onClickTab: (index: Int) -> Unit,
    onClickExitSelectMode: () -> Unit,
    onClickDeleteSelected: () -> Unit,
) {
    val isSelectMode = selectedIds.isNotEmpty()
    val tabTitles = listOf("离线内容", "本地书本")
    val appBarColor by animateColorAsState(
        if (!isSelectMode) colorScheme.surface else colorScheme.surfaceVariant
    )

    Column {
        TopAppBar(
            title = {
                Text(
                    text = if (isSelectMode) "选择 ${selectedIds.size} 本" else "本地书本管理",
                    style = typography.displayLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onClickBack) {
                    Icon(
                        painter = painterResource(
                            if (isSelectMode) R.drawable.cancel_24px else R.drawable.arrow_back_24px
                        ),
                        contentDescription = "back"
                    )
                }
            },
            actions = {
                if (isSelectMode) {
                    IconButton(onClick = onClickDeleteSelected) {
                        Icon(
                            painter = painterResource(R.drawable.delete_forever_24px),
                            contentDescription = "delete"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = appBarColor,
                scrolledContainerColor = appBarColor
            )
        )
        AnimatedVisibility(
            visible = !isSelectMode,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier
                            .tabIndicatorOffset(selectedTabIndex, matchContentSize = true)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        height = 4.dp,
                        width = Dp.Unspecified,
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isActive = selectedTabIndex == index
                    val badgeBg by animateColorAsState(
                        targetValue = if (isActive) colorScheme.primary else colorScheme.surfaceContainerHighest,
                    )
                    val badgeTextColor by animateColorAsState(
                        targetValue = if (isActive) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    )
                    Tab(
                        selected = isActive,
                        onClick = {
                            onClickTab(index)
                            onClickExitSelectMode()
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = title)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${tabCounts[index]}",
                                        style = typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = badgeTextColor
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}