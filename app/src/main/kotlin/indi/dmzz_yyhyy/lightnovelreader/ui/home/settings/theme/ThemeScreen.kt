package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.theme

import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import indi.dmzz_yyhyy.lightnovelreader.R
import indi.dmzz_yyhyy.lightnovelreader.theme.AppTypography
import indi.dmzz_yyhyy.lightnovelreader.ui.LocalAppTheme
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.SettingState
import indi.dmzz_yyhyy.lightnovelreader.ui.book.reader.selectDataFile
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SettingsClickableEntry
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SettingsMenuEntry
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SettingsSliderEntry
import indi.dmzz_yyhyy.lightnovelreader.ui.components.SettingsSwitchEntry
import indi.dmzz_yyhyy.lightnovelreader.ui.home.settings.data.MenuOptions
import indi.dmzz_yyhyy.lightnovelreader.utils.readerTextColor
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderBackgroundPainter
import indi.dmzz_yyhyy.lightnovelreader.utils.rememberReaderFontFamily
import indi.dmzz_yyhyy.lightnovelreader.utils.uriLauncher
import indi.dmzz_yyhyy.lightnovelreader.utils.uriLauncherWithFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileInputStream

@Composable
fun ThemeScreen(
    themeSettingState: SettingState,
    onClickBack: () -> Unit,
    onClickChangeTextColor: () -> Unit,
    onClickChangeBackgroundColor: () -> Unit
) {
    Scaffold(
        topBar = {
            TopBar(onClickBack)
        },
    ) {
        LazyColumn (
            modifier = Modifier.padding(it)
        ) {
            item {
                DarkModeSettings(themeSettingState)
            }
            item {
                ThemeSettingsList(themeSettingState)
            }
            item {
                ReaderThemeSettingsList(themeSettingState, onClickChangeTextColor, onClickChangeBackgroundColor)
            }
        }
    }
}

@Composable
fun DarkModeSettings(
    settingState: SettingState
) {
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        text = stringResource(R.string.theme_settings),
        style = AppTypography.titleSmall,
        fontWeight = FontWeight.W600
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LightThemeSettingsItem(settingState = settingState)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    modifier = Modifier.size(32.dp),
                    selected = settingState.darkModeKey == "Disabled",
                    onClick = { settingState.darkModeKeyUserData.asynchronousSet("Disabled") }
                )
                Text(stringResource(R.string.key_dark_mode_disabled), style = AppTypography.labelMedium)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DarkThemeSettingsItem(settingState = settingState)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    modifier = Modifier.size(32.dp),
                    selected = settingState.darkModeKey == "Enabled",
                    onClick = { settingState.darkModeKeyUserData.asynchronousSet("Enabled") }
                )
                Text(stringResource(R.string.key_dark_mode_enabled), style = AppTypography.labelMedium)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .height(170.dp)
                    .width(110.dp)
            ) {
                val shapeTop = GenericShape { size: Size, _ ->
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height / 2)
                    close()
                }

                val shapeBottom = GenericShape { size: Size, _ ->
                    moveTo(0f, size.height / 2)
                    lineTo(size.width, size.height / 2)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }

                val modifierTop = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = shapeTop
                    }

                val modifierBottom = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = shapeBottom
                    }

                LightThemeSettingsItem(modifier = modifierTop, settingState = settingState)
                DarkThemeSettingsItem(modifier = modifierBottom, settingState = settingState)
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    modifier = Modifier.size(32.dp),
                    selected = settingState.darkModeKey == "FollowSystem",
                    onClick = { settingState.darkModeKeyUserData.asynchronousSet("FollowSystem") }
                )
                Text(stringResource(R.string.key_dark_mode_follow_system), style = AppTypography.labelMedium)
            }
        }
    }

}

