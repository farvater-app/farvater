package app.farvater.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Code
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
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.farvater.BuildConfig
import app.farvater.core.catalog.RuApps
import app.farvater.data.AppSettings
import app.farvater.data.ThemeMode
import app.farvater.data.UiStyle
import app.farvater.engine.TunBridge
import app.farvater.engine.XrayEngine
import app.farvater.ui.LocalBottomInset
import app.farvater.ui.components.AppLogo
import app.farvater.ui.ios.IosButton
import app.farvater.ui.ios.IosButtonStyle
import app.farvater.ui.ios.IosChoiceRow
import app.farvater.ui.ios.IosCompactBar
import app.farvater.ui.ios.glassSource
import app.farvater.ui.ios.rememberGlassBackdrop
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosLargeTitle
import app.farvater.ui.ios.IosPushedPage
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.ios.IosSpinner
import app.farvater.ui.ios.IosSwitch
import app.farvater.ui.ios.SheetCorner
import app.farvater.R
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.RkpBlue
import kotlinx.coroutines.launch

private val TestUrls = listOf(
    "https://www.gstatic.com/generate_204" to "Google, по умолчанию",
    "https://cp.cloudflare.com/generate_204" to "Cloudflare",
    "https://www.apple.com/library/test/success.html" to "Apple",
    "https://detectportal.firefox.com/success.txt" to "Firefox",
)

private val ConcurrencyOptions = listOf(8 to "Бережно", 16 to "Обычно", 32 to "Быстро")

