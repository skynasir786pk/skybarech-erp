package com.skybarech.mobileshoperp.ui

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skybarech.mobileshoperp.model.AppScreen
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.screens.*
import com.skybarech.mobileshoperp.ui.theme.BrandBlue
import com.skybarech.mobileshoperp.ui.theme.BrandBlueSoft
import com.skybarech.mobileshoperp.ui.theme.CardStroke
import com.skybarech.mobileshoperp.ui.theme.MutedInk
import com.skybarech.mobileshoperp.ui.theme.CardSurface
import com.skybarech.mobileshoperp.ui.theme.BrandBlueDark
import com.skybarech.mobileshoperp.ui.theme.Ink
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SkyBarechApp(vm: AppViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    UiLanguage.initialize(context)
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
        if (UiLanguage.code == "ur") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
        SkyBarechAppContent(vm)
    }
}

@Composable
private fun SkyBarechAppContent(vm: AppViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = vm.snackbarMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(tr(message), duration = SnackbarDuration.Short)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(vm.loggedIn) {
        if (vm.loggedIn) {
            vm.refreshServerControl()
            while (true) {
                delay(30_000)
                vm.refreshServerControl()
            }
        }
    }

    val authScreens = setOf(AppScreen.SPLASH, AppScreen.ONBOARDING, AppScreen.ACTIVATION, AppScreen.LOGIN, AppScreen.WELCOME, AppScreen.CHANGE_PASSWORD)
    val showAuthShell = vm.screen in authScreens || (vm.screen == AppScreen.HELP && !vm.loggedIn)
    SideEffect {
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.authLight = showAuthShell && vm.screen != AppScreen.SPLASH
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.darkBrandBackdrop = false
        if (vm.loggedIn) com.skybarech.mobileshoperp.ui.theme.UiAppearance.dark = false
    }
    if (showAuthShell) {
        AppBackground {
            when (vm.screen) {
                AppScreen.SPLASH -> SplashScreen(vm)
                AppScreen.ONBOARDING -> OnboardingScreen(vm)
                AppScreen.ACTIVATION -> ActivationScreen(vm)
                AppScreen.LOGIN -> LoginScreen(vm)
                AppScreen.WELCOME -> WelcomeScreen(vm)
                AppScreen.CHANGE_PASSWORD -> ChangePasswordScreen(vm)
                AppScreen.HELP -> HelpCenterScreen(vm, Modifier.fillMaxSize())
                else -> Unit
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.widthIn(max = 360.dp).heightIn(min = 36.dp),
                        shape = RoundedCornerShape(14.dp),
                        containerColor = CardSurface,
                        contentColor = BrandBlueDark,
                        actionColor = BrandBlue
                    )
                }
            )
        }
    } else {
        AppShell(vm = vm, snackbarHostState = snackbarHostState)
    }
    FingerprintEnrollment(vm)
    ConfirmationDialog(vm)
}

@Composable
private fun AppShell(vm: AppViewModel, snackbarHostState: SnackbarHostState) {
    BackHandler(enabled = vm.canGoBack()) { vm.goBack() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showRail = maxWidth >= 1000.dp
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val body: @Composable BoxScope.() -> Unit = {
            Row(Modifier.fillMaxSize().imePadding()) {
                if (showRail) {
                    SideRail(vm)
                }
                if (showRail) Column(Modifier.fillMaxSize()) {
                    AppTopBar(vm = vm, showMenu = false, onMenuClick = {})
                    Box(Modifier.weight(1f)) { ScreenRouter(vm, Modifier.fillMaxSize()) }
                } else Box(Modifier.fillMaxSize()) {
                    ScreenRouter(vm, Modifier.fillMaxSize().padding(bottom = 100.dp))
                    BottomNavigation(vm, Modifier.align(Alignment.BottomCenter))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier.widthIn(max = 360.dp).heightIn(min = 36.dp),
                        shape = RoundedCornerShape(14.dp),
                        containerColor = CardSurface,
                        contentColor = BrandBlueDark,
                        actionColor = BrandBlue
                    )
                }
            )
        }

        if (showRail) {
            AppBackground(content = body)
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(286.dp), drawerContainerColor = CardSurface) {
                        DrawerMenuContent(vm = vm, onSelect = {
                            scope.launch { drawerState.close() }
                            vm.navigateRoot(it)
                        })
                    }
                }
            ) {
                AppBackground(content = body)
            }
        }
    }
}

