package app.farvater.ui.screens

import androidx.compose.ui.res.stringResource
import app.farvater.ui.components.WitchStyleMark
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.farvater.BuildConfig
import app.farvater.core.catalog.RuApps
import app.farvater.data.AppIcon
import app.farvater.data.AppIcons
import app.farvater.data.AppSettings
import app.farvater.data.ThemeMode
import app.farvater.data.UiStyle
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import app.farvater.ui.LocalBottomInset
import app.farvater.ui.components.AppIconImage
import app.farvater.ui.components.AppLogo
import app.farvater.ui.components.OsLogo
import app.farvater.ui.components.OsLogoMark
import app.farvater.ui.ios.EdgeHaptics
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosChoiceRow
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosIcon
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosPushedPage
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.SheetCorner
import app.farvater.R
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.RkpBlue
import app.farvater.ui.theme.SumieSeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TestUrls: List<Pair<String, String>>
    @Composable get() = listOf(
    "https://www.gstatic.com/generate_204" to stringResource(R.string.probe_google_default),
    "https://cp.cloudflare.com/generate_204" to "Cloudflare",
    "https://www.apple.com/library/test/success.html" to "Apple",
    "https://detectportal.firefox.com/success.txt" to "Firefox",
)

private val ConcurrencyOptions: List<Pair<Int, String>>
    @Composable get() = listOf(8 to stringResource(R.string.speed_gentle), 16 to stringResource(R.string.speed_normal), 32 to stringResource(R.string.speed_fast))

private enum class SettingsPage(val title: Int) {
    Appearance(R.string.settings_appearance),
    Connection(R.string.settings_connection),
    Security(R.string.settings_security),
    Subscriptions(R.string.settings_subscriptions),
    Testing(R.string.settings_testing),
    Background(R.string.settings_background),
    Updates(R.string.settings_updates),
    Documents(R.string.settings_documents),
    About(R.string.settings_about),
    Language(R.string.settings_language),
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenDocument: (String) -> Unit,
    pageName: String? = null,
    onPageName: ((String?) -> Unit)? = null,
    hwid: String = "",
    onCopied: () -> Unit = {},
    updateVersion: String? = null,
    checkingUpdates: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    duplicates: Int = 0,
) {
    var localPage by rememberSaveable { mutableStateOf<String?>(null) }
    val page = (if (onPageName != null) pageName else localPage)?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } }
    val openPage: (SettingsPage?) -> Unit = { next -> if (onPageName != null) onPageName(next?.name) else localPage = next?.name }
    val context = LocalContext.current
    val slideDirection = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1 else 1
    var appIcon by remember { mutableStateOf(AppIcons.selected(context)) }
    var showApps by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    BackHandler(enabled = page != null) { openPage(null) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            val motion = spring<IntOffset>(dampingRatio = 1f, stiffness = 420f)
            if (forward) {
                (slideInHorizontally(motion) { it * slideDirection } togetherWith
                    slideOutHorizontally(motion) { -it * slideDirection / 3 } + fadeOut(targetAlpha = 0.6f)).apply { targetContentZIndex = 1f }
            } else {
                (slideInHorizontally(motion) { -it * slideDirection / 3 } + fadeIn(initialAlpha = 0.6f) togetherWith
                    slideOutHorizontally(motion) { it * slideDirection }).apply { targetContentZIndex = -1f }
            }
        },
        label = "settings",
    ) { current ->
        when (current) {
            null -> SettingsRoot(
                settings = settings,
                appIcon = appIcon,
                updateVersion = updateVersion,
                onOpen = { openPage(it) },
                onChangelog = { showChangelog = true },
            )
            else -> IosPushedPage(title = stringResource(current.title), backTitle = stringResource(R.string.settings_title), onBack = { openPage(null) }) {
                when (current) {
                    SettingsPage.Appearance -> AppearancePage(settings, onChange, appIcon) { icon ->
                        AppIcons.choose(context, icon)
                        appIcon = icon
                    }
                    SettingsPage.Connection -> ConnectionPage(settings, onChange) { showApps = true }
                    SettingsPage.Security -> SecurityPage(settings, onChange)
                    SettingsPage.Subscriptions -> SubscriptionsPage(settings, onChange, hwid, onCopied)
                    SettingsPage.Testing -> TestingPage(settings, onChange, duplicates)
                    SettingsPage.Background -> BackgroundPage()
                    SettingsPage.Updates -> UpdatesPage(settings, onChange, updateVersion, checkingUpdates, onCheckUpdates) { showChangelog = true }
                    SettingsPage.Documents -> DocumentsPage(onOpenDocument)
                    SettingsPage.About -> AboutPage()
                    SettingsPage.Language -> LanguagePage()
                }
                Spacer(Modifier.height(24.dp + LocalBottomInset.current))
            }
        }
    }

    if (showChangelog) ChangelogSheet(onClose = { showChangelog = false })

    if (showApps) {
        BypassAppsSheet(
            selected = settings.bypassApps,
            ruApps = if (settings.directRuServices) RuApps.packages else emptySet(),
            onChange = { apps -> onChange { it.copy(bypassApps = apps) } },
            onClose = { showApps = false },
        )
    }
}

