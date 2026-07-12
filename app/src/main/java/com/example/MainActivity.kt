package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.network.FirebaseConnectionManager
import com.example.network.FirebaseUpdateManager
import com.example.ui.FinanceViewModel
import com.example.ui.WeeklyFinanceApp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import java.util.Locale

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: FinanceViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    // Initialize translation context holder
    com.example.ui.AppContextHolder.context = applicationContext

    // Standardize JVM default timezone on Asia/Kolkata for perfect multi-device sync and date congruence
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Clean up any obsolete/installed update APK files from local storage on launch
    try {
        com.example.network.FirebaseUpdateManager.cleanupObsoleteApks(this)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Launch APK cleanup failed: ${e.message}", e)
    }

    // Initialize our advanced Firebase Managers
    com.example.network.FirebaseAnalyticsManager.initialize(this)
    com.example.network.FirebaseRemoteConfigManager.initializeAndFetch()
    com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance())
    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

    // ---- FAIL-SAFE PRODUCTION OBSERVABILITY (SILENT BLACK BOX RECORDER) ----
    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val sharedPrefs = getSharedPreferences("weekly_finance_prefs", android.content.Context.MODE_PRIVATE)
        val crashDetails = "Crash at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\n" +
            "Message: ${throwable.message}\n" +
            "Stack Trace:\n${throwable.stackTraceToString()}"
        sharedPrefs.edit().putString("last_fatal_crash_log", crashDetails).apply()
      } catch (e: Exception) {
        e.printStackTrace()
      }

      originalHandler?.uncaughtException(thread, throwable)
    }

    val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> 
        // Foreground Sync Service start removed as the database state is now universally live
    }

    val permissionsArray = mutableListOf(
        android.Manifest.permission.CALL_PHONE
    ).apply {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    requestPermissionLauncher.launch(permissionsArray)

    // Trigger the silent cloud vault handshake instantly on boot
    FirebaseConnectionManager.initializeSilentCloudConnection(
        onSuccess = {
            Log.i("Main", "Cloud sync engine warmed up and standing by.")
            FirebaseUpdateManager.checkForCloudUpdates(this@MainActivity)
            
            // Schedule unique periodic background update check every 1 hour
            try {
                val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.service.AppUpdateWorker>(
                    1, java.util.concurrent.TimeUnit.HOURS
                ).build()
                androidx.work.WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                    "AppUpdatePeriodicCheck",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.i("Main", "Unique periodic AppUpdateWorker scheduled successfully.")
            } catch (e: Exception) {
                Log.e("Main", "Failed to schedule AppUpdateWorker", e)
            }
        },
        onFailure = { error ->
            Log.w("Main", "Running in pure offline-safe isolation mode: $error")
        }
    )

    setContent {
      MyApplicationTheme {
        viewModel = viewModel()

        val currentLanguage by viewModel.language.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val localizedContext = remember(context, currentLanguage) {
            val locale = when (currentLanguage) {
                "Tamil" -> Locale("ta")
                "Hindi" -> Locale("hi")
                "Telugu" -> Locale("te")
                "Spanish" -> Locale("es")
                "French" -> Locale("fr")
                else -> Locale("en")
            }
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            val configContext = context.createConfigurationContext(config)
            object : android.content.ContextWrapper(context) {
                override fun getResources(): android.content.res.Resources = configContext.resources
            }
        }

        // Set localized context for the fallback translator
        com.example.ui.AppContextHolder.context = localizedContext

        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalActivityResultRegistryOwner provides (context as ActivityResultRegistryOwner)
        ) {
            // Handle initial intent for deep linking
            LaunchedEffect(Unit) {
                handleDeepLink(intent)
            }

            WeeklyFinanceApp(viewModel = viewModel)
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      if (::viewModel.isInitialized) {
          handleDeepLink(intent)
      }
  }

  private fun handleDeepLink(intent: android.content.Intent?) {
      val uri = intent?.data ?: return
      
      when (uri.host) {
          "dashboard" -> viewModel.navigateToHome()
          "settings" -> viewModel.navigateTo(com.example.ui.Screen.Settings)
          "history" -> viewModel.navigateTo(com.example.ui.Screen.History)
          "full_ledger" -> viewModel.navigateTo(com.example.ui.Screen.FullLedgerHistory)
          "ai_chat" -> viewModel.navigateTo(com.example.ui.Screen.AiChat)
          "add_customer" -> viewModel.navigateTo(com.example.ui.Screen.AddCustomer)
          "customer" -> {
              val idStr = uri.lastPathSegment
              idStr?.toIntOrNull()?.let { id ->
                  viewModel.navigateTo(com.example.ui.Screen.CustomerDetail(id))
              }
          }
          "bulk_entry" -> {
              val day = uri.lastPathSegment ?: "Home"
              viewModel.navigateTo(com.example.ui.Screen.BulkEntry(day))
          }
          "search" -> {
              val day = uri.lastPathSegment ?: "Home"
              viewModel.navigateTo(com.example.ui.Screen.Search(day))
          }
      }
  }

  override fun onResume() {
    super.onResume()
    try {
      FirebaseUpdateManager.checkForCloudUpdates(this)
    } catch (e: Exception) {
      Log.e("Main", "OnResume update check failed: ${e.message}")
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
      super.onSaveInstanceState(outState)
      outState.putBoolean("isAppRunning", true)
      outState.putLong("lastSavedTime", System.currentTimeMillis())
      Log.d("MainActivity", "onSaveInstanceState: Saving critical state")
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
      super.onRestoreInstanceState(savedInstanceState)
      val isAppRunning = savedInstanceState.getBoolean("isAppRunning", false)
      val lastSavedTime = savedInstanceState.getLong("lastSavedTime", 0L)
      Log.d("MainActivity", "onRestoreInstanceState: Restoring critical state. Running: $isAppRunning, Time: $lastSavedTime")
  }
}
