package com.ghostlock.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostlock.app.R
import com.ghostlock.app.data.KsuManagerCatalog
import com.ghostlock.app.data.RootTerminal
import com.ghostlock.app.domain.model.CpuPair
import com.ghostlock.app.domain.model.ExecutionFieldValue
import com.ghostlock.app.domain.model.ProfileFieldNode
import com.ghostlock.app.domain.model.ShizukuStatus
import com.ghostlock.app.domain.model.UserProfileFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class GhostlockUiState(
    val deviceName: String = "",
    val kernelRelease: String = "",
    val socName: String = "",
    val kernelSupported: Boolean = false,
    val shizukuEnabled: Boolean = false,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_REQUIRED,
    val running: Boolean = false,
    val cpuPairLabels: List<String> = emptyList(),
    val cpuPairIndex: Int = 0,
    /** CPU pair from the resolved profile when it differs from the device pick. */
    val customCpuPair: CpuPair? = null,
    val safeModeEnabled: Boolean = false,
    val forceAttackTestEnabled: Boolean = false,
    val tcpRouteEnabled: Boolean = true,
    val compact: Boolean = false,
    val executionSheetVisible: Boolean = false,
    val executionSheetDismissible: Boolean = false,
    val dialogVisible: Boolean = false,
    val dialogType: DialogType = DialogType.NONE,
    val dialogTitleRes: Int = 0,
    val dialogMessage: String = "",
    val dialogMessageRes: Int = 0,
    val dialogItems: List<String> = emptyList(),
    val dialogItemResIds: List<Int> = emptyList(),
    val dialogCurrentItemIndex: Int = -1,
    val dialogInput: String = "",
    val dialogConfirmLabelRes: Int = R.string.parse_start,
    /** Documentation URL shown as an extra button on a NOTICE dialog. */
    val dialogDocUrl: String? = null,
    val overwriteDialogVisible: Boolean = false,
    val overwriteMessage: String = "",
    val logLines: List<GhostlockLogLine> = emptyList(),
    val executionRelease: String = "",
    val executionHasProfile: Boolean = false,
    val executionFields: List<ExecutionFieldValue> = emptyList(),
    val executionEditing: Map<String, String> = emptyMap(),
    val advancedScreenVisible: Boolean = false,
    val debugExportEnabled: Boolean = true,
    val debugExportLocation: String = "",
    val debugKernelLogEnabled: Boolean = true,
    val aboutVisible: Boolean = false,
    val parametersVisible: Boolean = false,
    val profileOverrideVisible: Boolean = false,
    val advancedOverrideVisible: Boolean = false,
    /** Stored document edited by the open session; null for the builtin. */
    val editTargetName: String? = null,
    val profileOverrideRelease: String = "",
    val profileOverrideRoots: List<ProfileFieldNode> = emptyList(),
    val profileOverrideEditing: Map<String, String> = emptyMap(),
    /** Controller-reported geometry violations, dotted paths. */
    val profileInvalidPaths: Set<String> = emptySet(),
    /** Explicit route from the profile; null means geometry inference. */
    val profileRoute: String? = null,
    /** Declared fallback route; null/"none" means disabled. */
    val profileFallback: String? = null,
    /** Manually selected builtin source; null means automatic matching. */
    val activeBuiltinProfile: String? = null,
    val builtinScreenVisible: Boolean = false,
    /** Unfilled reference templates, listed separately on the builtin picker. */
    val builtinTemplates: List<String> = emptyList(),
    /** Builtin releases sorted by similarity to the device kernel. */
    val builtinProfiles: List<String> = emptyList(),
    val loadConfigVisible: Boolean = false,
    /** Verbatim documents in the user profile folder, newest first. */
    val userProfiles: List<UserProfileFile> = emptyList(),
    /** Loaded user document feeding the imported layer; null means none. */
    val activeUserProfile: String? = null,
    /** File name of the open user-profile detail screen, null when closed. */
    val userProfileDetail: String? = null,
    val userProfileRenameTarget: String? = null,
    val userProfileDeleteTarget: String? = null,
)

enum class DialogType { NONE, LIST, INPUT, CONFIRM, NOTICE }

data class GhostlockLogLine(val text: String, val color: Int)

