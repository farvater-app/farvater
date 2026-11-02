package app.farvater

import app.farvater.core.catalog.RuApps
import org.junit.Assert.assertTrue
import org.junit.Test

class RuAppsTest {
    @Test fun requestedBankAssistantShoppingAndMailAppsHaveVerifiedPackageIds() {
        assertTrue(RuApps.packages.containsAll(setOf(
            "ru.otpbank.mobile", "ru.sberbank.investor", "com.sdkit.search.app",
            "com.yandex.aliceapp", "com.icemobile.lenta.prod", "ru.bestprice.fixprice", "com.vk.mail",
        )))
    }

    @Test fun existingRequestedAppsRemainInTheBypassList() {
        assertTrue(RuApps.packages.containsAll(setOf(
            "ru.alfabank.mobile.android", "com.uma.musicvk", "ru.foodfox.client",
            "ru.yandex.yandexmaps", "ru.vk.store",
        )))
    }

    @Test fun everyEntryIsAnExactAndroidPackageName() {
        val packageName = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
        RuApps.packages.forEach { assertTrue(it, packageName.matches(it)) }
    }
}
