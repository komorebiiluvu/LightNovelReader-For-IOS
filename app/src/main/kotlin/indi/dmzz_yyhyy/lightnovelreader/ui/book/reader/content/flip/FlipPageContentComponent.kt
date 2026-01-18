package indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.content.flip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import indi.dmzz_yyhyy.lightnovelreader.theme.AppTypography
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.SettingState
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.content.BaseContentComponent
import indi.dmzz_yyhyy.lightnovelreader.ui.components.Loading
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.data.MenuOptions
import indi.dmzz_yyhyy.lightnovelreader.utils.LocalSnackbarHost
import indi.dmzz_yyhyy.lightnovelreader.utils.readerTextColor
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderBackgroundPainter
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderFontFamily
import indi.dmzz_yyhyy.lightnovelreader.utils.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.LocalResources
import indi.dmzz_yyhyy.lightnovelreader.R

@Composable
fun FlipPageContentComponent(
    modifier: Modifier,
    uiState: FlipPageContentUiState,
    settingState: SettingState,
    paddingValues: PaddingValues,
    changeIsImmersive: () -> Unit,
    onZoomImage: (String) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit
) {
    SimpleFlipPageTextComponent(
        modifier = modifier,
        paddingValues = paddingValues,
        uiState = uiState,
        settingState = settingState,
        changeIsImmersive = changeIsImmersive,
        onZoomImage = onZoomImage,
        onClickNextChapter = onClickNextChapter,
        onClickPrevChapter = onClickPrevChapter
    )
}

@Composable
private fun SimpleFlipPageTextComponent(
    modifier: Modifier,
    paddingValues: PaddingValues,
    uiState: FlipPageContentUiState,
    settingState: SettingState,
    changeIsImmersive: () -> Unit,
    onZoomImage: (String) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var contentKey by remember { mutableIntStateOf(0) }
    var slipTextJob by remember { mutableStateOf<Job?>(null) }
    var constraints by remember { mutableStateOf<Constraints?>(null) }
    var textStyle by remember { mutableStateOf<TextStyle?>(null) }
    var slippedTextList by remember { mutableStateOf(emptyList<String>()) }
    var readingPageFistCharOffset by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = LocalSnackbarHost.current

    fun lastPage(pagerState: PagerState) {
        if (pagerState.currentPage != 0) {
            scope.launch {
                if (settingState.flipAnime != MenuOptions.FlipAnimationOptions.None) {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                } else {
                    pagerState.scrollToPage(pagerState.currentPage - 1)
                }
            }
        } else if (settingState.fastChapterChange && slippedTextList.isNotEmpty()) {
            uiState.loadLastChapter.invoke()
        } else {
            scope.launch {
                showSnackbar(
                    coroutineScope = scope,
                    hostState = snackbarHostState,
                    duration = SnackbarDuration.Short,
                    message = context.getString(R.string.reader_first_page),
                    actionLabel = context.getString(R.string.previous_chapter)
                ) {
                    if (it == SnackbarResult.ActionPerformed) {
                        onClickPrevChapter()
                    }
                }
            }
        }
    }

    fun nextPage(pagerState: PagerState) {
        if (pagerState.currentPage + 1 < pagerState.pageCount) {
            scope.launch {
                if (settingState.flipAnime != MenuOptions.FlipAnimationOptions.None) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                } else {
                    pagerState.scrollToPage(pagerState.currentPage + 1)
                }
            }
        } else if (settingState.fastChapterChange && slippedTextList.isNotEmpty()) {
            uiState.loadNextChapter.invoke()
        } else {
            scope.launch {
                showSnackbar(
                    coroutineScope = scope,
                    hostState = snackbarHostState,
                    duration = SnackbarDuration.Short,
                    message = context.getString(R.string.reader_last_page),
                    actionLabel = context.getString(R.string.next_chapter)
                ) {
                    if (it == SnackbarResult.ActionPerformed) {
                        onClickNextChapter()
                    }
                }
            }
        }
    }

    LaunchedEffect(
        uiState.readingChapterContent.content,
        textStyle,
        settingState.fontLineHeight.sp,
        settingState.fontSize.sp,
        constraints?.maxHeight,
        constraints?.maxWidth
    ) {
        val key =
            uiState.readingChapterContent.content.hashCode() + settingState.fontLineHeight.sp.value.hashCode() + settingState.fontSize.sp.value.hashCode() + constraints?.maxHeight.hashCode() + constraints?.maxWidth.hashCode()
        if (constraints == null || textStyle == null || key == contentKey) return@LaunchedEffect
        contentKey = key
        slipTextJob?.cancel()
        slipTextJob = scope.launch(Dispatchers.IO) {
            readingPageFistCharOffset = slippedTextList
                .subList(0, uiState.pagerState.currentPage.coerceAtMost(slippedTextList.size))
                .sumOf { it.length }
                .plus(1)
            slippedTextList = slipText(
                textMeasurer = textMeasurer,
                constraints = constraints!!,
                text = uiState.readingChapterContent.content,
                style = textStyle!!.copy(
                    fontSize = settingState.fontSize.sp,
                    fontWeight = FontWeight.W400,
                    lineHeight = (settingState.fontLineHeight + settingState.fontSize).sp
                )
            )
            uiState.updatePageState(PagerState { slippedTextList.size })
        }
    }
    LocalResources.current.displayMetrics.let { displayMetrics ->
        constraints = Constraints(
            maxWidth = displayMetrics
                .widthPixels
                .minus(
                    with(LocalDensity.current) {
                        (paddingValues.calculateStartPadding(LayoutDirection.Ltr) + paddingValues.calculateEndPadding(
                            LayoutDirection.Ltr
                        ))
                            .toPx()
                    }.toInt()
                ),
            maxHeight = displayMetrics
                .heightPixels
                .minus(
                    with(LocalDensity.current) {
                        (paddingValues.calculateTopPadding() + paddingValues.calculateBottomPadding() + 10.dp)
                            .toPx()
                    }.toInt()
                ),
        )
    }
    textStyle = AppTypography.bodyMedium
    AnimatedVisibility(
        uiState.readingChapterContent.isEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Loading()
    }
    AnimatedVisibility(
        !uiState.readingChapterContent.isEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        var volumeJob by remember { mutableStateOf<Job?>(null) }
        val intervalMs = (settingState.volumeKeyContinuousFlipInterval * 1000).toLong()

        HorizontalPager(
            state = uiState.pagerState,
            modifier = modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (!settingState.isUsingVolumeKeyFlip) {
                        false
                    } else if (event.key == Key.VolumeUp || event.key == Key.VolumeDown) {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.repeatCount == 0) {
                                    if (event.key == Key.VolumeUp) lastPage(uiState.pagerState)
                                    else nextPage(uiState.pagerState)

                                    if (intervalMs > 0) {
                                        volumeJob?.cancel()
                                        volumeJob = scope.launch {
                                            while (isActive) {
                                                delay(intervalMs)
                                                if (event.key == Key.VolumeUp) lastPage(uiState.pagerState)
                                                else nextPage(uiState.pagerState)
                                            }
                                        }
                                    }
                                }
                                true
                            }
                            KeyEventType.KeyUp -> {
                                volumeJob?.cancel()
                                volumeJob = null
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .draggable(
                    enabled = settingState.isUsingFlipPage,
                    interactionSource = remember { MutableInteractionSource() },
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState {},
                    onDragStopped = {
                        if (it.absoluteValue > 60) changeIsImmersive.invoke()
                    }
                )
                .pointerInput(
                    settingState.isUsingClickFlipPage,
                    settingState.isUsingFlipPage,
                    settingState.flipAnime,
                    settingState.fastChapterChange
                ) {
                    detectTapGestures(
                        onTap = {
                            if (settingState.isUsingFlipPage && settingState.isUsingClickFlipPage)
                                if (it.x <= context.resources.displayMetrics.widthPixels * 0.425) lastPage(
                                    uiState.pagerState
                                )
                                else nextPage(uiState.pagerState)
                            else changeIsImmersive.invoke()
                        }
                    )
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                if (settingState.enableBackgroundImage && settingState.backgroundImageDisplayMode == MenuOptions.ReaderBgImageDisplayModeOptions.Loop) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = rememberReaderBackgroundPainter(settingState),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }

                BaseContentComponent(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    imageModifier = Modifier.fillMaxSize().padding(paddingValues),
                    text = slippedTextList.getOrNull(it) ?: "",
                    fontSize = settingState.fontSize.sp,
                    fontLineHeight = settingState.fontLineHeight.sp,
                    fontWeight = FontWeight(settingState.fontWeigh.toInt()),
                    fontFamily = rememberReaderFontFamily(settingState),
                    color = readerTextColor(settingState),
                    onZoomImage = onZoomImage
                )
            }
        }
    }
}

