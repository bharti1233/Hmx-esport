package com.hmx.arena

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hmx.arena.core.designsystem.HmxTheme
import com.hmx.arena.core.designsystem.HmxThemeBootstrap
import com.hmx.arena.core.designsystem.HmxThemeMode
import com.hmx.arena.core.designsystem.ThemeViewModel
import com.hmx.arena.data.remote.SessionManager
import com.hmx.arena.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var session: SessionManager
    @Inject lateinit var auth: AuthRepository

    private val themeViewModel: ThemeViewModel by viewModels()
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Match the platform splash to an explicitly selected in-app theme.
        // values-night already handles the System-dark case.
        if (HmxThemeBootstrap.resolveDark(this)) {
            setTheme(R.style.Theme_HMXArena_Splash_Dark)
        }
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until DataStore has resolved SYSTEM/LIGHT/DARK. A
        // persisted Dark choice therefore never paints a white first frame.
        splash.setKeepOnScreenCondition { !themeViewModel.loaded }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        deepLink.value = intent?.dataString
        lifecycleScope.launch { session.bootstrap() }
        setContent {
            val mode by themeViewModel.mode.collectAsStateWithLifecycle()
            HmxTheme(
                mode = mode ?: HmxThemeMode.SYSTEM,
                onModeChange = themeViewModel::setMode,
            ) {
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
