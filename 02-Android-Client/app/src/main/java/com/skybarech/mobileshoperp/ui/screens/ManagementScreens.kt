@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.skybarech.mobileshoperp.model.*
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*

@Composable
fun InstallmentsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var search by rememberSaveable { mutableStateOf("") }
    val visible = vm.installments.filter { it.customer.contains(search, true) || it.phone.contains(search, true) || it.product.contains(search, true) }
    val dueToday = visible.filter { it.paidMonths < it.months && (runCatching { !java.time.LocalDate.parse(it.dueLabel).isAfter(java.time.LocalDate.now()) }.getOrDefault(it.dueSoon)) }
    val upcoming = visible.filter { it.paidMonths < it.months && it !in dueToday }
    Column(modifier = modifier.padding(ScreenPadding)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Due Installments", dueToday.size.toString(), Icons.Outlined.EventNote, modifier = Modifier.weight(1f))
            MetricCard("Due Amount", vm.formatMoney(dueToday.sumOf { it.amount }), Icons.Outlined.Payments, accent = Success, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        SearchBox(search, { search = it }, "Search customer or mobile")
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Row(Modifier.fillMaxWidth()) { SectionLabel("Due / Overdue"); Spacer(Modifier.weight(1f)); UiText("${visible.size} total", color = MutedInk, fontSize = 12.sp) } }
            items(dueToday, key = { it.id }) { installment -> InstallmentRow(installment, vm) }
            item { Spacer(Modifier.height(6.dp)); Row(Modifier.fillMaxWidth()) { SectionLabel("Upcoming Dues"); Spacer(Modifier.weight(1f)); UiText("${visible.size} total", color = MutedInk, fontSize = 12.sp) } }
            items(upcoming, key = { it.id }) { installment -> InstallmentRow(installment, vm) }
            if (dueToday.isEmpty() && upcoming.isEmpty()) item { ListEmpty("No unpaid installments", "Create a new installment plan to start tracking dues.") }
        }
        Spacer(Modifier.height(10.dp))
        if (vm.lastInstallmentReceipt.isNotBlank()) OutlineButton("Print last saved payment", { printInvoice(context, "Installment payment", vm.lastInstallmentReceipt) }, Modifier.fillMaxWidth())
        PrimaryButton("Add Installment", { vm.navigate(AppScreen.ADD_INSTALLMENT) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
    }
}

