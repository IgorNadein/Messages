package com.afkanerd.deku

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afkanerd.deku.RemoteListeners.RemoteListenerConnectionService
import com.afkanerd.deku.messages.domain.ExternalMessageRoute
import com.afkanerd.deku.messages.domain.ExternalMessageRouteMapper
import com.afkanerd.deku.security.SecureCiphertextSanitizer
import com.afkanerd.deku.messages.ui.MessagesNavHost
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NEW_NOTIFICATION_ACTION
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getNativesLoaded
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.makeE16PhoneNumber
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetTheme
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ComposeNewMessageScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ConversationsScreenNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity(){

    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Fix for three-button nav not properly going edge-to-edge.
            window.isNavigationBarContrastEnforced = false
        }

        lifecycleScope.launch(Dispatchers.IO) {
            // First launch imports Telephony rows asynchronously. Wait for that
            // boundary before hiding secure ciphertext left by older builds that
            // swallowed decryption failures.
            var attemptsRemaining = 240
            while(!getNativesLoaded() && attemptsRemaining-- > 0) delay(250)
            SecureCiphertextSanitizer.sanitize(applicationContext)
        }

        val messageService = (application as MessagesApplication).messageService
        val appSettingsService = (application as MessagesApplication).appSettingsService
        val developerToolsService = (application as MessagesApplication).developerToolsService
        setContent {
            navController = rememberNavController()
            MessagesAppTheme {
                Surface(Modifier.fillMaxSize()) {
                    MessagesNavHost(
                        navController = navController,
                        messageService = messageService,
                        appSettingsService = appSettingsService,
                        developerToolsService = developerToolsService,
                    )
                    LaunchedEffect(Unit) { processIntent(navController) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if(::navController.isInitialized)
            processIntent(navController, intent)
    }

    private fun processIntent(navController: NavController, newIntent: Intent? = null) {
        val intent = newIntent ?: intent
        if(intent.getBooleanExtra(EXTRA_EXTERNAL_ROUTE_CONSUMED, false)) return

        val route = when(intent.action) {
            notificationAction -> ExternalMessageRouteMapper.fromNotification(
                intent.getStringExtra("address")
            )
            Intent.ACTION_SEND -> ExternalMessageRouteMapper.fromSharedText(
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            )
            Intent.ACTION_SENDTO -> ExternalMessageRouteMapper.fromSendTo(
                dataUri = intent.dataString,
                smsBody = intent.getStringExtra("sms_body"),
                sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
            else -> null
        }

        if(intent.action in EXTERNAL_ROUTE_ACTIONS) {
            intent.putExtra(EXTRA_EXTERNAL_ROUTE_CONSUMED, true)
        }
        when(route) {
            is ExternalMessageRoute.RecipientPicker -> navController.navigate(
                ComposeNewMessageScreenNav(text = route.text)
            ) {
                launchSingleTop = true
            }
            is ExternalMessageRoute.Conversation -> navController.navigate(
                ConversationsScreenNav(
                    address = makeE16PhoneNumber(route.address),
                    text = route.text,
                )
            ) {
                launchSingleTop = true
            }
            null -> Unit
        }
    }

    override fun onStart() {
        super.onStart()
        CoroutineScope(Dispatchers.Default).launch {
            startServices()
        }
    }

    override fun onResume() {
        super.onResume()
        AppCompatDelegate.setDefaultNightMode(settingsGetTheme)
    }

    fun startServices() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED) {
            Datastore.getDatastore(applicationContext).remoteListenerDAO().fetchActivated().apply {
                if(this.any { it.activated }) {
                    val intent = Intent(applicationContext,
                        RemoteListenerConnectionService::class.java)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    } catch(e: Exception) {
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_EXTERNAL_ROUTE_CONSUMED =
            "com.afkanerd.deku.extra.EXTERNAL_ROUTE_CONSUMED"
        val notificationAction = Intent().NEW_NOTIFICATION_ACTION
        val EXTERNAL_ROUTE_ACTIONS = setOf(
            notificationAction,
            Intent.ACTION_SEND,
            Intent.ACTION_SENDTO,
        )
    }
}