@Composable
private fun AppTopBar(vm: AppViewModel, showMenu: Boolean, onMenuClick: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                when {
                    vm.canGoBack() -> vm.goBack()
                    showMenu -> onMenuClick()
                    else -> vm.navigateRoot(AppScreen.DASHBOARD)
                }
            }) {
                Icon(
                    imageVector = when {
                        vm.canGoBack() -> Icons.Outlined.ArrowBack
                        showMenu -> Icons.Outlined.Menu
                        else -> Icons.Outlined.Home
                    },
                    contentDescription = tr("Navigation"),
                    tint = BrandBlue
                )
            }
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F6FF)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        UiText(vm.shopName.ifBlank { "Your business workspace" }, translate = false, color = Color(0xFF10244D), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        UiText(if (vm.screen == AppScreen.DASHBOARD) "Dashboard" else vm.screen.title, color = Color(0xFF0F43D8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
            LanguageSelector()
            IconButton(onClick = { vm.navigateRoot(AppScreen.STOCK_ALERTS) }) {
                BadgedBox(badge = { if (vm.lowStockCount() > 0) Badge { UiText(vm.lowStockCount().toString()) } }) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = tr("Notifications"), tint = BrandBlue)
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(vm: AppViewModel, modifier: Modifier = Modifier) {
    val items = listOf(
        NavItem(AppScreen.DASHBOARD, "Home", Icons.Outlined.Home),
        NavItem(AppScreen.INVENTORY, "Stock", Icons.Outlined.GridView),
        NavItem(AppScreen.POS, "POS", Icons.Outlined.PointOfSale),
        NavItem(AppScreen.STOCK_ALERTS, "Alerts", Icons.Outlined.Notifications),
        NavItem(AppScreen.SETTINGS, "Profile", Icons.Outlined.Person)
    )
    val selectedScreen = when (vm.screen) {
        AppScreen.INVENTORY, AppScreen.ADD_PRODUCT, AppScreen.MOBILE_ACCESSORIES, AppScreen.MOBILE_SPARE_PARTS, AppScreen.LAPTOP -> AppScreen.INVENTORY
        AppScreen.POS -> AppScreen.POS
        AppScreen.STOCK_ALERTS -> AppScreen.STOCK_ALERTS
        AppScreen.SETTINGS, AppScreen.CHANGE_PASSWORD, AppScreen.STAFF_ACTIVITY -> AppScreen.SETTINGS
        else -> AppScreen.DASHBOARD
    }
    val active = items.indexOfFirst { it.screen == selectedScreen }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(active.toFloat(), animationSpec = androidx.compose.animation.core.spring(dampingRatio = .62f, stiffness = 210f), label = "liquid-position")
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp).height(86.dp)) {
        val itemWidth = maxWidth / items.size
        Canvas(Modifier.fillMaxSize().shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = Color(0xFF0B1730), spotColor = Color(0xFF0B1730))) {
            val barTop = 20.dp.toPx()
            drawRoundRect(Color(0xFF1B2A4A), topLeft = androidx.compose.ui.geometry.Offset(0f, barTop), size = androidx.compose.ui.geometry.Size(size.width, size.height - barTop), cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()))
            val centerX = itemWidth.toPx() * (animatedIndex + .5f)
            drawCircle(Color(0xFF1B2A4A), 32.dp.toPx(), androidx.compose.ui.geometry.Offset(centerX, 29.dp.toPx()))
            drawCircle(Color(0xFFFF7A3D), 23.dp.toPx(), androidx.compose.ui.geometry.Offset(centerX, 29.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter)) {
            items.forEachIndexed { index, item ->
                LiquidNavItem(item, selected = index == active, badge = if (item.screen == AppScreen.STOCK_ALERTS) vm.lowStockCount() else 0, modifier = Modifier.weight(1f)) { vm.navigateRoot(item.screen) }
            }
        }
    }
}