interface GhostlockActions {
    fun onRun()
    fun onProfileInvalid()
    fun onStatusClick()
    fun onCloseExecutionSheet()
    fun onCopyLogs()
    fun onImportOffsetsHocon()
    fun onImportOffsetsJson()
    fun onDocumentsResult(request: DocumentRequest, uris: List<String>)
    fun onParseOta()
    fun onParseImage()
    fun onCpuPairSelected(index: Int)
    fun onSafeModeChanged(enabled: Boolean)
    fun onForceAttackTestChanged(enabled: Boolean)
    fun onShizukuChanged(enabled: Boolean)
    fun onDialogItemSelected(index: Int)
    fun onDialogInputChange(value: String)
    fun onDialogConfirm(value: String)
    fun onDialogDismiss()
    fun onDialogDismissFinished()
    fun onOverwriteConfirm()
    fun onOverwriteDismiss()
    fun onExecutionFieldChanged(path: String, value: String)
    fun onRouteChanged(index: Int)
    fun onFallbackChanged(index: Int)
    fun onExportProfile()
    fun onSaveProfileEdits()
    fun onSaveProfileAs()
    fun onExportProfileEdits()
    fun onRevertProfileEdits()
    fun onOpenAdvanced()
    fun onCloseAdvanced()
    fun onShowAbout()
    fun onCloseAbout()
    fun onDebugExportChanged(enabled: Boolean)
    fun onDebugExportLocationPick()
    fun onDebugKernelLogChanged(enabled: Boolean)
    fun onOpenParameters()
    fun onCloseParameters()
    fun onOpenLoadConfig()
    fun onCloseLoadConfig()
    fun onOpenUserProfileDetail(name: String)
    fun onCloseUserProfileDetail()
    fun onLoadUserProfile(name: String)
    fun onUnloadUserProfile()
    fun onEditUserProfile(name: String)
    fun onUserProfileRename(name: String)
    fun onUserProfileExport(name: String)
    fun onConvertUserProfile(name: String)
    fun onUserProfileDelete(name: String)
    fun onUserProfileDeleteConfirm()
    fun onUserProfileDeleteDismiss()
    fun onOpenBuiltinProfiles()
    fun onCloseBuiltinProfiles()
    fun onSelectBuiltinProfile(release: String?)
    fun onOpenProfileOverrides()
    fun onCloseProfileOverrides()
    fun onOpenAdvancedOverrides()
    fun onCloseAdvancedOverrides()
    fun onProfileOverrideChanged(path: String, value: String)
}

internal sealed interface GhostlockScreen : NavKey {
    data object Main : GhostlockScreen
    data object Advanced : GhostlockScreen
    data object About : GhostlockScreen
    data object Parameters : GhostlockScreen
    data object LoadConfig : GhostlockScreen
    data object Builtin : GhostlockScreen
    data class UserProfileDetail(val name: String) : GhostlockScreen
    data object ProfileOverride : GhostlockScreen
    data object AdvancedOverride : GhostlockScreen
}

internal fun navigationPath(state: GhostlockUiState): List<GhostlockScreen> {
    val path = mutableListOf<GhostlockScreen>(GhostlockScreen.Main)
    if (!state.advancedScreenVisible) return path
    path += GhostlockScreen.Advanced
    if (state.aboutVisible) {
        path += GhostlockScreen.About
        return path
    }
    if (!state.parametersVisible) return path
    path += GhostlockScreen.Parameters
    if (state.loadConfigVisible) {
        path += GhostlockScreen.LoadConfig
        when {
            state.builtinScreenVisible -> path += GhostlockScreen.Builtin
            state.userProfileDetail != null ->
                path += GhostlockScreen.UserProfileDetail(state.userProfileDetail)
        }
    }
    if (state.profileOverrideVisible) {
        path += GhostlockScreen.ProfileOverride
        if (state.advancedOverrideVisible) path += GhostlockScreen.AdvancedOverride
    }
    return path
}

internal fun syncNavigationPath(backStack: NavBackStack, desired: List<GhostlockScreen>) {
    var common = 0
    while (common < backStack.size && common < desired.size &&
        backStack[common] == desired[common]
    ) {
        common++
    }
    while (backStack.size > common) backStack.removeAt(backStack.lastIndex)
    backStack.addAll(desired.drop(common))
}

private fun closeScreen(screen: GhostlockScreen, actions: GhostlockActions) {
    when (screen) {
        GhostlockScreen.Main -> Unit
        GhostlockScreen.Advanced -> actions.onCloseAdvanced()
        GhostlockScreen.About -> actions.onCloseAbout()
        GhostlockScreen.Parameters -> actions.onCloseParameters()
        GhostlockScreen.LoadConfig -> actions.onCloseLoadConfig()
        GhostlockScreen.Builtin -> actions.onCloseBuiltinProfiles()
        is GhostlockScreen.UserProfileDetail -> actions.onCloseUserProfileDetail()
        GhostlockScreen.ProfileOverride -> actions.onCloseProfileOverrides()
        GhostlockScreen.AdvancedOverride -> actions.onCloseAdvancedOverrides()
    }
}

