package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.bookmanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SectionHeader
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.potatoepub.Epub
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TxtImportSettingsBottomSheet(
    onClickImport: (Uri, MutableBookInformation) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )

    val state = remember { ImportFormState() }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            state.coverUri = uri
            state.coverUriText = uri.toString()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            state.selected = uri
        }
    }

    LaunchedEffect(state.coverUriText) {
        val t = state.coverUriText.trim()
        state.coverUri = if (t.isBlank()) null else runCatching { t.toUri() }.getOrNull()
    }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    if (showConfirmDialog) {
        ConfirmDialog(
            onHide = { showConfirmDialog = false },
            onDismissRequest = {
                showConfirmDialog = false
                onDismissRequest()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { showConfirmDialog = true },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showConfirmDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.close_24px),
                            contentDescription = "close",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "导入 TXT",
                        style = typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        enabled = state.canImport,
                        onClick = {
                            val file = state.selected ?: return@Button
                            onClickImport(file, state.toBookInformation())
                        }
                    ) { Text("导入") }
                }

                if (state.selected != null) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colorScheme.surfaceContainerLow,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(
                                        pagerState.currentPage,
                                        matchContentSize = true
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 4.dp,
                                            topEnd = 4.dp
                                        )
                                    ),
                                height = 4.dp,
                                width = Dp.Unspecified,
                            )
                        }
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("设置") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("信息") }
                        )
                    }
                }

                if (state.selected == null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filePicker.launch(arrayOf("text/plain"))
                                }
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(28.dp),
                                painter = painterResource(R.drawable.folder_24px),
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "选择文件",
                                    color = colorScheme.onSurface,
                                    style = typography.titleMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "选择 *.txt 本地文件导入",
                                    color = colorScheme.onSurfaceVariant,
                                    style = typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        when (page) {
                            0 -> TxtBasicSettingsPage(
                                state = state,
                                onPickFile = { filePicker.launch(arrayOf("text/plain")) }
                            )
                            1 -> InfoSettingsPage(
                                state = state,
                                onPickCover = { coverPicker.launch("image/*") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EpubImportSettingsBottomSheet(
    onClickImport: (Uri, MutableBookInformation) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )

    val state = remember { ImportFormState() }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            state.coverUri = uri
            state.coverUriText = uri.toString()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            state.selected = uri
        }
    }

    LaunchedEffect(state.coverUriText) {
        val t = state.coverUriText.trim()
        state.coverUri = if (t.isBlank()) null else runCatching { t.toUri() }.getOrNull()
    }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    if (showConfirmDialog) {
        ConfirmDialog(
            onHide = { showConfirmDialog = false },
            onDismissRequest = {
                showConfirmDialog = false
                onDismissRequest()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { showConfirmDialog = true },
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showConfirmDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.close_24px),
                            contentDescription = "close",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "导入 EPUB",
                        style = typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        enabled = state.canImport,
                        onClick = {
                            val file = state.selected ?: return@Button
                            onClickImport(file, state.toBookInformation())
                        }
                    ) { Text("导入") }
                }

                if (state.selected != null) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colorScheme.surfaceContainerLow,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(
                                        pagerState.currentPage,
                                        matchContentSize = true
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 4.dp,
                                            topEnd = 4.dp
                                        )
                                    ),
                                height = 4.dp,
                                width = Dp.Unspecified,
                            )
                        }
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("设置") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("信息") }
                        )
                    }
                }

                if (state.selected == null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filePicker.launch(arrayOf("text/plain"))
                                }
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(28.dp),
                                painter = painterResource(R.drawable.folder_24px),
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "选择文件",
                                    color = colorScheme.onSurface,
                                    style = typography.titleMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "选择 *.epub 本地文件导入",
                                    color = colorScheme.onSurfaceVariant,
                                    style = typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        when (page) {
                            0 -> EpubBasicSettingsPage(
                                state = state,
                                onPickFile = { filePicker.launch(arrayOf("application/epub+zip")) }
                            )
                            1 -> InfoSettingsPage(
                                state = state,
                                onPickCover = { coverPicker.launch("image/*") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    onHide: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onHide,
        confirmButton = {
            TextButton(
                onClick = {
                    onHide()
                    onDismissRequest()
                }
            ) { Text("退出") }
        },
        dismissButton = {
            TextButton(onClick = onHide) { Text("取消") }
        },
        title = {
            Text(
                text = "确认",
                style = typography.titleLarge
            )
        },
        text = {
            Text(
                text = "当前内容还未导入。确定要退出吗？",
                style = typography.bodyMedium
            )
        }
    )
}

@Composable
private fun TxtBasicSettingsPage(
    state: ImportFormState,
    onPickFile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(28.dp),
                        painter = painterResource(R.drawable.folder_24px),
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已选择文件",
                            color = colorScheme.onSurface,
                            style = typography.titleMedium
                        )
                        Text(
                            text = state.selected?.lastPathSegment ?: "unknown",
                            color = colorScheme.onSurfaceVariant,
                            style = typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            state.reset()
                            onPickFile()
                        }
                    ) { Text("重新选择") }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionHeader(
                modifier = Modifier.padding(4.dp),
                text = "设置"
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    LineInput(
                        label = "书名",
                        value = state.title,
                        onValueChange = { state.title = it },
                        placeholder = "必填"
                    )
                    LineInput(
                        label = "作者",
                        value = state.author,
                        onValueChange = { state.author = it },
                        placeholder = "必填",
                        showDivider = false
                    )
                }
            }
        }

        /*item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionHeader(
                modifier = Modifier.padding(4.dp),
                text = "分卷设置"
            )
        }*/
    }
}