@Composable
private fun InstallmentRow(installment: Installment, vm: AppViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(installment.customer, if (installment.dueSoon) Warning else BrandBlue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(installment.customer, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                UiText("${installment.phone} · ${installment.product}", color = MutedInk, fontSize = 12.sp)
                UiText(installment.dueLabel, color = if (installment.dueSoon) Danger else BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                UiText(vm.formatMoney(installment.amount), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TextButton(onClick = { vm.selectInstallment(installment.id) }, contentPadding = PaddingValues(0.dp)) { UiText("Receive", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun AddInstallmentScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var customer by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }
    var imei by rememberSaveable { mutableStateOf("") }
    var totalPrice by rememberSaveable { mutableStateOf("") }
    var advance by rememberSaveable { mutableStateOf("0") }
    var months by rememberSaveable { mutableStateOf("6 Months") }
    var firstDueDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().plusMonths(1).toString()) }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Add Installment", "Create a simple, trackable payment plan.") }
            item { AppDropdown("Customer Name", customer, vm.customers.map { it.name }, { customer = it }) }
            item { AppTextField("Mobile Number", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PhoneAndroid) }
            item { AppTextField("Mobile / Product", product, { product = it }) }
            item { AppTextField("IMEI (Optional)", imei, { imei = it }, placeholder = "Enter IMEI") }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Total Price", totalPrice, { totalPrice = it }, Modifier.weight(1f))
                AppTextField("Advance (Optional)", advance, { advance = it }, Modifier.weight(1f))
            } }
            item { AppDropdown("Months", months, listOf("3 Months", "6 Months", "9 Months", "12 Months"), { months = it }) }
            item { AppTextField("First Due Date", firstDueDate, { firstDueDate = it }, supportingText = "YYYY-MM-DD") }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save", { vm.addInstallment(customer, mobile, product, totalPrice, advance, months, firstDueDate) }, Modifier.fillMaxWidth(), Icons.Outlined.Save)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun ReceivePaymentScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val installment = vm.installments.firstOrNull { it.id == vm.selectedInstallmentId }
    if (installment == null) {
        Column(modifier.padding(ScreenPadding)) { ListEmpty("Select a payment plan", "Open Installments and tap Receive on the correct customer.", "Open Installments") { vm.navigateRoot(AppScreen.INSTALLMENTS) } }
        return
    }
    var amount by remember { mutableStateOf(installment.amount.toString()) }
    var method by rememberSaveable { mutableStateOf("Cash") }
    var date by rememberSaveable { mutableStateOf("Today") }
    var notes by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Receive Payment", "Collect a monthly installment and issue a receipt.") }
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductAvatar(installment.customer)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            UiText(installment.customer, translate = false, color = Ink, fontWeight = FontWeight.Bold)
                            UiText(installment.phone, translate = false, color = MutedInk, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            UiText(installment.product, translate = false, color = Ink, fontSize = 12.sp)
                        }
                        StatusPill("${installment.paidMonths + 1} / ${installment.months} Months", StatusTone.INFO)
                    }
                }
            }
            item { SectionLabel("Payment ${installment.paidMonths + 1} of ${installment.months}") }
            item { AppTextField("Amount", amount, { amount = it }, leadingIcon = Icons.Outlined.Payments) }
            item { UiText("Received on ${java.time.LocalDate.now()}", color = MutedInk) }
            item { AppDropdown("Payment Method", method, listOf("Cash", "EasyPaisa In", "JazzCash In", "Bank Transfer", "Card"), { method = it }) }

            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    UiText("Remaining After This Payment", color = MutedInk, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    UiText(vm.formatMoney((installment.amount * (installment.months - installment.paidMonths - 1)).coerceAtLeast(0)), color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                }
            }

        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Receive Payment", { vm.receiveInstallment(installment, amount, method) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun ReportsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var period by rememberSaveable { mutableStateOf("All recorded") }
    val sales = when (period) { "Today" -> vm.salesForDays(1); "Last 7 days" -> vm.salesForDays(7); "Last 30 days" -> vm.salesForDays(30); else -> vm.sales.toList() }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(ScreenPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Reports", "Totals from records currently available on this device.") }
        item { AppDropdown("Sales period", period, listOf("All recorded", "Today", "Last 7 days", "Last 30 days"), { period = it }) }
        item { AdaptiveRow {
            MetricCard("Recorded Sales", vm.formatMoney(sales.sumOf { it.total }), Icons.Outlined.Payments, modifier = Modifier.weight(1f))
            MetricCard("Invoices", sales.size.toString(), Icons.Outlined.ReceiptLong, modifier = Modifier.weight(1f))
        } }
        item { UiText("Stock, expenses and dues below cover all locally recorded data. Older records without a valid date are included only in All recorded.", color = MutedInk, fontSize = 13.sp) }
        item { AdaptiveRow {
            MetricCard("Stock Units", vm.products.sumOf { it.stock }.toString(), Icons.Outlined.Inventory2, modifier = Modifier.weight(1f))
            MetricCard("Recorded Expenses", vm.formatMoney(vm.expenses.sumOf { it.amount }), Icons.Outlined.ReceiptLong, modifier = Modifier.weight(1f))
        } }
        item { AdaptiveRow {
            MetricCard("Repairs", vm.repairs.size.toString(), Icons.Outlined.Build, modifier = Modifier.weight(1f))
            MetricCard("Scheduled Dues", vm.formatMoney(vm.outstandingInstallments()), Icons.Outlined.EventNote, modifier = Modifier.weight(1f))
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("Subscription")
            UiText("Plan: ${vm.subscriptionPlan}", color = Ink)
            UiText("Status: ${vm.subscriptionStatus}", color = Ink)
            UiText("Validity: ${vm.expiryLabel}", color = MutedInk)
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("Sales · last 7 days")
            MiniBarChart(Modifier.fillMaxWidth(), vm.recentSalesBars())
            UiText("${vm.formatMoney(vm.salesForDays(7).sumOf { it.total })} recorded", color = MutedInk)
        } }
        if (sales.isEmpty()) item { ListEmpty("No sales in this period", "Complete a sale or choose a different period.") }
        items(sales, key = { it.id }) { sale -> SoftCard(Modifier.fillMaxWidth()) {
            UiText(sale.id, translate = false, color = Ink, fontWeight = FontWeight.Bold)
            UiText("${sale.customer} · ${sale.time}", color = MutedInk)
            UiText(vm.formatMoney(sale.total), color = BrandBlue, fontWeight = FontWeight.Bold)
        } }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(vm.exportBackup()) } }
            .onSuccess { vm.showMessage("Backup file created successfully.") }
            .onFailure { vm.showMessage("Backup create nahi hua: ${it.message}") }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
            .onSuccess { vm.restoreBackup(it) }
            .onFailure { vm.showMessage("Backup read nahi hua: ${it.message}") }
    }
    var showBackupDialog by remember { mutableStateOf(false) }
    val settingItems = listOf(
        Triple("Shop Profile", "Manage shop information", Icons.Outlined.Store),
        Triple("Shop PIN", "Change your shop PIN", Icons.Outlined.Lock),
        Triple("Theme", if (UiAppearance.dark) "Dark" else "Light", Icons.Outlined.LightMode),
        Triple("Help Center", "Get help and support", Icons.Outlined.SupportAgent),
        Triple("Backup & Restore", "Secure your data", Icons.Outlined.Backup),
        Triple("Subscription", "Manage your plan", Icons.Outlined.VerifiedUser),
        Triple("Cloud", "View pending changes and retry", Icons.Outlined.Backup),
        Triple("Sign Out", "Securely end this shop session", Icons.Outlined.Logout)
    )
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(ScreenPadding), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageTitle("Settings") }
        item { SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UiText("Language", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                LanguageSelector()
            }
        } }
        item { FingerprintSetting(vm) }
        items(settingItems, key = { it.first }) { item ->
            SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(item.third, contentDescription = null, tint = BrandBlue) }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        UiText(item.first, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        UiText(item.second, color = MutedInk, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        when (item.first) {
                            "Help Center" -> vm.navigate(AppScreen.HELP)
                            "Backup & Restore" -> showBackupDialog = true
                            "Shop Profile" -> vm.requestConfirmation(com.skybarech.mobileshoperp.ui.ConfirmAction(
                                title = vm.shopName, literalTitle = true, body = "Owner mobile: ${vm.ownerMobile}\nShop identity is managed by your platform administrator.", confirm = {}))
                            "Shop PIN" -> vm.navigate(AppScreen.CHANGE_PASSWORD)
                            "Theme" -> vm.toggleTheme()
                            "Subscription" -> vm.navigate(AppScreen.REPORTS)
                            "Cloud" -> vm.syncNow()
                            "Sign Out" -> vm.requestConfirmation(com.skybarech.mobileshoperp.ui.ConfirmAction(
                                title = "Sign out?", body = "You can sign in again with your owner mobile and password.", confirm = { vm.signOut() }
                            ))
                            else -> Unit
                        }
                    }) { Icon(Icons.Outlined.ChevronRight, contentDescription = tr(item.first), tint = MutedInk) }
                }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = BrandBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        UiText("Cloud", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        UiText(vm.storageStatus, color = MutedInk, fontSize = 12.sp)
                        UiText("Plan: ${vm.subscriptionPlan} · ${vm.subscriptionStatus}", color = MutedInk, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.syncNow() }) { UiText("Cloud sync") }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(44.dp, initials = vm.shopInitials)
                Spacer(Modifier.height(7.dp))
                UiText(vm.shopName, translate = false, color = MutedInk, fontSize = 12.sp)
                UiText("v${com.skybarech.mobileshoperp.BuildConfig.VERSION_NAME}", color = MutedInk, fontSize = 12.sp)
            }
        }
    }
    if (showBackupDialog) AlertDialog(
        onDismissRequest = { showBackupDialog = false },
        title = { UiText("Backup & Restore") },
        text = { UiText("Encrypted cloud sync ke baghair JSON backup apne safe folder/drive mein rakhein. Restore current local records replace karega.") },
        confirmButton = { TextButton(onClick = {
            showBackupDialog = false
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            createBackup.launch("skybarech-backup-$stamp.json")
        }) { UiText("Create Backup") } },
        dismissButton = { TextButton(onClick = { showBackupDialog = false; restoreBackup.launch(arrayOf("application/json", "text/plain")) }) { UiText("Restore") } }
    )
}

