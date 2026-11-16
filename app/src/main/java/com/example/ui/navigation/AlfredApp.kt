package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.FinanceScope
import com.example.data.remote.AlfredAiService
import com.example.data.remote.GoogleAuthHelper
import com.example.data.repository.FinanceRepository
import com.example.ui.components.CalculatorEntrySheet
import com.example.ui.screens.AlfredChatScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.GothamHairline
import com.example.ui.theme.GothamHairlineStrong
import com.example.ui.theme.GothamObsidian
import com.example.ui.theme.GothamPureWhite
import com.example.ui.theme.GothamSilver
import com.example.ui.theme.GothamSlate
import com.example.ui.theme.GothamSurfaceElevated
import com.example.ui.theme.GothamTranslucentDock
import com.example.ui.theme.GothamVoid
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_FINANCES = "finances"
const val ROUTE_BUSINESS = "business"
const val ROUTE_ALFRED = "alfred"
const val ROUTE_ANALYTICS = "analytics"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val NAV_TABS = listOf(
    NavTab(
        route = ROUTE_FINANCES,
        label = "Finanças",
        selectedIcon = Icons.Rounded.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_tab_finances",
    ),
    NavTab(
        route = ROUTE_BUSINESS,
        label = "Empresa",
        selectedIcon = Icons.Rounded.BusinessCenter,
        unselectedIcon = Icons.Outlined.BusinessCenter,
        testTag = "nav_tab_business",
    ),
    NavTab(
        route = ROUTE_ALFRED,
        label = "Alfred",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_tab_alfred",
    ),
    NavTab(
        route = ROUTE_ANALYTICS,
        label = "Análise",
        selectedIcon = Icons.Rounded.PieChart,
        unselectedIcon = Icons.Outlined.PieChart,
        testTag = "nav_tab_analytics",
    ),
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AlfredApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(context)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAlfredShell(currentUser = user)
        }
    }
}