private const val Divider = 59
private const val StyleDivider = 100
private const val ChannelUrl = "https://t.me/farvater_app"

@Composable
private fun SettingsRoot(
    settings: AppSettings,
    appIcon: AppIcon,
    updateVersion: String?,
    onOpen: (SettingsPage) -> Unit,
    onChangelog: () -> Unit,
) {
    val c = Ios.colors
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scroll = rememberScrollState()
    EdgeHaptics(scroll)
    val collapsed by remember { derivedStateOf { scroll.value > 70 } }
    val divider = Divider.dp
    var unrestricted by remember { mutableStateOf(context.isBatteryUnrestricted()) }
    LifecycleResumeEffect(Unit) {
        unrestricted = context.isBatteryUnrestricted()
        onPauseOrDispose { }
    }
    val complete = XrayEngine.isAvailable && TunBridge.isAvailable

    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().glassSource(backdrop).verticalScroll(scroll)) {
            IosLargeTitle(stringResource(R.string.settings_title))

            IosSection {
                IosRow(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.version_label, BuildConfig.VERSION_NAME) + (XrayEngine.version?.let { " · Xray $it" } ?: ""),
                    leading = { AppIconImage(appIcon, 58.dp) },
                    chevron = true,
                    onClick = { onOpen(SettingsPage.About) },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            IosSection {
                IosRow(
                    title = stringResource(R.string.settings_appearance),
                    icon = Icons.Rounded.Palette,
                    iconTint = c.pink,
                    value = when (settings.uiStyle) {
                        UiStyle.IOS -> "iOS 26"
                        UiStyle.MATERIAL -> "Material You"
                        UiStyle.ROSKOMPOZOR -> stringResource(R.string.style_rkp)
                        UiStyle.SUMIE -> stringResource(R.string.style_sumie)
                        UiStyle.NOTHING -> stringResource(R.string.brand_nothing)
                        UiStyle.ORIGIN -> stringResource(R.string.brand_origin)
                        UiStyle.HYPER -> stringResource(R.string.brand_hyper)
                        UiStyle.HARMONY -> stringResource(R.string.brand_harmony)
                        UiStyle.ONEUI -> stringResource(R.string.brand_one_ui)
                        UiStyle.OXYGEN -> stringResource(R.string.brand_oxygen)
                        UiStyle.WITCH -> stringResource(R.string.style_witch)
                    },
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Appearance) },
                )
                IosRow(
                    title = stringResource(R.string.settings_language),
                    value = when (app.farvater.data.AppLanguages.selected(LocalContext.current).substringBefore('-')) {
                        "ru" -> stringResource(R.string.language_ru)
                        "en" -> stringResource(R.string.language_en)
                        "fa" -> stringResource(R.string.language_fa)
                        else -> stringResource(R.string.language_system)
                    },
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Language) },
                )
            }

            IosSection(
                footer = if (complete) null else stringResource(R.string.incomplete_build),
                footerColor = c.red,
            ) {
                IosRow(
                    title = stringResource(R.string.settings_connection),
                    icon = Icons.Rounded.Hub,
                    iconTint = c.green,
                    value = if (settings.directRuServices) stringResource(R.string.russian_direct_short) else null,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Connection) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.settings_security),
                    icon = Icons.Rounded.Shield,
                    iconTint = c.blue,
                    value = if (settings.encryptedDns && settings.safeMode) stringResource(R.string.all_enabled) else stringResource(R.string.partly_enabled),
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Security) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.settings_subscriptions),
                    icon = Icons.Rounded.Link,
                    iconTint = c.indigo,
                    value = if (settings.sendHwid) stringResource(R.string.hwid_on) else stringResource(R.string.hwid_off),
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Subscriptions) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.settings_testing),
                    icon = Icons.Rounded.Speed,
                    iconTint = c.tint,
                    value = ConcurrencyOptions.minBy { kotlin.math.abs(it.first - settings.concurrency) }.second,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Testing) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.settings_background),
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = if (unrestricted) c.green else c.red,
                    value = if (unrestricted) stringResource(R.string.background_allowed) else stringResource(R.string.background_restricted),
                    valueColor = if (unrestricted) null else c.red,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Background) },
                )
            }

            IosSection {
                IosRow(
                    title = stringResource(R.string.settings_updates),
                    icon = Icons.Rounded.SystemUpdate,
                    iconTint = c.blue,
                    value = updateVersion?.let { stringResource(R.string.update_exists, it) },
                    valueColor = c.blue,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Updates) },
                    trailing = {
                        if (updateVersion != null) Box(Modifier.size(9.dp).clip(CircleShape).background(c.red))
                    },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.changelog_title),
                    icon = Icons.Rounded.History,
                    iconTint = c.indigo,
                    chevron = true,
                    onClick = onChangelog,
                )
            }

            IosSection {
                IosRow(
                    title = stringResource(R.string.telegram_channel),
                    icon = ImageVector.vectorResource(R.drawable.ic_telegram),
                    iconTint = c.blue,
                    value = app.farvater.ui.ltrText("@farvater_app"),
                    chevron = true,
                    onClick = { runCatching { uri.openUri(ChannelUrl) } },
                )
            }

            IosSection {
                IosRow(
                    title = stringResource(R.string.settings_documents),
                    icon = Icons.Rounded.Description,
                    iconTint = c.gray,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Documents) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = stringResource(R.string.settings_about),
                    icon = Icons.Rounded.Info,
                    iconTint = c.gray,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.About) },
                )
            }
            Spacer(Modifier.height(20.dp + LocalBottomInset.current))
        }
        IosCompactBar(stringResource(R.string.settings_title), visible = collapsed, backdrop = backdrop)
    }
}

