package indi.dmzz_yyhyy.lightnovelreader.ui.home.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.dmzz_yyhyy.lightnovelreader.data.work.ExportDataWork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportUserDataDialogViewModel @Inject constructor(
    private val workManager: WorkManager,
) : ViewModel() {

    private fun buildExportWork(
        uri: Uri,
        exportLocalBookCache: Boolean,
        exportBookshelf: Boolean,
        exportReadingData: Boolean,
        exportSettings: Boolean,
    ): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<ExportDataWork>()
            .setInputData(
                workDataOf(
                    "uri" to uri.toString(),
                    "exportLocalBookCache" to exportLocalBookCache,
                    "exportBookshelf" to exportBookshelf,
                    "exportReadingData" to exportReadingData,
                    "exportSetting" to exportSettings,
                )
            )
            .build()
    }

    fun exportAndSendToFile(
        exportLocalBookCache: Boolean,
        exportBookshelf: Boolean,
        exportReadingData: Boolean,
        exportSettings: Boolean,
        context: Context,
        onFinish: () -> Unit
    ) {
        val file = File(context.cacheDir, "LightNovelReaderData.lnr")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val request = buildExportWork(
            uri = uri,
            exportLocalBookCache = exportLocalBookCache,
            exportBookshelf = exportBookshelf,
            exportReadingData = exportReadingData,
            exportSettings = exportSettings,
        )

        workManager.enqueueUniqueWork(
            uri.toString(),
            ExistingWorkPolicy.REPLACE,
            request
        )

        viewModelScope.launch {
            val workInfo = workManager
                .getWorkInfoByIdFlow(request.id)
                .first { it?.state?.isFinished == true }
            if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                val intent = ShareCompat.IntentBuilder(context)
                    .setType("application/zip")
                    .setSubject("分享文件")
                    .addStream(uri)
                    .setChooserTitle("分享")
                    .intent
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(
                    Intent.createChooser(intent, "Share")
                )
            }
            onFinish()
        }
    }

    fun exportToFile(
        uri: Uri,
        exportLocalBookCache: Boolean,
        exportBookshelf: Boolean,
        exportReadingData: Boolean,
        exportSettings: Boolean,
    ): OneTimeWorkRequest {
        val workRequest = buildExportWork(
            uri = uri,
            exportLocalBookCache = exportLocalBookCache,
            exportBookshelf = exportBookshelf,
            exportReadingData = exportReadingData,
            exportSettings = exportSettings,
        )
        workManager.enqueueUniqueWork(
            uri.toString(),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workRequest
    }
}
