package com.oneglobal.billboard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.oneglobal.billboard.model.ReignReceipt
import com.oneglobal.billboard.ui.OneApp
import com.oneglobal.billboard.ui.theme.OneTheme
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: OneViewModel by viewModels()
    private var identifiedUserId: String? = null
    private var pushSubscriptionObserver: IPushSubscriptionObserver? = null
    private val rewardedAds = RewardedAds()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleGoogleAuthLink(intent)
        handleChallengeLink(intent)
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
                    onRestorePurchases = ::restorePurchases,
                    onRequestPush = ::requestPushPermission,
                    onDisablePush = ::disablePush,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    onShareReceipt = ::shareReceipt,
                    onShareONE = ::shareONE,
                    onGoogle = ::googleIdentity,
                    onWatchAd = ::watchAd,
                    onIdentifyUser = ::identifyUser,
                    onOpenPrivacy = { openUrl("${BuildConfig.ONE_WEB_URL}/privacy") },
                    onOpenDeletionHelp = { openUrl("${BuildConfig.ONE_WEB_URL}/delete-account") },
                    onBuyOnWeb = ::buyOnWeb,
                )
            }
        }
        syncPushPermission()
        observePushRegistration()
        observeNotificationClicks()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGoogleAuthLink(intent)
        handleChallengeLink(intent)
    }

    private fun handleGoogleAuthLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "one" || uri.host != "auth" || uri.path != "/callback") return
        val params = Uri.parse("one://callback?${uri.fragment.orEmpty()}")
        val error = params.getQueryParameter("error_description") ?: params.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            viewModel.identityStatus("Google sign-in failed: $error. Your handle is unchanged.")
            return
        }
        val access = params.getQueryParameter("access_token")
        val refresh = params.getQueryParameter("refresh_token")
        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
            viewModel.identityStatus("Google returned no session. Your handle is unchanged.")
            return
        }
        viewModel.completeGoogleBrowserIdentity(access, refresh)
    }

    private fun handleChallengeLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "https" && uri.host == Uri.parse(BuildConfig.ONE_WEB_URL).host &&
            uri.path in listOf("", "/") && uri.getQueryParameter("challenge") == "1") {
            openLiveChallengeWhenConnected()
        }
    }

    // Shared by the web share-challenge deep link and the dethroned push notification:
    // both want to land straight on the take-the-screen action, not the home screen,
    // but the live world state may still be loading on a cold launch.
    private fun openLiveChallengeWhenConnected() {
        lifecycleScope.launch {
            viewModel.world.collect { state ->
                if (state.connected) { viewModel.openChallenge(); throw kotlinx.coroutines.CancellationException() }
            }
        }
    }

    private fun observeNotificationClicks() {
        if (BuildConfig.ONESIGNAL_APP_ID.isBlank()) return
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val destination = event.notification.additionalData?.optString("destination")
                if (destination == "revenge") openLiveChallengeWhenConnected()
                if (destination == "message_library") viewModel.selectTab(com.oneglobal.billboard.model.MainTab.LIBRARY)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        syncPushPermission()
    }

    override fun onDestroy() {
        pushSubscriptionObserver?.let { OneSignal.User.pushSubscription.removeObserver(it) }
        pushSubscriptionObserver = null
        super.onDestroy()
    }

    private fun requestPushPermission(onResult: (Boolean) -> Unit) {
        if (BuildConfig.ONESIGNAL_APP_ID.isBlank()) {
            onResult(false)
            return
        }
        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
            delay(750)
            val granted = OneSignal.Notifications.permission
            if (granted) OneSignal.User.pushSubscription.optIn()
            onResult(granted)
        }
    }

    private fun syncPushPermission() {
        if (BuildConfig.ONESIGNAL_APP_ID.isBlank()) {
            viewModel.syncPushPermission(false, false)
            return
        }
        val granted = OneSignal.Notifications.permission
        if (granted) OneSignal.User.pushSubscription.optIn()
        // canRequestPermission is false once Android has permanently denied the
        // system dialog (two denials, or "don't ask again") - at that point the
        // in-app toggle is powerless and the only real fix is Android's own
        // per-app notification settings.
        val blocked = !granted && !OneSignal.Notifications.canRequestPermission
        viewModel.syncPushPermission(granted, blocked)
    }

    private fun disablePush() {
        if (BuildConfig.ONESIGNAL_APP_ID.isNotBlank()) OneSignal.User.pushSubscription.optOut()
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun observePushRegistration() {
        if (BuildConfig.ONESIGNAL_APP_ID.isBlank() || pushSubscriptionObserver != null) return
        val observer = object : IPushSubscriptionObserver {
            override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                viewModel.syncPushRegistration(isRegisteredSubscription(state.current.id))
            }
        }
        pushSubscriptionObserver = observer
        OneSignal.User.pushSubscription.addObserver(observer)
        refreshPushRegistration()

        // OneSignal finishes obtaining an FCM token asynchronously.  The observer catches
        // ordinary state changes, while these short retries cover the initial app launch
        // where the subscription can become available before the observer is attached.
        lifecycleScope.launch {
            repeat(30) {
                delay(1_000)
                refreshPushRegistration()
            }
        }
    }

    private fun refreshPushRegistration() {
        viewModel.syncPushRegistration(isRegisteredSubscription(OneSignal.User.pushSubscription.id))
    }

    private fun isRegisteredSubscription(subscriptionId: String?): Boolean =
        !subscriptionId.isNullOrBlank() && !subscriptionId.startsWith("local-")

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
            result(false, "Purchases are not configured in this build yet.", 0)
            return
        }
        val userId = viewModel.world.value.currentUserId
        if (userId.isBlank()) {
            result(false, "Your ONE identity is still loading. Try again in a moment.", 0)
            return
        }

        fun loadOfferingAndPurchase() {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: com.revenuecat.purchases.Offerings) {
                    // Store product IDs are permanent, so support both the current and legacy aliases.
                    val creditOffering = offerings.all["credits"] ?: offerings.all["tickets"] ?: offerings.current
                    val productIds = when {
                        requestedAmount >= 50 -> setOf("one_credits_headliner_v1", "one_tickets_headliner_v1")
                        requestedAmount >= 20 -> setOf("one_credits_challenger_v1", "one_tickets_challenger_v1")
                        else -> setOf("one_credits_spark_v1", "one_tickets_spark_v1")
                    }
                    val packageToBuy = creditOffering?.availablePackages?.firstOrNull { it.product.id in productIds }
                    if (packageToBuy == null) {
                        result(false, "This ONE Credit pack is not available right now.", 0)
                        return
                    }
                    Purchases.sharedInstance.purchase(
                        PurchaseParams.Builder(this@MainActivity, packageToBuy).build(),
                        object : PurchaseCallback {
                            override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                                Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
                                result(true, "Purchase complete. Syncing your ONE Credits...", requestedAmount)
                            }

                            override fun onError(error: PurchasesError, userCancelled: Boolean) {
                                result(false, if (userCancelled) "Purchase cancelled." else error.message, 0)
                            }
                        },
                    )
                }

                override fun onError(error: PurchasesError) = result(false, error.message, 0)
            })
        }

        // A purchase made under RevenueCat's temporary anonymous ID cannot be credited
        // to the Supabase profile. Complete identification before opening Play Billing.
        Purchases.sharedInstance.logIn(userId, object : LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                identifiedUserId = userId
                loadOfferingAndPurchase()
            }

            override fun onError(error: PurchasesError) {
                identifiedUserId = null
                result(false, "Could not connect this purchase to your ONE account: ${error.message}", 0)
            }
        })
    }

    private fun restorePurchases(result: (success: Boolean, message: String) -> Unit) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) {
            result(false, "Purchases are not configured in this build.")
            return
        }
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
                result(true, "PURCHASES RESTORED // BALANCE IS SYNCING")
            }

            override fun onError(error: PurchasesError) {
                result(false, error.message)
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

    private fun watchAd() {
        // Reserved before the ad is even shown, not after it verifies - if the
        // redemption call drops mid-flight, retrying watchAd() reuses this same
        // id instead of risking an already-verified reward being lost.
        val requestId = viewModel.reserveAdBypassRequestId()
        rewardedAds.show(
            activity = this,
            status = { message -> viewModel.showToast(message) },
            verified = { viewModel.redeemAdReward(requestId) },
        )
    }

    private fun googleIdentity(restore: Boolean) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            viewModel.showToast("Google sign-in is not configured in this build yet.")
            return
        }
        viewModel.startGoogleBrowserIdentity(restore) { url -> openUrl(url) }
    }

    private fun shareONE() {
        share(
            "${viewModel.world.value.reign.owner.handle} owns ONE. Take it from them. ${BuildConfig.ONE_WEB_URL}/?challenge=1",
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

    // The app already knows the signed-in user's real RevenueCat app_user_id,
    // so it's baked into the link here - nobody visiting from this button
    // ever has to know or type their own ID.
    private fun buyOnWeb() {
        if (BuildConfig.ONE_WEB_FUNNEL_URL.isBlank()) return
        val userId = viewModel.world.value.currentUserId
        if (userId.isBlank()) {
            viewModel.showToast("Your ONE identity is still loading. Try again in a moment.")
            return
        }
        openUrl("${BuildConfig.ONE_WEB_FUNNEL_URL}?app_user_id=${Uri.encode(userId)}")
    }
}
