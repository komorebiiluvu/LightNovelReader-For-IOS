package indi.dmzz_yyhyy.lightnovelreader.data.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.DexClassLoader
import indi.dmzz_yyhyy.lightnovelreader.data.userdata.UserDataRepository
import indi.dmzz_yyhyy.lightnovelreader.data.web.WebBookDataSourceManager
import indi.dmzz_yyhyy.lightnovelreader.utils.AnnotationScanner
import indi.dmzz_yyhyy.lightnovelreader.utils.getApkSignatures
import indi.dmzz_yyhyy.lightnovelreader.utils.isSignatureMatch
import io.nightfish.lightnovelreader.api.ApiCompat
import io.nightfish.lightnovelreader.api.PluginContext
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin
import io.nightfish.lightnovelreader.api.userdata.UserDataPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @field:ApplicationContext private val appContext: Context,
    private val webBookDataSourceManager: WebBookDataSourceManager,
    private val pluginInjector: PluginInjector,
    userDataRepository: UserDataRepository
) {
    companion object {
        const val TAG = "PluginManager"
    }

    private val mutableAllPluginMetadataList = mutableListOf<PluginMetadata>()
    val allPluginList: List<PluginMetadata> get() = mutableAllPluginMetadataList
    private val mutableLoadedPluginMap = mutableMapOf<String, LightNovelReaderPlugin>()
    val loadedPluginMap: Map<String, LightNovelReaderPlugin> get() = mutableLoadedPluginMap
    private val enabledPluginsUserData =
        userDataRepository.stringListUserData(UserDataPath.Plugin.EnabledPlugins.path)

    val pluginsDir: File = appContext.dataDir.resolve("plugins")
    val pluginsTempDir: File = appContext.cacheDir.resolve("plugins_tmp")
    fun getPluginDir(name: String): File = pluginsDir.resolve(name)
    fun getPluginDataDir(pluginDir: File) = pluginDir.resolve("data")
    fun getPluginFile(pluginDir: File): File = pluginDir.resolve("plugin")
    fun getPluginAssetDir(pluginDir: File): File = pluginDir.resolve("asset")
    fun getPluginLibsDir(pluginDir: File): File = pluginDir.resolve("libs")
    private fun getPluginInstallLock(pluginDir: File) = pluginDir.resolve("lock")
    private fun getPluginInstallError(pluginDir: File) = pluginDir.resolve("error")
    private fun getPluginMetadataFile(pluginDir: File): File = pluginDir.resolve("metadata.json")

    private fun deletePluginWithoutData(pluginDir: File) {
        pluginDir.listFiles {
            it.name != "data"
        }?.forEach {
            it.deleteRecursively()
        }
    }

    fun initAllPlugin() {
        val enabledPlugins = enabledPluginsUserData.getOrDefault(emptyList())
        val pluginDirs = pluginsDir.listFiles() ?: return
        for (dir in pluginDirs) {
            if (getPluginInstallLock(dir).exists()) continue
            allPluginMetadataList.removeAll {
                it.packageName == dir.name
            }
            val metadata = getPluginMetadataFile(dir).inputStream().use {
                Json.decodeFromString<PluginMetadata>(it.readBytes().toString())
            }.also(allPluginMetadataList::add)
            if (enabledPlugins.contains(dir.name) && ApiCompat.isSupported(metadata.apiVersion)) {
                val pluginMetadataResult = loadPlugin(dir.name)
                if (pluginMetadataResult.isErr) {
                    Log.e(TAG, "failed to load plugin ${dir.name}")
                    pluginMetadataResult.unwrapError().printStackTrace()
                }
            }
        }
    }

    private fun extractAssetFromApk(apk: File, targetDir: File) = runCatching {
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.startsWith("assets/")) {
                    zip.getInputStream(entry).buffered().use { input ->
                        val out = targetDir.resolve(entry.name.removePrefix("assets/"))
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    private fun extractLibFromApk(apk: File, targetDir: File) = runCatching {
        val tempDir = targetDir.resolve("temp").also { it.mkdir() }
        val packageInfo = appContext.packageManager.getPackageArchiveInfo(apk.path, 0)
        packageInfo?.applicationInfo?.let {
            ZipFile(apk.path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (
                        entry.name.startsWith("lib/") &&
                        !entry.isDirectory &&
                        !entry.name.endsWith("libandroidx.graphics.path.so")
                    ) {
                        val out = tempDir.resolve(entry.name.removePrefix("lib/"))
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).buffered().use { input ->
                            out.outputStream().buffered().use { input.copyTo(it) }
                        }
                    }
                }
            }
        }
        val abiList = Build.SUPPORTED_ABIS
        for (abi in abiList.reversed()) {
            val abiDir = tempDir.resolve(abi)
            if (!abiDir.exists()) continue
            abiDir.listFiles()?.forEach { file ->
                val outputFile = targetDir.resolve(file.name)
                outputFile.parentFile?.mkdirs()
                if (!outputFile.exists()) outputFile.createNewFile()
                outputFile.outputStream().buffered().use {
                    file.inputStream().buffered().copyTo(it)
                }
            }
        }
        tempDir.deleteRecursively()
        return@runCatching
    }

    fun installPlugin(
        plugin: File
    ): Flow<InstallState> = flow {
        emit(InstallState.Start.PrasePackageInfo)
        val packageInfo = appContext.packageManager.getPackageArchiveInfo(
            plugin.path,
            PackageManager.GET_PERMISSIONS
        )
        if (packageInfo == null) {
            emit(InstallState.Error(Error("Failed to get package info")))
            return@flow
        }
        val packageName = packageInfo.packageName

        emit(InstallState.Start.Clean)
        val pluginDir = getPluginDir(packageName)
        val lock = getPluginInstallLock(pluginDir)
        if (lock.exists()) {
            deletePluginWithoutData(pluginDir)
        }
        loadedPluginMap[packageName]?.let(LightNovelReaderPlugin::onUnload)

        emit(InstallState.Start.PrasePluginMetadata)
        val pluginMetadataResult = getPluginMetadata(plugin, packageName)
        if (pluginMetadataResult.isErr) {
            emit(InstallState.Error(pluginMetadataResult.unwrapError()))
            return@flow
        }
        val pluginMetadata = pluginMetadataResult.unwrap()

        emit(InstallState.Start.CheckPluginInstallLegality)
        val checkResult = runBlocking {
            val metadataFile = getPluginMetadataFile(pluginDir)
            val currentPluginApk = getPluginFile(pluginDir)
            if (!currentPluginApk.exists()) return@runBlocking Ok(Unit)
            return@runBlocking runCatching {
                metadataFile
                    .inputStream()
                    .use {
                        Json.decodeFromString<PluginMetadata>(it.readBytes().toString())
                    }
            }.andThen { currentPluginMetadata ->
                if (!ApiCompat.isSupported(currentPluginMetadata.apiVersion)) {
                    return@andThen Err(PluginInstallError.PluginNotSupport(currentPluginMetadata.apiVersion))
                }
                if (currentPluginMetadata.version > pluginMetadata.version) {
                    return@andThen Err(PluginInstallError.CurrentPluginVersionTooHighError())
                }
                if (!isSignatureMatch(getApkSignatures(currentPluginApk), getApkSignatures(plugin))) {
                    return@andThen Err(PluginInstallError.PluginSignatureNotMatchError())
                }
                return@andThen Ok(Unit)
            }
        }
        if (checkResult.isErr) {
            emit(InstallState.Error(checkResult.unwrapError()))
            return@flow
        }

        emit(InstallState.Start.WritePluginMetadataToFile)
        deletePluginWithoutData(pluginDir)
        pluginDir.mkdirs()
        lock.createNewFile()
        val writeMetadataResult = runCatching {
            getPluginMetadataFile(pluginDir)
                .outputStream()
                .use {
                    it.write(Json.encodeToString<PluginMetadata>(pluginMetadata).toByteArray())
                }
        }
        if (writeMetadataResult.isErr) {
            emit(InstallState.Error(writeMetadataResult.unwrapError()))
            return@flow
        }

        emit(InstallState.Start.CopyPlugin)
        val extractLibFromApkResult = extractLibFromApk(plugin, getPluginLibsDir(pluginDir))
        if (extractLibFromApkResult.isErr) {
            emit(InstallState.Error(extractLibFromApkResult.unwrapError()))
            return@flow
        }
        val extractAssetFromApkResult = extractAssetFromApk(plugin, getPluginAssetDir(pluginDir))
        if (extractAssetFromApkResult.isErr) {
            emit(InstallState.Error(extractAssetFromApkResult.unwrapError()))
            return@flow
        }
        val copyMainApkResult = runCatching {
            val target = getPluginFile(pluginDir)
            plugin.inputStream().buffered().use { inputStream ->
                target.outputStream().buffered().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        if (copyMainApkResult.isErr) {
            emit(InstallState.Error(copyMainApkResult.unwrapError()))
            return@flow
        }
        lock.delete()
        emit(InstallState.Completed(packageName))
    }

    fun markPluginError(packageName: String, message: String) {
        val pluginDir = getPluginDir(packageName)
            .also { it.mkdirs() }
        val error = getPluginInstallError(pluginDir)
        error.outputStream().buffered().use {
            it.write(message.toByteArray())
        }
    }

    fun loadPlugin(
        pluginPackage: String
    ): Result<PluginMetadata, Throwable> {
        val pluginDir = getPluginDir(pluginPackage)
        val packageInfo = appContext.packageManager.getPackageArchiveInfo(
            getPluginFile(pluginDir).absolutePath,
            PackageManager.GET_PERMISSIONS
        ) ?: return Err(Error("Failed to get package info"))
        val plugin = getPluginFile(pluginDir)
        return runCatching {
            plugin.setReadOnly()
        }.andThen {
            getPluginMetadataAndPluginClass(packageInfo.packageName)
        }.andThen {
            val pluginClazz = it.second
            val pluginContext = PluginContext(
                dataDir = getPluginDataDir(pluginDir),
                pluginFile = plugin,
                assetDir = getPluginAssetDir(pluginDir)
            )
            val instance = pluginInjector.providePlugin(
                pluginClazz,
                pluginContext
            ) ?: return@andThen Err(Error("Failed to get instance of plugin with class"))
            instance.onLoad()

            val classLoader = instance.javaClass.classLoader
            if (classLoader !is DexClassLoader) return@andThen Err(Error("Failed to get DexClassLoader from plugin instance"))
            webBookDataSourceManager.loadWebDataSourcesFromClassLoader(
                classLoader,
                pluginInjector,
                pluginPackage
            )
            return@andThen Ok(it.first)
        }.also {
            if (it.isErr) {
                markPluginError(pluginPackage, it.unwrapError().toString())
            }
        }
    }

    fun deletePlugin(packageName: String) {
        loadedPluginMap[packageName]?.onUnload()
        webBookDataSourceManager.unloadWebDataSourcesFromClassLoader(packageName)
        getPluginDir(packageName).deleteRecursively()
    }

    private fun getPluginMetadata(file: File, packageName: String): Result<PluginMetadata, Throwable> =
        runCatching {
            DexClassLoader(
                file.absolutePath,
                null,
                null,
                this.javaClass.classLoader
            )
        }.andThen {
            AnnotationScanner.findAnnotatedClasses(
                classLoader = it,
                annotationClass = Plugin::class.java,
                scanPackage = packageName
            )
        }.andThen {
            runCatching {
                val plugin = it
                    .first(LightNovelReaderPlugin::class.java::isAssignableFrom)
                    .getAnnotation(Plugin::class.java)
                    ?: return@andThen Err(Error("Failed to get plugin annotation from the plugin class"))
                PluginMetadata.parse(plugin, packageName, getApkSignatures(file)?.isNotEmpty() == true)
            }
        }

    private fun getPluginMetadataAndPluginClass(packageName: String): Result<Pair<PluginMetadata, Class<*>>, Throwable> =
        runCatching {
            val pluginDir = getPluginDir(packageName)
            DexClassLoader(
                getPluginFile(pluginDir).absolutePath,
                null,
                getPluginLibsDir(pluginDir).absolutePath,
                this.javaClass.classLoader
            )
        }.andThen {
            AnnotationScanner.findAnnotatedClasses(
                classLoader = it,
                annotationClass = Plugin::class.java,
                scanPackage = packageName
            )
        }.andThen {
            runCatching {
                val clazz = it.first(LightNovelReaderPlugin::class.java::isAssignableFrom)
                val plugin = clazz.getAnnotation(Plugin::class.java)
                    ?: return@andThen Err(Error("Failed to get plugin annotation from the plugin class"))
                Pair(
                    PluginMetadata.parse(
                        plugin,
                        packageName,
                        getApkSignatures(getPluginFile(getPluginDir(packageName)))?.isNotEmpty() == true
                    ),
                    clazz
                )
            }
        }

    @Composable
    fun PluginContent(packageName: String, paddingValues: PaddingValues) {
        loadedPluginMap[packageName]?.PageContent(paddingValues)
    }

    fun NavGraphBuilder.onBuildNavHost() {
        loadedPluginMap.values.forEach {
            with(it) {
                onBuildNavHost()
            }
        }
    }
}
