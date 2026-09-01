package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Base64
import android.widget.Toast
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import com.afkanerd.lib_smsmms_android.R
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsParser
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsBlockedException
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsDecision
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsPolicyRegistry
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSmsPolicyRegistry
import com.afkanerd.smswithoutborders_libsmsmms.security.SECURE_TRANSPORT_TEXT_EXTRA
import com.afkanerd.smswithoutborders_libsmsmms.security.SECURE_RETRY_TRANSPORT_TEXT_EXTRA
import com.afkanerd.smswithoutborders_libsmsmms.receivers.MmsSentReceiverImpl
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsTextReceivedReceiver
import com.google.gson.GsonBuilder
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Collections


@Throws
fun Context.updateMms(conversation: Conversations) {
    try {
        getDatabase().conversationsDao()?.update(conversation)
    } catch(e: Exception) {
        throw e
    }
}

@Throws
fun Context.updateSms(uri: Uri, conversation: Conversations) {
    try {
        if(settingsGetStoreTelephonyDb)
            updateSmsToLocalDb(uri,conversation)
        getDatabase().conversationsDao()?.update(conversation)
    } catch(e: Exception) {
        throw e
    }
}

fun Context.deleteSmsThreads(threadIds: Array<String>): Int {
    val placeholders = TextUtils.join(",", Collections.nCopies(threadIds.size, "?"))
    val selection = Telephony.TextBasedSmsColumns.THREAD_ID + " in (" + placeholders + ")"
    val deletedSms = contentResolver.delete(
        Telephony.Sms.CONTENT_URI,
        selection,
        threadIds,
    )
    val deletedMms = contentResolver.delete(
        Telephony.Mms.CONTENT_URI,
        selection,
        threadIds,
    )
    return deletedSms + deletedMms
}

@Throws
private fun Context.updateSmsToLocalDb(
    uri: Uri,
    conversations: Conversations
) {
    val contentValues = ContentValues().apply {
        put(Telephony.TextBasedSmsColumns.TYPE, conversations.sms?.type)
        put(Telephony.TextBasedSmsColumns.STATUS, conversations.sms?.status)
        put(Telephony.TextBasedSmsColumns.ERROR_CODE, conversations.sms?.error_code)
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentResolver.update(uri, contentValues, null)
        } else {
            contentResolver.update(uri, contentValues, null, null)
        }
    } catch (e: Exception) {
        throw e
    }
}

@Throws
private fun Context.insertSmsTelephony(
    text: String?,
    sub_id: Long,
    address: String,
    date: Long,
    type: Int,
    read: Int,
): Uri? {
    val contentValues = ContentValues().apply {
        put(Telephony.TextBasedSmsColumns.BODY, text)
        put(Telephony.TextBasedSmsColumns.DATE, date)
        put(Telephony.TextBasedSmsColumns.TYPE, type)
        put(Telephony.TextBasedSmsColumns.ADDRESS, address)
        put(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID, sub_id)
        put(Telephony.TextBasedSmsColumns.READ, read)
        put(Telephony.TextBasedSmsColumns.STATUS, Telephony.TextBasedSmsColumns.STATUS_NONE)
    }

    try {
        return contentResolver.insert( Telephony.Sms.CONTENT_URI, contentValues)
    } catch (e: Exception) {
        throw e
    }
}

@Throws
fun Context.insertMms(conversation: Conversations) {
    try{
        getDatabase().conversationsDao()
            ?.insert(conversation, settingsGetKeepMessagesArchived)
            ?.let { id -> conversation.id = id }

    } catch (e: Exception) {
        throw e
    }
}