private enum class SettingsPage(val title: String) {
    Appearance("Оформление"),
    Connection("Подключение"),
    Security("Безопасность"),
    Subscriptions("Подписки"),
    Testing("Проверка узлов"),
    Background("Работа в фоне"),
    Updates("Обновления"),
    Documents("Документы"),
    About("О приложении"),
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenDocument: (String) -> Unit,
    hwid: String = "",
    onCopied: () -> Unit = {},
    updateVersion: String? = null,
    checkingUpdates: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    duplicates: Int = 0,
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var showApps by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    BackHandler(enabled = page != null) { page = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            val motion = spring<IntOffset>(dampingRatio = 1f, stiffness = 420f)
            if (forward) {
                (slideInHorizontally(motion) { it } togetherWith
                    slideOutHorizontally(motion) { -it / 3 } + fadeOut(targetAlpha = 0.6f)).apply { targetContentZIndex = 1f }
            } else {
                (slideInHorizontally(motion) { -it / 3 } + fadeIn(initialAlpha = 0.6f) togetherWith
                    slideOutHorizontally(motion) { it }).apply { targetContentZIndex = -1f }
            }
        },
        label = "settings",
    ) { current ->
        when (current) {
            null -> SettingsRoot(
                settings = settings,
                updateVersion = updateVersion,
                onOpen = { page = it },
                onChangelog = { showChangelog = true },
            )
            else -> IosPushedPage(title = current.title, backTitle = "Настройки", onBack = { page = null }) {
                when (current) {
                    SettingsPage.Appearance -> AppearancePage(settings, onChange)
                    SettingsPage.Connection -> ConnectionPage(settings, onChange) { showApps = true }
                    SettingsPage.Security -> SecurityPage(settings, onChange)
                    SettingsPage.Subscriptions -> SubscriptionsPage(settings, onChange, hwid, onCopied)
                    SettingsPage.Testing -> TestingPage(settings, onChange, duplicates)
                    SettingsPage.Background -> BackgroundPage()
                    SettingsPage.Updates -> UpdatesPage(settings, onChange, updateVersion, checkingUpdates, onCheckUpdates) { showChangelog = true }
                    SettingsPage.Documents -> DocumentsPage(onOpenDocument)
                    SettingsPage.About -> AboutPage()
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

@Composable
private fun SettingsRoot(
    settings: AppSettings,
    updateVersion: String?,
    onOpen: (SettingsPage) -> Unit,
    onChangelog: () -> Unit,
) {
    val c = Ios.colors
    val context = LocalContext.current
    val scroll = rememberScrollState()
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
            IosLargeTitle("Настройки")

            IosSection {
                IosRow(
                    title = "Фарватер",
                    subtitle = "Версия ${BuildConfig.VERSION_NAME}" + (XrayEngine.version?.let { " · Xray $it" } ?: ""),
                    leading = { AppLogo(58.dp) },
                    chevron = true,
                    onClick = { onOpen(SettingsPage.About) },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            IosSection {
                IosRow(
                    title = "Оформление",
                    icon = Icons.Rounded.Palette,
                    iconTint = c.pink,
                    value = when (settings.uiStyle) {
                        UiStyle.IOS -> "iOS 26"
                        UiStyle.MATERIAL -> "Material You"
                        UiStyle.ROSKOMPOZOR -> "#РосКомПозор"
                    },
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Appearance) },
                )
            }

            IosSection(
                footer = if (complete) null else "Неполная сборка: каталог и проверка работают, а подключиться нельзя. Скачайте официальную сборку.",
                footerColor = c.red,
            ) {
                IosRow(
                    title = "Подключение",
                    icon = Icons.Rounded.Hub,
                    iconTint = c.green,
                    value = if (settings.directRuServices) "РФ напрямую" else null,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Connection) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "Безопасность",
                    icon = Icons.Rounded.Shield,
                    iconTint = c.blue,
                    value = if (settings.encryptedDns && settings.safeMode) "всё включено" else "частично",
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Security) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "Подписки",
                    icon = Icons.Rounded.Link,
                    iconTint = c.indigo,
                    value = if (settings.sendHwid) "HWID вкл." else "HWID выкл.",
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Subscriptions) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "Проверка узлов",
                    icon = Icons.Rounded.Speed,
                    iconTint = c.tint,
                    value = ConcurrencyOptions.minBy { kotlin.math.abs(it.first - settings.concurrency) }.second,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Testing) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "Работа в фоне",
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = if (unrestricted) c.green else c.red,
                    value = if (unrestricted) "разрешена" else "ограничена",
                    valueColor = if (unrestricted) null else c.red,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Background) },
                )
            }

            IosSection {
                IosRow(
                    title = "Обновления",
                    icon = Icons.Rounded.SystemUpdate,
                    iconTint = c.blue,
                    value = updateVersion?.let { "есть $it" },
                    valueColor = c.blue,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Updates) },
                    trailing = {
                        if (updateVersion != null) Box(Modifier.size(9.dp).clip(CircleShape).background(c.red))
                    },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "История изменений",
                    icon = Icons.Rounded.History,
                    iconTint = c.indigo,
                    chevron = true,
                    onClick = onChangelog,
                )
            }

            IosSection {
                IosRow(
                    title = "Документы",
                    icon = Icons.Rounded.Description,
                    iconTint = c.gray,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.Documents) },
                )
                IosDivider(start = divider)
                IosRow(
                    title = "О приложении",
                    icon = Icons.Rounded.Info,
                    iconTint = c.gray,
                    chevron = true,
                    onClick = { onOpen(SettingsPage.About) },
                )
            }
            Spacer(Modifier.height(20.dp + LocalBottomInset.current))
        }
        IosCompactBar("Настройки", visible = collapsed, backdrop = backdrop)
    }
}