@Composable
fun HelpCenterScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var issueType by rememberSaveable { mutableStateOf("Select Issue Type") }
    var priority by rememberSaveable { mutableStateOf("Select Priority") }
    var message by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Help Center", "Send a support request and track its progress.") }
            item { SectionLabel("Send us a Request") }
            item { AppDropdown("Issue Type", issueType, listOf("Select Issue Type", "App not syncing data", "Receipt printing issue", "Stock count mismatch", "Other"), { issueType = it }) }
            item { AppDropdown("Priority", priority, listOf("Select Priority", "Low", "Normal", "High", "Urgent"), { priority = it }) }
            item { AppTextField("Complaint / Message", message, { if (it.length <= 500) message = it }, placeholder = "Describe your issue in detail...", singleLine = false, supportingText = "${message.length}/500") }
            item { PrimaryButton("Send Request", { vm.submitSupport(issueType.takeIf { it != "Select Issue Type" } ?: "", priority.takeIf { it != "Select Priority" } ?: "", message) }, Modifier.fillMaxWidth(), Icons.Outlined.Send) }
            item { SectionLabel("Previous Requests") }
            items(vm.supportRequests, key = { it.id }) { request -> SupportRequestRow(request) }
        }
    }
}

@Composable
private fun SupportRequestRow(request: SupportRequest) {
    val tone = when (request.status) { "Open" -> StatusTone.INFO; "In Progress" -> StatusTone.WARNING; else -> StatusTone.SUCCESS }
    SoftCard(Modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SupportAgent, contentDescription = null, tint = BrandBlue)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                UiText(request.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                UiText("#${request.id} · ${request.date}", color = MutedInk, fontSize = 12.sp)
            }
            StatusPill(request.status, tone)
        }
    }
}