@Throws
fun Context.insertSms(
    conversation: Conversations,
    telephonyBody: String? = conversation.sms?.body,
): Uri? {
    var uri: Uri? = null
    if(settingsGetStoreTelephonyDb) {
        try {
            uri = insertSmsTelephony(
                telephonyBody,
                conversation.sms?.sub_id!!,
                conversation.sms?.address!!,
                conversation.sms?.date!!,
                conversation.sms?.type!!,
                conversation.sms?.read!!
            )
        } catch(e: Exception) {
            throw e
        }
    }

    try{
        conversation.sms?._id = if(uri != null)
            getIdFromLocalDb(uri) else System.currentTimeMillis()

        getDatabase().conversationsDao()
            ?.insert(conversation, settingsGetKeepMessagesArchived)
            ?.let { id -> conversation.id = id }

    } catch (e: Exception) {
        throw e
    }
    return uri
}

@SuppressLint("Range")
fun Context.getIdFromLocalDb(uri: Uri): Long? {
    contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    )?.let { cursor ->
        if(cursor.moveToFirst()) {
            val id = cursor.getLong(cursor
                .getColumnIndex(Telephony.Sms._ID))
            return id
        }
        cursor.close()
    }
    return null
}

@Throws
suspend fun Context.sendSms(
    text: String,
    address: String,
    threadId: Int,
    subscriptionId: Long,
    data: ByteArray? = null,
    bundle: Bundle
): Conversations? {
    if(text.isEmpty() && data == null) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@sendSms,
                getString(R.string.text_body_cannot_empty), Toast.LENGTH_LONG).show()
        }
        return null
    }

    val address = makeE16PhoneNumber(address)
    val outbound = when(val decision = OutboundSmsPolicyRegistry.evaluate(
        applicationContext,
        OutboundSms(
            address = address,
            displayText = text,
            transportData = data,
            retryTransportText = bundle.getString(SECURE_RETRY_TRANSPORT_TEXT_EXTRA),
        )
    )) {
        is OutboundSmsDecision.Allow -> decision.message
        is OutboundSmsDecision.Block -> throw OutboundSmsBlockedException(
            decision.reason,
            decision.cause,
        )
    }

    val date = System.currentTimeMillis()

    var conversation: Conversations?

    try {
        conversation = Conversations(sms = SmsMmsNatives.Sms(
            thread_id = threadId,
            address = address,
            date = date,
            date_sent = date,
            read = 1,
            status = Telephony.Sms.STATUS_PENDING,
            type = Telephony.Sms.MESSAGE_TYPE_QUEUED,
            body = if(outbound.transportData == null) outbound.displayText else {
                Base64.encodeToString(outbound.transportData, Base64.NO_WRAP)
            },
            sub_id = subscriptionId,
        ),
            sms_data = outbound.transportData,
            secure_transport_text = outbound.transportText.takeIf {
                outbound.transportData == null && it != outbound.displayText
            },
        )

        insertSms(conversation)?.let { uri ->
            if(outbound.transportData == null &&
                outbound.transportText != outbound.displayText
            ) {
                bundle.putString(SECURE_TRANSPORT_TEXT_EXTRA, outbound.transportText)
            }
            val pendingIntents = getSmsPendingIntents(uri, conversation, bundle)

            sendSms(
                address = address,
                conversation = conversation,
                transportText = outbound.transportText,
                transportData = outbound.transportData,
                uri = uri,
                sentPendingIntent = pendingIntents.first,
                deliveredPendingIntent = if(settingsGetGetDeliveryReports)
                    pendingIntents.second else null,
            )
        }

    } catch (e: Exception) {
        throw e
    }

    return conversation
}

@Throws
private fun Context.sendSms(
    address: String,
    conversation: Conversations,
    transportText: String,
    transportData: ByteArray?,
    uri: Uri,
    sentPendingIntent: PendingIntent?,
    deliveredPendingIntent: PendingIntent?,
) {
    val smsManager = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        getSystemService(SmsManager::class.java)
            .createForSubscriptionId(conversation.sms?.sub_id!!.toInt())
    else SmsManager.getSmsManagerForSubscriptionId(conversation.sms?.sub_id!!.toInt())

    try {
        dispatchSmsToAndroid(
            smsManager,
            address,
            transportText,
            transportData,
            sentPendingIntent,
            deliveredPendingIntent,
        )

    } catch(e: Exception) {
        conversation.sms?.status = Telephony.Sms.STATUS_FAILED
        conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_FAILED
        updateSms(uri, conversation)
        throw e
    }
    conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_OUTBOX
    updateSms(uri, conversation)
}

