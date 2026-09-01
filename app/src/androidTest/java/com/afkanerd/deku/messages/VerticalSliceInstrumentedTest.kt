package com.afkanerd.deku.messages

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationGroup
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.ConversationThreadAction
import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.ImportProgress
import com.afkanerd.deku.messages.domain.ImportResult
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MessageRecipient
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.SecureSessionActionResult
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.domain.SimSubscription
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.presentation.ConversationViewModel
import com.afkanerd.deku.messages.presentation.InboxViewModel
import com.afkanerd.deku.messages.presentation.NewMessageDestination
import com.afkanerd.deku.messages.presentation.NewMessageViewModel
import com.afkanerd.deku.messages.service.MessagePagingPolicy
import com.afkanerd.deku.messages.ui.ConversationScreen
import com.afkanerd.deku.messages.ui.InboxScreen
import com.afkanerd.deku.messages.ui.NewMessageScreen
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class VerticalSliceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inboxRestoresScrollPositionAfterOpeningConversationAndReturning() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 40,
        )
        val viewModel = InboxViewModel(service)
        val showInbox = mutableStateOf(true)

        composeRule.setContent {
            MessagesAppTheme {
                val stateHolder = rememberSaveableStateHolder()
                if(showInbox.value) {
                    stateHolder.SaveableStateProvider("inbox") {
                        InboxScreen(
                            viewModel = viewModel,
                            onRequestDefaultSmsRole = {},
                            onRequestContacts = {},
                            onConversationClick = { showInbox.value = false },
                            onSearchClick = {},
                            onSettingsClick = {},
                            onNewMessageClick = {},
                        )
                    }
                } else {
                    Button(onClick = { showInbox.value = true }) {
                        Text("Return to inbox")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("oneui-inbox-list").performScrollToIndex(25)
        composeRule.onNodeWithText("$EXISTING_CONTACT 23").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Return to inbox").performClick()

        composeRule.onNodeWithText("$EXISTING_CONTACT 23").assertIsDisplayed()
    }

    @Test
    fun firstRunMovesFromDefaultRoleThroughContactsAndReadyToExistingInbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = false, contactAccess = false)
        val viewModel = InboxViewModel(service)
        var openedThread: Int? = null

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {
                        service.defaultSms = true
                        viewModel.onResume()
                    },
                    onRequestContacts = viewModel::completeContactsPrompt,
                    onConversationClick = { openedThread = it.id },
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.oneui_default_action))
            .assertIsDisplayed()
        saveScreenshot("oneui_vertical_onboarding.png")
        composeRule.onNodeWithText(context.getString(R.string.oneui_default_action)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_contacts_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_not_now)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_ready_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_continue)).performClick()
        composeRule.onNodeWithText(EXISTING_CONTACT).assertIsDisplayed()
        // Finish the finite pane transition while the indeterminate import animation exists.
        composeRule.mainClock.advanceTimeBy(1_000)
        val progressNodes = composeRule.onAllNodes(INDETERMINATE_PROGRESS).fetchSemanticsNodes()
        assertTrue(
            progressNodes.joinToString { "bounds=${it.boundsInRoot}, config=${it.config}" },
            progressNodes.isEmpty(),
        )
        saveScreenshot("oneui_vertical_inbox.png")
        composeRule.onNodeWithText(EXISTING_CONTACT).performClick()

        assertEquals(THREAD_ID, openedThread)
        assertEquals(1, service.importCalls)
    }

    @Test
    fun conversationListUsesLayeredOneUiCompositionAndPreservesPrimaryActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)
        var searchClicks = 0
        var newMessageClicks = 0
        var contactsClicks = 0

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = { searchClicks += 1 },
                    onSettingsClick = {},
                    onNewMessageClick = { newMessageClicks += 1 },
                    onContactsClick = { contactsClicks += 1 },
                )
            }
        }

        val controls = composeRule.onNodeWithTag("oneui-inbox-controls")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val listSurface = composeRule.onNodeWithTag("oneui-inbox-list-surface")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val fab = composeRule.onNodeWithTag("oneui-inbox-compose-fab")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("oneui-inbox-list")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density

        assertTrue("controls=$controls", controls.height / density in 56f..72f)
        assertTrue("surface=$listSurface root=$root", listSurface.left / density in 8f..14f)
        assertTrue("surface=$listSurface root=$root", (root.right - listSurface.right) / density in 8f..14f)
        assertTrue("fab=$fab", fab.width / density in 52f..60f)
        assertTrue("fab=$fab", fab.height / density in 52f..60f)
        assertEquals(root.top, list.top, 1f)
        assertEquals(root.bottom, list.bottom, 1f)
        composeRule.onNodeWithTag("oneui-inbox-top-scrim").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-bottom-scrim").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.oneui_search))
            .performClick()
        composeRule.onNodeWithTag("oneui-inbox-compose-fab").performClick()
        composeRule.onNodeWithTag("oneui-inbox-contacts").performClick()
        assertEquals(1, searchClicks)
        assertEquals(1, newMessageClicks)
        assertEquals(1, contactsClicks)
        composeRule.onNodeWithContentDescription(context.getString(R.string.oneui_more_options))
            .assertIsDisplayed()
    }

    @Test
    fun conversationListShowsExactUnreadSummaryWithoutMarkingMessagesRead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            unreadMessages = 3,
        )
        val viewModel = InboxViewModel(service)
        var openedThread: Int? = null

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = { openedThread = it.id },
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("oneui-inbox-unread-summary").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.oneui_unread_messages, 3, 3)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_view_unread)).performClick()

        assertEquals(THREAD_ID, openedThread)
        assertEquals(emptyList<Pair<Int, ConversationThreadAction>>(), service.threadActions)
    }

    @Test
    fun conversationPopupMenusUseOneUiGeometryAndPreserveActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)
        var settingsClicks = 0

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = { settingsClicks += 1 },
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.oneui_more_options))
            .performClick()
        val overflow = composeRule.onNodeWithTag("oneui-popup-overflow")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("overflow=$overflow", overflow.width / density in 278f..286f)
        assertTrue("overflow=$overflow", overflow.height / density in 152f..168f)
        composeRule.onNodeWithText(context.getString(R.string.settings)).performClick()
        assertEquals(1, settingsClicks)

        composeRule.onNodeWithContentDescription(context.getString(R.string.oneui_filter_folders))
            .performClick()
        val filter = composeRule.onNodeWithTag("oneui-filter-sheet")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("filter=$filter", filter.width / density in 355f..372f)
        assertTrue("filter=$filter", filter.height / density in 390f..460f)
        composeRule.onNodeWithText(context.getString(R.string.oneui_archived)).performClick()
        composeRule.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        composeRule.onNodeWithTag("oneui-inbox-folder-archived").assertIsDisplayed()
    }

    @Test
    fun conversationCategoriesAreUserCreatedFiltersNotStaticLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        assertTrue(
            runCatching {
                composeRule.onNodeWithText(
                    context.getString(R.string.oneui_conversations_category)
                ).fetchSemanticsNode()
            }.isFailure
        )
        composeRule.onNodeWithTag("oneui-inbox-group-add").performClick()
        composeRule.onNodeWithTag("oneui-category-name").performTextInput(TEST_GROUP_NAME)
        composeRule.onNodeWithText(context.getString(R.string.oneui_add_category_action))
            .performClick()
        composeRule.onNodeWithTag("oneui-category-member-$THREAD_ID").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_done))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.createdGroups.singleOrNull()?.name == TEST_GROUP_NAME
        }
        composeRule.onNodeWithTag("oneui-inbox-group-$TEST_GROUP_ID")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.requestedGroupIds.lastOrNull() == TEST_GROUP_ID
        }

        assertEquals(setOf(THREAD_ID), service.createdGroups.single().threadIds)

        composeRule.onNodeWithTag("oneui-inbox-group-$TEST_GROUP_ID")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.oneui_additional_settings))
            .performClick()
        composeRule.onNodeWithTag("oneui-category-manage-$TEST_GROUP_ID").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_delete_all_categories))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.deletedGroupIds.singleOrNull() == TEST_GROUP_ID
        }

        assertTrue(service.threadActions.isEmpty())
        assertTrue(
            runCatching {
                composeRule.onNodeWithTag("oneui-inbox-group-$TEST_GROUP_ID")
                    .fetchSemanticsNode()
            }.isFailure
        )
    }

    @Test
    fun addConversationsFooterAppearsOnlyInsideCategoryAndUpdatesItsMembers() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 3,
            shortCategoryCount = 1,
        )
        service.seedGroups(
            listOf(ConversationGroup(TEST_GROUP_ID, TEST_GROUP_NAME, setOf(THREAD_ID)))
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        assertTrue(
            runCatching {
                composeRule.onNodeWithTag("oneui-add-conversations-to-category")
                    .fetchSemanticsNode()
            }.isFailure
        )

        composeRule.runOnIdle { viewModel.selectGroup(TEST_GROUP_ID) }
        composeRule.onNodeWithTag("oneui-add-conversations-to-category")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("oneui-category-member-$THREAD_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-category-member-${THREAD_ID + 1}").performClick()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.oneui_done
            )
        ).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.updatedGroups.singleOrNull()?.threadIds ==
                setOf(THREAD_ID, THREAD_ID + 1)
        }
        composeRule.runOnIdle { viewModel.selectGroup(null) }
        assertTrue(
            runCatching {
                composeRule.onNodeWithTag("oneui-add-conversations-to-category")
                    .fetchSemanticsNode()
            }.isFailure
        )
    }

    @Test
    fun shortCategoryReturnsToExpandedHeaderAndCannotRetainLongListScroll() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 20,
            shortCategoryCount = 3,
        )
        service.seedGroups(
            listOf(ConversationGroup(TEST_GROUP_ID, TEST_GROUP_NAME, setOf(THREAD_ID)))
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        val expandedTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val collapsedTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(collapsedTop < expandedTop)
        composeRule.onNodeWithTag("oneui-inbox-top-scrim").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-bottom-scrim").assertIsDisplayed()

        composeRule.onNodeWithTag("oneui-inbox-group-$TEST_GROUP_ID").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.requestedGroupIds.lastOrNull() == TEST_GROUP_ID
        }
        composeRule.waitForIdle()
        val shortCategoryTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(expandedTop, shortCategoryTop, 1f)
        composeRule.onNodeWithTag("oneui-inbox-top-scrim").assertIsDisplayed()

        composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val afterRejectedScrollTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(shortCategoryTop, afterRejectedScrollTop, 1f)
    }

    @Test
    fun conversationHeaderStaysPlainAfterPinningUntilAChatMovesUnderIt() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 20,
            unreadMessages = 3,
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("oneui-inbox-category-plain").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-actions-plain").assertIsDisplayed()

        var foundPinnedPlainStage = false
        repeat(24) {
            if(!foundPinnedPlainStage) {
                composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput {
                    swipe(
                        start = Offset(center.x, center.y + 24f),
                        end = Offset(center.x, center.y - 24f),
                        durationMillis = 500,
                    )
                }
                composeRule.waitForIdle()
                val controls = composeRule.onNodeWithTag("oneui-inbox-controls")
                    .fetchSemanticsNode().boundsInRoot
                val isPlain = runCatching {
                    composeRule.onNodeWithTag("oneui-inbox-category-plain")
                        .fetchSemanticsNode()
                }.isSuccess
                if(
                    isPlain &&
                    controls.top <= controls.height
                ) {
                    val firstChat = composeRule.onNodeWithTag("oneui-thread-row-$THREAD_ID")
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue("controls=$controls firstChat=$firstChat", firstChat.top >= controls.bottom - 1f)
                    foundPinnedPlainStage = true
                }
            }
        }
        assertTrue("The pinned, non-capsuled fourth scroll stage was skipped", foundPinnedPlainStage)

        var foundOverlapStage = false
        repeat(8) {
            if(!foundOverlapStage) {
                composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput {
                    swipe(
                        start = Offset(center.x, center.y + 24f),
                        end = Offset(center.x, center.y - 24f),
                        durationMillis = 500,
                    )
                }
                composeRule.waitForIdle()
                foundOverlapStage = runCatching {
                    composeRule.onNodeWithTag("oneui-inbox-category-capsule")
                        .fetchSemanticsNode()
                }.isSuccess
            }
        }
        assertTrue("Capsules did not appear after a chat moved under the controls", foundOverlapStage)
        composeRule.onNodeWithTag("oneui-inbox-actions-capsule").assertIsDisplayed()
        val controls = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot
        val firstChat = composeRule.onNodeWithTag("oneui-thread-row-$THREAD_ID")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("controls=$controls firstChat=$firstChat", firstChat.top < controls.bottom)
    }

    @Test
    fun fastReverseFlingFromCompactHeaderReturnsToFullyExpandedHeader() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 20,
            unreadMessages = 3,
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        val expandedTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        var reachedCompactHeader = false
        repeat(16) {
            if(!reachedCompactHeader) {
                composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput {
                    swipe(
                        start = Offset(center.x, center.y + 24f),
                        end = Offset(center.x, center.y - 24f),
                        durationMillis = 500,
                    )
                }
                composeRule.waitForIdle()
                reachedCompactHeader = runCatching {
                    composeRule.onNodeWithTag("oneui-inbox-category-capsule")
                        .fetchSemanticsNode()
                }.isSuccess
            }
        }
        assertTrue("Compact header was not reached", reachedCompactHeader)

        composeRule.onNodeWithTag("oneui-inbox-list").performTouchInput {
            swipe(
                start = Offset(center.x, center.y - 35f),
                end = Offset(center.x, center.y + 35f),
                durationMillis = 35,
            )
        }
        composeRule.waitForIdle()

        val returnedTop = composeRule.onNodeWithTag("oneui-inbox-controls")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(expandedTop, returnedTop, 1f)
        composeRule.onNodeWithTag("oneui-inbox-unread-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-category-plain").assertIsDisplayed()
    }

    @Test
    fun selectingLastOfSeveralCategoriesKeepsAddActionVisible() {
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val groups = listOf(
            ConversationGroup("group-personal", "Personal", setOf(THREAD_ID)),
            ConversationGroup("group-work", "Work", setOf(THREAD_ID)),
            ConversationGroup("group-travel", "Travel", setOf(THREAD_ID)),
        )
        service.seedGroups(groups)
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("oneui-inbox-group-group-travel")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.requestedGroupIds.lastOrNull() == "group-travel"
        }

        composeRule.onNodeWithTag("oneui-inbox-group-group-travel").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-group-add").assertIsDisplayed()
    }

    @Test
    fun conversationListWidensNavigationForTwoHundredPercentFontScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                MessagesAppTheme {
                    InboxScreen(
                        viewModel = viewModel,
                        onRequestDefaultSmsRole = {},
                        onRequestContacts = {},
                        onConversationClick = {},
                        onSearchClick = {},
                        onSettingsClick = {},
                        onNewMessageClick = {},
                        onContactsClick = {},
                    )
                }
            }
        }

        val navigation = composeRule.onNodeWithTag("oneui-inbox-bottom-navigation")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("navigation=$navigation", navigation.width / density >= 276f)
        assertTrue("navigation=$navigation", navigation.height / density >= 68f)
        composeRule.onNodeWithTag("oneui-inbox-conversations").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-contacts").assertIsDisplayed()

        composeRule.onNodeWithText(EXISTING_CONTACT).performTouchInput { longClick() }
        composeRule.onNodeWithText(
            context.getString(R.string.oneui_notifications_compact)
        ).assertIsDisplayed()
    }

    @Test
    fun conversationLongPressEntersContextualSelectionAndPreservesThreadActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithText(EXISTING_CONTACT).performTouchInput { longClick() }
        composeRule.onNodeWithTag("oneui-inbox-selection-header").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-selection-actions").assertIsDisplayed()
        val selectedRow = composeRule.onNodeWithTag("oneui-inbox-selected-$THREAD_ID")
            .assertIsDisplayed()
        val selectedRowBitmap = selectedRow.captureToImage().asAndroidBitmap()
        assertNotEquals(
            "Selected first row must retain the rounded list-surface corner",
            selectedRowBitmap.getPixel(0, 0),
            selectedRowBitmap.getPixel(selectedRowBitmap.width / 2, 0),
        )
        assertEquals(emptyList<Pair<Int, ConversationThreadAction>>(), service.threadActions)

        composeRule.onNodeWithTag("oneui-inbox-selection-more").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_pin)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_archive)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.oneui_pin)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { service.threadActions.size == 1 }
        assertEquals(
            listOf(THREAD_ID to ConversationThreadAction.PIN),
            service.threadActions,
        )
    }

    @Test
    fun conversationSelectionKeepsTwoThreadsAndAppliesBatchAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 3,
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("oneui-thread-row-$THREAD_ID")
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("oneui-thread-row-${THREAD_ID + 1}").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.oneui_selection_count, 2)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-selected-$THREAD_ID").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-inbox-selected-${THREAD_ID + 1}").assertIsDisplayed()

        composeRule.onNodeWithTag("oneui-inbox-selection-more").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_pin)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { service.threadActions.size == 2 }

        assertEquals(
            listOf(
                THREAD_ID to ConversationThreadAction.PIN,
                (THREAD_ID + 1) to ConversationThreadAction.PIN,
            ),
            service.threadActions,
        )
    }

    @Test
    fun inboxCanReachLastConversationWithoutJumpingBackToTop() {
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            conversationCount = 40,
        )
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("oneui-inbox-list").performScrollToIndex(41)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("$EXISTING_CONTACT 39").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("$EXISTING_CONTACT 39").assertIsDisplayed()
    }

    @Test
    fun secureConversationWithFiftyThousandMessagesRendersAndSendsThroughService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = ConversationViewModel(
            messageService = service,
            address = ADDRESS,
            initialThreadId = THREAD_ID,
        )

        composeRule.setContent {
            MessagesAppTheme {
                ConversationScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onCall = {},
                    onMore = {},
                    onOpenMedia = {},
                )
            }
        }

        composeRule.onNodeWithText("message-50000").assertIsDisplayed()
        saveScreenshot("oneui_vertical_secure_conversation.png")
        composeRule.onNode(hasSetTextAction()).performTextInput("secure vertical reply")
        composeRule.onNodeWithContentDescription(context.getString(R.string.oneui_send))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            service.sentText == "secure vertical reply"
        }

        assertEquals(SELECTED_SIM, service.sentSubscriptionId)
        assertTrue(service.generatedTimelineItems <= MessagePagingPolicy.MAX_CACHED_ITEMS)
        assertTrue(service.maximumRequestedLoad <= MessagePagingPolicy.INITIAL_LOAD_SIZE)
    }

    @Test
    fun conversationExposesComposerDualSimAndMessageStateToTalkBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(
            defaultSms = true,
            contactAccess = true,
            dualSim = true,
        )
        val viewModel = ConversationViewModel(
            messageService = service,
            address = ADDRESS,
            initialThreadId = THREAD_ID,
        )

        composeRule.setContent {
            MessagesAppTheme {
                ConversationScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onCall = {},
                    onMore = {},
                    onOpenMedia = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.oneui_message_hint)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.attachment_photo)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.attachment_camera)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.choose_sim_card)
        ).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "SIM 1")
        ).assertIsDisplayed()
        val messageNode = composeRule.onNodeWithContentDescription(
            "message-50000",
            substring = true,
        )
        messageNode.assertIsDisplayed()
        val spokenMessage = messageNode.fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString()
        assertTrue(spokenMessage.contains(context.getString(R.string.oneui_received)))

        val mediaNode = composeRule.onNodeWithContentDescription(
            ACCESSIBLE_MEDIA_FILE,
            substring = true,
        )
        mediaNode.assertIsDisplayed()
        val spokenMedia = mediaNode.fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString()
        assertTrue(spokenMedia.contains(context.getString(R.string.oneui_received)))

        val transferNode = composeRule.onNodeWithContentDescription(
            ACCESSIBLE_TRANSFER_FILE,
            substring = true,
        )
        transferNode.assertIsDisplayed()
        val spokenTransfer = transferNode.fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString()
        assertTrue(spokenTransfer.contains(context.getString(R.string.oneui_received)))
        assertTrue(spokenTransfer.contains(context.getString(R.string.attachment_status_receiving)))
    }

    @Test
    fun newConversationSelectsRecipientAndCreatesThreadOnlyOnSend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = NewMessageViewModel(
            messageService = service,
            initialText = "shared vertical draft",
            initialSubscriptionId = null,
        )
        var destination: NewMessageDestination? = null

        composeRule.setContent {
            MessagesAppTheme {
                NewMessageScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onOpenConversation = { destination = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.compose_new_message_title))
            .fetchSemanticsNode()
        composeRule.onNodeWithTag("oneui-new-conversation").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-recipient-field").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-new-message-composer").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-new-message-input").assertIsNotEnabled()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.isLoading
        }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.oneui_select_recipient)
        ).performClick()
        composeRule.onNodeWithTag("oneui-recipient-picker").assertIsDisplayed()
        val pickerSearch = composeRule.onNodeWithTag("oneui-recipient-picker-search")
        pickerSearch.assertIsDisplayed()
        pickerSearch.performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.oneui_find_contact_or_enter_number)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(EXISTING_CONTACT).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.oneui_selected_recipients_count, 1)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-selected-recipient").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_done)).performClick()
        composeRule.onNodeWithTag("oneui-new-conversation").assertIsDisplayed()
        composeRule.onNodeWithText(EXISTING_CONTACT).assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-new-message-input").assertIsEnabled()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.attachment_photo)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.attachment_camera)
        ).assertIsDisplayed()
        assertEquals(null, destination)
        assertEquals(0, service.sendCalls)

        composeRule.onNodeWithTag("oneui-create-conversation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { destination != null }

        assertEquals(ADDRESS, destination?.address)
        assertEquals(THREAD_ID, destination?.threadId)
        assertEquals(null, destination?.preservedText)
        assertEquals(null, service.savedDraftText)
        assertEquals(1, service.sendCalls)
    }

    @Test
    fun newConversationKeepsMultipleRecipientsAndUsesOneGroupMms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = NewMessageViewModel(service, "group body", SELECTED_SIM)
        var destination: NewMessageDestination? = null

        composeRule.setContent {
            MessagesAppTheme {
                NewMessageScreen(viewModel, {}, { destination = it })
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !viewModel.state.value.isLoading }
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.oneui_select_recipient)
        ).performClick()
        composeRule.onNodeWithText(EXISTING_CONTACT).performClick()
        composeRule.onNodeWithText(SECOND_CONTACT).performClick()
        composeRule.onNodeWithTag("oneui-selected-recipient").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-selected-recipient-2").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_done)).performClick()

        composeRule.onNodeWithText(
            context.getString(R.string.oneui_recipient_summary, EXISTING_CONTACT, 1)
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.oneui_selected_recipients_count, 2)
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-selected-recipient").assertIsDisplayed()
        composeRule.onNodeWithTag("oneui-selected-recipient-2").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_done)).performClick()
        composeRule.onNodeWithTag("oneui-create-conversation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { destination != null }

        assertEquals(1, service.mmsSendCalls)
        assertEquals(0, service.sendCalls)
        assertEquals(listOf(ADDRESS, SECOND_ADDRESS), service.mmsRecipients)
        assertEquals(SELECTED_SIM, service.sentSubscriptionId)
    }

    @Test
    fun conversationDeletionRequiresConfirmationAndCancelIsNonDestructive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VerticalSliceService(defaultSms = true, contactAccess = true)
        val viewModel = InboxViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                InboxScreen(
                    viewModel = viewModel,
                    onRequestDefaultSmsRole = {},
                    onRequestContacts = {},
                    onConversationClick = {},
                    onSearchClick = {},
                    onSettingsClick = {},
                    onNewMessageClick = {},
                )
            }
        }

        composeRule.onNodeWithText(EXISTING_CONTACT).performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.message_threads_menu_delete))
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.messages_thread_delete_confirmation_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.messages_thread_delete_confirmation_cancel)
        ).performClick()
        assertEquals(emptyList<Pair<Int, ConversationThreadAction>>(), service.threadActions)

        composeRule.onNodeWithText(EXISTING_CONTACT).performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.message_threads_menu_delete))
            .performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.messages_thread_delete_confirmation_yes)
        ).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { service.threadActions.size == 1 }

        assertEquals(
            listOf(THREAD_ID to ConversationThreadAction.DELETE),
            service.threadActions,
        )
    }

    private fun saveScreenshot(fileName: String) {
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(requireNotNull(context.externalCacheDir), fileName)
        output.outputStream().use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream,
            )
        }
    }

    private class VerticalSliceService(
        var defaultSms: Boolean,
        private val contactAccess: Boolean,
        private val dualSim: Boolean = false,
        private val unreadMessages: Int = 0,
        private val conversationCount: Int = 1,
        private val shortCategoryCount: Int = 1,
    ) : MessageService {
        var promptCompleted = false
        var importCalls = 0
        var messageStoreReady = false
        var sentText: String? = null
        var sentSubscriptionId: Long? = null
        var savedDraftText: String? = null
        var sendCalls = 0
        var mmsSendCalls = 0
        var mmsRecipients: List<String>? = null
        var generatedTimelineItems = 0
        var maximumRequestedLoad = 0
        val threadActions = mutableListOf<Pair<Int, ConversationThreadAction>>()
        val createdGroups = mutableListOf<ConversationGroup>()
        val updatedGroups = mutableListOf<ConversationGroup>()
        val deletedGroupIds = mutableListOf<String>()
        val requestedGroupIds = mutableListOf<String?>()
        private val groups = MutableStateFlow<List<ConversationGroup>>(emptyList())

        fun seedGroups(value: List<ConversationGroup>) {
            groups.value = value
        }

        override fun isDefaultSmsApp() = defaultSms
        override fun hasContactAccess() = contactAccess
        override fun isContactPromptCompleted() = promptCompleted
        override fun completeContactPrompt() {
            promptCompleted = true
        }
        override fun isMessageStoreReady() = messageStoreReady

        override fun conversationThreads(): Flow<PagingData<ConversationThread>> =
            conversationThreads(com.afkanerd.deku.messages.domain.ConversationFolder.INBOX)

        override fun conversationThreads(
            folder: com.afkanerd.deku.messages.domain.ConversationFolder,
        ): Flow<PagingData<ConversationThread>> = flowOf(
            PagingData.from(
                List(conversationCount) { index ->
                    ConversationThread(
                        id = THREAD_ID + index,
                        address = if(index == 0) ADDRESS else "$ADDRESS-$index",
                        displayName = if(index == 0) EXISTING_CONTACT else "$EXISTING_CONTACT $index",
                        avatarUri = null,
                        snippet = "existing SMS $index",
                        timestampMillis = 1_725_000_000_000 - index,
                        unreadCount = 1,
                        isPinned = false,
                        isMuted = false,
                    )
                }
            )
        )

        override fun conversationThreads(
            folder: com.afkanerd.deku.messages.domain.ConversationFolder,
            groupId: String?,
        ): Flow<PagingData<ConversationThread>> {
            requestedGroupIds += groupId
            return if(groupId == null) conversationThreads(folder) else flowOf(
                PagingData.from(
                    List(shortCategoryCount) { index ->
                        ConversationThread(
                            id = THREAD_ID + index,
                            address = if(index == 0) ADDRESS else "$ADDRESS-$index",
                            displayName = if(index == 0) EXISTING_CONTACT
                                else "$EXISTING_CONTACT $index",
                            avatarUri = null,
                            snippet = "existing SMS $index",
                            timestampMillis = 1_725_000_000_000 - index,
                            unreadCount = 1,
                            isPinned = false,
                            isMuted = false,
                        )
                    }
                )
            )
        }

        override fun conversationGroups(): Flow<List<ConversationGroup>> = groups

        override suspend fun createConversationGroup(
            name: String,
            threadIds: Set<Int>,
        ): String {
            val group = ConversationGroup(TEST_GROUP_ID, name, threadIds)
            createdGroups += group
            groups.value = listOf(group)
            return group.id
        }

        override suspend fun deleteConversationGroup(id: String): Boolean {
            deletedGroupIds += id
            groups.value = groups.value.filterNot { it.id == id }
            return true
        }

        override suspend fun updateConversationGroup(
            id: String,
            name: String,
            threadIds: Set<Int>,
        ): Boolean {
            val existing = groups.value.firstOrNull { it.id == id } ?: return false
            val updated = existing.copy(name = name, threadIds = threadIds)
            updatedGroups += updated
            groups.value = groups.value.map { group ->
                if(group.id == id) updated else group
            }
            return true
        }

        override fun unreadMessageCount(): Flow<Int> = flowOf(unreadMessages)

        override suspend fun updateConversationThread(
            threadId: Int,
            action: ConversationThreadAction,
        ): Boolean {
            threadActions += threadId to action
            return true
        }

        override fun timeline(threadId: Int): Flow<PagingData<TimelineItem>> = Pager(
            config = MessagePagingPolicy.config(),
            pagingSourceFactory = {
                object : PagingSource<Int, TimelineItem>() {
                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TimelineItem> {
                        val start = params.key ?: 0
                        val end = (start + params.loadSize).coerceAtMost(TOTAL_MESSAGES)
                        maximumRequestedLoad = maxOf(maximumRequestedLoad, params.loadSize)
                        val data = (start until end).map { offset ->
                            val id = TOTAL_MESSAGES - offset
                            if(dualSim && id == TOTAL_MESSAGES - 1) {
                                TimelineItem.Media(
                                    stableId = "synthetic-media",
                                    timestampMillis = id.toLong(),
                                    uri = "content://fake/media",
                                    fileName = ACCESSIBLE_MEDIA_FILE,
                                    mimeType = "image/jpeg",
                                    caption = "accessible media caption",
                                    direction = MessageDirection.INCOMING,
                                    deliveryState = DeliveryState.RECEIVED,
                                )
                            } else {
                                TimelineItem.Text(
                                    stableId = "synthetic-$id",
                                    timestampMillis = id.toLong(),
                                    text = "message-$id",
                                    direction = MessageDirection.INCOMING,
                                    deliveryState = DeliveryState.RECEIVED,
                                    isSecure = true,
                                )
                            }
                        }
                        generatedTimelineItems += data.size
                        return LoadResult.Page(
                            data = data,
                            prevKey = null,
                            nextKey = end.takeIf { it < TOTAL_MESSAGES },
                        )
                    }

                    override fun getRefreshKey(state: PagingState<Int, TimelineItem>): Int? = 0
                }
            },
        ).flow

        override fun attachmentTransfers(address: String): Flow<List<AttachmentTransfer>> =
            flowOf(
                if(!dualSim) emptyList() else listOf(
                    AttachmentTransfer(
                        stableId = "synthetic-transfer",
                        timestampMillis = (TOTAL_MESSAGES - 2).toLong(),
                        direction = MessageDirection.INCOMING,
                        kind = AttachmentKind.FILE,
                        fileName = ACCESSIBLE_TRANSFER_FILE,
                        mimeType = "application/pdf",
                        encodedBytes = 4096L,
                        completedSms = 2,
                        totalSms = 8,
                        state = AttachmentTransferState.RECEIVING,
                        completedPath = null,
                        durationMillis = 0L,
                        hasError = false,
                    )
                )
            )
        override suspend fun performAttachmentAction(
            transferId: String,
            action: AttachmentAction,
        ) = Unit
        override suspend fun prepareAttachment(
            address: String,
            subscriptionId: Long,
            attachment: PreparedAttachment,
        ) = AttachmentPrepareResult.Queued
        override suspend fun importIfNeeded(
            onProgress: suspend (ImportProgress) -> Unit,
        ): ImportResult {
            importCalls++
            messageStoreReady = true
            return ImportResult.AlreadyReady
        }

        override suspend fun conversationHeader(address: String, threadId: Int?) =
            ConversationHeader(
                threadId = THREAD_ID,
                address = address,
                displayName = EXISTING_CONTACT,
                avatarUri = null,
                subscriptionId = SELECTED_SIM,
                subscriptions = buildList {
                    add(SimSubscription(SELECTED_SIM, "SIM 1", 0))
                    if(dualSim) add(SimSubscription(SECOND_SIM, "SIM 2", 1))
                },
                securityState = ConversationSecurityState.SECURE_VERIFIED,
            )
        override suspend fun conversationHeader(
            addresses: List<String>,
            threadId: Int?,
        ) = conversationHeader(addresses.joinToString(","), threadId).copy(
            securityState = ConversationSecurityState.PLAIN,
        )
        override suspend fun loadDraft(threadId: Int) = ""
        override suspend fun searchRecipients(query: String) = listOf(
            MessageRecipient(
                id = 1L,
                address = ADDRESS,
                displayName = EXISTING_CONTACT,
                avatarUri = null,
            ),
            MessageRecipient(
                id = 2L,
                address = SECOND_ADDRESS,
                displayName = SECOND_CONTACT,
                avatarUri = null,
            ),
        )
        override suspend fun saveDraft(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ) {
            savedDraftText = text
        }
        override suspend fun selectSubscription(address: String, subscriptionId: Long) = Unit
        override suspend fun sendText(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            sendCalls++
            sentText = text
            sentSubscriptionId = subscriptionId
            return SendResult.Sent(1)
        }
        override suspend fun sendMms(
            addresses: List<String>,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            mmsSendCalls++
            mmsRecipients = addresses
            sentText = text
            sentSubscriptionId = subscriptionId
            return SendResult.Sent(2)
        }
        override suspend fun requestOrRepairSecureSession(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            forceRenewal: Boolean,
        ) = SecureSessionActionResult.RequestSent
        override suspend fun securityFingerprint(address: String) = "00001 00002"
        override suspend fun acceptChangedIdentityAndRepair(
            address: String,
            threadId: Int,
            subscriptionId: Long,
        ) = SecureSessionActionResult.RequestSent
    }

    private companion object {
        const val THREAD_ID = 77
        const val ADDRESS = "+15550000000"
        const val SECOND_ADDRESS = "+15550000001"
        const val EXISTING_CONTACT = "Existing contact"
        const val SECOND_CONTACT = "Second contact"
        const val TEST_GROUP_NAME = "Family"
        const val TEST_GROUP_ID = "group-family"
        const val SELECTED_SIM = 22L
        const val SECOND_SIM = 23L
        const val TOTAL_MESSAGES = 50_000
        const val ACCESSIBLE_MEDIA_FILE = "a11y-photo.jpg"
        const val ACCESSIBLE_TRANSFER_FILE = "a11y-file.pdf"
        val INDETERMINATE_PROGRESS = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
    }
}
