package app.farvater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.farvater.data.LEGAL_VERSION
import app.farvater.ui.components.rememberReduceMotion
import app.farvater.ui.ios.AlertAction
import app.farvater.ui.ios.AlertRole
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.screens.HomeScreen
import app.farvater.ui.screens.LegalDocs
import app.farvater.ui.screens.LegalDocument
import app.farvater.ui.screens.OnboardingDialog
import app.farvater.ui.screens.ServersScreen
import app.farvater.ui.screens.SettingsScreen
import app.farvater.ui.screens.SourcesScreen
import app.farvater.ui.screens.UpdateSheet
import app.farvater.ui.theme.EaseOutQuint
import app.farvater.vpn.VpnBus

@Composable
fun FarvaterRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }
    var reopenOnboarding by remember { mutableStateOf(false) }
    var openDocument by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.consumeMessage()
    }

    val still = rememberReduceMotion()
    FarvaterFrame(tab = tab, onTab = { tab = it }, snackbar = snackbar) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            transitionSpec = {
                if (still) {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                } else {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(340, easing = EaseOutQuint)) { it * dir } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(340, easing = EaseOutQuint)) { -it * dir } + fadeOut(tween(200)))
                }
            },
            label = "tabs",
        ) { current ->
            when (current) {
                Tab.Home -> {
                    val vpn by vm.vpnState.collectAsStateWithLifecycle()
                    val traffic by vm.traffic.collectAsStateWithLifecycle()
                    val history by vm.speedHistory.collectAsStateWithLifecycle()
                    val failed by vm.failedChecks.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        vpn = vpn,
                        traffic = traffic,
                        history = history,
                        onToggle = vm::toggleConnection,
                        onFindWorking = vm::findWorking,
                        onCancelTest = vm::cancelTest,
                        onOpenServers = { tab = Tab.Servers },
                        onOpenUpdate = vm::openUpdate,
                        onRefresh = vm::refreshAll,
                        stalled = failed >= VpnBus.STALL_CHECKS,
                    )
                }
                Tab.Servers -> ServersScreen(
                    state = state,
                    onSelect = vm::select,
                    onTestAll = { vm.testAll() },
                    onTestNode = vm::testNode,
                    onToggleGroup = vm::toggleGroup,
                    onCancelTest = vm::cancelTest,
                    onDeleteManual = vm::deleteManualNode,
                    onCopied = { vm.showMessage("Ссылка скопирована") },
                    onDuplicates = { remove -> vm.updateSettings { it.copy(dropDuplicates = remove, duplicatesAsked = true) } },
                )
                Tab.Sources -> SourcesScreen(
                    state = state,
                    onRefresh = vm::refreshAll,
                    onToggle = vm::setSourceEnabled,
                    onAddSubscription = vm::addSubscription,
                    onPaste = vm::importText,
                    onRemoveUserSource = vm::removeUserSource,
                    onEnableCommunity = { reopenOnboarding = true },
                    onRefreshSource = vm::refreshSource,
                    onNetMode = vm::setNetMode,
                    onSourceMode = vm::setSourceMode,
                    onSourceInterval = vm::setSourceInterval,
                )
                Tab.Settings -> SettingsScreen(
                    settings = state.settings,
                    onChange = vm::updateSettings,
                    onOpenDocument = { openDocument = it },
                    pageName = settingsPage,
                    onPageName = { settingsPage = it },
                    hwid = vm.hwid,
                    onCopied = { vm.showMessage("HWID скопирован") },
                    updateVersion = state.update?.versionName,
                    checkingUpdates = state.updateStage is UpdateStage.Checking,
                    onCheckUpdates = vm::openUpdate,
                    duplicates = state.duplicates,
                )
            }
        }
    }

    if (!state.settings.onboardingDone || reopenOnboarding) {
        OnboardingDialog(
            onChoice = { enable ->
                reopenOnboarding = false
                vm.finishOnboarding(enable)
            },
            onOpenDocument = { openDocument = it },
        )
    }

    if (state.settings.onboardingDone && !reopenOnboarding && state.settings.acceptedLegalVersion < LEGAL_VERSION) {
        IosAlert(
            title = "Условия обновились",
            message = "Прочитайте, что изменилось в условиях и политике конфиденциальности, и подтвердите согласие.",
            onDismiss = {},
            actions = listOf(
                AlertAction("Условия") { openDocument = LegalDocs.TERMS },
                AlertAction("Конфиденциальность") { openDocument = LegalDocs.PRIVACY },
                AlertAction("Принимаю", AlertRole.Preferred) { vm.acceptLegal() },
            ),
        )
    }

    openDocument?.let { LegalDocument(asset = it, onClose = { openDocument = null }) }

    val update = state.update
    if (state.showUpdate && update != null) {
        UpdateSheet(info = update, stage = state.updateStage, onUpdate = vm::installUpdate, onLater = vm::dismissUpdate)
    }

    state.deadPrompt?.let { node ->
        IosAlert(
            title = "«${splitFlag(node.name).second}» не отвечает",
            message = "Этот узел не ответил на последних проверках. Найти рабочий узел или всё равно подключиться к этому?",
            onDismiss = vm::dismissDeadPrompt,
            actions = listOf(
                AlertAction("Подключиться") { vm.connectAnyway() },
                AlertAction("Найти рабочий", AlertRole.Preferred) { vm.findWorkingInstead() },
            ),
        )
    }

    state.pendingImport?.let { text ->
        IosAlert(
            title = "Добавить из ссылки?",
            message = text.take(300),
            onDismiss = vm::dismissImport,
            actions = listOf(
                AlertAction("Отмена") { vm.dismissImport() },
                AlertAction("Добавить", AlertRole.Preferred) { vm.confirmImport() },
            ),
        )
    }
}
