package com.afkanerd.smswithoutborders_libsmsmms.data.data.models

import android.content.Context
import android.os.Bundle
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations

/**
 * Transport-facing SMS dispatch contract.
 *
 * This deliberately lives outside the UI package so receivers and workers do not
 * need to construct a ViewModel to send through the existing fail-closed policy.
 */
interface SmsSender {
    fun sendSms(
        context: Context,
        text: String,
        address: String,
        subscriptionId: Long,
        threadId: Int,
        data: ByteArray? = null,
        bundle: Bundle = Bundle(),
        callback: (Conversations?) -> Unit,
    )
}
