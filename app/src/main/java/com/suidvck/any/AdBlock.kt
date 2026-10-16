package com.suidvck.any

import android.net.Uri

/**
 * Small host-based ad / tracker blocker used by the WebView's request interceptor.
 * Matching is suffix based: an entry "doubleclick.net" also blocks "ad.doubleclick.net".
 */
object AdBlock {

    private val HOSTS = setOf(
        // Google ads / analytics
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "2mdn.net",
        // Large ad networks / exchanges
        "adnxs.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net",
        "casalemedia.com", "criteo.com", "criteo.net", "smartadserver.com", "33across.com",
        "3lift.com", "yieldmo.com", "sharethrough.com", "teads.tv", "spotxchange.com",
        "tremorhub.com", "freewheel.tv", "innovid.com", "flashtalking.com", "sizmek.com",
        "adform.net", "advertising.com", "adtechus.com", "amazon-adsystem.com",
        // Native ads / content recommendation
        "outbrain.com", "taboola.com", "revcontent.com", "mgid.com", "zergnet.com",
        "bidvertiser.com", "adcash.com", "propellerads.com", "popads.net", "popcash.net",
        "onclickads.net", "exoclick.com", "juicyads.com",
        // Mobile ad SDKs
        "adcolony.com", "applovin.com", "inmobi.com", "mopub.com", "chartboost.com",
        "vungle.com", "ironsrc.com", "supersonicads.com", "startapp.com", "tapjoy.com",
        // Trackers / analytics / ad verification
        "scorecardresearch.com", "quantserve.com", "quantcount.com", "hotjar.com",
        "mixpanel.com", "segment.io", "amplitude.com", "fullstory.com", "mouseflow.com",
        "clarity.ms", "crazyegg.com", "luckyorange.com", "matomo.cloud", "statcounter.com",
        "histats.com", "sitemeter.com", "moatads.com", "serving-sys.com",
        "adsafeprotected.com", "doubleverify.com", "iasds01.com", "admaster.com.cn",
        // Platform ad hosts
        "ads.tiktok.com", "analytics.tiktok.com", "ads.linkedin.com", "px.ads.linkedin.com"
    )

    fun isAd(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        for (h in HOSTS) {
            if (host == h || host.endsWith(".$h")) return true
        }
        // YouTube serves its ad requests from the site itself.
        if ((host == "youtube.com" || host.endsWith(".youtube.com")) &&
            uri.path.orEmpty().contains("/pagead/")
        ) {
            return true
        }
        return false
    }
}