@Composable
private fun EpubBasicSettingsPage(
    state: ImportFormState,
    onPickFile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(28.dp),
                        painter = painterResource(R.drawable.folder_24px),
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已选择文件",
                            color = colorScheme.onSurface,
                            style = typography.titleMedium
                        )
                        Text(
                            text = state.selected?.lastPathSegment ?: "unknown",
                            color = colorScheme.onSurfaceVariant,
                            style = typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            state.reset()
                            onPickFile()
                        }
                    ) { Text("重新选择") }
                }
            }
        }

        /*item { Spacer(Modifier.height(12.dp)) }

        item {
            SectionHeader(
                modifier = Modifier.padding(4.dp),
                text = "分卷设置"
            )
        }*/
    }
}

@Composable
private fun InfoSettingsPage(
    state: ImportFormState,
    onPickCover: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
    ) {
        item {
            SectionHeader(
                modifier = Modifier.padding(4.dp),
                text = "书本信息"
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    LineInput(
                        label = "书名",
                        value = state.title,
                        onValueChange = { state.title = it },
                        placeholder = "必填"
                    )
                    LineInput(
                        label = "副标题",
                        value = state.subtitle,
                        onValueChange = { state.subtitle = it },
                        placeholder = "可选"
                    )
                    LineInput(
                        label = "作者",
                        value = state.author,
                        onValueChange = { state.author = it },
                        placeholder = "必填"
                    )
                    LineInput(
                        label = "出版社",
                        value = state.publishingHouse,
                        onValueChange = { state.publishingHouse = it },
                        placeholder = "可选"
                    )
                    LineInput(
                        label = "标签",
                        value = state.tagsText,
                        onValueChange = { state.tagsText = it },
                        placeholder = "使用半角逗号分隔"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                    ) {
                        Text(
                            text = "简介",
                            style = typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(
                            value = state.description,
                            onValueChange = { state.description = it },
                            textStyle = typography.bodyLarge.copy(
                                color = colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                        ) { inner ->
                            if (state.description.isBlank()) {
                                Text(
                                    text = "输入书本简介…",
                                    style = typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.6f
                                    )
                                )
                            }
                            inner()
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        item {
            SectionHeader(
                modifier = Modifier.padding(4.dp),
                text = "封面"
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickCover() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewUri = state.coverUri
                    val painter =
                        rememberAsyncImagePainter(model = previewUri)

                    Box(
                        modifier = Modifier
                            .size(64.dp, 90.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewUri == null || previewUri == Uri.EMPTY) {
                            Icon(
                                painter = painterResource(R.drawable.add_photo_alternate_24px),
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.coverUri != null && state.coverUri != Uri.EMPTY) "已选择封面"
                            else "封面图片",
                            style = typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        val coverDisplayText =
                            if (state.coverUri != null && state.coverUri != Uri.EMPTY) "本地图片"
                            else "点击选择本地图片"
                        Text(
                            text = coverDisplayText,
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.coverUriText.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    state.coverUriText = ""
                                    state.coverUri = null
                                }
                            ) { Text("清除封面") }
                        }
                    }
                }

                val isLocalCover = state.coverUri?.scheme == "content"
                if (!isLocalCover) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "封面 URL",
                            style = typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = state.coverUriText,
                            onValueChange = { state.coverUriText = it },
                            singleLine = true,
                            textStyle = typography.bodyLarge.copy(
                                color = colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            if (state.coverUriText.isBlank()) {
                                Text(
                                    text = "http(s)://",
                                    style = typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.6f
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LineInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = typography.bodyLarge.copy(color = colorScheme.onSurface),
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier.weight(1f)
            ) { innerTextField ->
                if (value.isBlank() && placeholder.isNotBlank()) {
                    Text(
                        text = placeholder,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = DividerDefaults.Thickness,
                color = colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}