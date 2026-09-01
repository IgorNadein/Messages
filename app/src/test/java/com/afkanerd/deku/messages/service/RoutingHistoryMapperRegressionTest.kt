package com.afkanerd.deku.messages.service

import android.provider.Telephony
import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.messages.domain.RoutingHistoryState
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingHistoryMapperRegressionTest {
    @Test
    fun orphanedWorkIsIgnoredWhenItsSystemMessageWasDeleted() {
        assertNull(
            RoutingHistoryMapper.toItem(
                workId = "work-1",
                tags = routeTags(),
                workerState = "RUNNING",
                conversation = null,
            )
        )
    }

    @Test
    fun malformedOrIncompleteWorkTagsAreIgnoredInsteadOfCrashing() {
        assertNull(RoutingHistoryMapper.parseTags(setOf("unrelated", "broken:")))
        assertNull(
            RoutingHistoryMapper.parseTags(
                setOf(RouterHandler.getTagForMessages("42"))
            )
        )
        assertNull(
            RoutingHistoryMapper.parseTags(
                setOf(
                    "${RouterHandler.TAG_GATEWAY_SERVER_MESSAGE_ID}not-a-number",
                    RouterHandler.getTagForGatewayServers(8),
                )
            )
        )
    }

    @Test
    fun twoGatewaysForOneMessageKeepDistinctStableWorkKeys() {
        val conversation = conversation()
        val first = requireNotNull(
            RoutingHistoryMapper.toItem(
                workId = "work-a",
                tags = routeTags(gatewayId = 8),
                workerState = "ENQUEUED",
                conversation = conversation,
            )
        )
        val second = requireNotNull(
            RoutingHistoryMapper.toItem(
                workId = "work-b",
                tags = routeTags(gatewayId = 9),
                workerState = "SUCCEEDED",
                conversation = conversation,
            )
        )

        assertEquals(first.messageId, second.messageId)
        assertNotEquals(first.workId, second.workId)
        assertNotEquals(first.gatewayId, second.gatewayId)
        assertEquals(RoutingHistoryState.SUCCEEDED, second.state)
    }

    @Test
    fun unknownFutureWorkerStateRemainsVisibleAsUnknown() {
        val item = requireNotNull(
            RoutingHistoryMapper.toItem(
                workId = "work-1",
                tags = routeTags(),
                workerState = "PAUSED_BY_OS",
                conversation = conversation(),
            )
        )

        assertEquals(RoutingHistoryState.UNKNOWN, item.state)
    }

    private fun routeTags(messageId: Long = 42, gatewayId: Long = 8) = setOf(
        RouterHandler.getTagForMessages(messageId.toString()),
        RouterHandler.getTagForGatewayServers(gatewayId),
        RouterHandler.TAG_NAME_GATEWAY_SERVER,
    )

    private fun conversation() = Conversations(
        sms = SmsMmsNatives.Sms(
            thread_id = 1,
            address = "+15551234567",
            date = 1_700_000_000_000,
            date_sent = 1_700_000_000_000,
            read = 1,
            status = Telephony.Sms.STATUS_COMPLETE,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = "Forward me",
            sub_id = 1,
        )
    )
}
