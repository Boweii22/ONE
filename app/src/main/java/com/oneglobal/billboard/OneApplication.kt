package com.oneglobal.billboard

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class OneApplication : Application() {
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
        }
    }
}
