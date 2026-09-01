package com.afkanerd.deku.attachments.transport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afkanerd.deku.attachments.AttachmentManager

class AttachmentSendWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val transferId = inputData.getString(AttachmentSmsStatusReceiver.EXTRA_TRANSFER_ID)
            ?: return Result.failure()
        return when (val result = AttachmentManager.get(applicationContext).sendNext(transferId)) {
            is BinarySendResult.Dispatched -> Result.success()
            is BinarySendResult.Failed -> Result.retry()
        }
    }
}
