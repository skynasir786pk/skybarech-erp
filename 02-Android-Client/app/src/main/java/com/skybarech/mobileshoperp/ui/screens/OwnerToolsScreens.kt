@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*

@Composable
fun ExpensesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Utilities") }
    val total = vm.expenses.sumOf { it.amount }
    LazyColumn(modifier = modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Expenses", "Record shop spending for local cash closing.") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("Recorded Expenses", vm.formatMoney(total), Icons.Outlined.ReceiptLong, accent = Danger, modifier = Modifier.weight(1f))
            MetricCard("Cash Impact", vm.formatMoney(total), Icons.Outlined.AccountBalanceWallet, modifier = Modifier.weight(1f))
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("Quick Add Expense")
            Spacer(Modifier.height(10.dp))
            AppTextField("Expense title", title, { title = it }, leadingIcon = Icons.Outlined.EditNote)
            Spacer(Modifier.height(8.dp))
            AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppDropdown("Category", category, listOf("Rent", "Utilities", "Salary", "Transport", "Repair Parts", "Other"), { category = it }, Modifier.weight(1f))
                AppTextField("Amount", amount, { amount = it }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Payments)
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save Expense", {
                if (vm.addExpense(title, category, amount)) { title = ""; amount = "" }
            }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Save)
        } }
        item { SectionLabel("Recent Expenses") }
        items(vm.expenses, key = { it.id }) { row -> InfoRow(row.title, "${row.category} · ${row.date}", vm.formatMoney(row.amount), Icons.Outlined.ReceiptLong) }
    }
}

@Composable
fun CashLedgerScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cashSales = vm.salesForDays(1).filter { it.payment == "Cash" }.sumOf { it.total }
    val easyPaisaIn = vm.ewallets.filter { it.wallet == "EasyPaisa" && it.direction == "In" }.sumOf { it.amount + it.fee }
    val easyPaisaOut = vm.ewallets.filter { it.wallet == "EasyPaisa" && it.direction == "Out" }.sumOf { it.amount + it.fee }
    val jazzCashIn = vm.ewallets.filter { it.wallet == "JazzCash" && it.direction == "In" }.sumOf { it.amount + it.fee }
    val jazzCashOut = vm.ewallets.filter { it.wallet == "JazzCash" && it.direction == "Out" }.sumOf { it.amount + it.fee }
    var wallet by rememberSaveable { mutableStateOf("EasyPaisa") }
    var direction by rememberSaveable { mutableStateOf("In") }
    var amount by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var openingInput by rememberSaveable { mutableStateOf("0") }
    val opening = openingInput.toIntOrNull() ?: 0
    val expenses = vm.expenses.filter { it.date.take(10) == java.time.LocalDate.now().toString() }.sumOf { it.amount }
    val expected = opening + cashSales - expenses
    var actualClosing by remember { mutableStateOf(expected.coerceAtLeast(0).toString()) }
    LazyColumn(modifier = modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Cash & Wallet Ledger", "Cash, EasyPaisa and JazzCash in-out control with thermal receipt.") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("Cash Sales", vm.formatMoney(cashSales), Icons.Outlined.PointOfSale, accent = Success, modifier = Modifier.weight(1f))
            MetricCard("Expected Cash", vm.formatMoney(expected), Icons.Outlined.DoneAll, accent = Success, modifier = Modifier.weight(1f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("EasyPaisa", vm.formatMoney(easyPaisaIn - easyPaisaOut), Icons.Outlined.AccountBalanceWallet, modifier = Modifier.weight(1f))
            MetricCard("JazzCash", vm.formatMoney(jazzCashIn - jazzCashOut), Icons.Outlined.AccountBalanceWallet, modifier = Modifier.weight(1f))
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("EasyPaisa / JazzCash In-Out")
            Spacer(Modifier.height(8.dp))
            AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppDropdown("Wallet", wallet, listOf("EasyPaisa", "JazzCash"), { wallet = it }, Modifier.weight(1f))
                AppDropdown("Type", direction, listOf("In", "Out"), { direction = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField("Amount", amount, { amount = it }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Payments)
                AppTextField("Mobile", mobile, { mobile = it }, Modifier.weight(1f), leadingIcon = Icons.Outlined.PhoneAndroid)
            }
            Spacer(Modifier.height(8.dp))
            AppTextField("Note / Reference", note, { note = it }, leadingIcon = Icons.Outlined.EditNote)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save Wallet Entry", { if (vm.saveWalletEntry(wallet, direction, amount, mobile, "Walk-in Customer", note)) { amount = ""; note = "" } }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Save)
            Spacer(Modifier.height(8.dp))
            OutlineButton("Print Thermal Receipt", { vm.ewallets.firstOrNull()?.let { row -> printInvoice(context, row.id, "${vm.shopName}\n${row.id}\n${row.date}\n${row.wallet} ${row.direction}\n${row.party}\nAmount: ${vm.formatMoney(row.amount)}\nFee: ${vm.formatMoney(row.fee)}\nReference: ${row.reference}") } ?: vm.showMessage("Save a wallet transaction first.") }, Modifier.fillMaxWidth(), Icons.Outlined.Print)
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("Close Today")
            AppTextField("Opening Cash", openingInput, { openingInput = it })
            Spacer(Modifier.height(8.dp))
            SummaryLine("Opening Cash", vm.formatMoney(opening))
            SummaryLine("Cash Sales", vm.formatMoney(cashSales))
            SummaryLine("Expenses", vm.formatMoney(expenses))
            SummaryLine("Expected Closing", vm.formatMoney(expected), highlight = true)
            Spacer(Modifier.height(8.dp))
            AppTextField("Actual Cash Count", actualClosing, { actualClosing = it.filter(Char::isDigit) }, leadingIcon = Icons.Outlined.Payments)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save Cash Closing", { vm.saveCashClosing(opening, actualClosing) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Save)
        } }
        if (vm.cashClosings.isNotEmpty()) item {
            SectionLabel("Closing History")
            vm.cashClosings.take(5).forEach { closing ->
                InfoRow(closing.date, "Expected ${vm.formatMoney(closing.expectedCash)} · Actual ${vm.formatMoney(closing.actualCash)}", vm.formatMoney(closing.difference), Icons.Outlined.FactCheck)
                Spacer(Modifier.height(7.dp))
            }
        }
        item { SectionLabel("Wallet Summary") }
        item { InfoRow("EasyPaisa In / Out", "In ${vm.formatMoney(easyPaisaIn)} · Out ${vm.formatMoney(easyPaisaOut)}", vm.formatMoney(easyPaisaIn - easyPaisaOut), Icons.Outlined.AccountBalanceWallet) }
        item { InfoRow("JazzCash In / Out", "In ${vm.formatMoney(jazzCashIn)} · Out ${vm.formatMoney(jazzCashOut)}", vm.formatMoney(jazzCashIn - jazzCashOut), Icons.Outlined.AccountBalanceWallet) }
    }
}


