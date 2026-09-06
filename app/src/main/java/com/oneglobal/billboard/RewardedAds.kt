package com.oneglobal.billboard

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.revenuecat.purchases.ExperimentalPreviewRevenueCatPurchasesAPI
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.admob.*

/** Prepared integration. Connect the server redemption bridge before exposing it in the UI. */
@OptIn(ExperimentalPreviewRevenueCatPurchasesAPI::class)
class RewardedAds {
    private var busy = false
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun clearBusy() {
        busy = false
        timeoutRunnable?.let(timeoutHandler::removeCallbacks)
        timeoutRunnable = null
    }

    fun show(activity: Activity, status: (String) -> Unit, verified: () -> Unit) {
        if (busy) return
        if (BuildConfig.ADMOB_REWARDED_UNIT_ID.isBlank()) { status("Rewarded ads are not available yet."); return }
        busy = true
        // Safety net: if AdMob/consent/RevenueCat never call back (dropped network, SDK hiccup),
        // don't leave every future tap silently ignored forever.
        timeoutRunnable = Runnable {
            busy = false
            status("Ad request timed out. Please try again.")
        }.also { timeoutHandler.postDelayed(it, 25_000L) }
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null || !consent.canRequestAds()) { clearBusy(); status("Ads are unavailable with your current privacy choices.") }
                else MobileAds.initialize(activity) { activity.runOnUiThread {
                    status("Loading your ad…")
                    Purchases.sharedInstance.adTracker.loadAndTrackRewardedAd(
                        context = activity, adUnitId = BuildConfig.ADMOB_REWARDED_UNIT_ID,
                        adRequest = AdRequest.Builder().build(), placement = "cooldown_skip",
                        loadCallback = object : RewardedAdLoadCallback() {
                            override fun onAdFailedToLoad(error: LoadAdError) { clearBusy(); status("No ad is available. Please try again later.") }
                            override fun onAdLoaded(ad: RewardedAd) {
                                ad.enableRewardVerification()
                                ad.show(activity = activity,
                                    rewardVerificationStarted = { status("Verifying your reward…") },
                                    rewardVerificationCompleted = { result ->
                                        clearBusy()
                                        if (result.verifiedReward != null) verified()
                                        else status("Reward verification is pending. No cooldown has been changed.")
                                    })
                            }
                        })
                } }
            }
        }, { clearBusy(); status("Could not load ad privacy settings. Please try again.") })
    }
}