@Composable
private fun AppIconSection(selected: AppIcon, onSelect: (AppIcon) -> Unit) {
    IosSection(
        header = stringResource(R.string.app_icon_title),
        footer = stringResource(R.string.app_icon_hint),
    ) {
        AppIcon.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
                row.forEach { icon ->
                    IconChoice(icon, icon == selected, Modifier.weight(1f)) { onSelect(icon) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconChoice(icon: AppIcon, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ios.colors
    val ring = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .border(2.5.dp, if (selected) c.tint else Color.Transparent, ring)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppIconImage(icon, 58.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(icon.titleRes),
            style = IosType.caption1,
            color = if (selected) c.tint else c.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppearancePage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    appIcon: AppIcon,
    onIcon: (AppIcon) -> Unit,
) {
    IosSection(
        header = stringResource(R.string.style_title),
        footer = stringResource(R.string.style_hint),
    ) {
        IosChoiceRow(
            title = "iOS 26",
            subtitle = stringResource(R.string.style_ios_hint),
            selected = settings.uiStyle == UiStyle.IOS,
            leading = { StyleMark { OsLogoMark(OsLogo.IOS) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.IOS) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = "Material You",
            subtitle = stringResource(R.string.style_material_hint),
            selected = settings.uiStyle == UiStyle.MATERIAL,
            leading = { StyleMark { OsLogoMark(OsLogo.MATERIAL) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.MATERIAL) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.style_rkp),
            subtitle = stringResource(R.string.style_rkp_hint),
            selected = settings.uiStyle == UiStyle.ROSKOMPOZOR,
            leading = { StyleMark { IosIcon(ImageVector.vectorResource(R.drawable.ic_rkp), RkpBlue) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.ROSKOMPOZOR) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.style_sumie),
            subtitle = stringResource(R.string.style_sumie_hint),
            selected = settings.uiStyle == UiStyle.SUMIE,
            leading = { StyleMark { IosIcon(Icons.Rounded.Brush, SumieSeal) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.SUMIE) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_nothing),
            subtitle = stringResource(R.string.style_nothing_hint),
            selected = settings.uiStyle == UiStyle.NOTHING,
            leading = { StyleMark { OsLogoMark(OsLogo.NOTHING) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.NOTHING) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_origin),
            subtitle = stringResource(R.string.style_origin_hint),
            selected = settings.uiStyle == UiStyle.ORIGIN,
            leading = { StyleMark { OsLogoMark(OsLogo.ORIGIN) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.ORIGIN) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_hyper),
            subtitle = stringResource(R.string.style_hyper_hint),
            selected = settings.uiStyle == UiStyle.HYPER,
            leading = { StyleMark { OsLogoMark(OsLogo.HYPER) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.HYPER) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_harmony),
            subtitle = stringResource(R.string.style_harmony_hint),
            selected = settings.uiStyle == UiStyle.HARMONY,
            leading = { StyleMark { OsLogoMark(OsLogo.HARMONY) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.HARMONY) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_one_ui),
            subtitle = stringResource(R.string.style_oneui_hint),
            selected = settings.uiStyle == UiStyle.ONEUI,
            leading = { StyleMark { OsLogoMark(OsLogo.ONEUI) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.ONEUI) } },
        )
        IosDivider(start = StyleDivider.dp)
        IosChoiceRow(
            title = stringResource(R.string.brand_oxygen),
            subtitle = stringResource(R.string.style_oxygen_hint),
            selected = settings.uiStyle == UiStyle.OXYGEN,
            leading = { StyleMark { OsLogoMark(OsLogo.OXYGEN) } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.OXYGEN) } },
        )
    }
    IosSection {
        IosChoiceRow(
            title = stringResource(R.string.style_witch) + if (!settings.witchSeen) " · " + stringResource(R.string.new_badge) else "",
            subtitle = stringResource(R.string.style_witch_description),
            selected = settings.uiStyle == UiStyle.WITCH,
            leading = { StyleMark { WitchStyleMark() } },
            onClick = { onChange { it.copy(uiStyle = UiStyle.WITCH) } },
        )
    }

    if (settings.uiStyle !in setOf(UiStyle.ROSKOMPOZOR, UiStyle.WITCH)) {
        IosSection(header = stringResource(R.string.theme_title)) {
            ThemeChoices.forEachIndexed { i, (mode, title) ->
                if (i > 0) IosDivider()
                IosChoiceRow(
                    title = title,
                    selected = settings.themeMode == mode,
                    onClick = { onChange { it.copy(themeMode = mode) } },
                )
            }
        }
    }

    if (settings.uiStyle == UiStyle.MATERIAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        IosSection(
            footer = stringResource(R.string.theme_wallpaper_hint),
        ) {
            IosRow(
                title = stringResource(R.string.theme_wallpaper),
                checked = settings.dynamicColor,
                onCheckedChange = { v -> onChange { it.copy(dynamicColor = v) } },
            )
        }
    }

    AppIconSection(appIcon, onIcon)
}

private val MtuChoices = listOf(8500, 1500, 1480, 1400, 1280)

private val ThemeChoices: List<Pair<ThemeMode, String>>
    @Composable get() = listOf(
    ThemeMode.SYSTEM to stringResource(R.string.language_system),
    ThemeMode.LIGHT to stringResource(R.string.theme_light),
    ThemeMode.DARK to stringResource(R.string.theme_dark),
)

@Composable
private fun ConnectionPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onApps: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    val bypassCount by produceState<Int?>(null, settings.bypassApps, settings.directRuServices) {
        val packages = if (settings.directRuServices) settings.bypassApps + RuApps.packages else settings.bypassApps
        value = withContext(Dispatchers.IO) { context.installedCount(packages) }
    }
    IosSection(
        footer = stringResource(R.string.russian_services_hint),
    ) {
        IosRow(
            title = stringResource(R.string.russian_services),
            titleLines = 2,
            icon = Icons.Rounded.AccountBalance,
            iconTint = c.green,
            checked = settings.directRuServices,
            onCheckedChange = { v -> onChange { it.copy(directRuServices = v) } },
        )
    }
    IosSection(footer = stringResource(R.string.bypass_apps_hint)) {
        IosRow(
            title = stringResource(R.string.bypass_apps),
            titleLines = 2,
            icon = Icons.Rounded.Apps,
            iconTint = c.indigo,
            value = bypassCount?.let { if (it == 0) stringResource(R.string.none) else it.toString() },
            chevron = true,
            onClick = onApps,
        )
    }
    IosSection(
        header = stringResource(R.string.network_mode_title),
        footer = stringResource(R.string.mode_nodes_hint),
    ) {
        IosRow(
            title = stringResource(R.string.mode_nodes),
            titleLines = 2,
            icon = Icons.Rounded.SwapHoriz,
            iconTint = c.teal,
            checked = settings.modeNodes,
            onCheckedChange = { v -> onChange { it.copy(modeNodes = v) } },
        )
    }
    IosSection(
        header = stringResource(R.string.on_failure),
        footer = stringResource(R.string.failover_hint),
    ) {
        IosRow(
            title = stringResource(R.string.failover_enabled),
            titleLines = 2,
            icon = Icons.Rounded.SyncAlt,
            iconTint = c.green,
            checked = settings.autoSwitch,
            onCheckedChange = { v -> onChange { it.copy(autoSwitch = v) } },
        )
    }
    IosSection(
        header = stringResource(R.string.autostart_title),
        footer = stringResource(R.string.autostart_hint),
    ) {
        IosRow(
            title = stringResource(R.string.autoconnect_title),
            titleLines = 2,
            subtitle = stringResource(R.string.autoconnect_hint),
            subtitleLines = Int.MAX_VALUE,
            icon = Icons.Rounded.PowerSettingsNew,
            iconTint = c.green,
            checked = settings.autoStart,
            onCheckedChange = { v -> onChange { it.copy(autoStart = v) } },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = stringResource(R.string.always_on_title),
            titleLines = 2,
            subtitle = stringResource(R.string.always_on_hint),
            subtitleLines = Int.MAX_VALUE,
            icon = Icons.Rounded.Lock,
            iconTint = c.blue,
            chevron = true,
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        )
    }
    IosSection(
        header = "MTU",
        footer = stringResource(R.string.mtu_hint),
    ) {
        MtuChoices.forEachIndexed { i, mtu ->
            if (i > 0) IosDivider()
            IosChoiceRow(
                title = if (i == 0) stringResource(R.string.mtu_default, mtu) else mtu.toString(),
                selected = settings.mtu == mtu,
                onClick = { onChange { it.copy(mtu = mtu) } },
            )
        }
    }
}

@Composable
private fun SecurityPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Ios.colors
    val divider = Divider.dp
    IosSection(
        footer = stringResource(R.string.security_hint),
    ) {
        IosRow(
            title = stringResource(R.string.safe_nodes),
            titleLines = 2,
            subtitle = stringResource(R.string.safe_nodes_hint),
            subtitleLines = Int.MAX_VALUE,
            icon = Icons.Rounded.GppGood,
            iconTint = c.green,
            checked = settings.safeMode,
            onCheckedChange = { v -> onChange { it.copy(safeMode = v) } },
        )
        IosDivider(start = divider)
        IosRow(
            title = stringResource(R.string.encrypted_dns),
            subtitle = stringResource(R.string.encrypted_dns_hint),
            icon = Icons.Rounded.Dns,
            iconTint = c.blue,
            checked = settings.encryptedDns,
            onCheckedChange = { v -> onChange { it.copy(encryptedDns = v) } },
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SubscriptionsPage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    hwid: String,
    onCopied: () -> Unit,
) {
    val c = Ios.colors
    val clipboard = LocalClipboardManager.current
    IosSection(
        footer = stringResource(R.string.hwid_hint),
    ) {
        IosRow(
            title = stringResource(R.string.send_hwid),
            icon = Icons.Rounded.PhoneAndroid,
            iconTint = c.indigo,
            checked = settings.sendHwid,
            onCheckedChange = { v -> onChange { it.copy(sendHwid = v) } },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = "HWID",
            icon = Icons.Rounded.Fingerprint,
            iconTint = c.gray,
            value = if (hwid.length > 12) hwid.take(8) + "…" + hwid.takeLast(4) else hwid,
            onClick = {
                clipboard.setText(AnnotatedString(hwid))
                onCopied()
            },
        )
    }
}

@Composable
private fun TestingPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, duplicates: Int) {
    val c = Ios.colors
    IosSection(
        footer = stringResource(R.string.dead_group_hint),
    ) {
        IosRow(
            title = stringResource(R.string.dead_group),
            titleLines = 2,
            icon = Icons.Rounded.CloudOff,
            iconTint = c.red,
            checked = settings.groupDead,
            onCheckedChange = { v -> onChange { it.copy(groupDead = v) } },
        )
    }
    IosSection(
        footer = stringResource(R.string.duplicates_hint),
    ) {
        IosRow(
            title = stringResource(R.string.hide_dead),
            icon = Icons.Rounded.VisibilityOff,
            iconTint = c.gray,
            checked = settings.hideDead,
            onCheckedChange = { v -> onChange { it.copy(hideDead = v) } },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = stringResource(R.string.sort_testing),
            subtitle = stringResource(R.string.sort_testing_hint),
            icon = Icons.AutoMirrored.Rounded.Sort,
            iconTint = c.teal,
            checked = settings.liveSort,
            onCheckedChange = { v -> onChange { it.copy(liveSort = v) } },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = stringResource(R.string.remove_duplicates),
            subtitle = when {
                duplicates == 0 -> stringResource(R.string.duplicates_none)
                settings.dropDuplicates -> stringResource(R.string.duplicates_hidden_count, duplicates)
                else -> stringResource(R.string.duplicates_found, duplicates)
            },
            icon = Icons.Rounded.ContentCopy,
            iconTint = c.indigo,
            checked = settings.dropDuplicates,
            onCheckedChange = { v -> onChange { it.copy(dropDuplicates = v) } },
        )
    }
    IosSection(
        header = stringResource(R.string.probe_url),
        footer = stringResource(R.string.probe_url_hint),
    ) {
        TestUrls.forEachIndexed { i, (url, title) ->
            if (i > 0) IosDivider()
            IosChoiceRow(
                title = title,
                subtitle = url.removePrefix("https://"),
                selected = settings.testUrl == url,
                onClick = { onChange { it.copy(testUrl = url) } },
            )
        }
    }
    val concurrencyOptions = ConcurrencyOptions
    IosSection(header = stringResource(R.string.probe_speed), footer = stringResource(R.string.probe_speed_hint)) {
        val current = concurrencyOptions.minBy { kotlin.math.abs(it.first - settings.concurrency) }.first
        IosSegmented(
            options = concurrencyOptions.map { it.second },
            selected = concurrencyOptions.indexOfFirst { it.first == current },
            onSelect = { i -> onChange { it.copy(concurrency = concurrencyOptions[i].first) } },
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun BackgroundPage() {
    val c = Ios.colors
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(context.isBatteryUnrestricted()) }
    LifecycleResumeEffect(Unit) {
        unrestricted = context.isBatteryUnrestricted()
        onPauseOrDispose { }
    }
    IosSection(footer = stringResource(R.string.battery_hint)) {
        IosRow(
            title = stringResource(R.string.battery_restrictions),
            icon = Icons.Rounded.BatteryChargingFull,
            iconTint = if (unrestricted) c.green else c.red,
            value = if (unrestricted) stringResource(R.string.none) else stringResource(R.string.present),
            valueColor = if (unrestricted) null else c.red,
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = stringResource(R.string.open_app_settings),
            titleColor = c.tint,
            icon = Icons.Rounded.Tune,
            iconTint = c.gray,
            onClick = { runCatching { context.startActivity(context.appSettingsIntent()) } },
        )
    }
    IosSection(header = stringResource(R.string.remove_restrictions)) {
        Column(Modifier.padding(16.dp)) {
            BackgroundTip("1", stringResource(R.string.battery_step_one))
            BackgroundTip("2", stringResource(R.string.battery_step_two))
            BackgroundTip("3", stringResource(R.string.battery_step_three))
        }
    }
}

@Composable
private fun BackgroundTip(number: String, text: String) {
    val c = Ios.colors
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(number, style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = IosType.subheadline, color = c.label)
    }
}

@Composable
private fun UpdatesPage(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    updateVersion: String?,
    checking: Boolean,
    onCheck: () -> Unit,
    onChangelog: () -> Unit,
) {
    val c = Ios.colors
    IosSection(
        footer = stringResource(R.string.update_check_hint),
    ) {
        IosRow(
            title = if (updateVersion != null) stringResource(R.string.update_install, updateVersion.orEmpty()) else stringResource(R.string.update_check),
            titleColor = c.tint,
            icon = Icons.Rounded.SystemUpdate,
            iconTint = c.blue,
            onClick = if (checking) null else onCheck,
            trailing = { if (checking) IosSpinner() },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = stringResource(R.string.update_auto),
            subtitle = stringResource(R.string.update_auto_hint),
            icon = Icons.Rounded.NotificationsActive,
            iconTint = c.red,
            checked = settings.autoUpdates,
            onCheckedChange = { v -> onChange { it.copy(autoUpdates = v) } },
        )
    }
    IosSection {
        IosRow(
            title = stringResource(R.string.changelog_title),
            icon = Icons.Rounded.History,
            iconTint = c.indigo,
            chevron = true,
            onClick = onChangelog,
        )
    }
}

@Composable
private fun DocumentsPage(onOpenDocument: (String) -> Unit) {
    val c = Ios.colors
    val divider = Divider.dp
    IosSection {
        IosRow(stringResource(R.string.terms_title), icon = Icons.Rounded.Description, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.TERMS) })
        IosDivider(start = divider)
        IosRow(stringResource(R.string.privacy_title), icon = Icons.Rounded.Lock, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.PRIVACY) })
        IosDivider(start = divider)
        IosRow(stringResource(R.string.licenses_title), icon = Icons.Rounded.Code, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.LICENSES) })
    }
}

