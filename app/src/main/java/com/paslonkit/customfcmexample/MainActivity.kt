package com.paslonkit.customfcmexample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.paslonkit.customfcmexample.push.PushMessagingService
import com.paslonkit.customfcmexample.ui.theme.CustomFCMExampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Единственный Activity. Server-integration flow:
 *   1) просит `POST_NOTIFICATIONS` на Android 13+; после ответа
 *      запускает первый init-запрос (чтобы `notification_enabled` в
 *      теле отражал решение пользователя — см. spec §3.7);
 *   2) при тапе по пушу читает `push_send_id` из intent-экстр (в
 *      `onCreate` и `onNewIntent`) и дёргает push-open телеметрию.
 */
class MainActivity : ComponentActivity() {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Отдельный ответ не важен — важно, что он получен и теперь
            // notification_enabled корректен.
            launchColdStart()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePushIntent(intent)
        enableEdgeToEdge()
        kickOffInitAfterPermission()
        setContent {
            CustomFCMExampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "CustomFCMExample",
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    private fun kickOffInitAfterPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            launchColdStart()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchColdStart()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun launchColdStart() {
        val app = application as App
        bgScope.launch { app.initService.onColdStart() }
    }

    private fun handlePushIntent(intent: Intent?) {
        if (intent == null) return
        val pushSendId = intent.getStringExtra(PushMessagingService.EXTRA_PUSH_SEND_ID).orEmpty()
        if (pushSendId.isEmpty()) return
        val app = application as App
        val uuid = app.identity.uuid()
        bgScope.launch { app.telemetry.report(uuid, pushSendId) }
        intent.removeExtra(PushMessagingService.EXTRA_PUSH_SEND_ID)
    }
}

@Composable
private fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
    CustomFCMExampleTheme {
        Greeting("CustomFCMExample")
    }
}