internal fun dispatchSmsToAndroid(
    smsManager: SmsManager,
    address: String,
    transportText: String,
    transportData: ByteArray?,
    sentPendingIntent: PendingIntent?,
    deliveredPendingIntent: PendingIntent?,
) {
    if(transportData != null) {
        smsManager.sendDataMessage(
            address,
            null,
            DATA_TRANSMISSION_PORT,
            transportData,
            sentPendingIntent,
            deliveredPendingIntent,
        )
        return
    }

    val dividedMessage = smsManager.divideMessage(transportText)
    if(dividedMessage.size < 2) {
        smsManager.sendTextMessage(
            address,
            null,
            transportText,
            sentPendingIntent,
            deliveredPendingIntent,
        )
        return
    }

    val sentPendingIntents = ArrayList<PendingIntent?>()
    val deliveredPendingIntents = ArrayList<PendingIntent?>()
    repeat(dividedMessage.size - 1) {
        sentPendingIntents.add(null)
        deliveredPendingIntents.add(null)
    }
    sentPendingIntents.add(sentPendingIntent)
    deliveredPendingIntents.add(deliveredPendingIntent)
    smsManager.sendMultipartTextMessage(
        address,
        null,
        dividedMessage,
        sentPendingIntents,
        deliveredPendingIntents,
    )
}

private const val DATA_TRANSMISSION_PORT: Short = 8200

private fun Context.getSmsPendingIntents(
    uri: Uri?,
    conversation: Conversations,
    bundle: Bundle
): Pair<PendingIntent, PendingIntent> {
    val sentPendingIntent = PendingIntent.getBroadcast(
        this,
        conversation.id.toInt(),
        Intent().apply {
            setPackage(packageName)
            action = if(conversation.sms_data == null)
                SmsTextReceivedReceiver.SMS_SENT_BROADCAST_INTENT else
                    SmsTextReceivedReceiver.DATA_SENT_BROADCAST_INTENT

            this.putExtra("id", conversation.id)
            this.putExtra("address", conversation.sms?.address)
            this.putExtra("thread_id", conversation.sms?.thread_id)
            this.putExtra("sub_id", conversation.sms?.sub_id)
            this.putExtra("uri", uri?.toString())
            this.putExtras(bundle)
        },
        PendingIntent.FLAG_IMMUTABLE
    )

    val deliveredPendingIntent = PendingIntent.getBroadcast(
        this,
        conversation.id.toInt(),
        Intent().apply {
            setPackage(packageName)
            action = if(conversation.sms_data == null)
                SmsTextReceivedReceiver.SMS_DELIVERED_BROADCAST_INTENT else
                    SmsTextReceivedReceiver.DATA_DELIVERED_BROADCAST_INTENT

            this.putExtra("id", conversation.id)
            this.putExtra("address", conversation.sms?.address)
            this.putExtra("thread_id", conversation.sms?.thread_id)
            this.putExtra("sub_id", conversation.sms?.sub_id)
            this.putExtra("uri", uri?.toString())
        },
        PendingIntent.FLAG_IMMUTABLE
    )

    return Pair(sentPendingIntent, deliveredPendingIntent)
}

@Throws
suspend fun Context.sendMms(
    contentUri: Uri,
    text: String,
    address: String,
    threadId: Int,
    subscriptionId: Long,
    filename: String,
    mimeType: String
): Conversations? = sendMms(
    text = text,
    addresses = listOf(address),
    threadId = threadId,
    subscriptionId = subscriptionId,
    contentUri = contentUri,
    filename = filename,
    mimeType = mimeType,
)

