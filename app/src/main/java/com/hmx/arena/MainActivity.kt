package com.hmx.arena

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.hmx.arena.core.designsystem.HMXArenaTheme
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var session: SessionManager
    @Inject lateinit var auth: AuthRepository

    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.dataString
        lifecycleScope.launch { session.bootstrap() }
        setContent {
            HMXArenaTheme {
                HmxRoot(
                    session = session,
                    auth = auth,
                    deepLink = deepLink.value,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.dataString
    }
}