@Composable
internal fun GhostlockApp(
    state: GhostlockUiState,
    actions: GhostlockActions,
) {
    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        val desiredPath = navigationPath(state)
        val backStack = remember {
            navBackStackOf(GhostlockScreen.Main).apply { addAll(desiredPath.drop(1)) }
        }
        LaunchedEffect(desiredPath) { syncNavigationPath(backStack, desiredPath) }
        val swipeBack = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            NavSwipeDirection.RightToLeft
        } else {
            NavSwipeDirection.LeftToRight
        }
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                    onBack = {
                        when {
                            state.executionSheetVisible -> {
                                if (state.executionSheetDismissible) actions.onCloseExecutionSheet()
                            }

                            state.overwriteDialogVisible -> actions.onOverwriteDismiss()
                            state.dialogVisible -> actions.onDialogDismiss()
                            state.userProfileDeleteTarget != null -> actions.onUserProfileDeleteDismiss()
                            else -> {
                                val screen = backStack.lastOrNull() as? GhostlockScreen
                                if (screen != null && screen != GhostlockScreen.Main) {
                                    closeScreen(screen, actions)
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            }
                        }
                    },
                ) {
                    entry<GhostlockScreen.Main>(swipeDismiss = NavSwipeDirection.None) {
                        MainScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.Advanced>(swipeDismiss = swipeBack) {
                        AdvancedScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.About>(swipeDismiss = swipeBack) {
                        AboutScreen(onBack = actions::onCloseAbout)
                    }
                    entry<GhostlockScreen.Parameters>(swipeDismiss = swipeBack) {
                        ParameterScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.LoadConfig>(swipeDismiss = swipeBack) {
                        LoadConfigScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.Builtin>(swipeDismiss = swipeBack) {
                        BuiltinProfileScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.UserProfileDetail>(swipeDismiss = swipeBack) { screen ->
                        UserProfileDetailScreen(state = state, actions = actions, name = screen.name)
                    }
                    entry<GhostlockScreen.ProfileOverride>(swipeDismiss = swipeBack) {
                        ProfileOverrideScreen(state = state, actions = actions)
                    }
                    entry<GhostlockScreen.AdvancedOverride>(swipeDismiss = swipeBack) {
                        AdvancedOverrideScreen(state = state, actions = actions)
                    }
                }
                GhostlockDialog(state = state, actions = actions)
                GhostlockOverwriteDialog(state = state, actions = actions)
                GhostlockExecutionSheet(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: GhostlockUiState,
    actions: GhostlockActions,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var terminalVisible by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = "GhostLock For IQOO Neo9",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        MainContent(
            state = state,
            actions = actions,
            scrollBehavior = scrollBehavior,
            scaffoldPadding = paddingValues,
            onOpenTerminal = { terminalVisible = true },
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (terminalVisible) {
        TerminalOverlay(onClose = { terminalVisible = false })
    }
}

@Composable
internal fun pageContentPadding(
    scaffoldPadding: PaddingValues,
    top: Dp = 8.dp,
    bottom: Dp = 12.dp,
    horizontalMin: Dp = 12.dp,
): PaddingValues {
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val horizontal = ((windowWidth - 800.dp) / 2).coerceAtLeast(horizontalMin)
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = scaffoldPadding.calculateTopPadding() + top,
        bottom = scaffoldPadding.calculateBottomPadding() + bottom,
    )
}

@Composable
private fun GhostlockExecutionSheet(
    state: GhostlockUiState,
    actions: GhostlockActions,
) {
    OverlayBottomSheet(
        show = state.executionSheetVisible,
        title = stringResource(R.string.log_title),
        allowDismiss = state.executionSheetDismissible,
        onDismissRequest = actions::onCloseExecutionSheet,
        startAction = {
            IconButton(onClick = actions::onCopyLogs) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            IconButton(
                enabled = state.executionSheetDismissible,
                onClick = actions::onCloseExecutionSheet,
            ) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
        content = {
            LogPanel(
                lines = state.logLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 520.dp)
                    .navigationBarsPadding(),
            )
        },
    )
}

@Composable
private fun GhostlockDialog(
    state: GhostlockUiState,
    actions: GhostlockActions,
) {
    OverlayDialog(
        show = state.dialogVisible,
        title = if (state.dialogType == DialogType.NONE) null else stringResource(state.dialogTitleRes),
        onDismissRequest = actions::onDialogDismiss,
        onDismissFinished = actions::onDialogDismissFinished,
        content = {
            when (state.dialogType) {
                DialogType.LIST -> {
                    val items = state.dialogItems.ifEmpty { state.dialogItemResIds.map { stringResource(it) } }
                    items.forEachIndexed { index, item ->
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            text = if (index == state.dialogCurrentItemIndex) {
                                stringResource(R.string.export_current_marker, item)
                            } else {
                                item
                            },
                            onClick = { actions.onDialogItemSelected(index) },
                        )
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.cancel),
                        onClick = actions::onDialogDismiss,
                    )
                }

                DialogType.INPUT -> {
                    TextField(
                        value = state.dialogInput,
                        onValueChange = actions::onDialogInputChange,
                        label = stringResource(state.dialogMessageRes),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.cancel),
                            onClick = actions::onDialogDismiss,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(state.dialogConfirmLabelRes),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = { actions.onDialogConfirm(state.dialogInput) },
                        )
                    }
                }

                DialogType.CONFIRM -> {
                    Text(
                        text = stringResource(state.dialogMessageRes),
                        modifier = Modifier.fillMaxWidth(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.cancel),
                            onClick = actions::onDialogDismiss,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.w3_shizuku_hint_enable),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = { actions.onDialogConfirm("") },
                        )
                    }
                }

                DialogType.NOTICE -> {
                    Text(
                        text = stringResource(state.dialogMessageRes),
                        modifier = Modifier.fillMaxWidth(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) {
                        state.dialogDocUrl?.let { docUrl ->
                            val uriHandler = LocalUriHandler.current
                            TextButton(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.dialog_open_guide),
                                onClick = { uriHandler.openUri(docUrl) },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.dialog_dismiss),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = actions::onDialogDismiss,
                        )
                    }
                }

                DialogType.NONE -> Unit
            }
        },
    )
}