@Composable
private fun AboutPage() {
    val c = Ios.colors
    val uri = LocalUriHandler.current
    val divider = Divider.dp
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(96.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.app_name), style = IosType.title2, color = c.label)
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = IosType.subheadline, color = c.secondaryLabel)
    }
    IosSection {
        IosRow(
            stringResource(R.string.xray_core),
            icon = Icons.Rounded.Memory,
            iconTint = c.purple,
            value = XrayEngine.version ?: if (XrayEngine.isAvailable) stringResource(R.string.built_in_core) else stringResource(R.string.none),
            valueColor = if (XrayEngine.isAvailable) null else c.red,
        )
        IosDivider(start = divider)
        IosRow(
            stringResource(R.string.tun_bridge),
            icon = Icons.Rounded.SwapVert,
            iconTint = c.teal,
            value = if (TunBridge.isAvailable) stringResource(R.string.built_in_bridge) else stringResource(R.string.none),
            valueColor = if (TunBridge.isAvailable) null else c.red,
        )
    }
    IosSection(footer = stringResource(R.string.about_license)) {
        IosRow(
            stringResource(R.string.source_code),
            icon = Icons.Rounded.Code,
            iconTint = c.gray,
            chevron = true,
            onClick = { runCatching { uri.openUri("https://github.com/${BuildConfig.UPDATE_REPO}") } },
        )
        IosDivider(start = divider)
        IosRow(
            stringResource(R.string.telegram_channel),
            icon = ImageVector.vectorResource(R.drawable.ic_telegram),
            iconTint = c.blue,
            value = app.farvater.ui.ltrText("@farvater_app"),
            chevron = true,
            onClick = { runCatching { uri.openUri(ChannelUrl) } },
        )
        IosDivider(start = divider)
        IosRow(
            stringResource(R.string.contact_developer),
            icon = Icons.Rounded.Email,
            iconTint = c.blue,
            chevron = true,
            onClick = { runCatching { uri.openUri("mailto:injexor@proton.me") } },
        )
    }
}

