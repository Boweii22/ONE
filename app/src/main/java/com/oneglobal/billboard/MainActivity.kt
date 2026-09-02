package com.oneglobal.billboard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.oneglobal.billboard.model.ReignReceipt
import com.oneglobal.billboard.ui.OneApp
import com.oneglobal.billboard.ui.theme.OneTheme
import com.onesignal.OneSignal
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: OneViewModel by viewModels()
    private var identifiedUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            OneTheme {
                OneApp(
                    viewModel = viewModel,
                    revenueCatReady = BuildConfig.REVENUECAT_API_KEY.isNotBlank(),
                    oneSignalReady = BuildConfig.ONESIGNAL_APP_ID.isNotBlank(),
                    onPurchaseCredits = ::purchaseCredits,
                    onRequestPush = ::requestPushPermission,
                    onShareReceipt = ::shareReceipt,
                    onShareONE = ::shareONE,
                    onIdentifyUser = ::identifyUser,
                    onOpenPrivacy = { openUrl("${BuildConfig.ONE_WEB_URL}/privacy") },
                    onOpenDeletionHelp = { openUrl("${BuildConfig.ONE_WEB_URL}/delete-account") },
                )
            }
        }
    }

    private fun requestPushPermission() {
        if (BuildConfig.ONESIGNAL_APP_ID.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(false)
        }
    }

    private fun identifyUser(userId: String) {
        if (userId.isBlank() || identifiedUserId == userId) return
        identifiedUserId = userId
        if (BuildConfig.ONESIGNAL_APP_ID.isNotBlank()) OneSignal.login(userId)
        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            Purchases.sharedInstance.logIn(userId, object : LogInCallback {
                override fun onReceived(customerInfo: CustomerInfo, created: Boolean) = Unit
                override fun onError(error: PurchasesError) {
                    identifiedUserId = null
                }
            })
        }
    }

    private fun purchaseCredits(
        requestedAmount: Int,
        result: (success: Boolean, message: String, grantedCredits: Int) -> Unit,
    ) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) {
            result(false, "Demo mode: add your RevenueCat public SDK key to activate live credit packs.", 0)
            return
        }
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                val packages = offerings.current?.availablePackages.orEmpty()
                val packageToBuy = when {
                    packages.isEmpty() -> null
                    requestedAmount >= 50 -> packages.getOrNull(2) ?: packages.last()
                    requestedAmount >= 20 -> packages.getOrNull(1) ?: packages.last()
                    else -> packages.first()
                }
                if (packageToBuy == null) {
                    result(false, "No Revenge Ticket package exists in the current RevenueCat offering.", 0)
                    return
                }
                Purchases.sharedInstance.purchase(
                    PurchaseParams.Builder(this@MainActivity, packageToBuy).build(),
                    object : PurchaseCallback {
                        override fun onCompleted(
                            storeTransaction: StoreTransaction,
                            customerInfo: CustomerInfo,
                        ) {
                            Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
                            result(true, "$requestedAmount Revenge Tickets are being verified.", requestedAmount)
                        }

                        override fun onError(error: PurchasesError, userCancelled: Boolean) {
                            result(false, if (userCancelled) "Purchase cancelled." else error.message, 0)
                        }
                    },
                )
            }

            override fun onError(error: PurchasesError) {
                result(false, error.message, 0)
            }
        })
    }

    private fun shareReceipt(receipt: ReignReceipt) {
        val duration = receipt.durationSeconds.coerceAtLeast(1)
        val text = buildString {
            appendLine("I OWNED THE INTERNET.")
            appendLine("$duration seconds · ${receipt.totalViews} verified views")
            appendLine("“${receipt.message}”")
            append("One message. One owner. Take it back: ${BuildConfig.ONE_WEB_URL} #OWNONE #Shipaton")
        }
        share(text, "Share your reign")
    }

    private fun shareONE() {
        share(
            "One person owns the only live screen. Everyone can watch. Anyone can steal it. ${BuildConfig.ONE_WEB_URL} #OWNONE #Shipaton",
            "Invite someone to ONE",
        )
    }

    private fun share(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
