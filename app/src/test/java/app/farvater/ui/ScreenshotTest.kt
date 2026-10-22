package app.farvater.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.farvater.core.model.NetMode
import app.farvater.core.model.Protocol
import app.farvater.core.model.ProxyNode
import app.farvater.core.model.SourceMode
import app.farvater.core.parser.Usage
import app.farvater.data.AppSettings
import app.farvater.data.UiStyle
import app.farvater.engine.TestMethod
import app.farvater.engine.TestResult
import app.farvater.ui.ios.AlertAction
import app.farvater.ui.ios.AlertRole
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.screens.HomeScreen
import app.farvater.ui.screens.OnboardingDialog
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.screens.SettingsScreen
import app.farvater.ui.screens.SourceSheet
import app.farvater.ui.screens.SourcesScreen
import app.farvater.ui.theme.FarvaterTheme
import app.farvater.ui.theme.LocalUiStyle
import app.farvater.vpn.Traffic
import app.farvater.vpn.VpnState
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeIdleDark() = shot("01_home_idle_dark", dark = true) { Home(VpnState.Idle) }
    @Test fun homeIdleLight() = shot("01_home_idle_light", dark = false) { Home(VpnState.Idle) }
    @Test fun homeConnectedDark() = shot("02_home_connected_dark", dark = true) { Home(Fake.connected) }
    @Test fun homeConnectedLight() = shot("02_home_connected_light", dark = false) { Home(Fake.connected) }
    @Test fun homeSearchingDark() = shot("03_home_searching_dark", dark = true) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(48, 120, 9)))
    }
    @Test fun serversDark() = shot("04_servers_dark", dark = true) { Servers() }
    @Test fun serversLight() = shot("04_servers_light", dark = false) { Servers() }
    @Test fun sourcesDark() = shot("05_sources_dark", dark = true) { Sources() }
    @Test fun sourcesLight() = shot("05_sources_light", dark = false) { Sources() }
    @Test fun settingsDark() = shot("06_settings_dark", dark = true) { Settings() }
    @Test fun settingsLight() = shot("06_settings_light", dark = false) { Settings() }
    @Test fun onboardingDark() = shot("07_onboarding_dark", dark = true, screen = true) {
        OnboardingDialog(onChoice = {}, onOpenDocument = {})
    }

    @Test fun alertDark() = shot("08_alert_dark", dark = true, screen = true) { Alert() }
    @Test fun nodeActionsDark() = shot("10_node_actions_dark", dark = true, screen = true, act = ::longPressNode) { Servers() }
    @Test fun sheetLight() = shot("09_sheet_light", dark = false, screen = true) { Sheet() }
    @Test fun materialAlertLight() = shot("18_material_alert_light", dark = false, screen = true, material = true) { Alert() }
    @Test fun materialSheetDark() = shot("19_material_sheet_dark", dark = true, screen = true, material = true) { Sheet() }

    @Test fun materialHomeIdleLight() = shot("11_material_home_idle_light", dark = false, material = true) { Home(VpnState.Idle) }
    @Test fun materialHomeConnectedDark() = shot("12_material_home_connected_dark", dark = true, material = true) { Home(Fake.connected) }
    @Test fun materialServersLight() = shot("14_material_servers_light", dark = false, material = true) { Servers() }
    @Test fun materialServersDark() = shot("14_material_servers_dark", dark = true, material = true) { Servers() }
    @Test fun materialSourcesLight() = shot("15_material_sources_light", dark = false, material = true) { Sources() }
    @Test fun materialNodeActionsLight() = shot(
        "17_material_node_actions_light", dark = false, screen = true, material = true, act = ::longPressNode,
    ) { Servers() }
    @Test fun materialSettingsLight() = shot("16_material_settings_light", dark = false, material = true) { Settings() }
    @Test fun materialSettingsDark() = shot("16_material_settings_dark", dark = true, material = true) { Settings() }

    @Test fun rkpHomeIdle() = shot("21_rkp_home_idle", dark = true, rkp = true) { Home(VpnState.Idle) }
    @Test fun rkpHomeConnected() = shot("22_rkp_home_connected", dark = true, rkp = true) { Home(Fake.connected) }
    @Test fun rkpServers() = shot("23_rkp_servers", dark = true, rkp = true) { Servers() }
    @Test fun rkpSources() = shot("24_rkp_sources", dark = true, rkp = true) { Sources() }
    @Test fun connectionDark() = shot(
        "26_connection_dark", dark = true,
        act = { rule.onNodeWithText("Подключение").performClick() },
    ) { Settings() }
    @Test fun duplicatesDark() = shot("29_servers_duplicates_dark", dark = true) { Servers(Fake.state.copy(duplicates = 13)) }
    @Test fun testingDark() = shot(
        "28_testing_dark", dark = true,
        act = { rule.onNodeWithText("Проверка узлов").performClick() },
    ) { Settings() }
    @Test fun sourcesBlackDark() = shot("27_sources_black_dark", dark = true) {
        Sources(
            Fake.state.copy(
                netMode = NetMode.BLACK,
                detectedMode = NetMode.BLACK,
                sources = Fake.state.sources.map { it.copy(inMode = it.mode.fits(NetMode.BLACK)) },
            ),
        )
    }

    @Test fun sumieIdleLight() = shot("31_sumie_home_idle_light", dark = false, sumie = true) { Home(VpnState.Idle) }
    @Test fun sumieConnectedLight() = shot("32_sumie_home_connected_light", dark = false, sumie = true) { Home(Fake.connected) }
    @Test fun sumieConnectedDark() = shot("33_sumie_home_connected_dark", dark = true, sumie = true) { Home(Fake.connected) }
    @Test fun sumieSearchingLight() = shot("34_sumie_home_searching_light", dark = false, sumie = true) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun sumieErrorDark() = shot("35_sumie_home_error_dark", dark = true, sumie = true) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun sumieServersLight() = shot("36_sumie_servers_light", dark = false, sumie = true) { Servers() }
    @Test fun sumieAppearanceDark() = shot(
        "37_sumie_appearance_dark", dark = true, sumie = true,
        act = { rule.onNodeWithText("Оформление").performClick() },
    ) { Settings(Fake.state.settings.copy(uiStyle = UiStyle.SUMIE)) }

    @Test fun nothingIdleLight() = shot("41_nothing_home_idle_light", dark = false, nothing = true) { Home(VpnState.Idle) }
    @Test fun nothingIdleDark() = shot("41_nothing_home_idle_dark", dark = true, nothing = true) { Home(VpnState.Idle) }
    @Test fun nothingConnectedDark() = shot("42_nothing_home_connected_dark", dark = true, nothing = true) { Home(Fake.connected) }
    @Test fun nothingConnectedLight() = shot("42_nothing_home_connected_light", dark = false, nothing = true) { Home(Fake.connected) }
    @Test fun nothingSearchingDark() = shot("43_nothing_home_searching_dark", dark = true, nothing = true) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun nothingErrorDark() = shot("44_nothing_home_error_dark", dark = true, nothing = true) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun nothingServersDark() = shot("45_nothing_servers_dark", dark = true, nothing = true) { Servers() }
    @Test fun nothingServersLight() = shot("45_nothing_servers_light", dark = false, nothing = true) { Servers() }
    @Test fun nothingSourcesLight() = shot("46_nothing_sources_light", dark = false, nothing = true) { Sources() }
    @Test fun nothingSettingsDark() = shot("47_nothing_settings_dark", dark = true, nothing = true) { Settings() }
    @Test fun nothingAppearanceLight() = shot(
        "48_nothing_appearance_light", dark = false, nothing = true,
        act = { rule.onNodeWithText("Оформление").performClick() },
    ) { Settings(Fake.state.settings.copy(uiStyle = UiStyle.NOTHING)) }
    @Test fun nothingNodeActionsDark() = shot(
        "50_nothing_node_actions_dark", dark = true, screen = true, nothing = true, act = ::longPressNode,
    ) { Servers() }
    @Test fun nothingAlertDark() = shot("49_nothing_alert_dark", dark = true, screen = true, nothing = true) { Alert() }
    @Test fun appIconsLight() = shot("51_app_icons_light", dark = false, act = ::openAppIcons) { Settings() }
    @Test fun nothingAppIconsDark() = shot("52_nothing_app_icons_dark", dark = true, nothing = true, act = ::openAppIcons) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.NOTHING))
    }

    @Test fun originIdleDark() = shot("61_origin_home_idle_dark", dark = true, style = UiStyle.ORIGIN) { Home(VpnState.Idle) }
    @Test fun originIdleLight() = shot("61_origin_home_idle_light", dark = false, style = UiStyle.ORIGIN) { Home(VpnState.Idle) }
    @Test fun originConnectedDark() = shot("62_origin_home_connected_dark", dark = true, style = UiStyle.ORIGIN) { Home(Fake.connected) }
    @Test fun originConnectedLight() = shot("62_origin_home_connected_light", dark = false, style = UiStyle.ORIGIN) { Home(Fake.connected) }
    @Test fun originSearchingDark() = shot("63_origin_home_searching_dark", dark = true, style = UiStyle.ORIGIN) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun originErrorLight() = shot("64_origin_home_error_light", dark = false, style = UiStyle.ORIGIN) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun originServersDark() = shot("65_origin_servers_dark", dark = true, style = UiStyle.ORIGIN) { Servers() }
    @Test fun originSourcesLight() = shot("66_origin_sources_light", dark = false, style = UiStyle.ORIGIN) { Sources() }
    @Test fun originAppearanceDark() = shot("67_origin_appearance_dark", dark = true, style = UiStyle.ORIGIN, act = ::openAppearance) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.ORIGIN))
    }
    @Test fun originAppIconsLight() = shot("68_origin_app_icons_light", dark = false, style = UiStyle.ORIGIN, act = ::openAppIcons) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.ORIGIN))
    }

    @Test fun hyperIdleLight() = shot("71_hyper_home_idle_light", dark = false, style = UiStyle.HYPER) { Home(VpnState.Idle) }
    @Test fun hyperIdleDark() = shot("71_hyper_home_idle_dark", dark = true, style = UiStyle.HYPER) { Home(VpnState.Idle) }
    @Test fun hyperConnectedLight() = shot("72_hyper_home_connected_light", dark = false, style = UiStyle.HYPER) { Home(Fake.connected) }
    @Test fun hyperConnectedDark() = shot("72_hyper_home_connected_dark", dark = true, style = UiStyle.HYPER) { Home(Fake.connected) }
    @Test fun hyperSearchingLight() = shot("73_hyper_home_searching_light", dark = false, style = UiStyle.HYPER) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun hyperErrorDark() = shot("74_hyper_home_error_dark", dark = true, style = UiStyle.HYPER) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun hyperServersLight() = shot("75_hyper_servers_light", dark = false, style = UiStyle.HYPER) { Servers() }
    @Test fun hyperSourcesDark() = shot("76_hyper_sources_dark", dark = true, style = UiStyle.HYPER) { Sources() }
    @Test fun hyperAppearanceLight() = shot("77_hyper_appearance_light", dark = false, style = UiStyle.HYPER, act = ::openAppearance) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.HYPER))
    }
    @Test fun hyperAppIconsDark() = shot("78_hyper_app_icons_dark", dark = true, style = UiStyle.HYPER, act = { openAppIcons(aboveBar = true) }) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.HYPER))
    }

    @Test fun harmonyIdleLight() = shot("81_harmony_home_idle_light", dark = false, style = UiStyle.HARMONY) { Home(VpnState.Idle) }
    @Test fun harmonyIdleDark() = shot("81_harmony_home_idle_dark", dark = true, style = UiStyle.HARMONY) { Home(VpnState.Idle) }
    @Test fun harmonyConnectedLight() = shot("82_harmony_home_connected_light", dark = false, style = UiStyle.HARMONY) { Home(Fake.connected) }
    @Test fun harmonyConnectedDark() = shot("82_harmony_home_connected_dark", dark = true, style = UiStyle.HARMONY) { Home(Fake.connected) }
    @Test fun harmonySearchingDark() = shot("83_harmony_home_searching_dark", dark = true, style = UiStyle.HARMONY) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun harmonyErrorLight() = shot("84_harmony_home_error_light", dark = false, style = UiStyle.HARMONY) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun harmonyServersDark() = shot("85_harmony_servers_dark", dark = true, style = UiStyle.HARMONY) { Servers() }
    @Test fun harmonySourcesLight() = shot("86_harmony_sources_light", dark = false, style = UiStyle.HARMONY) { Sources() }
    @Test fun harmonyAppearanceDark() = shot("87_harmony_appearance_dark", dark = true, style = UiStyle.HARMONY, act = ::openAppearance) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.HARMONY))
    }
    @Test fun harmonyAppIconsLight() = shot("88_harmony_app_icons_light", dark = false, style = UiStyle.HARMONY, act = { openAppIcons(aboveBar = true) }) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.HARMONY))
    }

    @Test fun oneUiIdleLight() = shot("91_oneui_home_idle_light", dark = false, style = UiStyle.ONEUI) { Home(VpnState.Idle) }
    @Test fun oneUiIdleDark() = shot("91_oneui_home_idle_dark", dark = true, style = UiStyle.ONEUI) { Home(VpnState.Idle) }
    @Test fun oneUiConnectedLight() = shot("92_oneui_home_connected_light", dark = false, style = UiStyle.ONEUI) { Home(Fake.connected) }
    @Test fun oneUiConnectedDark() = shot("92_oneui_home_connected_dark", dark = true, style = UiStyle.ONEUI) { Home(Fake.connected) }
    @Test fun oneUiSearchingLight() = shot("93_oneui_home_searching_light", dark = false, style = UiStyle.ONEUI) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun oneUiErrorDark() = shot("94_oneui_home_error_dark", dark = true, style = UiStyle.ONEUI) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun oneUiServersLight() = shot("95_oneui_servers_light", dark = false, style = UiStyle.ONEUI) { Servers() }
    @Test fun oneUiAppearanceDark() = shot("97_oneui_appearance_dark", dark = true, style = UiStyle.ONEUI, act = ::openAppearance) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.ONEUI))
    }
    @Test fun oneUiAppIconsLight() = shot("98_oneui_app_icons_light", dark = false, style = UiStyle.ONEUI, act = { openAppIcons(aboveBar = true) }) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.ONEUI))
    }

    @Test fun oxygenIdleLight() = shot("A1_oxygen_home_idle_light", dark = false, style = UiStyle.OXYGEN) { Home(VpnState.Idle) }
    @Test fun oxygenIdleDark() = shot("A1_oxygen_home_idle_dark", dark = true, style = UiStyle.OXYGEN) { Home(VpnState.Idle) }
    @Test fun oxygenConnectedLight() = shot("A2_oxygen_home_connected_light", dark = false, style = UiStyle.OXYGEN) { Home(Fake.connected) }
    @Test fun oxygenConnectedDark() = shot("A2_oxygen_home_connected_dark", dark = true, style = UiStyle.OXYGEN) { Home(Fake.connected) }
    @Test fun oxygenSearchingLight() = shot("A3_oxygen_home_searching_light", dark = false, style = UiStyle.OXYGEN) {
        Home(VpnState.Idle, Fake.state.copy(progress = TestProgress(37, 120, 9)))
    }
    @Test fun oxygenErrorDark() = shot("A4_oxygen_home_error_dark", dark = true, style = UiStyle.OXYGEN) {
        Home(VpnState.Failed("Узел не ответил", Fake.state.nodes.first()))
    }
    @Test fun oxygenServersLight() = shot("A5_oxygen_servers_light", dark = false, style = UiStyle.OXYGEN) { Servers() }
    @Test fun oxygenAppearanceDark() = shot("A7_oxygen_appearance_dark", dark = true, style = UiStyle.OXYGEN, act = ::openAppearance) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.OXYGEN))
    }
    @Test fun oxygenAppIconsLight() = shot("A8_oxygen_app_icons_light", dark = false, style = UiStyle.OXYGEN, act = { openAppIcons(aboveBar = true) }) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.OXYGEN))
    }

    @Test fun nothingConnectionLargeFontDark() = shot(
        "54_nothing_connection_large_font_dark", dark = true, nothing = true, fontScale = 1.3f, act = { openConnection(swipes = 2) },
    ) { Settings(Fake.state.settings.copy(uiStyle = UiStyle.NOTHING)) }
    @Test fun connectionPageDark() = shot("53_connection_dark", dark = true, act = ::openConnection) { Settings() }
    @Test fun originConnectionLight() = shot("69_origin_connection_light", dark = false, style = UiStyle.ORIGIN, act = ::openConnection) {
        Settings(Fake.state.settings.copy(uiStyle = UiStyle.ORIGIN))
    }

    private fun openConnection(swipes: Int = 3) {
        rule.onNodeWithText("Подключение").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        repeat(swipes) {
            rule.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.15f) }
            rule.mainClock.advanceTimeBy(1_000)
        }
    }

    private fun openAppearance() {
        rule.onNodeWithText("Оформление").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onRoot().performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun openAppIcons(aboveBar: Boolean = false) {
        rule.onNodeWithText("Оформление").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        repeat(if (aboveBar) 5 else 3) {
            rule.onRoot().performTouchInput { if (aboveBar) swipeUp(startY = height * 0.7f, endY = height * 0.15f) else swipeUp() }
            rule.mainClock.advanceTimeBy(1_000)
        }
    }

    @Test fun appsDark() {
        installApps(
            "ru.sberbankmobile" to "СберБанк", "com.idamob.tinkoff.android" to "Т-Банк", "ru.rostel" to "Госуслуги",
            "ru.ozon.app.android" to "Ozon", "com.wildberries.ru" to "Wildberries", "ru.yandex.yandexmaps" to "Яндекс Карты",
            "com.vkontakte.android" to "ВКонтакте", "com.mojang.minecraftpe" to "Minecraft", "org.telegram.messenger" to "Telegram",
            "com.google.android.youtube" to "YouTube", "com.discord" to "Discord",
        )
        shot(
            "30_apps_dark", dark = true, screen = true,
            act = {
                rule.onNodeWithText("Подключение").performClick()
                rule.mainClock.advanceTimeBy(1_500)
                rule.onNodeWithText("Приложения мимо VPN").performClick()
                rule.waitUntil(5_000) {
                    rule.mainClock.advanceTimeBy(100)
                    rule.onAllNodesWithText("Т-Банк").fetchSemanticsNodes().isNotEmpty()
                }
            },
        ) { Settings(Fake.state.settings.copy(bypassApps = setOf("com.mojang.minecraftpe"))) }
    }

    private fun installApps(vararg apps: Pair<String, String>) {
        val pm = shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.forEach { (pkg, label) ->
            pm.addResolveInfoForIntent(
                launcher,
                ResolveInfo().apply {
                    nonLocalizedLabel = label
                    activityInfo = ActivityInfo().apply {
                        packageName = pkg
                        name = "$pkg.Main"
                        applicationInfo = ApplicationInfo().apply { packageName = pkg }
                    }
                },
            )
        }
    }

    @Test fun rkpAppearance() = shot(
        "25_rkp_appearance", dark = true, rkp = true,
        act = { rule.onNodeWithText("Оформление").performClick() },
    ) { Settings(Fake.state.settings.copy(uiStyle = UiStyle.ROSKOMPOZOR)) }

    @Composable
    private fun Alert() {
        Home(VpnState.Idle)
        IosAlert(
            title = "Добавить из ссылки?",
            message = "https://sub.example.net/farvater",
            onDismiss = {},
            actions = listOf(
                AlertAction("Отмена") {},
                AlertAction("Добавить", AlertRole.Preferred) {},
            ),
        )
    }

    @Composable
    private fun Sheet() {
        Sources()
        SourceSheet(
            source = Fake.state.sources.first { !it.community },
            status = "12 узлов, обновлено 3 ч назад",
            onMode = {},
            onInterval = {},
            onRefresh = {},
            onRemove = {},
            onClose = {},
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(
        name: String,
        dark: Boolean,
        screen: Boolean = false,
        material: Boolean = false,
        rkp: Boolean = false,
        sumie: Boolean = false,
        nothing: Boolean = false,
        style: UiStyle? = null,
        fontScale: Float = 1f,
        act: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FarvaterTheme(
                darkTheme = dark,
                style = style ?: when {
                    rkp -> UiStyle.ROSKOMPOZOR
                    sumie -> UiStyle.SUMIE
                    nothing -> UiStyle.NOTHING
                    material -> UiStyle.MATERIAL
                    else -> UiStyle.IOS
                },
                dynamicColor = false,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        if (act != null) {
            act()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1_500)
        }
        val path = "build/outputs/roborazzi/$name.png"
        if (screen) captureScreenRoboImage(path) else rule.onRoot().captureRoboImage(path)
    }

    private fun longPressNode() {
        rule.onAllNodesWithText("Санкт-Петербург, Selectel")[0].performTouchInput { longClick() }
    }

    @Composable
    private fun Home(vpn: VpnState, state: UiState = Fake.state) = FarvaterFrame(tab = Tab.Home, onTab = {}) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            HomeScreen(
                state = state, vpn = vpn, traffic = Fake.traffic, history = Fake.history,
                onToggle = {}, onFindWorking = {}, onCancelTest = {}, onOpenServers = {},
            )
        }
    }

    @Composable
    private fun Servers(state: UiState = Fake.state) = FarvaterFrame(tab = Tab.Servers, onTab = {}) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            ServersScreen(
                state = state, onSelect = {}, onTestAll = {}, onCancelTest = {},
                onDeleteManual = {}, onCopied = {},
            )
        }
    }

    @Composable
    private fun Sources(state: UiState = Fake.state) = FarvaterFrame(tab = Tab.Sources, onTab = {}) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            SourcesScreen(
                state = state, onRefresh = {}, onToggle = { _, _ -> }, onAddSubscription = {},
                onPaste = {}, onRemoveUserSource = {}, onEnableCommunity = {},
            )
        }
    }

    @Composable
    private fun Settings(settings: AppSettings = Fake.state.settings) = FarvaterFrame(tab = Tab.Settings, onTab = {}) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            SettingsScreen(settings = settings.copy(uiStyle = LocalUiStyle.current), onChange = {}, onOpenDocument = {})
        }
    }
}