fun slipText(
    textMeasurer: TextMeasurer,
    constraints: Constraints,
    text: String,
    style: TextStyle,
): List<String> {
    val resultList: MutableList<String> = mutableListOf()
    text.split("[image]").filter { it.isNotEmpty() }.forEach { single ->
        if (single.trim().startsWith("http://") || single.trim().startsWith("https://"))
            resultList.add(single)
        else {
            textMeasurer
                .measure(
                    text = single,
                    style = style,
                    constraints = constraints
                )
                .getSlipString(single, constraints)
                .let(resultList::addAll)
        }
    }
    return resultList
}

fun TextLayoutResult.getSlipString(text: String, constraints: Constraints): List<String> {
    val result: MutableList<String> = mutableListOf()
    var lastLine = 0
    fun getNotOverflowText(startLine: Int): String {
        fun getNotOverflowLine(): Int {
            val startHeight = getLineTop(startLine)
            fun isLineOverflow(line: Int): Boolean =
                getLineBottom(line) > startHeight + constraints.maxHeight

            var checkLine = getLineForOffset(
                getOffsetForPosition(
                    Offset(
                        constraints.maxWidth.toFloat(),
                        startHeight + constraints.maxHeight
                    )
                )
            )
            while (isLineOverflow(checkLine))
                checkLine--
            return checkLine
        }

        val startTextOffset = getLineStart(startLine)
        lastLine = getNotOverflowLine()
        val endTextOffset = getLineEnd(lastLine)
        lastLine++
        return text.slice(startTextOffset..<endTextOffset)
    }
    while (lastLine != this.lineCount) {
        getNotOverflowText(lastLine).let(result::add)
    }
    return result.filter { it.isNotBlank() }
}