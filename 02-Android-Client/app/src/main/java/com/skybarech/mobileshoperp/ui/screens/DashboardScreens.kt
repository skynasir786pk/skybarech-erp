@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skybarech.mobileshoperp.model.AppScreen
import com.skybarech.mobileshoperp.model.SaleRecord
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*

@Composable
fun DashboardScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    UiText("Welcome back,", color = MutedInk, fontSize = 13.sp)
                    UiText(vm.shopName, translate = false, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BrandMark(44.dp, initials = vm.shopInitials)
            }
        }
        item { DashboardMetrics(vm) }
        item { SectionLabel("Modules") }
        item { DashboardActions(vm) }
        item { SectionLabel("Business Overview") }
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        UiText("Recorded Sales", color = MutedInk, fontSize = 12.sp)
                        UiText(vm.formatMoney(vm.localSalesTotal()), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Spacer(Modifier.height(3.dp))
                        UiText("From records on this device", color = MutedInk, fontSize = 12.sp)
                    }
                    MiniBarChart(Modifier.width(100.dp), vm.recentSalesBars())
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Recent Sales")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.navigate(AppScreen.REPORTS) }) { UiText("View All") }
            }
        }
        if (vm.sales.isEmpty()) item { ListEmpty("No sales yet", "Create your first sale from POS Billing.") }
        items(vm.sales.take(4), key = { it.id }) { sale -> SaleRow(sale, vm) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DashboardMetrics(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Today Sales", vm.formatMoney(vm.salesForDays(1).sumOf { it.total }), Icons.Outlined.Payments, Color(0xFF0069E0), Modifier.weight(1f)) { vm.navigate(AppScreen.POS) }
            MetricCard("Sales Count", vm.sales.size.toString(), Icons.Outlined.TrendingUp, Color(0xFF008052), Modifier.weight(1f)) { vm.navigate(AppScreen.REPORTS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Total Stock", vm.products.sumOf { it.stock }.toString(), Icons.Outlined.Inventory2, Color(0xFFCE3748), Modifier.weight(1f)) { vm.navigate(AppScreen.INVENTORY) }
            MetricCard("Low Stock", vm.lowStockCount().toString(), Icons.Outlined.WarningAmber, Color(0xFFD12750), Modifier.weight(1f)) { vm.navigate(AppScreen.STOCK_ALERTS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Customer Balance", vm.formatMoney(vm.customers.sumOf { it.balance }), Icons.Outlined.People, Color(0xFF6131CE), Modifier.weight(1f)) { vm.navigate(AppScreen.CUSTOMERS) }
            MetricCard("Supplier Balance", vm.formatMoney(vm.suppliers.sumOf { it.payable }), Icons.Outlined.LocalShipping, Color(0xFF7521CB), Modifier.weight(1f)) { vm.navigate(AppScreen.SUPPLIERS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricCard("Pending Repairs", vm.repairs.count { it.status != com.skybarech.mobileshoperp.model.RepairStatus.DELIVERED }.toString(), Icons.Outlined.Build, Color(0xFFC22C83), Modifier.weight(1f)) { vm.navigate(AppScreen.REPAIRS) }
            MetricCard("Expenses", vm.formatMoney(vm.expenses.sumOf { it.amount }), Icons.Outlined.AccountBalanceWallet, Color(0xFFCD4229), Modifier.weight(1f)) { vm.navigate(AppScreen.EXPENSES) }
        }
    }
}

private data class DashboardShortcut(val label: String, val icon: ImageVector, val screen: AppScreen, val color: Color)

@Composable
private fun DashboardActions(vm: AppViewModel) {
    val shortcuts = listOf(
        DashboardShortcut("POS Billing", Icons.Filled.PointOfSale, AppScreen.POS, BrandBlue),
        DashboardShortcut("Add Product", Icons.Filled.Inventory2, AppScreen.ADD_PRODUCT, Color(0xFF187255)),
        DashboardShortcut("Accessories", Icons.Filled.PhoneAndroid, AppScreen.MOBILE_ACCESSORIES, Color(0xFF7551B5)),
        DashboardShortcut("Spare Parts", Icons.Filled.Construction, AppScreen.MOBILE_SPARE_PARTS, Color(0xFF9B5D19)),
        DashboardShortcut("Laptop", Icons.Filled.Computer, AppScreen.LAPTOP, Color(0xFF395784)),
        DashboardShortcut("Mobile Purchase", Icons.Filled.AddShoppingCart, AppScreen.MOBILE_PURCHASE, Color(0xFF186C83)),
        DashboardShortcut("Repair Job", Icons.Filled.Build, AppScreen.ADD_REPAIR, Color(0xFFB44445)),
        DashboardShortcut("Reports", Icons.Filled.BarChart, AppScreen.REPORTS, Color(0xFF6550B0)),
        DashboardShortcut("Find Product", Icons.Filled.Search, AppScreen.PRODUCT_SEARCH, Color(0xFF376C94)),
        DashboardShortcut("Expenses", Icons.Filled.ReceiptLong, AppScreen.EXPENSES, Color(0xFF91631D)),
        DashboardShortcut("Easypaisa/Jazz", Icons.Filled.AccountBalanceWallet, AppScreen.EWALLET, Color(0xFF1D715E)),
        DashboardShortcut("Stock Alerts", Icons.Filled.WarningAmber, AppScreen.STOCK_ALERTS, Color(0xFF9A492C))
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 900.dp -> 6; maxWidth >= 640.dp -> 4; maxWidth >= 340.dp -> 2; else -> 2 }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shortcuts.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { shortcut ->
                        Surface(onClick = { vm.navigate(shortcut.screen) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp), color = CardSurface, border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp).heightIn(min = 108.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(shortcut.color, androidx.compose.ui.graphics.lerp(shortcut.color, Color.Black, .18f)))), contentAlignment = Alignment.Center) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-10).dp).size(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .16f)))
                            Icon(shortcut.icon, contentDescription = null, tint = contrastingInk(shortcut.color), modifier = Modifier.size(32.dp))
                        }
                        UiText(shortcut.label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp,
                            textAlign = TextAlign.Center, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                    }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(sale: SaleRecord, vm: AppViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(sale.item)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                UiText(sale.item, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText("${sale.customer} · ${sale.time}", color = MutedInk, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                UiText(vm.formatMoney(sale.total), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                StatusPill(sale.payment, if (sale.payment == "Cash") StatusTone.SUCCESS else StatusTone.INFO)
            }
        }
    }
}