@Composable
fun OnboardingDialog(onChoice: (enableCommunity: Boolean) -> Unit, onOpenDocument: (String) -> Unit) {
    val c = Ios.colors
    val corner = SheetCorner
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.88f, stiffness = 280f)) }
    fun close(enable: Boolean) {
        scope.launch {
            slide.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f))
            onChoice(enable)
        }
    }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * (1f - slide.value)))) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 10.dp)
                    .graphicsLayer { translationY = size.height * slide.value }
                    .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                    .background(c.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))
                AppLogo(84.dp)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.welcome_title), style = IosType.title1, color = c.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Feature(Icons.Rounded.Lock, c.green, stringResource(R.string.own_subscriptions), stringResource(R.string.welcome_own_hint))
                Feature(Icons.Rounded.Public, c.tint, stringResource(R.string.public_subscriptions), stringResource(R.string.welcome_public_hint))
                Feature(Icons.Rounded.Shield, c.red, stringResource(R.string.password_caution), stringResource(R.string.welcome_password_hint))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.terms_short),
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.tint,
                        modifier = Modifier.clickable { onOpenDocument(LegalDocs.TERMS) }.padding(8.dp),
                    )
                    Text(
                        stringResource(R.string.privacy_short),
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.tint,
                        modifier = Modifier.clickable { onOpenDocument(LegalDocs.PRIVACY) }.padding(8.dp),
                    )
                }
                Text(
                    stringResource(R.string.welcome_agreement),
                    style = IosType.caption1,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                IosButton(stringResource(R.string.welcome_public_action), onClick = { close(true) }, modifier = Modifier.fillMaxWidth())
                IosButton(
                    stringResource(R.string.welcome_own_action),
                    onClick = { close(false) },
                    style = IosButtonStyle.Plain,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StyleMark(content: @Composable () -> Unit) {
    Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, text: String) {
    val c = Ios.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = IosType.headline, color = c.label)
            Text(text, style = IosType.subheadline, color = c.secondaryLabel)
        }
    }
}

private fun Context.installedCount(packages: Set<String>): Int =
    packages.count { packageManager.getLaunchIntentForPackage(it) != null }

private fun Context.isBatteryUnrestricted(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

private fun Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

@Composable
private fun LanguagePage() {
    val context = LocalContext.current
    val selected = app.farvater.data.AppLanguages.selected(context).substringBefore('-')
    IosSection {
        listOf("" to R.string.language_system, "ru" to R.string.language_ru,
            "en" to R.string.language_en, "fa" to R.string.language_fa).forEach { (tag, label) ->
            IosChoiceRow(stringResource(label), selected == tag, {
                app.farvater.data.AppLanguages.set(context, tag)
            })
        }
    }
}