@Throws
suspend fun Context.sendMms(
    text: String,
    addresses: List<String>,
    threadId: Int,
    subscriptionId: Long,
    contentUri: Uri? = null,
    filename: String? = null,
    mimeType: String? = null,
): Conversations? {
    val normalizedAddresses = addresses
        .map(::makeE16PhoneNumber)
        .filter(String::isNotBlank)
        .distinct()
    require(normalizedAddresses.isNotEmpty()) { "At least one MMS recipient is required" }
    require(text.isNotBlank() || contentUri != null) { "MMS body and attachment cannot both be empty" }

    normalizedAddresses.forEach { recipient ->
        when(val decision = OutboundSmsPolicyRegistry.evaluate(
            applicationContext,
            OutboundSms(
                address = recipient,
                displayText = text,
                // A non-null marker makes MMS transport impossible to mistake for
                // an encryptable text SMS in a secure session.
                transportData = byteArrayOf(),
            ),
        )) {
            is OutboundSmsDecision.Allow -> Unit
            is OutboundSmsDecision.Block -> throw OutboundSmsBlockedException(
                decision.reason,
                decision.cause,
            )
        }
    }

    val now = System.currentTimeMillis()
    val storedAddress = normalizedAddresses.joinToString(",")
    val conversation = Conversations(
        sms = SmsMmsNatives.Sms(
            _id = now,
            thread_id = threadId,
            date = now,
            date_sent = 0,
            type = Telephony.Sms.MESSAGE_TYPE_QUEUED,
            status = Telephony.Sms.STATUS_PENDING,
            read = 1,
            sub_id = subscriptionId,
            address = storedAddress,
            body = text,
        ),
        mms = SmsMmsNatives.Mms(
            _id = now,
            thread_id = threadId,
            date = now / 1000L,
            date_sent = 0,
            msg_box = Telephony.Mms.MESSAGE_BOX_OUTBOX,
            read = 1,
            m_type = SmsMmsNatives.MMS_MESSAGE_TYPES_M_SEND_REQ,
            sub_id = subscriptionId,
            text_only = if(contentUri == null) 1 else 0,
        ),
        mms_text = text,
        mms_content_uri = contentUri?.toString(),
        mms_mimetype = mimeType,
        mms_filename = filename,
    ).apply {
        participantAddresses = normalizedAddresses
    }

    // Validate and resize before persisting the queued row. A carrier-limit or
    // content-read failure must not leave a message stuck in QUEUED forever.
    val preparedAttachment = contentUri?.let { uri ->
        prepareMmsAttachment(
            uri = uri,
            mimeType = requireNotNull(mimeType) { "MMS attachment MIME type is required" },
            fileName = filename ?: "attachment",
            subscriptionId = subscriptionId,
            body = text,
        )
    }

    try {
        insertMms(conversation)
        val sendSettings = MmsParser.getSendMessageSettings(
            group = normalizedAddresses.size > 1,
        )
        sendSettings.subscriptionId = subscriptionId.toInt()

        val sendTransaction = Transaction(this, sendSettings)

        val intent = Intent(this, MmsSentReceiverImpl::class.java)
            .apply {
                this.putExtra(
                    MmsSentReceiverImpl.EXTRA_ORIGINAL_RESENT_MESSAGE_ID,
                    conversation.id,
                )
            }
        sendTransaction.setExplicitBroadcastForSentMms(intent)

        val mMessage = Message(text, normalizedAddresses.toTypedArray())
        if(preparedAttachment != null) {
            mMessage.addMedia(
                preparedAttachment.bytes,
                preparedAttachment.mimeType,
                preparedAttachment.fileName,
            )
        }

        try {
            sendTransaction.sendNewMessage(mMessage)
        } catch(e: Exception) {
            conversation.sms?.status = Telephony.Sms.STATUS_FAILED
            conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_FAILED
            updateMms(conversation)
            throw e
        }
        conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_OUTBOX
        updateMms(conversation)

    } catch (e: Exception) {
        throw e
    }
    return conversation
}


