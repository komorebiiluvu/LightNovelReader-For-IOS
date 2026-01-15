package indi.dmzz_yyhyy.lightnovelreader.ui.book.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context.BATTERY_SERVICE
import android.os.BatteryManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.data.book.BookVolumes
import indi.dmzz_yyhyy.lightnovelreader.data.book.ChapterContent
import indi.dmzz_yyhyy.lightnovelreader.theme.AppTypography
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.content.ContentComponent
import indi.dmzz_yyhyy.lightnovelreader.ui.components.AnimatedText
import indi.dmzz_yyhyy.lightnovelreader.ui.components.AnimatedTextLine
import indi.dmzz_yyhyy.lightnovelreader.ui.components.LnrSnackbar
import indi.dmzz_yyhyy.lightnovelreader.ui.components.RollingNumber
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.data.MenuOptions
import indi.dmzz_yyhyy.lightnovelreader.utils.LocalSnackbarHost
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderBackgroundPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    readingScreenUiState: ReaderScreenUiState,
    settingState: SettingState,
    onClickBackButton: () -> Unit,
    accumulateReadTime: (Int, Int) -> Unit,
    updateTotalReadingTime: (Int, Int) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit,
    onChangeChapter: (Int) -> Unit,
    onClickThemeSettings: () -> Unit,
    onZoomImage: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var isImmersive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val backBlockMode = settingState.backBlockMode
    var lastBackPressTime: Long by remember { mutableLongStateOf(0) }

    BackHandler {
        when (backBlockMode) {
            MenuOptions.ReaderBackBlockMode.None -> {
                isImmersive = false
                onClickBackButton()
            }
            MenuOptions.ReaderBackBlockMode.DoublePress -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 1500) {
                    onClickBackButton()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_back_press_again),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            MenuOptions.ReaderBackBlockMode.FullyBlocked -> { }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            isImmersive = false
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedVisibility(
                visible = !isImmersive,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                TopBar(
                    onClickBackButton = onClickBackButton,
                    title = readingScreenUiState.contentUiState.readingChapterContent.title,
                    scrollBehavior
                )
            }
        },
        snackbarHost = {
            Box {
                SnackbarHost(LocalSnackbarHost.current) {
                    LnrSnackbar(it, modifier = Modifier.padding(bottom = 56.dp).align(Alignment.TopCenter))
                }
            }
        },
        containerColor = if (settingState.backgroundColor.isUnspecified) colorScheme.background else settingState.backgroundColor
    ) { _ ->
        if (settingState.enableBackgroundImage) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = rememberReaderBackgroundPainter(settingState),
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
        }
        Content(
            isImmersive = isImmersive,
            readingScreenUiState = readingScreenUiState,
            settingState = settingState,
            accumulateReadingTime = accumulateReadTime,
            updateTotalReadingTime = updateTotalReadingTime,
            onClickPrevChapter = onClickPrevChapter,
            onClickNextChapter = onClickNextChapter,
            onChangeChapter = onChangeChapter,
            onChangeIsImmersive = { isImmersive = !isImmersive },
            onClickThemeSettings = onClickThemeSettings,
            onZoomImage = onZoomImage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Content(
    isImmersive: Boolean,
    readingScreenUiState: ReaderScreenUiState,
    settingState: SettingState,
    updateTotalReadingTime: (Int, Int) -> Unit,
    accumulateReadingTime: (Int, Int) -> Unit,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit,
    onChangeChapter: (Int) -> Unit,
    onChangeIsImmersive: () -> Unit,
    onClickThemeSettings: () -> Unit,
    onZoomImage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val settingsBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val chaptersBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isRunning by remember { mutableStateOf(false) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showChapterSelectorBottomSheet by remember { mutableStateOf(false) }
    var totalReadingTime by remember { mutableIntStateOf(0) }
    var selectedVolumeId by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isImmersive) {
        if (!settingState.enableHideStatusBar) return@LaunchedEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        @Suppress("DEPRECATION")
        controller.apply {
            if (isImmersive) {
                if (settingState.batteryIndicatorDisplayMode == "immersed") {
                    var visibility =
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    val navbar =
                        View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    visibility = visibility or navbar
                    window.decorView.systemUiVisibility = visibility
                } else {
                    val visibility =
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    window.decorView.systemUiVisibility = visibility
                }

            } else {
                show(WindowInsetsCompat.Type.systemBars())
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }

    }
    LaunchedEffect(readingScreenUiState.bookVolumes) {
        selectedVolumeId = readingScreenUiState.bookVolumes.volumes.firstOrNull { volume -> volume.chapters.any { it.id == readingScreenUiState.contentUiState.readingChapterContent.id } }?.volumeId ?: -1
    }
    LifecycleResumeEffect(Unit) {
        isRunning = true
        onPauseOrDispose {
            isRunning = false
            if (totalReadingTime <= 60) {
                updateTotalReadingTime(readingScreenUiState.bookId, totalReadingTime)
            } else {
                Log.e("ReaderScreen", "time counter error, time now is $totalReadingTime over 60s")
            }
            totalReadingTime = 0
        }
    }
    LaunchedEffect(settingState.keepScreenOn) {
        if (settingState.keepScreenOn)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    LaunchedEffect(isRunning) {
        while (isRunning) {
            totalReadingTime += 1
            if (totalReadingTime > 60) {
                updateTotalReadingTime(readingScreenUiState.bookId, totalReadingTime)
                totalReadingTime = 0
            }
            delay(1000)
        }
    }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            accumulateReadingTime(readingScreenUiState.bookId, 1)
            delay(1000)
        }
    }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            accumulateReadingTime(readingScreenUiState.bookId, -1)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (totalReadingTime <= 60) {
                updateTotalReadingTime(readingScreenUiState.bookId, totalReadingTime)
            } else {
                Log.e("ReaderScreen", "time counter error, time now is $totalReadingTime over 60s")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isEnableIndicator = settingState.enableTimeIndicator || settingState.enableReadingChapterProgressIndicator || settingState.enableChapterTitleIndicator
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                readingScreenUiState.contentUiState,
                label = "ContentAnimate"
            ) { contentUiState ->
                ContentComponent(
                    uiState = contentUiState,
                    settingState = settingState,
                    paddingValues = if (settingState.autoPadding)
                        with(density) {
                            PaddingValues(
                                top = WindowInsets.safeContent.getTop(density).toDp(),
                                bottom = if (isEnableIndicator) 46.dp else 12.dp,
                                start = 16.dp,
                                end = 16.dp
                            )
                        }
                    else PaddingValues(
                        top = settingState.topPadding.dp,
                        bottom = if (isEnableIndicator) (settingState.bottomPadding + 38).dp else settingState.bottomPadding.dp,
                        start = settingState.leftPadding.dp,
                        end = settingState.rightPadding.dp
                    ),
                    changeIsImmersive = onChangeIsImmersive,
                    onZoomImage = onZoomImage,
                    onClickPrevChapter = onClickPrevChapter,
                    onClickNextChapter = onClickNextChapter
                )

            }
            AnimatedVisibility (
                modifier = Modifier.align(Alignment.BottomCenter),
                visible = isEnableIndicator,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Indicator(
                    Modifier
                        .padding(
                            if (settingState.autoPadding)
                                PaddingValues(
                                    bottom = 8.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                            else PaddingValues(
                                bottom = settingState.bottomPadding.dp,
                                start = settingState.leftPadding.dp,
                                end = settingState.rightPadding.dp
                            )
                        ),
                    enableBatteryIndicator = settingState.batteryIndicatorDisplayMode == "classic",
                    enableTimeIndicator = settingState.enableTimeIndicator,
                    enableChapterTitle = settingState.enableChapterTitleIndicator,
                    chapterTitle = readingScreenUiState.contentUiState.readingChapterContent.title,
                    enableReadingChapterProgressIndicator = settingState.enableReadingChapterProgressIndicator,
                    readingChapterProgress = readingScreenUiState.contentUiState.readingProgress,
                )
            }
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = !isImmersive,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            BottomBar(
                chapterContent = readingScreenUiState.contentUiState.readingChapterContent,
                onClickPrevChapter = onClickPrevChapter,
                onClickNextChapter = onClickNextChapter,
                onClickSettings = { showSettingsBottomSheet = true },
                onClickChapterSelector = { showChapterSelectorBottomSheet = true },
            )
        }
        AnimatedVisibility(visible = showSettingsBottomSheet) {
            SettingsBottomSheet(
                sheetState = settingsBottomSheetState,
                onDismissRequest = {
                    coroutineScope.launch { settingsBottomSheetState.hide() }.invokeOnCompletion {
                        if (!settingsBottomSheetState.isVisible) {
                            showSettingsBottomSheet = false
                        }
                    }
                    showSettingsBottomSheet = false
                },
                settingState = settingState,
                onClickThemeSettings = onClickThemeSettings
            )
        }

        AnimatedVisibility(visible = showChapterSelectorBottomSheet) {
            ChapterSelectorBottomSheet(
                sheetState = chaptersBottomSheetState,
                selectedVolumeId = selectedVolumeId,
                bookVolumes = readingScreenUiState.bookVolumes,
                readingChapterId = readingScreenUiState.contentUiState.readingChapterContent.id,
                onDismissRequest = {
                    coroutineScope.launch { chaptersBottomSheetState.hide() }.invokeOnCompletion {
                        if (!chaptersBottomSheetState.isVisible) {
                            showChapterSelectorBottomSheet = false
                        }
                    }
                    showChapterSelectorBottomSheet = false
                    selectedVolumeId =
                        readingScreenUiState.bookVolumes.volumes.firstOrNull { volume -> volume.chapters.any { it.id == readingScreenUiState.contentUiState.readingChapterContent.id } }?.volumeId
                            ?: -1
                },
                onClickChapter = onChangeChapter,
                onChangeSelectedVolumeId = {
                    selectedVolumeId = it
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onClickBackButton: () -> Unit,
    title: String,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onClickBackButton) {
                Icon(painterResource(id = R.drawable.arrow_back_24px), "back")
            }
        },
        title = {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(title, label = "TitleAnimate") { text ->
                    Text(
                        text = text,
                        style = AppTypography.titleTopBar,
                        fontWeight = FontWeight.W400,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun BottomBar(
    chapterContent: ChapterContent,
    onClickPrevChapter: () -> Unit,
    onClickNextChapter: () -> Unit,
    onClickSettings: () -> Unit,
    onClickChapterSelector: () -> Unit
) {
    BottomAppBar {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onClickPrevChapter,
                enabled = chapterContent.hasPrevChapter()
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "prevChapter"
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                enabled = false,
                onClick = {
                    // TODO 添加至书签
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_bookmark_24px),
                    contentDescription = "mark"
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onClickChapterSelector) {
                Icon(
                    painter = painterResource(id = R.drawable.menu_24px),
                    contentDescription = "menu"
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onClickSettings) {
                Icon(
                    painter = painterResource(R.drawable.outline_settings_24px),
                    contentDescription = "setting"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onClickNextChapter,
                enabled = chapterContent.hasNextChapter()
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward_24px),
                    contentDescription = "nextChapter"
                )
            }

            Spacer(Modifier.width(4.dp))
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSelectorBottomSheet(
    sheetState: SheetState,
    selectedVolumeId: Int,
    bookVolumes: BookVolumes,
    readingChapterId: Int,
    onDismissRequest: () -> Unit,
    onClickChapter: (Int) -> Unit,
    onChangeSelectedVolumeId: (Int) -> Unit
) {
    val lazyColumnState = rememberLazyListState()
    var hasScrolled by remember { mutableStateOf(false) }

    val flatItems = remember(bookVolumes) {
        bookVolumes.volumes.flatMap { volume ->
            listOf(volume to null) + volume.chapters.map { volume to it }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasScrolled) {
            val targetIndex = flatItems.indexOfFirst { (_, chapter) ->
                chapter?.id == readingChapterId
            }
            if (targetIndex >= 0) {
                hasScrolled = true
                lazyColumnState.scrollToItem(targetIndex)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(R.drawable.read_more_24px), contentDescription = null)
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = stringResource(R.string.select_chapter),
                style = AppTypography.titleLarge,
                fontWeight = FontWeight.W600
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(state = lazyColumnState) {
            items(flatItems, key = { (volume, chapter) ->
                chapter?.id ?: "v_${volume.volumeId}"
            }) { (volume, chapter) ->
                if (chapter == null) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                onChangeSelectedVolumeId(
                                    if (selectedVolumeId == volume.volumeId) -1 else volume.volumeId
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = volume.volumeTitle,
                                    fontWeight = FontWeight.W600,
                                    style = AppTypography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        R.string.info_volume_chapters_count,
                                        volume.chapters.size
                                    ),
                                    color = colorScheme.secondary,
                                    style = AppTypography.labelMedium
                                )
                            }
                            Spacer(Modifier.weight(2f))
                            Icon(
                                modifier = Modifier
                                    .scale(0.75f)
                                    .rotate(if (selectedVolumeId == volume.volumeId) -90f else 90f),
                                painter = painterResource(R.drawable.arrow_forward_ios_24px),
                                tint = colorScheme.onSurface,
                                contentDescription = null
                            )
                        }
                    }
                } else {
                    val expanded = selectedVolumeId == volume.volumeId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(durationMillis = 200))
                    ) {
                        if (expanded) {
                            val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(180))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clickable { onClickChapter(chapter.id) }
                                    .padding(horizontal = 22.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
                                    val isSelected = readingChapterId == chapter.id
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.play_arrow_24px),
                                            tint = colorScheme.outline,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = chapter.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = AppTypography.titleSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Indicator(
    modifier: Modifier = Modifier,
    enableBatteryIndicator: Boolean,
    enableTimeIndicator: Boolean,
    enableChapterTitle: Boolean,
    chapterTitle: String,
    enableReadingChapterProgressIndicator: Boolean,
    readingChapterProgress: Float
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (enableBatteryIndicator) {
                val batteryManager = LocalContext.current.getSystemService(BATTERY_SERVICE) as BatteryManager
                val batLevel: Int = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                RollingNumber(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    number = batLevel,
                    style = AppTypography.labelMedium.copy(
                        fontWeight = FontWeight.W500
                    ),
                    color = colorScheme.onSurfaceVariant,
                    length = 3
                )
                Text(
                    text = "%",
                    style = AppTypography.labelMedium,
                    fontWeight = FontWeight.W500,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter =
                        when {
                            (batLevel in 0..15) -> painterResource(R.drawable.battery_android_alert_24px)
                            (batLevel in 16..35) -> painterResource(R.drawable.battery_android_3_24px)
                            (batLevel in 36..65) -> painterResource(R.drawable.battery_android_4_24px)
                            (batLevel in 66..80) -> painterResource(R.drawable.battery_android_5_24px)
                            (batLevel in 81..95) -> painterResource(R.drawable.battery_android_6_24px)
                            (batLevel in 96..100) -> painterResource(R.drawable.battery_android_full_24px)
                            else -> painterResource(R.drawable.battery_android_question_24px)
                        },
                    tint = colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
                Spacer(Modifier.width(14.dp))
            }
            if (enableTimeIndicator) {
                AnimatedText(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = String.format(Locale.US, "%d:%02d", LocalTime.now().hour, LocalTime.now().minute),
                    style = AppTypography.labelMedium.copy(
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.W500,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (enableChapterTitle) {
                AnimatedTextLine(
                    modifier = Modifier.fillMaxWidth(),
                    text = chapterTitle,
                    textAlign = TextAlign.End,
                    style = AppTypography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (enableReadingChapterProgressIndicator) {
                RollingNumber(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    number = (readingChapterProgress * 100).toInt(),
                    style = AppTypography.labelMedium.copy(
                        fontWeight = FontWeight.W500
                    ),
                    color = colorScheme.onSurfaceVariant,
                    length = 3
                )
                Text(
                    text = "%",
                    style = AppTypography.labelMedium,
                    fontWeight = FontWeight.W500,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}