@Composable
private fun LiquidNavItem(item: NavItem, selected: Boolean, badge: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val lift by animateFloatAsState(if (selected) -22f else 0f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = .60f), label = "liquid-lift")
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight(), color = Color.Transparent) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.graphicsLayer { translationY = lift }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = tr(item.label), tint = if (selected) Color.White else Color(0xFF9FB0D0), modifier = Modifier.size(24.dp))
                    if (badge > 0 && !selected) Badge(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-7).dp), containerColor = Color(0xFFFF7A3D)) { UiText(badge.coerceAtMost(99).toString(), color = Color.White, fontSize = 8.sp) }
                }
                if (selected) UiText(item.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 25.dp))
            }
        }
    }
}

@Composable
private fun SideRail(vm: AppViewModel) {
    Surface(modifier = Modifier.fillMaxHeight().width(220.dp), color = CardSurface, shadowElevation = 4.dp) {
        DrawerMenuContent(vm = vm, compact = false, onSelect = { vm.navigateRoot(it) })
    }
}

@Composable
private fun DrawerMenuContent(vm: AppViewModel, compact: Boolean = false, onSelect: (AppScreen) -> Unit) {
    val items = listOf(
        NavItem(AppScreen.DASHBOARD, "Dashboard", Icons.Outlined.Home),
        NavItem(AppScreen.POS, "POS Billing", Icons.Outlined.PointOfSale),
        NavItem(AppScreen.INVENTORY, "Inventory", Icons.Outlined.Inventory2),
        NavItem(AppScreen.MOBILE_ACCESSORIES, "Accessories", Icons.Outlined.PhoneAndroid),
        NavItem(AppScreen.MOBILE_SPARE_PARTS, "Spare Parts", Icons.Outlined.Construction),
        NavItem(AppScreen.LAPTOP, "Laptop", Icons.Outlined.Computer),
        NavItem(AppScreen.MOBILE_PURCHASE, "Purchase", Icons.Outlined.AddShoppingCart),
        NavItem(AppScreen.REPAIRS, "Repairs", Icons.Outlined.Build),
        NavItem(AppScreen.CUSTOMERS, "Customers", Icons.Outlined.PeopleOutline),
        NavItem(AppScreen.INSTALLMENTS, "Installments", Icons.Outlined.EventNote),
        NavItem(AppScreen.REPORTS, "Reports", Icons.Outlined.BarChart),
        NavItem(AppScreen.EXPENSES, "Expenses", Icons.Outlined.ReceiptLong),
        NavItem(AppScreen.CASH_LEDGER, "Cash Ledger", Icons.Outlined.AccountBalanceWallet),
        NavItem(AppScreen.EWALLET, "Easypaisa/Jazz", Icons.Outlined.AccountBalanceWallet),
        NavItem(AppScreen.STOCK_ALERTS, "Stock Alerts", Icons.Outlined.WarningAmber),
        NavItem(AppScreen.STAFF_ACTIVITY, "Staff Activity", Icons.Outlined.Groups),
        NavItem(AppScreen.SETTINGS, "Settings", Icons.Outlined.Settings),
        NavItem(AppScreen.HELP, "Help", Icons.Outlined.SupportAgent)
    )
    Column(
        modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandMark(if (compact) 40.dp else 48.dp)
        Spacer(Modifier.height(8.dp))
        if (!compact) {
            UiText(vm.shopName.ifBlank { "SkyBarech Hisab Pro" }, translate = false, fontWeight = FontWeight.Bold, color = BrandBlue, fontSize = 15.sp, maxLines = 2)
            UiText("All screens", color = MutedInk, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
        items.forEach { item ->
            val selected = vm.screen == item.screen
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                onClick = { onSelect(item.screen) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) BrandBlue.copy(alpha = 0.11f) else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) BrandBlue.copy(alpha = 0.18f) else Color.Transparent)
            ) {
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.icon, contentDescription = tr(item.label), tint = if (selected) BrandBlue else MutedInk)
                        Spacer(Modifier.height(4.dp))
                        UiText(item.label, fontSize = 12.sp, color = if (selected) BrandBlue else MutedInk, maxLines = 2)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(if (selected) BrandBlue else BrandBlueSoft), contentAlignment = Alignment.Center) {
                            Icon(item.icon, contentDescription = tr(item.label), tint = if (selected) MaterialTheme.colorScheme.onPrimary else BrandBlue, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        UiText(item.label, color = if (selected) BrandBlue else MutedInk, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenRouter(vm: AppViewModel, modifier: Modifier = Modifier) {
    when (vm.screen) {
        AppScreen.DASHBOARD -> DashboardScreen(vm, modifier)
        AppScreen.POS -> PosScreen(vm, modifier)
        AppScreen.PRODUCT_SEARCH -> ProductSearchScreen(vm, modifier)
        AppScreen.CART_PAYMENT -> CartPaymentScreen(vm, modifier)
        AppScreen.INVOICE -> InvoiceScreen(vm, modifier)
        AppScreen.INVENTORY -> InventoryScreen(vm, modifier)
        AppScreen.MOBILE_ACCESSORIES -> MobileAccessoriesScreen(vm, modifier)
        AppScreen.ADD_ACCESSORY -> AddAccessoryScreen(vm, modifier)
        AppScreen.MOBILE_SPARE_PARTS -> MobileSparePartsScreen(vm, modifier)
        AppScreen.ADD_SPARE_PART -> AddSparePartScreen(vm, modifier)
        AppScreen.LAPTOP -> LaptopScreen(vm, modifier)
        AppScreen.ADD_LAPTOP -> AddLaptopScreen(vm, modifier)
        AppScreen.ADD_PRODUCT -> AddProductScreen(vm, modifier)
        AppScreen.MOBILE_PURCHASE -> MobilePurchaseScreen(vm, modifier)
        AppScreen.MOBILE_SALE -> MobileSaleScreen(vm, modifier)
        AppScreen.REPAIRS -> RepairsScreen(vm, modifier)
        AppScreen.ADD_REPAIR -> AddRepairScreen(vm, modifier)
        AppScreen.CUSTOMERS -> CustomersScreen(vm, modifier)
        AppScreen.SUPPLIERS -> SuppliersScreen(vm, modifier)
        AppScreen.INSTALLMENTS -> InstallmentsScreen(vm, modifier)
        AppScreen.ADD_INSTALLMENT -> AddInstallmentScreen(vm, modifier)
        AppScreen.RECEIVE_PAYMENT -> ReceivePaymentScreen(vm, modifier)
        AppScreen.REPORTS -> ReportsScreen(vm, modifier)
        AppScreen.EXPENSES -> ExpensesScreen(vm, modifier)
        AppScreen.CASH_LEDGER -> CashLedgerScreen(vm, modifier)
        AppScreen.EWALLET -> EWalletScreen(vm, modifier)
        AppScreen.STOCK_ALERTS -> StockAlertsScreen(vm, modifier)
        AppScreen.STAFF_ACTIVITY -> StaffActivityScreen(vm, modifier)
        AppScreen.SETTINGS -> SettingsScreen(vm, modifier)
        AppScreen.HELP -> HelpCenterScreen(vm, modifier)
        else -> Unit
    }
}

private data class NavItem(val screen: AppScreen, val label: String, val icon: ImageVector)