@Composable
private fun AppearancePage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Ios.colors
    val divider = Divider.dp
    IosSection(
        header = "Стиль",
        footer = "Меняет вид всего приложения: панели, кнопки, списки, переключатели и окна.",
    ) {
        IosChoiceRow(
            title = "iOS 26",
            subtitle = "Liquid Glass: стеклянные панели с размытием и преломлением",
            selected = settings.uiStyle == UiStyle.IOS,
            icon = Icons.Rounded.PhoneIphone,
            iconTint = c.blue,
            onClick = { onChange { it.copy(uiStyle = UiStyle.IOS) } },
        )
        IosDivider(start = divider)
        IosChoiceRow(
            title = "Material You",
            subtitle = "Стиль Android: Material 3 и цвета под обои телефона",
            selected = settings.uiStyle == UiStyle.MATERIAL,
            icon = Icons.Rounded.Android,
            iconTint = c.green,
            onClick = { onChange { it.copy(uiStyle = UiStyle.MATERIAL) } },
        )
        IosDivider(start = divider)
        IosChoiceRow(
            title = "#РосКомПозор",
            subtitle = "Material 3 в цветах #РКП: голубой на тёмно-сером фоне",
            selected = settings.uiStyle == UiStyle.ROSKOMPOZOR,
            icon = ImageVector.vectorResource(R.drawable.ic_rkp),
            iconTint = RkpBlue,
            onClick = { onChange { it.copy(uiStyle = UiStyle.ROSKOMPOZOR) } },
        )
    }

    if (settings.uiStyle != UiStyle.ROSKOMPOZOR) {
        IosSection(header = "Тема") {
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
            footer = "Цвета интерфейса подбираются под обои телефона. Если выключить, будет фирменный оранжевый.",
        ) {
            IosRow(
                title = "Цвета обоев",
                trailing = {
                    IosSwitch(settings.dynamicColor, { v -> onChange { it.copy(dynamicColor = v) } })
                },
            )
        }
    }
}

private val MtuChoices = listOf(8500, 1500, 1480, 1400, 1280)

private val ThemeChoices = listOf(
    ThemeMode.SYSTEM to "Как в системе",
    ThemeMode.LIGHT to "Светлая",
    ThemeMode.DARK to "Тёмная",
)