@Composable
private fun GhostlockOverwriteDialog(
    state: GhostlockUiState,
    actions: GhostlockActions,
) {
    OverlayDialog(
        show = state.overwriteDialogVisible,
        title = stringResource(R.string.overwrite_title),
        summary = stringResource(R.string.overwrite_message, state.overwriteMessage),
        onDismissRequest = actions::onOverwriteDismiss,
        content = {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = actions::onOverwriteDismiss,
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.overwrite_yes),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = actions::onOverwriteConfirm,
                )
            }
        },
    )
}

@Composable
private fun MainContent(
    state: GhostlockUiState,
    actions: GhostlockActions,
    scrollBehavior: ScrollBehavior,
    scaffoldPadding: PaddingValues,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .scrollEndHaptic()
            .fillMaxHeight()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        contentPadding = pageContentPadding(scaffoldPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "controls") {
            ControlPanel(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "debug_terminal") {
            DebugTerminalCard(onOpen = onOpenTerminal, modifier = Modifier.fillMaxWidth())
        }
        item(key = "run") {
            RunButton(
                running = state.running,
                supported = state.kernelSupported &&
                        state.executionHasProfile &&
                        (!state.shizukuEnabled ||
                                state.shizukuStatus == ShizukuStatus.READY),
                profileValid = state.profileInvalidPaths.isEmpty(),
                labelRes = R.string.action_run,
                onClick = actions::onRun,
                onBlockedClick = actions::onProfileInvalid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ControlPanel(
    state: GhostlockUiState,
    actions: GhostlockActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActivationStatusCard(
            supported = state.kernelSupported,
            profileAvailable = state.executionHasProfile,
            profileValid = state.profileInvalidPaths.isEmpty(),
            shizukuEnabled = state.shizukuEnabled,
            shizukuStatus = state.shizukuStatus,
            onParametersClick = actions::onOpenParameters,
            onShizukuClick = actions::onStatusClick,
            modifier = Modifier.fillMaxWidth(),
        )
        DeviceInfoCard(
            deviceName = state.deviceName,
            socName = state.socName,
            kernelRelease = state.kernelRelease,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Card(modifier = modifier.padding(top = 12.dp)) {
            if (state.cpuPairLabels.isNotEmpty()) {
                val customPair = state.customCpuPair
                val customSummary = customPair?.let {
                    stringResource(
                        R.string.cpu_pair_custom,
                        "${it.primary}, ${it.consumer}",
                    )
                }
                OverlaySpinnerPreference(
                    title = stringResource(R.string.cpu_pair_label),
                    items = state.cpuPairLabels.map { DropdownItem(icon = null, title = it) },
                    selectedIndex = if (customPair == null) state.cpuPairIndex else -1,
                    summary = customSummary,
                    showValue = customPair == null,
                    onSelectedIndexChange = actions::onCpuPairSelected,
                )
            }
            SwitchPreference(
                checked = state.safeModeEnabled,
                onCheckedChange = actions::onSafeModeChanged,
                title = stringResource(R.string.safe_mode_label),
                summary = stringResource(R.string.safe_mode_summary),
            )
            /* 调试用：只加载 vrpatch.ko 并保留临时 root，不激活 KernelSU */
            var tempRootOnly by remember { mutableStateOf(RootTerminal.debugRootOnly) }
            SwitchPreference(
                checked = tempRootOnly,
                onCheckedChange = {
                    tempRootOnly = it
                    RootTerminal.debugRootOnly = it
                },
                title = stringResource(R.string.terminal_skip_ksu),
                summary = stringResource(R.string.terminal_skip_ksu_summary),
            )
            /* PROFILE-SUGGEST-01: the profile suggestion seeds the toggle but no
             * longer hides it; an explicit user choice overrides either way. */
            SwitchPreference(
                checked = state.shizukuEnabled,
                onCheckedChange = actions::onShizukuChanged,
                title = stringResource(R.string.shizuku_label),
                summary = stringResource(
                    when {
                        !state.shizukuEnabled -> R.string.shizuku_summary
                        state.shizukuStatus == ShizukuStatus.READY -> R.string.shizuku_status_ready
                        state.shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED ->
                            R.string.shizuku_status_permission_required

                        state.shizukuStatus == ShizukuStatus.NOT_RUNNING ->
                            R.string.shizuku_status_not_running

                        else -> R.string.shizuku_status_checking
                    },
                ),
            )
        }
        KsuManagerPickerCard(modifier = modifier.padding(top = 12.dp))
        Card(modifier = modifier.padding(top = 12.dp)) {
            ArrowPreference(
                title = stringResource(R.string.advanced_settings),
                summary = stringResource(R.string.advanced_settings_summary),
                onClick = actions::onOpenAdvanced,
            )
        }
    }
}

@Composable
private fun ActivationStatusCard(
    supported: Boolean,
    profileAvailable: Boolean,
    profileValid: Boolean,
    shizukuEnabled: Boolean,
    shizukuStatus: ShizukuStatus,
    onParametersClick: () -> Unit,
    onShizukuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessReady = !shizukuEnabled || shizukuStatus == ShizukuStatus.READY
    val ready = supported && profileAvailable && profileValid && accessReady
    val missing = !supported || !profileAvailable
    val (backgroundColor, title, icon) = when {
        ready -> Triple(
            when {
                MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
                isSystemInDarkTheme() -> Color(0xFF1A3825)
                else -> Color(0xFFDFFAE4)
            },
            R.string.kernel_supported,
            Icons.Rounded.CheckCircleOutline,
        )

        missing -> Triple(
            MiuixTheme.colorScheme.errorContainer,
            if (!supported) R.string.kernel_unsupported else R.string.kernel_profile_required,
            Icons.Rounded.RemoveCircleOutline,
        )

        else -> Triple(
            when {
                MiuixTheme.isDynamicColor -> MiuixTheme.colorScheme.tertiaryContainer
                isSystemInDarkTheme() -> Color(0xFF3E2F1B)
                else -> Color(0xFFFFF0DB)
            },
            if (!profileValid) R.string.kernel_profile_invalid else R.string.shizuku_label,
            Icons.Rounded.ErrorOutline,
        )
    }
    val summary = when {
        ready && shizukuEnabled -> R.string.kernel_profile_ready_shizuku
        ready -> R.string.kernel_profile_ready
        !supported -> R.string.kernel_unsupported_summary
        !profileAvailable -> R.string.kernel_profile_required_summary
        !profileValid -> R.string.kernel_profile_invalid_summary
        shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED ->
            R.string.shizuku_status_permission_required

        else -> R.string.shizuku_status_not_running
    }
    val action = if (supported && profileAvailable && profileValid && !accessReady) {
        onShizukuClick
    } else {
        onParametersClick
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = backgroundColor),
        onClick = action,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (MiuixTheme.isDynamicColor) {
                    when (icon) {
                        Icons.Rounded.CheckCircleOutline -> MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                        Icons.Rounded.RemoveCircleOutline ->
                            MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)

                        else -> MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    }
                } else {
                    when (icon) {
                        Icons.Rounded.CheckCircleOutline -> Color(0xFF36D167)
                        Icons.Rounded.RemoveCircleOutline -> Color(0xFFF5A623)
                        else -> Color(0xFFF72727)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 27.dp, y = 31.dp)
                    .size(110.dp),
            )
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 120.dp, bottom = 14.dp),
            ) {
                Text(
                    text = stringResource(title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = stringResource(summary),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceName: String,
    socName: String,
    kernelRelease: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DeviceInfoItem(
                    title = stringResource(R.string.device_label),
                    value = deviceName,
                )
                DeviceInfoItem(
                    title = stringResource(R.string.soc_label),
                    value = socName,
                )
                DeviceInfoItem(
                    title = stringResource(R.string.kernel_label),
                    value = kernelRelease,
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}

/* profile-ui: resolved execution view with auto-saved sparse overrides. */
@Composable
internal fun ExecutionEditor(
    state: GhostlockUiState,
    actions: GhostlockActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (field in state.executionFields) {
            val text = state.executionEditing[field.path] ?: field.value.toString()
            val invalid = isFieldInputInvalid(text) || field.path in state.profileInvalidPaths
            TextField(
                value = text,
                onValueChange = { value -> actions.onExecutionFieldChanged(field.path, value) },
                label = fieldLabel(field.path, field.path.substringAfterLast('.')),
                colors = when {
                    invalid ->
                        TextFieldDefaults.textFieldColors(labelColor = FieldErrorHighlight)

                    field.overridden ->
                        TextFieldDefaults.textFieldColors(labelColor = OverrideHighlight)

                    else -> TextFieldDefaults.textFieldColors()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

internal val OverrideHighlight = Color(0xFFF5A623)

/** Red marks an unfilled or non-numeric field in the parameter editors. */
internal val FieldErrorHighlight = Color(0xFFE53935)

internal fun isFieldInputInvalid(text: String): Boolean = text.trim().toLongOrNull() == null

/** 终端状态文案：把 RootTerminal.diagnose 的结果映射到提示。 */
/** 第三方 KSU 管理器选择：决定 ksud 来源与 late-load 的 --package-name / --kmi。 */
@Composable
private fun KsuManagerPickerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(KsuManagerCatalog.selectedIndex(context)) }
    var custom by remember {
        mutableStateOf(
            if (KsuManagerCatalog.needsCustomPackage(context)) {
                KsuManagerCatalog.selectedPackage(context)
            } else {
                ""
            },
        )
    }
    var kmi by remember { mutableStateOf(KsuManagerCatalog.kmi(context)) }
    val manager = KsuManagerCatalog.managers[index]
    val resolved = manager.packageName.ifBlank { custom.trim() }
    val installed = resolved.isNotBlank() && runCatching {
        context.packageManager.getApplicationInfo(resolved, 0)
    }.isSuccess
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            OverlaySpinnerPreference(
                title = stringResource(R.string.ksu_picker_title),
                items = KsuManagerCatalog.managers.map { DropdownItem(icon = null, title = it.label) },
                selectedIndex = index,
                summary = if (resolved.isBlank()) {
                    stringResource(R.string.ksu_picker_custom_label)
                } else {
                    stringResource(
                        if (installed) R.string.ksu_picker_summary_installed
                        else R.string.ksu_picker_summary_missing,
                        resolved,
                    )
                },
                showValue = true,
                onSelectedIndexChange = { selected ->
                    index = selected
                    KsuManagerCatalog.save(context, KsuManagerCatalog.managers[selected], custom)
                },
            )
            if (manager.packageName.isBlank()) {
                TextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        KsuManagerCatalog.save(context, manager, it)
                    },
                    label = stringResource(R.string.ksu_picker_custom_hint),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            TextField(
                value = kmi,
                onValueChange = {
                    kmi = it
                    KsuManagerCatalog.saveKmi(context, it)
                },
                label = stringResource(R.string.ksu_kmi_label),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.ksu_picker_hint),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun terminalStatusText(status: RootTerminal.Status): String = when (status) {
    RootTerminal.Status.READY -> stringResource(R.string.terminal_ready)
    RootTerminal.Status.KSU_ACTIVE -> stringResource(R.string.terminal_state_ksu_active)
    RootTerminal.Status.STALE -> stringResource(R.string.terminal_state_stale)
    RootTerminal.Status.NOT_RUN -> stringResource(R.string.terminal_state_not_run)
}

/** 调试终端入口：用临时 root 执行命令，不依赖 KernelSU。 */
@Composable
private fun DebugTerminalCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(RootTerminal.diagnose(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            status = RootTerminal.diagnose(context)
            delay(1500)
        }
    }
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.terminal_title),
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = terminalStatusText(status),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(
                text = stringResource(R.string.terminal_open),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}

/** 终端最多保留的行数；超出丢弃最旧行，密集输出（dmesg/logcat）不会拖垮界面。 */
private const val TERMINAL_MAX_LINES = 3_000

/** IO 线程累积缓冲上限：防止 `while true; do echo x; done` 这类命令吃爆内存。 */
private const val TERMINAL_BUFFER_LIMIT = 256 * 1024

/** 渲染节拍：输出批量并入列表，最密 100ms 重组一次。 */
private const val TERMINAL_RENDER_MS = 100L

/** 单条命令的默认超时（可被"停止"提前终止）。 */
private const val TERMINAL_TIMEOUT_S = 60L

private fun terminalModeText(context: Context, mode: RootTerminal.Mode): String = context.getString(
    when (mode) {
        RootTerminal.Mode.SU -> R.string.terminal_mode_su
        RootTerminal.Mode.TEMP_ROOT -> R.string.terminal_mode_temp_root
        RootTerminal.Mode.LOCAL -> R.string.terminal_mode_local
    },
)

/**
 * 把累积输出并入行列表：只把**完整行**放进列表，最后半行留在 [partial]（单独渲染，
 * 避免每个字节都重组整个列表）；行数超上限时从头丢弃。通道探测的噪声行会被过滤。
 */
private fun mergeTerminalOutput(
    lines: MutableList<String>,
    partial: String,
    chunk: String,
): String {
    val text = (partial + chunk).replace("\r\n", "\n").replace('\r', '\n')
    var start = 0
    var index = text.indexOf('\n')
    while (index >= 0) {
        val line = text.substring(start, index)
        if (!line.contains(RootTerminal.PROBE_PREFIX)) lines.add(line)
        start = index + 1
        index = text.indexOf('\n', start)
    }
    val overflow = lines.size - TERMINAL_MAX_LINES
    if (overflow > 0) repeat(overflow) { lines.removeAt(0) }
    return text.substring(start)
}

/**
 * 全屏 root 终端。三种后端（管理器 su / 临时 root 文件通道 / 应用权限）由
 * [RootTerminal.resolveMode] 自动选择：KernelSU 激活后原版 KSU init 会收掉启动脚本，
 * 文件通道随之失效 —— 此时必须走管理器提供的 `su`，否则就是"只回显、无输出"。
 */
@Composable
private fun TerminalOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    val buffer = remember { StringBuilder() }
    val bufferLock = remember { Any() }
    val listState = rememberLazyListState()
    var partial by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<RootTerminal.Mode?>(null) }
    var status by remember { mutableStateOf(RootTerminal.diagnose(context)) }

    fun feed(text: String) {
        synchronized(bufferLock) {
            if (buffer.length > TERMINAL_BUFFER_LIMIT) {
                buffer.delete(0, buffer.length - TERMINAL_BUFFER_LIMIT / 2)
            }
            buffer.append(text)
        }
    }

    /* 渲染节拍 */
    LaunchedEffect(Unit) {
        while (true) {
            val chunk = synchronized(bufferLock) {
                val text = buffer.toString()
                buffer.setLength(0)
                text
            }
            if (chunk.isNotEmpty()) partial = mergeTerminalOutput(lines, partial, chunk)
            delay(TERMINAL_RENDER_MS)
        }
    }
    /* 后端探测 + 运行状态（KSU 激活会改变可用通道）。
     * su 一旦可用就固定用它；其余情况周期复查（本地 3s、文件通道 10s，避免频繁探活写命令）。 */
    LaunchedEffect(Unit) {
        var nextProbeAt = 0L
        while (true) {
            status = RootTerminal.diagnose(context)
            val now = System.currentTimeMillis()
            val current = mode
            if (current == null || (current != RootTerminal.Mode.SU && now >= nextProbeAt)) {
                val resolved = RootTerminal.resolveMode(context)
                nextProbeAt = now + if (resolved == RootTerminal.Mode.TEMP_ROOT) 10_000L else 3_000L
                if (resolved != current) {
                    mode = resolved
                    feed(terminalModeText(context, resolved) + "\n")
                }
            }
            delay(1_000)
        }
    }
    /* 文件通道模式：tail .gl_out */
    LaunchedEffect(mode) {
        if (mode != RootTerminal.Mode.TEMP_ROOT) return@LaunchedEffect
        var offset = RootTerminal.outLength(context)
        while (true) {
            val (chunk, next) = RootTerminal.readNew(context, offset)
            if (chunk.isNotEmpty()) {
                offset = next
                feed(chunk)
            }
            delay(300)
        }
    }
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lines.isEmpty() || last >= lines.lastIndex
        }
    }
    LaunchedEffect(lines.size, partial) {
        if (atBottom && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.terminal_title),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    TextButton(
                        text = stringResource(R.string.terminal_stop),
                        onClick = {
                            RootTerminal.cancelCurrent()
                            feed("! stopped\n")
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    text = stringResource(R.string.terminal_clear),
                    onClick = {
                        RootTerminal.clear(context)
                        lines.clear()
                        partial = ""
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = stringResource(R.string.terminal_close),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = terminalStatusText(status),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = when {
                    mode == null -> stringResource(R.string.terminal_mode_probing)
                    mode == RootTerminal.Mode.LOCAL && RootTerminal.ksuLoaded() ->
                        stringResource(R.string.terminal_ksu_no_root)
                    else -> terminalModeText(context, mode!!)
                },
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B1220)),
            ) {
                if (lines.isEmpty() && partial.isEmpty()) {
                    Text(
                        text = stringResource(R.string.terminal_summary),
                        color = Color(0xFFD1D5DB),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        items(count = lines.size) { index ->
                            SelectionContainer {
                                Text(
                                    text = lines[index],
                                    color = Color(0xFFD1D5DB),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                        if (partial.isNotEmpty()) {
                            item(key = "__partial__") {
                                Text(
                                    text = partial,
                                    color = Color(0xFFD1D5DB),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = stringResource(R.string.terminal_input_hint),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.terminal_send),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = !busy,
                    onClick = {
                        val command = input.trim()
                        input = ""
                        if (command.isEmpty()) return@TextButton
                        scope.launch {
                            busy = true
                            val current = mode ?: RootTerminal.resolveMode(context).also { mode = it }
                            when (current) {
                                RootTerminal.Mode.TEMP_ROOT -> {
                                    /* 文件通道由 root 侧回显 `$ cmd`，这里不重复回显 */
                                    if (!RootTerminal.submit(context, command)) {
                                        feed("! cannot submit command\n")
                                    }
                                }
                                RootTerminal.Mode.SU, RootTerminal.Mode.LOCAL -> {
                                    feed("$ $command\n")
                                    val result = RootTerminal.exec(
                                        command = command,
                                        mode = current,
                                        timeoutSeconds = TERMINAL_TIMEOUT_S,
                                        suBinary = if (current == RootTerminal.Mode.SU) {
                                            RootTerminal.suBinary()
                                        } else {
                                            null
                                        },
                                    ) { feed(it) }
                                    when {
                                        result.timedOut -> feed(
                                            context.getString(
                                                R.string.terminal_timeout,
                                                TERMINAL_TIMEOUT_S.toInt(),
                                            ) + "\n",
                                        )
                                        result.error != null -> feed("! ${result.error}\n")
                                        result.exitCode != null -> feed("[exit ${result.exitCode}]\n")
                                    }
                                }
                            }
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RunButton(
    running: Boolean,
    supported: Boolean,
    profileValid: Boolean,
    labelRes: Int,
    onClick: () -> Unit,
    onBlockedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        TextButton(
            text = stringResource(if (running) R.string.action_running else labelRes),
            enabled = supported && profileValid && !running,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        /* A disabled TextButton consumes no pointer input, so this overlay
         * explains why the run is blocked. */
        if (!running && (!supported || !profileValid)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onBlockedClick() },
            )
        }
    }
}

@Composable
private fun LogPanel(
    lines: List<GhostlockLogLine>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B1220))
            .padding(12.dp),
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text.trimEnd('\r', '\n'),
                        color = lineColor(line.color),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

private fun lineColor(color: Int): Color = when (color) {
    0xFFFF6B6B.toInt() -> Color(0xFFFF6B6B)
    0xFF5FD68A.toInt() -> Color(0xFF5FD68A)
    0xFFFFC94D.toInt() -> Color(0xFFFFC94D)
    0xFF60A5FA.toInt() -> Color(0xFF60A5FA)
    else -> Color(0xFFD1D5DB)
}
