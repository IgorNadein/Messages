# First Compose vertical slice report

Status: the non-destructive first vertical slice is implemented and regression-covered. It proves the UI/presentation/service boundaries with synthetic data; real carrier send/receive and the two-device secure matrix still require an explicitly approved controlled run.

## What changed

- Completed the Compose path from default-SMS onboarding through Contacts/Ready into a populated inbox.
- Exercised a secure conversation with a logical 50,000-message source, bounded paging, dual-SIM selection and send dispatch through `MessageService`.
- Fixed a lifecycle race that erased the Ready step after returning from the contacts dialog.
- Fixed a full-screen Paging spinner that covered already loaded inbox content.
- Added a shared message paging policy: 50-row pages, 100-row initial/prefetch load and a 500-row maximum cache.
- Added a missing accessibility description to the conversation call action.
- Restored confirmed conversation deletion after the final legacy-UI audit exposed that the new inbox lacked it.
- Added TalkBack semantics for the composer and selected dual-SIM control. Text, media and attachment-transfer timeline items now announce direction, content or filename, timestamp and delivery/transfer state; credential forms announce localized show/hide-password actions.
- Reworked the Conversation List presentation against Samsung Messages running on the connected Galaxy: a one-handed viewing zone collapses into a compact control island, conversations share one inset rounded surface, row/avatar/text/divider geometry follows measured device proportions, and the text FAB was replaced by a 56 dp circular action plus a floating Conversations/Contacts navigation surface.
- Reproduced clipped Russian bottom-navigation labels at 200% font scale, added a failing device regression, then made the navigation and FAB placement adapt without changing their normal-size Samsung-reference geometry.

The discovered UI defects were captured by failing regression/instrumentation tests before their implementations were changed.

## Compatibility and retained code

No completed Compose migration was rolled back. The obsolete Kotlin UI under `DefaultSMS/ui` and the vendor library's legacy composables/ViewModels remain removed. The vendor module retains only its serialized navigation route data classes so saved navigation identities remain compatible. Existing receivers, provider import, encrypted Room storage, workers, status callbacks, Double Ratchet/key exchange, attachment transport, gateways and remote-listener backends remain in place because they are behavior and transport infrastructure rather than legacy UI.

Conversation deletion now requires a second explicit confirmation. Regression tests prove that cancellation is non-destructive and repeated confirmation dispatches exactly once. The service preserves the legacy order—delete the local Room thread/conversations first, then delete the Android SMS thread only when the existing “delete from system database” setting is enabled. Device instrumentation used a fake service and did not delete provider or user data.

The active dependency direction is:

`Compose screens -> presentation ViewModels -> MessageService -> persistence/security domain -> SMS/Data-SMS transport`

Compose does not call Room, `SmsManager`, ratchet or attachment packet APIs directly. Secure sends remain fail closed; none of the new UI tests can fall back to plaintext.

## Evidence

- [onboarding](artifacts/one-ui/oneui_vertical_onboarding.png)
- [inbox](artifacts/one-ui/oneui_vertical_inbox.png)
- [secure conversation](artifacts/one-ui/oneui_vertical_secure_conversation.png)

The captures contain synthetic data only.

## Build and regression results

- 169 unit tests: app 136, SMS/MMS 12, Double Ratchet 21; zero failures.
- 18 targeted, non-destructive instrumentation tests passed on Samsung Android 16, including Samsung-reference Conversation List geometry/actions, its 200% font adaptation, new-message draft handoff, confirmed conversation deletion through fake services and primary-chat/credential TalkBack semantics.
- A 50,000-row in-memory Room data set returned only the requested 100 initial rows; the secure Compose slice kept generation/cache bounds at 500/100.
- Debug APK, instrumentation APKs and minified release APK build successfully.
- `lintDebug` completes with the current project baseline of 124 errors and 593 warnings; no issue points to this slice's Conversation List code, semantic tokens, paging policy, lifecycle fix, accessibility semantics or instrumentation tests.

The manual instrumentation route was used because Gradle's connected runner removes the user-data-bearing debug application after execution. The test package was removed afterward.

## Known issues and next gate

- Run approved two-device ordinary SMS send/receive, multipart, dual-SIM, notification reply and background receive.
- Run approved first/subsequent secure messages, request/accept, corrupted state recovery and key-change flows over the carrier.
- Benchmark the 50,000-message case on dedicated low-end hardware; the connected Samsung validates correctness and bounded loading, not low-end frame timing.
- Complete the manual TalkBack traversal, physical tablet/foldable, motion and microphone/voice checks where consent or suitable hardware is required; automated primary-chat semantics now pass.
- Continue the stricter Samsung-reference visual pass with the real Contacts destination, then Conversation, Composer, Search, Settings, Attachments and Security UI; the Conversation List is the first completed screen in this revised direction.

The detailed per-scenario state remains in [REGRESSION_MATRIX.md](REGRESSION_MATRIX.md); the full migration inventory is in [ONE_UI_MIGRATION_REPORT.md](ONE_UI_MIGRATION_REPORT.md).