private object Fake {
    private fun node(id: String, name: String, protocol: Protocol, params: Map<String, String>, source: String) =
        ProxyNode(id, protocol, name, "203.0.113.${id.length * 7}", 443, "secret", params, "vless://$id", source)

    private val reality = mapOf("security" to "reality", "type" to "tcp")
    val nodes = listOf(
        node("a1", "🇷🇺 Москва, VK Cloud", Protocol.VLESS, reality, "kfwl-wl"),
        node("b22", "🇷🇺 Санкт-Петербург, Selectel", Protocol.VLESS, reality + ("type" to "xhttp"), "zieng2-universal"),
        node("c333", "🇫🇮 Хельсинки", Protocol.VLESS, mapOf("security" to "tls", "type" to "ws"), "rjsxrd-bypass"),
        node("d4444", "🇳🇱 Амстердам", Protocol.TROJAN, mapOf("security" to "tls"), "rkp-wl"),
        node("e55555", "🇩🇪 Франкфурт, Join Telegram @SolVPN", Protocol.VMESS, mapOf("type" to "ws"), "rjsxrd-bypass"),
        node("f666666", "🇹🇷 Стамбул", Protocol.SHADOWSOCKS, mapOf("method" to "chacha20-ietf-poly1305"), "manual"),
        node("g7777777", "🇰🇿 Алматы", Protocol.VLESS, mapOf("security" to "none"), "rkp-wl"),
        node("h88888888", "🇺🇸 Нью-Йорк", Protocol.VLESS, reality + ("type" to "grpc"), "rkp-wl"),
    )
    val results = mapOf(
        "a1" to TestResult("a1", 84, TestMethod.REAL),
        "b22" to TestResult("b22", 142, TestMethod.REAL),
        "c333" to TestResult("c333", 388, TestMethod.REAL),
        "d4444" to TestResult("d4444", 912, TestMethod.REAL),
        "e55555" to TestResult("e55555", 96, TestMethod.TCP),
        "g7777777" to TestResult("g7777777", -1, TestMethod.REAL),
    )
    private fun source(
        id: String, title: String, author: String, license: String, count: Int,
        error: String? = null, mode: SourceMode = SourceMode.WHITE, announce: String? = null,
    ) =
        SourceUi(
            id = id, title = title, author = author, license = license, homepage = "https://example.org",
            description = "Белые списки, проверенные подсети. Обновляется раз в час.", community = true,
            enabled = error == null, nodeCount = count, updatedAt = System.currentTimeMillis() - 23 * 60_000,
            error = error, loading = false, mode = mode, inMode = mode.fits(NetMode.WHITE), announce = announce,
        )
    val sources = listOf(
        source("zieng2-universal", "WL Universal", "zieng2", "не указана", 85),
        source("rjsxrd-bypass", "rjsxrd", "whoahaow", "MIT", 475, announce = "t.me/rjsxrd · 475 configs · last update: 14:27 05/10/2026"),
        source(
            "rkp-wl", "РКП: белые списки", "@RKP_channel", "не указана", 64,
            announce = "⚠️ - Конфиг без шифрования трафика.\n🏴 - Конфиг с неизвестным выходным трафиком.",
        ),
        source("rkp-bl", "РКП: чёрные списки", "@RKP_channel", "не указана", 112, mode = SourceMode.BLACK),
        source("kfwl-wl", "KfWL: белые списки", "@qkfwl", "не указана", 20),
        source("kfwl-bl", "KfWL: чёрные списки", "@qkfwl", "не указана", 94, mode = SourceMode.BLACK, announce = "тг: @kfwlrus | BlackList"),
        source("bunker-wl", "Бункер Кота: белые списки", "@K0TLUX", "не указана", 36),
        source("bunker-bl", "Бункер Кота: чёрные списки", "@K0TLUX", "не указана", 140, mode = SourceMode.BLACK),
        SourceUi(
            id = "user-1", title = "sub.example.net", author = "ваша подписка", license = "",
            homepage = "https://sub.example.net/abc", description = "https://sub.example.net/abc", community = false,
            enabled = true, nodeCount = 12, updatedAt = System.currentTimeMillis() - 3 * 3_600_000, error = null, loading = false,
            intervalHours = 3,
            usage = Usage(used = 25_876_000_000, total = 0, expire = 1_792_584_000),
            announce = "Если узел не работает, выберите другую локацию.\n🐌 нескоростной, 🟩 для торрентов",
        ),
    )
    val state = UiState(
        settings = AppSettings(onboardingDone = true, communityEnabled = true, acceptedLegalVersion = 1),
        sources = sources,
        nodes = nodes,
        results = results,
        selectedId = "a1",
        netMode = NetMode.WHITE,
        detectedMode = NetMode.WHITE,
    )
    val connected = VpnState.Connected(nodes[0], System.currentTimeMillis() - 754_000)
    val traffic = Traffic(upBps = 18_400, downBps = 1_240_000, upTotal = 4_200_000, downTotal = 96_000_000, today = 1_380_000_000)
    val history = listOf(120, 340, 900, 1500, 1200, 800, 1900, 2400, 2100, 1300, 1600, 2200, 2600, 1800, 1240)
        .map { it * 1000L }
}
