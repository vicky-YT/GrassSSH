package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

object AppCheckDiagnostics {
  @Volatile var firebaseInitialized: Boolean = false
    internal set
  @Volatile var providerMode: String = "uninitialized"
    internal set
  @Volatile var initError: String? = null
    internal set
}

fun configureAppCheck(context: Context, intent: Intent?) {
  try {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    AppCheckDiagnostics.firebaseInitialized = FirebaseApp.getApps(context).isNotEmpty()

    if (BuildConfig.DEBUG) {
      val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
      if (!debugToken.isNullOrEmpty()) {
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
          "$DEBUG_STORE_PREFIX$persistenceKey",
          Context.MODE_PRIVATE,
        )
        prefs.edit()
          .putString(DEBUG_SECRET_KEY, debugToken)
          .commit()
        AppCheckDiagnostics.providerMode = "debug-with-intent-token"
        Log.d(TAG, "App Check debug secret configured successfully.")
      } else {
        AppCheckDiagnostics.providerMode = "debug-no-intent-token"
        Log.w(TAG, "No debug token found in Intent extra.")
      }

      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance(),
      )
      Log.d(TAG, "DebugAppCheckProviderFactory installed.")
    } else {
      val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName,
      )
      if (resId != 0) {
        val siteKey = context.getString(resId)
        if (siteKey.isNotBlank()) {
          FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            RecaptchaAppCheckProviderFactory.getInstance(siteKey),
          )
          AppCheckDiagnostics.providerMode = "recaptcha-enterprise"
          Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else {
          AppCheckDiagnostics.providerMode = "recaptcha-blank-key"
          AppCheckDiagnostics.initError = "recaptcha_site_key resource string is blank."
          Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
      } else {
        AppCheckDiagnostics.providerMode = "recaptcha-missing-key"
        AppCheckDiagnostics.initError = "recaptcha_site_key string resource not found."
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
      }
    }
  } catch (e: Exception) {
    AppCheckDiagnostics.initError = e.localizedMessage ?: e::class.java.simpleName
    Log.e(TAG, "Failed to initialize Firebase App Check", e)
  }
}