@Composable
fun ThemeSettingsList(
    settingState: SettingState,
) {
    Spacer(Modifier.height(14.dp))
    SettingsSwitchEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.format_color_fill_24px),
        title = stringResource(R.string.settings_theme_dynamic_colors),
        description = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            stringResource(R.string.settings_theme_dynamic_colors_desc_unavailable)
        else stringResource(R.string.settings_theme_dynamic_colors_desc),
        checked = settingState.dynamicColorsKey,
        booleanUserData = settingState.dynamicColorsKeyUserData,
        disabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    )
    if (!settingState.dynamicColorsKey) {
        SettingsMenuEntry(
            modifier = Modifier.background(colorScheme.background),
            painter = painterResource(R.drawable.light_mode_24px),
            title = stringResource(R.string.settings_theme_light_theme),
            description = stringResource(R.string.settings_theme_light_theme_desc),
            options = MenuOptions.LightThemeNameOptions,
            selectedOptionKey = settingState.lightThemeName,
            onOptionChange = settingState.lightThemeNameUserData::asynchronousSet
        )
        SettingsMenuEntry(
            modifier = Modifier.background(colorScheme.background),
            painter = painterResource(R.drawable.dark_mode_24px),
            title = stringResource(R.string.settings_theme_dark_theme),
            description = stringResource(R.string.settings_theme_dark_theme_desc),
            options = MenuOptions.DarkThemeNameOptions,
            selectedOptionKey = settingState.darkThemeName,
            onOptionChange = settingState.darkThemeNameUserData::asynchronousSet
        )
    }
}