suspend fun Context.registerIncomingSms(
    intent: Intent,
    data: Boolean = false,
): Conversations {
    val bundle = intent.extras
    val subscriptionId = bundle!!.getInt("subscription", -1)
    var address: String? = ""
    val bodyBuffer = StringBuilder()
    val dataBuffer = ByteArrayOutputStream()
    var dateSent: Long = 0
    val date = System.currentTimeMillis()
    var status = -1

    for (currentSMS in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
        address = currentSMS.displayOriginatingAddress
        bodyBuffer.append(currentSMS.displayMessageBody)
        dataBuffer.write(currentSMS.userData)
        dateSent = currentSMS.timestampMillis
        status = currentSMS.status
    }
    val transportBody = bodyBuffer.toString()
    val processedBody = if(data) null else InboundSmsPolicyRegistry.evaluate(
        applicationContext,
        InboundSms(address = address!!, transportText = transportBody),
    )

    val conversation = Conversations(
        sms = SmsMmsNatives.Sms(
            body = processedBody?.displayText ?: transportBody,
            sub_id = subscriptionId.toLong(),
            date = date,
            date_sent = dateSent,
            address = address!!,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            status = status,
            thread_id = getThreadId(address),
            read = 0,
        ),
        sms_data = if(data) dataBuffer.toByteArray() else null,
        secure_transport_text = processedBody?.secureTransportText,
    )

    // Keep the wire packet in Android's Telephony provider while exposing only
    // the policy-approved body through the app's encrypted Room database.
    insertSms(conversation, telephonyBody = transportBody)
    return conversation
}

@Throws
fun Context.loadRawThreads() : List<Pair<String, Boolean>>{
    // Some OEM providers (notably MIUI/HyperOS) reject or ignore
    // QUERY_ARG_SQL_GROUP_BY. Query the single cheap column and de-duplicate in
    // memory so discovery works consistently on every Telephony provider.
    val threadIds = linkedSetOf<Pair<String, Boolean>>()

    fun processCursor(cursor: Cursor, isMms: Boolean) {
        while (cursor.moveToNext()) {
            val threadId = cursor.getStringOrNull(0)
            if (!threadId.isNullOrBlank()) {
                threadIds.add(Pair(threadId, isMms))
            }
        }
    }

    contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID),
        null,
        null,
        "${Telephony.Sms.DATE} DESC",
    )?.use { cursor ->
        processCursor(cursor, false)
    } ?: error("SMS provider returned a null cursor")

    contentResolver.query(
        Telephony.Mms.CONTENT_URI,
        arrayOf(Telephony.Mms.THREAD_ID),
        null,
        null,
        "${Telephony.Mms.DATE} DESC",
    )?.use { cursor ->
        processCursor(cursor, true)
    } ?: error("MMS provider returned a null cursor")

    return threadIds.toList()
}

@Throws
fun Context.loadRawSmsMmsDb(threadId: String? = null, isMms: Boolean) : List<Conversations>{
    val conversationsList = arrayListOf<Conversations>()

    val selection = if(threadId != null) "thread_id =?" else threadId
    val selectionArgs = if(threadId != null) arrayOf(threadId) else null

    try {
        if(!isMms) {
            // SMS
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                "date desc"
            )?.let { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val sms = parseRawSmsContents(cursor) ?: continue
                        val rawAddress = sms.address?.trimEnd()
                            ?.takeIf { it.isNotEmpty() } ?: continue
                        sms.address = findRCSPhoneNumbers(rawAddress)?.firstOrNull()
                            ?: rawAddress
                        // Telephony already supplied the authoritative thread_id in
                        // this cursor. Calling getOrCreateThreadId for every imported
                        // SMS turns first launch into thousands of provider round-trips.
                        conversationsList.add(Conversations(sms = sms))
                    } while (cursor.moveToNext())
                }
                cursor.close()
            }
        } else {
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                "date desc"
            )?.let { cursor ->
                if(cursor.moveToFirst()) {
                    do {
                        val conversation = MmsParser.parse(this, cursor)
                        conversation?.sms?.let {
                            conversationsList.add(conversation)
                        }
                    } while(cursor.moveToNext())
                    cursor.close()
                }
            }
        }
    } catch (e: Exception) {
        throw e
    }

    return conversationsList
}