@Composable
fun EWalletScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    fun total(walletName: String, dir: String): Int = vm.ewallets
        .filter { it.wallet == walletName && it.direction == dir }
        .sumOf { it.amount + it.fee }
    val easyPaisaBalance = total("EasyPaisa", "In") - total("EasyPaisa", "Out")
    val jazzCashBalance = total("JazzCash", "In") - total("JazzCash", "Out")
    var wallet by rememberSaveable { mutableStateOf("EasyPaisa") }
    var direction by rememberSaveable { mutableStateOf("In") }
    var amount by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("0") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var party by rememberSaveable { mutableStateOf("Walk-in Customer") }
    var note by rememberSaveable { mutableStateOf("") }

    LazyColumn(modifier = modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Easypaisa/Jazz", "Simple wallet in and out entries with thermal receipt.") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("EasyPaisa", vm.formatMoney(easyPaisaBalance), Icons.Outlined.AccountBalanceWallet, modifier = Modifier.weight(1f))
            MetricCard("JazzCash", vm.formatMoney(jazzCashBalance), Icons.Outlined.AccountBalanceWallet, modifier = Modifier.weight(1f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("EasyPaisa In", { wallet = "EasyPaisa"; direction = "In" }, Modifier.weight(1f), icon = Icons.Outlined.SouthWest)
            OutlineButton("EasyPaisa Out", { wallet = "EasyPaisa"; direction = "Out" }, Modifier.weight(1f), Icons.Outlined.NorthEast)
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("JazzCash In", { wallet = "JazzCash"; direction = "In" }, Modifier.weight(1f), icon = Icons.Outlined.SouthWest)
            OutlineButton("JazzCash Out", { wallet = "JazzCash"; direction = "Out" }, Modifier.weight(1f), Icons.Outlined.NorthEast)
        } }
        item { SoftCard(Modifier.fillMaxWidth()) {
            SectionLabel("$wallet $direction")
            Spacer(Modifier.height(8.dp))
            AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppDropdown("Wallet", wallet, listOf("EasyPaisa", "JazzCash"), { wallet = it }, Modifier.weight(1f))
                AppDropdown("Type", direction, listOf("In", "Out"), { direction = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField("Amount", amount, { amount = it.filter { ch -> ch.isDigit() } }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Payments)
                AppTextField("Fee", fee, { fee = it.filter { ch -> ch.isDigit() } }, Modifier.weight(1f), leadingIcon = Icons.Outlined.ReceiptLong)
            }
            Spacer(Modifier.height(8.dp))
            AppTextField("Mobile Number", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PhoneAndroid)
            Spacer(Modifier.height(8.dp))
            AppTextField("Customer / Party", party, { party = it }, leadingIcon = Icons.Outlined.PersonOutline)
            Spacer(Modifier.height(8.dp))
            AppTextField("Reference", note, { note = it }, leadingIcon = Icons.Outlined.EditNote)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save & Print", {
                vm.saveWalletEntry(wallet, direction, amount, mobile, party, note, fee)
                amount = ""; fee = "0"; note = ""
            }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Print)
        } }
        item { SectionLabel("Saved Wallet Ledger") }
        items(vm.ewallets, key = { it.id }) { row ->
            SoftCard(Modifier.fillMaxWidth(), contentPadding = 11.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (row.direction == "In") Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, contentDescription = null, tint = if (row.direction == "In") Success else Danger)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        UiText("${row.wallet} ${row.direction}", color = Ink, fontWeight = FontWeight.Bold)
                        UiText("${row.party} · ${row.mobile.ifBlank { "No mobile" }} · ${row.reference.ifBlank { row.date }}", color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    UiText(vm.formatMoney(row.amount + row.fee), color = if (row.direction == "In") Success else Danger, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (vm.ewallets.isEmpty()) item { ListEmpty("No wallet entry", "Save EasyPaisa or JazzCash In/Out to build the ledger.") }
    }
}

@Composable
fun StockAlertsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val low = vm.products.filter { it.stock <= 5 }
    LazyColumn(modifier = modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("Stock Alerts", "Low stock, out of stock and reorder reminders.") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("Low Stock", low.size.toString(), Icons.Outlined.WarningAmber, accent = Warning, modifier = Modifier.weight(1f))
            MetricCard("Total Items", vm.products.size.toString(), Icons.Outlined.Inventory2, modifier = Modifier.weight(1f))
        } }
        item { SectionLabel("Items needing attention") }
        items(low, key = { it.id }) { product ->
            SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductAvatar(product.name, Warning)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        UiText(product.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        UiText("${product.sku} · Purchase ${vm.formatMoney(product.purchasePrice)}", color = MutedInk, fontSize = 12.sp)
                    }
                    StatusPill("Stock ${product.stock}", StatusTone.DANGER)
                }
            }
        }
        if (low.isEmpty()) item { ListEmpty("Stock healthy", "No product is below the alert limit right now.") }
    }
}

@Composable
fun StaffActivityScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(ScreenPadding)) {
        PageTitle("Shop Activity", "Recorded activity on this device.")
        Spacer(Modifier.height(16.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            UiText(vm.shopName, translate = false, color = Ink, fontWeight = FontWeight.Bold)
            UiText("${vm.sales.size} invoices · ${vm.repairs.size} repair jobs", color = MutedInk)
            UiText("Recorded sales: ${vm.formatMoney(vm.localSalesTotal())}", color = BrandBlue)
            Spacer(Modifier.height(12.dp))
            UiText("This app supports the shop owner account. Staff accounts are managed by the platform administrator; separate staff device access is not available in this version.", color = MutedInk)
        }
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String, trailing: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = BrandBlue) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                UiText(subtitle, color = MutedInk, fontSize = 12.sp)
            }
            UiText(trailing, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(label, color = MutedInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
        UiText(value, color = if (highlight) BrandBlue else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