@Composable
fun ReaderThemeSettingsList(
    settingState: SettingState,
    onClickChangeTextColor: () -> Unit,
    onClickChangeBackgroundColor: () -> Unit
) {
    val context = LocalContext.current
    val customBgIsEmpty = (settingState.backgroundImageUri.toString().isEmpty() && settingState.backgroundDarkImageUri.toString().isEmpty())

    Spacer(Modifier.height(12.dp))
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        text = stringResource(R.string.paper_settings),
        style = AppTypography.titleSmall,
        fontWeight = FontWeight.W600
    )
    SettingsSwitchEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.imagesearch_roller_24px),
        title = stringResource(R.string.settings_theme_bg_image),
        description = stringResource(R.string.settings_theme_bg_image_desc),
        checked = settingState.enableBackgroundImage,
        booleanUserData = settingState.enableBackgroundImageUserData
    )
    if (settingState.enableBackgroundImage) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_theme_bg_image_built_in), style = AppTypography.titleMedium)
                    Text(stringResource(R.string.settings_theme_bg_image_built_in_desc), style = AppTypography.labelMedium, color = colorScheme.secondary)
                }
                Spacer(Modifier.weight(1f))
                RadioButton(
                    selected = settingState.enableBackgroundImage && customBgIsEmpty,
                    onClick = {
                        settingState.enableBackgroundImageUserData.asynchronousSet(true)
                        settingState.backgroundImageUriUserData.asynchronousSet(Uri.EMPTY)
                        settingState.backgroundDarkImageUriUserData.asynchronousSet(Uri.EMPTY)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.settings_theme_bg_image_custom), style = AppTypography.titleMedium)
                }
                Spacer(Modifier.weight(1f))

                val isDark = LocalAppTheme.current.isDark
                val launcher = uriLauncher {
                    CoroutineScope(Dispatchers.IO).launch {
                        val fileName = if (isDark) "readerDarkBackgroundImage" else "readerBackgroundImage"
                        val image = context.filesDir.resolve(fileName).apply {
                            if (exists()) delete()
                            createNewFile()
                        }
                        try {
                            context.contentResolver.openFileDescriptor(it, "r")?.use { parcelFileDescriptor ->
                                FileInputStream(parcelFileDescriptor.fileDescriptor).use { fileInputStream ->
                                    fileInputStream.readBytes()
                                }.let(image::writeBytes)
                            }
                        } catch (e: Exception) {
                            Log.e("ReaderBackground", "failed to load chosen file (${if (isDark) "dark" else "light"})")
                            e.printStackTrace()
                        }
                        if (isDark) {
                            settingState.backgroundDarkImageUriUserData.set(image.toUri())
                        } else {
                            settingState.backgroundImageUriUserData.set(image.toUri())
                        }
                    }
                }

                RadioButton(
                    selected = !customBgIsEmpty,
                    onClick = {
                        selectDataFile(launcher, "image/*")
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            if (
                settingState.backgroundImageUri.toString().isNotBlank()
                || settingState.backgroundDarkImageUri.toString().isNotBlank()
                ) {
                Box(
                    modifier = Modifier
                        .height(240.dp)
                        .widthIn(400.dp)
                ) {
                    val shapeTop = GenericShape { size: Size, _ ->
                        val tiltOffset = size.height * 0.05f
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height / 2 - tiltOffset)
                        lineTo(0f, size.height / 2 + tiltOffset)
                        close()
                    }

                    val shapeBottom = GenericShape { size: Size, _ ->
                        val tiltOffset = size.height * 0.05f
                        moveTo(0f, size.height / 2 + tiltOffset)
                        lineTo(size.width, size.height / 2 - tiltOffset)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }

                    val modifierTop = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            clip = true
                            shape = shapeTop
                        }

                    val modifierBottom = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            clip = true
                            shape = shapeBottom
                        }

                    val (launcher, setIsDarkFlag) = uriLauncherWithFlag { uri, isDark ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val fileName = if (isDark) "readerDarkBackgroundImage" else "readerBackgroundImage"
                            val image = context.filesDir.resolve(fileName).apply {
                                if (exists()) delete()
                                createNewFile()
                            }
                            try {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                                    FileInputStream(parcelFileDescriptor.fileDescriptor).use { fileInputStream ->
                                        fileInputStream.readBytes()
                                    }.let(image::writeBytes)
                                }
                            } catch (e: Exception) {
                                Log.e("ReaderBackground", "failed to load chosen file (${if (isDark) "dark" else "light"})")
                                e.printStackTrace()
                            }
                            if (isDark) {
                                settingState.backgroundDarkImageUriUserData.set(image.toUri())
                            } else {
                                settingState.backgroundImageUriUserData.set(image.toUri())
                            }
                        }
                    }

                    LightBackgroundSettingsItem(modifierTop, settingState = settingState) {
                        settingState.backgroundImageUriUserData.asynchronousSet(Uri.EMPTY)
                        setIsDarkFlag(false)
                        selectDataFile(launcher, "image/*")
                    }

                    DarkBackgroundSettingsItem(modifierBottom, settingState = settingState) {
                        settingState.backgroundDarkImageUriUserData.asynchronousSet(Uri.EMPTY)
                        setIsDarkFlag(true)
                        selectDataFile(launcher, "image/*")
                    }
                }
            }
        }
    }
    if (settingState.enableBackgroundImage) {
        SettingsMenuEntry(
            modifier = Modifier.background(colorScheme.background),
            title = stringResource(R.string.settings_theme_bg_display_mode),
            painter = painterResource(R.drawable.insert_page_break_24px),
            description = stringResource(R.string.settings_theme_bg_display_mode_desc),
            options = MenuOptions.ReaderBgImageDisplayModeOptions,
            selectedOptionKey = settingState.backgroundImageDisplayMode,
            stringUserData = settingState.backgroundImageDisplayModeUserData
        )
    }
    if (!settingState.enableBackgroundImage){
        val onSecondaryContainer = colorScheme.onSecondaryContainer
        val background = colorScheme.background
        SettingsClickableEntry(
            modifier = Modifier.background(colorScheme.background),
            painter = painterResource(R.drawable.colorize_24px),
            title = stringResource(R.string.settings_theme_bg_color),
            description = stringResource(R.string.settings_theme_bg_color_desc),
            onClick = onClickChangeBackgroundColor,
            trailingContent = {
                Canvas(
                    modifier = Modifier.size(44.dp)
                ) {
                    drawCircle(
                        color = onSecondaryContainer,
                        radius = 20.dp.toPx(),
                    )
                    drawCircle(
                        color = background,
                        radius = 17.5.dp.toPx(),
                    )
                    drawCircle(
                        color = if (settingState.backgroundColor.isUnspecified) background else settingState.backgroundColor,
                        radius = 17.5.dp.toPx(),
                    )
                }
            }
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        text = stringResource(R.string.text_settings),
        style = AppTypography.titleSmall,
        fontWeight = FontWeight.W600
    )
    Spacer(Modifier.height(12.dp))
    val onSecondaryContainer = colorScheme.onSecondaryContainer
    val background = colorScheme.background
    val currentColor = readerTextColor(settingState)

    SettingsClickableEntry (
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.palette_24px),
        title = stringResource(R.string.settings_theme_text_color),
        description = stringResource(R.string.settings_theme_text_color_desc),
        onClick = { onClickChangeTextColor() },
        trailingContent = {
            Canvas(
                modifier = Modifier.size(44.dp)
            ) {
                drawCircle(
                    color = onSecondaryContainer,
                    radius = 20.dp.toPx(),
                )
                drawCircle(
                    color = background,
                    radius = 17.5.dp.toPx(),
                )
                drawCircle(
                    color = currentColor,
                    radius = 17.5.dp.toPx(),
                )
            }
        }
    )
    val textMeasurer = rememberTextMeasurer()
    val coroutineScope = rememberCoroutineScope()
    val launcher = uriLauncher {
        CoroutineScope(Dispatchers.IO).launch {
            val font = context.filesDir.resolve("readerTextFont")
                .also {
                    if (it.exists()) {
                        it.delete()
                        it.createNewFile()
                    } else it.createNewFile()
                }
            try {
                context.contentResolver.openFileDescriptor(it, "r")
                    ?.use { parcelFileDescriptor ->
                        FileInputStream(parcelFileDescriptor.fileDescriptor).use { fileInputStream ->
                            fileInputStream.readBytes()
                        }.let(font::writeBytes)
                    }
            } catch (e: Exception) {
                Log.e("ReaderTextFont", "failed to load chosen file")
                e.printStackTrace()
            }
            try {
                textMeasurer
                    .measure(
                        text = "",
                        style = TextStyle(
                            fontFamily = FontFamily(Font(font))
                        )
                    )
            } catch (_: Exception) {
                coroutineScope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.font_file_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            settingState.fontFamilyUriUserData.set(font.toUri())
        }
    }
    SettingsMenuEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.text_fields_24px),
        title = stringResource(R.string.settings_theme_text_font),
        description = stringResource(R.string.settings_theme_text_font_desc),
        options = MenuOptions.SelectText,
        selectedOptionKey = if (settingState.fontFamilyUri.toString()
                .isEmpty()
        ) MenuOptions.SelectText.Default else MenuOptions.SelectText.Customize,
        onOptionChange = {
            when (it) {
                MenuOptions.SelectText.Default -> settingState.fontFamilyUriUserData.asynchronousSet(
                    Uri.EMPTY
                )
                MenuOptions.SelectText.Customize -> selectDataFile(launcher, "*/*")
            }
        }
    )

    BasePageItem(Modifier
        .fillMaxWidth()
        .height(260.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box (
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {

            if (settingState.enableBackgroundImage)
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberReaderBackgroundPainter(settingState),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            else Box(modifier = Modifier.fillMaxSize().background(settingState.backgroundColor))
            Text(
                modifier = Modifier.padding(horizontal = 18.dp),
                text = stringResource(R.string.settings_about_oss),
                fontSize = settingState.fontSize.sp,
                lineHeight = (settingState.fontLineHeight + settingState.fontSize).sp,
                fontWeight = FontWeight(settingState.fontWeigh.toInt()),
                textAlign = TextAlign.Center,
                fontFamily = rememberReaderFontFamily(settingState),
                color = readerTextColor(settingState)
            )
        }
    }
    SettingsSliderEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.format_bold_24px),
        title = stringResource(R.string.settings_theme_text_font_weight),
        unit = "",
        valueRange = 100f..900f,
        value = settingState.fontWeigh,
        valueFormat = { (it / 100).toInt() * 100f },
        floatUserData = settingState.fontWeighUserData
    )
    SettingsSliderEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.format_size_24px),
        title = stringResource(R.string.settings_reader_font_size),
        unit = "sp",
        valueRange = 8f..64f,
        value = settingState.fontSize,
        floatUserData = settingState.fontSizeUserData
    )
    SettingsSliderEntry(
        modifier = Modifier.background(colorScheme.background),
        painter = painterResource(R.drawable.format_line_spacing_24px),
        title = stringResource(R.string.settings_reader_line_spacing),
        unit = "sp",
        valueRange = 0f..32f,
        value = settingState.fontLineHeight,
        floatUserData = settingState.fontLineHeightUserData
    )
}

