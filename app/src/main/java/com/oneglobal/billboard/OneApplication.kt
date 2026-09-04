package com.oneglobal.billboard

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class OneApplication : Application() {
    companion object {
        private const val PUSH_DIAGNOSTIC_TAG = "OnePush"
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            Purchases.configure(
                PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build(),
            )
        }

        if (BuildConfig.ONESIGNAL_APP_ID.isNotBlank()) {
            OneSignal.Debug.logLevel = LogLevel.NONE
            OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

            // Deliberately log only success/failure, never the sensitive FCM token itself.
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    Log.i(PUSH_DIAGNOSTIC_TAG, "FCM token registration succeeded")
                } else {
                    Log.e(
                        PUSH_DIAGNOSTIC_TAG,
                        "FCM token registration failed",
                        task.exception,
                    )
                }
            }
        }
    }
}