fun Context.exportRawWithColumnGuesses(): String {
    val mmsContents = arrayListOf<SmsMmsNatives.Mms>()
    val mmsAddrContents = arrayListOf<SmsMmsNatives.MmsAddr>()
    val mmsPartsContents = arrayListOf<SmsMmsNatives.MmsPart>()
    val smsContents = arrayListOf<SmsMmsNatives.Sms>()

    val mmsIds = mutableSetOf<Long>()

    // MMS
    contentResolver.query(
        Telephony.Mms.CONTENT_URI,
        null,
        null,
        null,
        null
    )?.let { cursor ->
        if(cursor.moveToFirst()) {
            do {
                mmsContents.add(parseRawMmsContents(cursor).apply {
                    mmsIds.add(this._id)
                })
            } while(cursor.moveToNext())
        }
        cursor.close()
    }

    // MMSAddr
    mmsIds.forEach {
        contentResolver.query(
            "content://mms/${it}/addr".toUri(),
            null,
            null,
            null,
            null
        )?.let { cursor ->
            if(cursor.moveToFirst()) {
                do {
                    mmsAddrContents.add(parseRawMmsAddrContentsParts(cursor))
                } while(cursor.moveToNext())
            }
            cursor.close()
        }
    }

    // MMS/Parts
    contentResolver.query(
        "content://mms/part".toUri(),
        null,
        null,
        null,
        null
    )?.let { cursor ->
        if(cursor.moveToFirst()) {
            do {
                mmsPartsContents.add(parseRawMmsContentsParts(cursor))
            } while(cursor.moveToNext())
        }
    }


    // SMS
    contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        null,
        null,
        null,
        null
    )?.let { cursor ->
        if(cursor.moveToFirst()) {
            do {
                parseRawSmsContents(cursor)?.let { smsContents.add(it) }
            } while(cursor.moveToNext())
        }
        cursor.close()
    }

    val smsMmsContents = SmsMmsNatives.SmsMmsContents(
        mapOf(
            Pair(
                Telephony.Mms.CONTENT_URI.toString(),
                mmsContents
            )
        ),

        mapOf(Pair("content://mms/{_id}/addr", mmsAddrContents)),
        mapOf(Pair("content://mms/part/{_id}", mmsPartsContents)),

        mapOf(
            Pair(
                Telephony.Sms.CONTENT_URI.toString(),
                smsContents
            )
        ),
    )

    val gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()
    return gson.toJson(smsMmsContents)
}

@SuppressLint("Range")
private fun parseRawMmsAddrContentsParts(cursor: Cursor): SmsMmsNatives.MmsAddr {
    val _id: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.Addr._ID))
    val msg_id : String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Addr.MSG_ID))
    val contact_id: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Addr.CONTACT_ID))
    val address: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Addr.ADDRESS))
    val type: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Addr.TYPE))
    val charset: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Addr.CHARSET))
    val sub_id: Long? = cursor.getLongOrNull(cursor
        .getColumnIndex("sub_id"))

    return SmsMmsNatives.MmsAddr(
        _id = _id,
        msg_id = msg_id,
        contact_id = contact_id,
        address = address,
        type = type,
        charset = charset,
        sub_id = sub_id
    )
}

