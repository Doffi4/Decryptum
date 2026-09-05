package com.doffi4.doffisecure

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doffi4.doffisecure.dev.RefreshRateController
import com.doffi4.doffisecure.security.AppLocaleManager
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.ui.lock.AppLockViewModel
import com.doffi4.doffisecure.ui.lock.LockScreen
import com.doffi4.doffisecure.ui.lock.LockState
import com.doffi4.doffisecure.ui.navigation.MainScreen
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : FragmentActivity() {

    private val refreshRateController: RefreshRateController by inject()
    private val userSettings: UserSettingsManager by inject()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = UserSettingsManager.getSavedLanguage(newBase)
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase, savedLang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshRateController.attach(this)

        setContent {
            val lockViewModel: AppLockViewModel = koinViewModel()
            val lockState by lockViewModel.lockState.collectAsState()

            // Re-lock after timeout: record activity on Stop, check timeout on Start
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            // Apply or remove screenshot protection based on setting
                            if (lockViewModel.isScreenshotsAllowed()) {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            } else {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                            lockViewModel.checkAndLockIfNeeded()
                        }
                        Lifecycle.Event.ON_STOP -> lockViewModel.touchLastActive()
                        Lifecycle.Event.ON_DESTROY -> lockViewModel.lock()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            DecryptumTheme {
                if (lockState == LockState.Unlocked) {
                    MainScreen()
                } else {
                    LockScreen(viewModel = lockViewModel)
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        refreshRateController.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        refreshRateController.onUserInteraction()
        // FragmentActivity.dispatchKeyEvent is @RestrictTo(LIBRARY_GROUP) in the
        // support library; the app has no Fragments, so calling through to the
        // platform Activity base is safe and matches the documented contract.
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        refreshRateController.detach()
        super.onDestroy()
    }
}

