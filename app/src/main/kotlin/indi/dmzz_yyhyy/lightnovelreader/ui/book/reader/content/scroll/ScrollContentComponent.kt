package indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.content.scroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.SettingState
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.content.BaseContentComponent
import indi.dmzz_yyhyy.lightnovelreader.ui.components.Loading
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.data.MenuOptions
import indi.dmzz_yyhyy.lightnovelreader.utils.LocalSnackbarHost
import indi.dmzz_yyhyy.lightnovelreader.utils.readerTextColor
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderBackgroundPainter
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderFontFamily
import indi.dmzz_yyhyy.lightnovelreader.utils.showSnackbar
import kotlinx.coroutines.launch

@Composable
fun ScrollContentComponent(
    modifier: Modifier,
    uiState: ScrollContentUiState,
    settingState: SettingState,
    paddingValues: PaddingValues,
    changeIsImmersive: () -> Unit,
    onZoomImage: (String) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit
) {
    ScrollContentTextComponent(
        modifier = modifier,
        uiState = uiState,
        settingState = settingState,
        paddingValues = paddingValues,
        changeIsImmersive = changeIsImmersive,
        onZoomImage = onZoomImage,
        onClickPrevChapter = onClickPrevChapter,
        onClickNextChapter = onClickNextChapter
    )
}

@Composable
fun ScrollContentTextComponent(
    modifier: Modifier,
    uiState: ScrollContentUiState,
    settingState: SettingState,
    paddingValues: PaddingValues,
    changeIsImmersive: () -> Unit,
    onZoomImage: (String) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHost.current
    val density = LocalDensity.current
    val screenHeight = LocalResources.current.displayMetrics.heightPixels
    val textColor = readerTextColor(settingState)
    val fontFamily = rememberReaderFontFamily(settingState)
    val listState = uiState.lazyListState

    LaunchedEffect(listState) {
        var atTop = false
        var atBottom = false

        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val layoutInfo = listState.layoutInfo
                    val totalCount = layoutInfo.totalItemsCount
                    val firstIndex = listState.firstVisibleItemIndex
                    val firstOffset = listState.firstVisibleItemScrollOffset
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()

                    val isAtTop = firstIndex == 0 && firstOffset == 0
                    val isAtBottom = lastVisible != null &&
                            lastVisible.index == totalCount - 1 &&
                            (lastVisible.offset + lastVisible.size) <= layoutInfo.viewportEndOffset

                    when {
                        isAtTop -> {
                            if (atTop) {
                                coroutineScope.launch {
                                    showSnackbar(
                                        coroutineScope = coroutineScope,
                                        hostState = snackbarHostState,
                                        duration = SnackbarDuration.Short,
                                        message = context.getString(R.string.reader_reached_top),
                                        actionLabel = context.getString(R.string.previous_chapter)
                                    ) {
                                        if (it == SnackbarResult.ActionPerformed) {
                                            onClickPrevChapter()
                                        }
                                    }
                                }
                            }
                            atTop = true
                            atBottom = false
                        }
                        isAtBottom -> {
                            if (atBottom) {
                                coroutineScope.launch {
                                    showSnackbar(
                                        coroutineScope = coroutineScope,
                                        hostState = snackbarHostState,
                                        duration = SnackbarDuration.Short,
                                        message = context.getString(R.string.reader_reached_bottom),
                                        actionLabel = context.getString(R.string.next_chapter)
                                    ) {
                                        if (it == SnackbarResult.ActionPerformed) {
                                            onClickNextChapter()
                                        }
                                    }
                                }
                            }
                            atBottom = true
                            atTop = false
                        }
                        else -> {
                            atTop = false
                            atBottom = false
                        }
                    }
                }
            }
    }


    if (settingState.enableBackgroundImage && settingState.backgroundImageDisplayMode == MenuOptions.ReaderBgImageDisplayModeOptions.Loop) {
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) {
                    screenHeight.toDp()
                })
                .offset(y = with(density) {
                    ((uiState.lazyListState.layoutInfo.visibleItemsInfo.getOrNull(0)?.offset
                        ?: 0) % screenHeight + screenHeight).toDp()
                }),
            painter = rememberReaderBackgroundPainter(settingState),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) {
                    screenHeight.toDp()
                })
                .offset(y = with(density) {
                    ((uiState.lazyListState.layoutInfo.visibleItemsInfo.getOrNull(0)?.offset
                        ?: 0) % screenHeight).toDp()
                }),
            painter = rememberReaderBackgroundPainter(settingState),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        uiState.writeProgressRightNow()
    }
    AnimatedVisibility(
        uiState.contentList.isEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Loading()
    }
    AnimatedVisibility(
        uiState.contentList.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LazyColumn(
            modifier = modifier
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            changeIsImmersive.invoke()
                        }
                    )
                }
                .onGloballyPositioned {
                    uiState.setLazyColumnSize(it.size)
                },
            state = listState,
        ) {
            items(
                count = uiState.contentList.size,
                key = { index -> uiState.contentList.getOrNull(index)?.id ?: -1 } ,
                contentType = { { null } }
            ) { index ->
                uiState.contentList.getOrNull(index)?.let{
                    Column(
                        Modifier.defaultMinSize(
                            minHeight = with(density) {
                                screenHeight.toDp()
                            }
                        )
                    ) {
                        if (settingState.isUsingContinuousScrolling) {
                            val titleRegex = Regex("^(第[一二三四五六七八九十]+卷)\\s+(.*)")
                            val matchResult = titleRegex.find(it.title)

                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (matchResult != null) {
                                    val (volumeTitle, chapterTitle) = matchResult.destructured
                                    Text(
                                        text = volumeTitle,
                                        textAlign = TextAlign.Center,
                                        fontSize = (settingState.fontSize + 2).sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = fontFamily,
                                        color = textColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        text = chapterTitle,
                                        textAlign = TextAlign.Center,
                                        fontSize = (settingState.fontSize + 6).sp,
                                        lineHeight = (settingState.fontSize + settingState.fontLineHeight + 6).sp,
                                        fontWeight = FontWeight((settingState.fontWeigh.toInt() + 100)),
                                        fontFamily = fontFamily,
                                        color = textColor
                                    )
                                } else {
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        text = it.title,
                                        textAlign = TextAlign.Center,
                                        fontSize = (settingState.fontSize + 6).sp,
                                        lineHeight = (settingState.fontSize + settingState.fontLineHeight + 6).sp,
                                        fontWeight = FontWeight((settingState.fontWeigh.toInt() + 100)),
                                        fontFamily = fontFamily,
                                        color = textColor
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.width(48.dp),
                                        color = textColor
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        it.content
                            .split("[image]")
                            .filter { it.isNotBlank() }
                            .forEach {
                                BaseContentComponent(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(
                                            minHeight = with(density) {
                                                screenHeight.toDp()
                                            }
                                        ),
                                    text = it,
                                    fontSize = settingState.fontSize.sp,
                                    fontLineHeight = settingState.fontLineHeight.sp,
                                    fontWeight = FontWeight(settingState.fontWeigh.toInt()),
                                    fontFamily = fontFamily,
                                    color = textColor,
                                    onZoomImage = onZoomImage
                                )
                            }
                    }
                }
            }
        }
    }
}