@Composable
private fun ConnectionPage(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, onApps: () -> Unit) {
    val c = Ios.colors
    val context = LocalContext.current
    val bypassCount = remember(settings.bypassApps, settings.directRuServices) {
        context.installedCount(if (settings.directRuServices) settings.bypassApps + RuApps.packages else settings.bypassApps)
    }
    IosSection(
        footer = "Сайты и приложения банков, Госуслуг, Яндекса, VK и маркетплейсов работают напрямую, как без VPN: " +
            "они видят ваш обычный адрес, а не адрес сервера. Что VPN включён, приложения всё равно могут узнать у Android.",
    ) {
        IosRow(
            title = "Российские сервисы",
            icon = Icons.Rounded.AccountBalance,
            iconTint = c.green,
            trailing = { IosSwitch(settings.directRuServices, { v -> onChange { it.copy(directRuServices = v) } }) },
        )
    }
    IosSection(footer = "Выбранные приложения ходят в интернет напрямую. Пригодится, если приложение не работает через VPN.") {
        IosRow(
            title = "Приложения мимо VPN",
            icon = Icons.Rounded.Apps,
            iconTint = c.indigo,
            value = if (bypassCount == 0) "нет" else bypassCount.toString(),
            chevron = true,
            onClick = onApps,
        )
    }
    IosSection(
        header = "MTU",
        footer = "Самый большой пакет в туннеле. Понизьте, если в играх, звонках или отдельных приложениях что-то не работает.",
    ) {
        MtuChoices.forEachIndexed { i, mtu ->
            if (i > 0) IosDivider()
            IosChoiceRow(
                title = if (i == 0) "$mtu, по умолчанию" else mtu.toString(),
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
        footer = "Владелец сервера видит, на какие сайты вы заходите. Содержимое защищает HTTPS: " +
            "не вводите пароли на сайтах без замочка и через публичные узлы. " +
            "Внутренний прокси Фарватера всегда под паролем: другие приложения на телефоне не смогут пользоваться туннелем.",
    ) {
        IosRow(
            title = "Только защищённые узлы",
            subtitle = "В публичных подписках скрывать узлы без шифрования, со старым шифрованием или без проверки сертификата",
            subtitleLines = 3,
            icon = Icons.Rounded.GppGood,
            iconTint = c.green,
            trailing = { IosSwitch(settings.safeMode, { v -> onChange { it.copy(safeMode = v) } }) },
        )
        IosDivider(start = divider)
        IosRow(
            title = "Зашифрованный DNS",
            subtitle = "Адреса сайтов узнаются по HTTPS, сервер не сможет подменить их",
            icon = Icons.Rounded.Dns,
            iconTint = c.blue,
            trailing = { IosSwitch(settings.encryptedDns, { v -> onChange { it.copy(encryptedDns = v) } }) },
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
        footer = "HWID нужен подпискам с лимитом устройств, иначе панель может выдать урезанный список. " +
            "Это случайный номер этой установки, не связанный с телефоном. Его получают только ваши подписки. Нажмите на HWID, чтобы скопировать.",
    ) {
        IosRow(
            title = "Отправлять HWID",
            icon = Icons.Rounded.PhoneAndroid,
            iconTint = c.indigo,
            trailing = { IosSwitch(settings.sendHwid, { v -> onChange { it.copy(sendHwid = v) } }) },
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
        footer = "Полные копии узлов, то есть одинаковые ссылки с разными названиями, Фарватер убирает всегда. " +
            "Этот переключатель убирает ещё и узлы с теми же адресом, портом и протоколом, даже если у них " +
            "другие SNI, ключ Reality, отпечаток TLS, пользователь или транспорт. Из каждой такой группы " +
            "остаётся выбранный узел, а если его там нет, тот, что раньше в подписке. Остальные не " +
            "показываются, не проверяются и не участвуют в поиске рабочего узла. Поэтому по умолчанию " +
            "выключено: если оставшийся узел не работает, другой из группы иногда работает.",
    ) {
        IosRow(
            title = "Скрывать неработающие",
            icon = Icons.Rounded.VisibilityOff,
            iconTint = c.gray,
            trailing = { IosSwitch(settings.hideDead, { v -> onChange { it.copy(hideDead = v) } }) },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = "Убирать дубли",
            subtitle = when {
                duplicates == 0 -> "Сейчас дублей нет"
                settings.dropDuplicates -> "Скрыто дублей: $duplicates"
                else -> "Найдено дублей: $duplicates"
            },
            icon = Icons.Rounded.ContentCopy,
            iconTint = c.indigo,
            trailing = { IosSwitch(settings.dropDuplicates, { v -> onChange { it.copy(dropDuplicates = v) } }) },
        )
    }
    IosSection(
        header = "Адрес проверки",
        footer = "Через узел открывается этот адрес, по ответу считается пинг. Если с Google узлы не отвечают, попробуйте другой.",
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
    IosSection(header = "Скорость проверки", footer = "Быстрая проверка нагружает сеть: оператор может начать сбрасывать соединения.") {
        val current = ConcurrencyOptions.minBy { kotlin.math.abs(it.first - settings.concurrency) }.first
        IosSegmented(
            options = ConcurrencyOptions.map { it.second },
            selected = ConcurrencyOptions.indexOfFirst { it.first == current },
            onSelect = { i -> onChange { it.copy(concurrency = ConcurrencyOptions[i].first) } },
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
    IosSection(footer = "Чтобы Android не останавливал VPN, когда приложение закрыто, снимите для Фарватера ограничения батареи.") {
        IosRow(
            title = "Ограничения батареи",
            icon = Icons.Rounded.BatteryChargingFull,
            iconTint = if (unrestricted) c.green else c.red,
            value = if (unrestricted) "нет" else "есть",
            valueColor = if (unrestricted) null else c.red,
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = "Открыть настройки Фарватера",
            titleColor = c.tint,
            icon = Icons.Rounded.Tune,
            iconTint = c.gray,
            onClick = { runCatching { context.startActivity(context.appSettingsIntent()) } },
        )
    }
    IosSection(header = "Как снять ограничения") {
        Column(Modifier.padding(16.dp)) {
            BackgroundTip("1", "Откройте настройки Фарватера кнопкой выше.")
            BackgroundTip("2", "Выберите «Батарея» или «Использование батареи» и отметьте «Без ограничений».")
            BackgroundTip("3", "На Xiaomi, Huawei, Honor, OPPO и vivo включите ещё «Автозапуск».")
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
        footer = "Пока включён VPN, новые версии проверяются раз в минуту, иначе раз в 15 минут. " +
            "Скачанный файл сверяется по SHA-256 перед установкой.",
    ) {
        IosRow(
            title = if (updateVersion != null) "Установить $updateVersion" else "Проверить обновления",
            titleColor = c.tint,
            icon = Icons.Rounded.SystemUpdate,
            iconTint = c.blue,
            onClick = if (checking) null else onCheck,
            trailing = { if (checking) IosSpinner() },
        )
        IosDivider(start = Divider.dp)
        IosRow(
            title = "Проверять автоматически",
            subtitle = "Уведомлять о новых версиях и срочных исправлениях",
            icon = Icons.Rounded.NotificationsActive,
            iconTint = c.red,
            trailing = { IosSwitch(settings.autoUpdates, { v -> onChange { it.copy(autoUpdates = v) } }) },
        )
    }
    IosSection {
        IosRow(
            title = "История изменений",
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
        IosRow("Условия использования", icon = Icons.Rounded.Description, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.TERMS) })
        IosDivider(start = divider)
        IosRow("Политика конфиденциальности", icon = Icons.Rounded.Lock, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.PRIVACY) })
        IosDivider(start = divider)
        IosRow("Лицензии открытого ПО", icon = Icons.Rounded.Code, iconTint = c.gray, chevron = true, onClick = { onOpenDocument(LegalDocs.LICENSES) })
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
        Text("Фарватер", style = IosType.title2, color = c.label)
        Text("Версия ${BuildConfig.VERSION_NAME}", style = IosType.subheadline, color = c.secondaryLabel)
    }
    IosSection {
        IosRow(
            "Ядро Xray",
            icon = Icons.Rounded.Memory,
            iconTint = c.purple,
            value = XrayEngine.version ?: if (XrayEngine.isAvailable) "встроено" else "нет",
            valueColor = if (XrayEngine.isAvailable) null else c.red,
        )
        IosDivider(start = divider)
        IosRow(
            "TUN-мост",
            icon = Icons.Rounded.SwapVert,
            iconTint = c.teal,
            value = if (TunBridge.isAvailable) "встроен" else "нет",
            valueColor = if (TunBridge.isAvailable) null else c.red,
        )
    }
    IosSection(footer = "Фарватер бесплатный и с открытым кодом. Лицензия GPL-3.0.") {
        IosRow(
            "Исходный код",
            icon = Icons.Rounded.Code,
            iconTint = c.gray,
            chevron = true,
            onClick = { runCatching { uri.openUri("https://github.com/${BuildConfig.UPDATE_REPO}") } },
        )
        IosDivider(start = divider)
        IosRow(
            "Написать разработчику",
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
                Text("Добро пожаловать\nв Фарватер", style = IosType.title1, color = c.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                Feature(Icons.Rounded.Lock, c.green, "Ваши подписки", "VLESS, Trojan, Shadowsocks, VMess и Hysteria2. Российские сервисы идут мимо VPN.")
                Feature(Icons.Rounded.Public, c.tint, "Публичные подписки", "Бесплатные серверы сообщества помогают при белых списках.")
                Feature(Icons.Rounded.Shield, c.red, "Осторожно с паролями", "Владельцы чужих серверов видят, куда вы ходите. Не вводите через них пароли.")
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Условия",
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.tint,
                        modifier = Modifier.clickable { onOpenDocument(LegalDocs.TERMS) }.padding(8.dp),
                    )
                    Text(
                        "Конфиденциальность",
                        style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = c.tint,
                        modifier = Modifier.clickable { onOpenDocument(LegalDocs.PRIVACY) }.padding(8.dp),
                    )
                }
                Text(
                    "Продолжая, вы принимаете условия использования и политику конфиденциальности.",
                    style = IosType.caption1,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                IosButton("Включить публичные", onClick = { close(true) }, modifier = Modifier.fillMaxWidth())
                IosButton(
                    "Только мои подписки",
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