@Composable
private fun LightThemeSettingsItem(
    modifier: Modifier = Modifier, settingState: SettingState
) {
    MaterialTheme (
        if (settingState.dynamicColorsKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        dynamicLightColorScheme(context = LocalContext.current) else lightColorScheme()
    ) {
        DarkModeSettingItem(modifier)
    }
}

@Composable
private fun DarkThemeSettingsItem(
    modifier: Modifier = Modifier, settingState: SettingState
) {
    MaterialTheme (
        if (settingState.dynamicColorsKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            dynamicDarkColorScheme(context = LocalContext.current) else darkColorScheme()
    ) {
        DarkModeSettingItem(modifier)
    }
}

@Composable
private fun LightBackgroundSettingsItem(
    modifier: Modifier = Modifier,
    settingState: SettingState,
    onClickSelectImage: () -> Unit
) {
    MaterialTheme (
        if (settingState.dynamicColorsKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            dynamicLightColorScheme(context = LocalContext.current) else lightColorScheme()
    ) {
        BackgroundImageSettingItem(modifier, uri = settingState.backgroundImageUri, onClickSelectImage = { onClickSelectImage() })
    }
}

@Composable
private fun DarkBackgroundSettingsItem(
    modifier: Modifier = Modifier,
    settingState: SettingState,
    onClickSelectImage: () -> Unit
) {
    MaterialTheme (
        if (settingState.dynamicColorsKey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            dynamicDarkColorScheme(context = LocalContext.current) else darkColorScheme()
    ) {
        BackgroundImageSettingItem(modifier, uri = settingState.backgroundDarkImageUri, onClickSelectImage = { onClickSelectImage() })
    }
}

@Composable
fun BackgroundImageSettingItem(
    modifier: Modifier,
    uri: Uri,
    onClickSelectImage: () -> Unit?
) {
    BasePageItem(modifier
        .widthIn(max = 400.dp)
        .height(240.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box (
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .fillMaxSize()
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        colors = ButtonDefaults.buttonColors().copy(containerColor = Color(0x22000000)),
                        onClick = { onClickSelectImage() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.library_add_24px),
                            contentDescription = "light_select",
                            tint = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.choose_light_bg), color = colorScheme.onSurface)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        colors = ButtonDefaults.buttonColors().copy(containerColor = Color(0x22FFFFFF)),
                        onClick = { onClickSelectImage() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.library_add_24px),
                            contentDescription = "dark_select",
                            tint = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.choose_dark_bg), color = colorScheme.onSurface)
                    }
                }
            }
        }
    }
    return
}

@Composable
private fun BasePageItem(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = colorScheme.background, // F1XME: incorrect colorScheme
                        shape = RoundedCornerShape(9.dp)
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DarkModeSettingItem(
    modifier: Modifier
) {
    BasePageItem(
        modifier = modifier
            .width(110.dp)
            .height(170.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .width(36.dp)
                        .background(
                            color = colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(8.dp)
                            .background(
                                color = colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(8.dp)
                            .background(
                                color = colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(8.dp)
                            .background(
                                color = colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(8.dp)
                        .background(
                            color = colorScheme.secondaryContainer,
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            color = colorScheme.secondaryContainer,
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(8.dp)
                        .background(
                            color = colorScheme.inversePrimary,
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(8.dp)
                        .background(
                            color = colorScheme.tertiaryContainer,
                            shape = CircleShape
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 5.dp, vertical = 8.dp)
                .height(height = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(18.dp)
                    .background(
                        color = colorScheme.primaryContainer,
                        shape = CircleShape
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onClickBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.settings_theme))
            }
        },
        navigationIcon = {
            IconButton(onClickBack) {
                Icon(
                    painterResource(id = R.drawable.arrow_back_24px),
                    contentDescription = "back"
                )
            }
        },
    )
}