@Composable
private fun AuthenticatedAlfredShell(currentUser: FirebaseUser) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uid = currentUser.uid

    val financeViewModel: FinanceViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FinanceViewModel(
                    repository = FinanceRepository(db, Firebase.auth),
                    aiService = AlfredAiService(),
                    currentUserId = uid,
                )
            }
        }
    )

    var currentRoute by rememberSaveable { mutableStateOf(ROUTE_FINANCES) }
    var showCalculatorSheet by remember { mutableStateOf(false) }
    var chatActiveScope by rememberSaveable { mutableStateOf(FinanceScope.PERSONAL) }

    val selectedMonthKey by financeViewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val hideValues by financeViewModel.hideValues.collectAsStateWithLifecycle()
    val personalSummary by financeViewModel.personalSummary.collectAsStateWithLifecycle()
    val businessSummary by financeViewModel.businessSummary.collectAsStateWithLifecycle()
    val personalInsight by financeViewModel.personalInsight.collectAsStateWithLifecycle()
    val businessInsight by financeViewModel.businessInsight.collectAsStateWithLifecycle()
    val chatMessagesState by financeViewModel.chatMessagesState.collectAsStateWithLifecycle()
    val chatStatus by financeViewModel.chatStatus.collectAsStateWithLifecycle()
    val actionError by financeViewModel.actionError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionError) {
        val msg = actionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            financeViewModel.clearActionError()
        }
    }

    BackHandler(enabled = currentRoute != ROUTE_FINANCES) {
        currentRoute = ROUTE_FINANCES
    }

    val firstName = currentUser.displayName
        ?.trim()
        ?.split(" ")
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Senhor"

    val activeQuickAddScope = when (currentRoute) {
        ROUTE_BUSINESS -> FinanceScope.BUSINESS
        ROUTE_ALFRED -> chatActiveScope
        else -> FinanceScope.PERSONAL
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GothamVoid),
    ) {
        val useNavigationRail = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = GothamVoid,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
            if (useNavigationRail) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    NavigationRail(
                        containerColor = GothamObsidian,
                        contentColor = GothamPureWhite,
                        header = {
                            FloatingActionButton(
                                onClick = { showCalculatorSheet = true },
                                containerColor = GothamPureWhite,
                                contentColor = GothamVoid,
                                shape = CircleShape,
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .testTag("quick_add_fab"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Lançamento rápido")
                            }
                        },
                    ) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentRoute = tab.route },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = GothamVoid,
                                    selectedTextColor = GothamPureWhite,
                                    indicatorColor = GothamPureWhite,
                                    unselectedIconColor = GothamSilver,
                                    unselectedTextColor = GothamSilver,
                                ),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        AlfredScreenHost(
                            currentRoute = currentRoute,
                            firstName = firstName,
                            photoUrl = currentUser.photoUrl?.toString(),
                            selectedMonthKey = selectedMonthKey,
                            hideValues = hideValues,
                            personalSummary = personalSummary,
                            businessSummary = businessSummary,
                            personalInsight = personalInsight,
                            businessInsight = businessInsight,
                            chatMessages = (chatMessagesState as? UiState.Success)?.data.orEmpty(),
                            chatStatus = chatStatus,
                            chatActiveScope = chatActiveScope,
                            onChatScopeChange = { chatActiveScope = it },
                            onNavigate = { currentRoute = it },
                            onOpenQuickAdd = { showCalculatorSheet = true },
                            onSignOut = { GoogleAuthHelper.signOut(context, coroutineScope) },
                            viewModel = financeViewModel,
                            contentBottomPadding = 16.dp,
                        )
                    }
                }
            } else {
                val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    AlfredScreenHost(
                        currentRoute = currentRoute,
                        firstName = firstName,
                        photoUrl = currentUser.photoUrl?.toString(),
                        selectedMonthKey = selectedMonthKey,
                        hideValues = hideValues,
                        personalSummary = personalSummary,
                        businessSummary = businessSummary,
                        personalInsight = personalInsight,
                        businessInsight = businessInsight,
                        chatMessages = (chatMessagesState as? UiState.Success)?.data.orEmpty(),
                        chatStatus = chatStatus,
                        chatActiveScope = chatActiveScope,
                        onChatScopeChange = { chatActiveScope = it },
                        onNavigate = { currentRoute = it },
                        onOpenQuickAdd = { showCalculatorSheet = true },
                        onSignOut = { GoogleAuthHelper.signOut(context, coroutineScope) },
                        viewModel = financeViewModel,
                        contentBottomPadding = if (isImeVisible) 0.dp else 92.dp,
                    )

                    if (!isImeVisible) {
                        GothamFloatingBottomDock(
                            currentRoute = currentRoute,
                            onSelectRoute = { currentRoute = it },
                            onQuickAdd = { showCalculatorSheet = true },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    if (showCalculatorSheet) {
        CalculatorEntrySheet(
            initialScope = activeQuickAddScope,
            personalAccounts = personalSummary.scopeAccounts,
            businessAccounts = businessSummary.scopeAccounts,
            onDismiss = { showCalculatorSheet = false },
            onConfirm = { scope, type, title, category, amount, account, isPaid, isRecurring ->
                financeViewModel.addTransaction(
                    scope = scope,
                    type = type,
                    title = title,
                    category = category,
                    amount = amount,
                    account = account,
                    isPaid = isPaid,
                    isRecurring = isRecurring,
                )
                showCalculatorSheet = false
            },
        )
    }
}

@Composable
private fun AlfredScreenHost(
    currentRoute: String,
    firstName: String,
    photoUrl: String?,
    selectedMonthKey: String,
    hideValues: Boolean,
    personalSummary: com.example.ui.viewmodel.ScopeSummary,
    businessSummary: com.example.ui.viewmodel.ScopeSummary,
    personalInsight: com.example.ui.viewmodel.InsightUiState,
    businessInsight: com.example.ui.viewmodel.InsightUiState,
    chatMessages: List<com.example.data.model.AlfredChatMessage>,
    chatStatus: com.example.ui.viewmodel.ChatStatus,
    chatActiveScope: FinanceScope,
    onChatScopeChange: (FinanceScope) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenQuickAdd: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: FinanceViewModel,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
) {
    Crossfade(
        targetState = currentRoute,
        animationSpec = tween(durationMillis = 180),
        label = "alfred_tab_crossfade",
    ) { route ->
        when (route) {
            ROUTE_FINANCES -> DashboardScreen(
                scope = FinanceScope.PERSONAL,
                userName = firstName,
                userPhotoUrl = photoUrl,
                summary = personalSummary,
                selectedMonthKey = selectedMonthKey,
                hideValues = hideValues,
                insightState = personalInsight,
                onSelectMonth = viewModel::selectMonth,
                onToggleHideValues = viewModel::toggleHideValues,
                onRequestInsight = { viewModel.requestInsight(FinanceScope.PERSONAL) },
                onOpenAlfredChat = {
                    onChatScopeChange(FinanceScope.PERSONAL)
                    onNavigate(ROUTE_ALFRED)
                },
                onOpenQuickAdd = onOpenQuickAdd,
                onToggleTransactionPaid = viewModel::toggleTransactionPaid,
                onDeleteTransaction = viewModel::deleteTransaction,
                onAddAccount = { name, kind, balance, limit ->
                    viewModel.addAccount(FinanceScope.PERSONAL, name, kind, balance, limit)
                },
                onDeleteAccount = viewModel::deleteAccount,
                onAddReminder = { title, amount, due ->
                    viewModel.addReminder(FinanceScope.PERSONAL, title, amount, due)
                },
                onToggleReminder = viewModel::toggleReminderCompleted,
                onDeleteReminder = viewModel::deleteReminder,
                onSignOut = onSignOut,
                contentBottomPadding = contentBottomPadding,
            )

            ROUTE_BUSINESS -> DashboardScreen(
                scope = FinanceScope.BUSINESS,
                userName = firstName,
                userPhotoUrl = photoUrl,
                summary = businessSummary,
                selectedMonthKey = selectedMonthKey,
                hideValues = hideValues,
                insightState = businessInsight,
                onSelectMonth = viewModel::selectMonth,
                onToggleHideValues = viewModel::toggleHideValues,
                onRequestInsight = { viewModel.requestInsight(FinanceScope.BUSINESS) },
                onOpenAlfredChat = {
                    onChatScopeChange(FinanceScope.BUSINESS)
                    onNavigate(ROUTE_ALFRED)
                },
                onOpenQuickAdd = onOpenQuickAdd,
                onToggleTransactionPaid = viewModel::toggleTransactionPaid,
                onDeleteTransaction = viewModel::deleteTransaction,
                onAddAccount = { name, kind, balance, limit ->
                    viewModel.addAccount(FinanceScope.BUSINESS, name, kind, balance, limit)
                },
                onDeleteAccount = viewModel::deleteAccount,
                onAddReminder = { title, amount, due ->
                    viewModel.addReminder(FinanceScope.BUSINESS, title, amount, due)
                },
                onToggleReminder = viewModel::toggleReminderCompleted,
                onDeleteReminder = viewModel::deleteReminder,
                onSignOut = onSignOut,
                contentBottomPadding = contentBottomPadding,
            )

            ROUTE_ALFRED -> AlfredChatScreen(
                messages = chatMessages,
                chatStatus = chatStatus,
                activeScope = chatActiveScope,
                onScopeChange = onChatScopeChange,
                onSendMessage = { text, bmp ->
                    viewModel.sendChatMessage(text, bmp, chatActiveScope)
                },
                onDismissError = viewModel::clearChatError,
                contentBottomPadding = contentBottomPadding,
            )

            else -> AnalyticsScreen(
                personalSummary = personalSummary,
                businessSummary = businessSummary,
                selectedMonthKey = selectedMonthKey,
                hideValues = hideValues,
                onAddGoal = viewModel::addGoal,
                onContributeGoal = viewModel::contributeToGoal,
                onDeleteGoal = viewModel::deleteGoal,
                contentBottomPadding = contentBottomPadding,
            )
        }
    }
}

@Composable
private fun GothamFloatingBottomDock(
    currentRoute: String,
    onSelectRoute: (String) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = GothamTranslucentDock,
        border = BorderStroke(1.dp, GothamHairlineStrong),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockNavItem(
                tab = NAV_TABS[0],
                selected = currentRoute == NAV_TABS[0].route,
                onClick = { onSelectRoute(NAV_TABS[0].route) },
                modifier = Modifier.weight(1f),
            )
            DockNavItem(
                tab = NAV_TABS[1],
                selected = currentRoute == NAV_TABS[1].route,
                onClick = { onSelectRoute(NAV_TABS[1].route) },
                modifier = Modifier.weight(1f),
            )

            // Center Calculator Quick-Add Trigger
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onQuickAdd)
                    .testTag("quick_add_fab"),
                shape = CircleShape,
                color = GothamPureWhite,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Novo lançamento",
                        tint = GothamVoid,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            DockNavItem(
                tab = NAV_TABS[2],
                selected = currentRoute == NAV_TABS[2].route,
                onClick = { onSelectRoute(NAV_TABS[2].route) },
                modifier = Modifier.weight(1f),
            )
            DockNavItem(
                tab = NAV_TABS[3],
                selected = currentRoute == NAV_TABS[3].route,
                onClick = { onSelectRoute(NAV_TABS[3].route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockNavItem(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp)
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) GothamSurfaceElevated else Color.Transparent)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label,
                tint = if (selected) GothamPureWhite else GothamSlate,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) GothamPureWhite else GothamSlate,
            maxLines = 1,
        )
    }
}