@SuppressLint("Range")
private fun parseRawMmsContentsParts(cursor: Cursor): SmsMmsNatives.MmsPart {
    val _id: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.Part._ID))
    val mid: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Mms.Part.MSG_ID))
    val seq: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.Part.SEQ))
    val ct: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE))
    val name: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.NAME))
    val cid: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.CONTENT_ID))
    val cl: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.CONTENT_ID))
    val text: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.TEXT))
    val sub_id: Long? = cursor.getLongOrNull(cursor
        .getColumnIndex("sub_id"))
    val _data: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part._DATA))
    val chset: Int? = cursor.getIntOrNull(cursor
        .getColumnIndex(Telephony.Mms.Part.CHARSET))

    return SmsMmsNatives.MmsPart(
        _id = _id,
        mid = mid,
        seq = seq,
        ct = ct,
        name = name,
        cid = cid,
        cl = cl,
        text = text,
        sub_id = sub_id,
        _data = _data,
        chset = chset,
    )
}

@SuppressLint("Range")
fun parseRawMmsContents(cursor: Cursor): SmsMmsNatives.Mms {
    val _id: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Mms._ID))
    val thread_id: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.THREAD_ID))
    val date: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Mms.DATE))
    val date_sent: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Mms.DATE_SENT))
    val msg_box: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.MESSAGE_BOX))
    val read: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.READ))
    val m_id: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.MESSAGE_ID))
    val sub: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.SUBJECT))
    val sub_cs: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.SUBJECT_CHARSET))
    val ct_t: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.CONTENT_TYPE))
    val ct_l: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.CONTENT_LOCATION))
    val m_cls: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.MESSAGE_CLASS))
    val m_type: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.MESSAGE_TYPE))
    val v: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.MMS_VERSION))
    val m_size: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.MESSAGE_SIZE))
    val pri: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.PRIORITY))
    val rr: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.READ_REPORT))
    val d_rpt: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.DELIVERY_REPORT))
    val locked: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.LOCKED))
    val sub_id: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID))
    val seen: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.SEEN))
    val creator: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Mms.CREATOR))
    val text_only: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Mms.TEXT_ONLY))

    return SmsMmsNatives.Mms(
        _id = _id,
        thread_id = thread_id,
        date = date,
        date_sent = date_sent,
        msg_box = msg_box,
        read = read,
        m_id = m_id,
        sub = sub,
        sub_cs = sub_cs,
        ct_t = ct_t,
        ct_l = ct_l,
        m_cls = m_cls,
        m_type = m_type,
        v = v,
        m_size = m_size,
        pri = pri,
        rr = rr,
        d_rpt = d_rpt,
        locked = locked,
        sub_id = sub_id,
        seen = seen,
        creator = creator,
        text_only = text_only
    )
}

@SuppressLint("Range")
private fun parseRawSmsContents(cursor: Cursor): SmsMmsNatives.Sms? {
    val body: String = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Sms.BODY)) ?: return null

    val _id: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Sms._ID))
    val thread_id: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.THREAD_ID))
    val address: String? = cursor.getString(cursor
        .getColumnIndex(Telephony.Sms.ADDRESS))
    val date: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Sms.DATE))
    val date_sent: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Sms.DATE_SENT))
    val read: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.READ))
    val status: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.STATUS))
    val type: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.TYPE))
    val locked: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.LOCKED))
    val sub_id: Long = cursor.getLong(cursor
        .getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID))
    val error_code: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.ERROR_CODE))
    // AOSP allows this provider column to be NULL. MIUI/HyperOS commonly leaves
    // it unset for imported or restored messages.
    val creator: String? = cursor.getStringOrNull(cursor
        .getColumnIndex(Telephony.Sms.CREATOR))
    val seen: Int = cursor.getInt(cursor
        .getColumnIndex(Telephony.Sms.SEEN))

    return SmsMmsNatives.Sms(
        _id = _id,
        thread_id = thread_id,
        address = address,
        date = date,
        date_sent = date_sent,
        read = read,
        status = status,
        type = type,
        body = body,
        locked = locked,
        sub_id = sub_id,
        error_code = error_code,
        creator = creator,
        seen = seen
    )
}
