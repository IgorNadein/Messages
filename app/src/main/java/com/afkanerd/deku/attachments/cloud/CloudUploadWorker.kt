package com.afkanerd.deku.attachments.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.afkanerd.deku.attachments.AttachmentManager
import java.util.concurrent.TimeUnit

class CloudUploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val transferId = inputData.getString(EXTRA_TRANSFER_ID) ?: return Result.failure()
        return if(AttachmentManager.get(applicationContext).resumeCloudUpload(transferId)) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val EXTRA_TRANSFER_ID = "cloud_upload_transfer_id"

        fun enqueue(context: Context, transferId: String) {
            val request = OneTimeWorkRequestBuilder<CloudUploadWorker>()
                .setInputData(Data.Builder().putString(EXTRA_TRANSFER_ID, transferId).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "cloud-upload-$transferId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, transferId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("cloud-upload-$transferId")
        }
    }
}
