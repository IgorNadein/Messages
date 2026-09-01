package com.afkanerd.deku.attachments.reliability

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.afkanerd.deku.attachments.AttachmentManager
import java.util.concurrent.TimeUnit

class AttachmentAckWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(EXTRA_TRANSFER_ID) ?: return Result.failure()
        return runCatching {
            AttachmentManager.get(applicationContext).sendPendingAcknowledgement(id)
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val EXTRA_TRANSFER_ID = "transfer_id"

        fun enqueue(context: Context, transferId: String, delaySeconds: Long = 12) {
            val request = OneTimeWorkRequestBuilder<AttachmentAckWorker>()
                .setInputData(Data.Builder().putString(EXTRA_TRANSFER_ID, transferId).build())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "attachment-ack-$transferId", ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
