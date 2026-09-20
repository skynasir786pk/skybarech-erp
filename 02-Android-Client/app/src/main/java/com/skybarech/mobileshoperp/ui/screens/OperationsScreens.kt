@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.skybarech.mobileshoperp.model.*
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*

@Composable
fun MobilePurchaseScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var brand by rememberSaveable { mutableStateOf("Samsung") }
    var model by rememberSaveable { mutableStateOf("") }
    var ram by rememberSaveable { mutableStateOf("12GB") }
    var storage by rememberSaveable { mutableStateOf("256GB") }
    var imeiDraft by rememberSaveable { mutableStateOf("") }
    val imeiList = remember { mutableStateListOf<String>() }
    var purchasePrice by rememberSaveable { mutableStateOf("") }
    var expectedSale by rememberSaveable { mutableStateOf("") }
    var supplier by rememberSaveable { mutableStateOf("") }
    var supplierMobile by rememberSaveable { mutableStateOf("") }
    var cnic by rememberSaveable { mutableStateOf("") }
    var cnicFront by rememberSaveable { mutableStateOf("") }
    var cnicBack by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Mobile Purchase", "Save complete device, supplier and IMEI details.") }
            item { AppDropdown("Brand *", brand, listOf("Samsung", "Apple", "Xiaomi", "Oppo", "Vivo", "Realme"), { brand = it }) }
            item { AppTextField("Model *", model, { model = it }, placeholder = "Galaxy S24 Ultra 5G") }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("RAM *", ram, listOf("4GB", "6GB", "8GB", "12GB", "16GB"), { ram = it }, Modifier.weight(1f))
                AppDropdown("Storage *", storage, listOf("64GB", "128GB", "256GB", "512GB"), { storage = it }, Modifier.weight(1f))
            } }
            item { SectionLabel("IMEI / Barcode") }
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    UiText("Use the portrait camera scan box for IMEI/barcode, or enter it manually. Bluetooth/USB scanners can type directly into the focused field.", color = MutedInk, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        "Scan / Manual IMEI *",
                        imeiDraft,
                        { value -> imeiDraft = value.filter { it.isDigit() }.take(17) },
                        placeholder = "354684120123456",
                        leadingIcon = Icons.Outlined.QrCodeScanner
                    )
                    Spacer(Modifier.height(10.dp))
                    AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BarcodeScanButton(Modifier.weight(1f), onScan = { imeiDraft = it.filter(Char::isDigit).take(17) }, onError = vm::showMessage)
                        PrimaryButton(
                            "Add IMEI",
                            {
                                val clean = imeiDraft.filter { it.isDigit() }
                                when {
                                    clean.length != 15 -> vm.showMessage("IMEI must contain exactly 15 digits.")
                                    imeiList.contains(clean) -> vm.showMessage("This IMEI is already added.")
                                    else -> { imeiList.add(clean); imeiDraft = ""; vm.showMessage("IMEI added.") }
                                }
                            },
                            Modifier.weight(1f),
                            icon = Icons.Outlined.Add
                        )
                    }
                    if (imeiList.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        imeiList.forEachIndexed { index, imei ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                UiText("${index + 1}. $imei", modifier = Modifier.weight(1f), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { imeiList.remove(imei) }) { UiText("Remove", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Purchase Price *", purchasePrice, { purchasePrice = it }, Modifier.weight(1f), placeholder = "Rs. 0")
                AppTextField("Expected Sale Price *", expectedSale, { expectedSale = it }, Modifier.weight(1f), placeholder = "Rs. 0")
            } }
            item { AppDropdown("Purchase From *", supplier, listOf("Walk-in Customer") + vm.suppliers.map { it.name }, { supplier = it }) }
            item { AppTextField(if (supplier == "Walk-in Customer") "Walk-in Customer Mobile Number" else "Supplier Mobile Number", supplierMobile, { supplierMobile = it.filter(Char::isDigit).take(15) }, placeholder = "03XX XXXXXXX", leadingIcon = Icons.Outlined.Phone) }
            item { AppTextField("CNIC (Optional)", cnic, { cnic = it }, placeholder = "35202-1234567-1", leadingIcon = Icons.Outlined.Badge) }
            item {
                AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        CameraImageButton(if (cnicFront.isBlank()) "Camera: Front" else "Front captured", { cnicFront = it; vm.showMessage("Front image saved. Now capture or upload the back image.") }, vm::showMessage, Modifier.fillMaxWidth())
                        ImageUploadButton("Upload Front", { cnicFront = it; vm.showMessage("Front image uploaded. Now add the back image.") }, vm::showMessage, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        CameraImageButton(if (cnicBack.isBlank()) "Camera: Back" else "Back captured", { cnicBack = it; vm.showMessage("Back image saved.") }, vm::showMessage, Modifier.fillMaxWidth())
                        ImageUploadButton("Upload Back", { cnicBack = it; vm.showMessage("Back image uploaded.") }, vm::showMessage, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save Purchase", {
            val pendingImei = imeiDraft.filter { it.isDigit() }
            val finalImeis = (imeiList + listOf(pendingImei).filter { it.isNotBlank() }).distinct()
            vm.savePurchase(brand, model, finalImeis.joinToString(", "), purchasePrice, expectedSale, supplier, supplierMobile, cnic, cnicFront, cnicBack)
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlineButton("Print last saved purchase", { if (vm.lastPurchaseReceipt.isBlank()) vm.showMessage("Save a purchase first.") else printInvoice(context, "Purchase", vm.lastPurchaseReceipt) }, Modifier.fillMaxWidth(), Icons.Outlined.Print)
        UiText("CNIC images stay on this device; they are not included in cloud sync or JSON backups.", color = MutedInk, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun UploadTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke)) {
        Column(Modifier.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = BrandBlue)
            Spacer(Modifier.height(5.dp))
            UiText(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { UiText("Upload Image", fontSize = 12.sp) }
        }
    }
}

@Composable
fun MobileSaleScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    // One stock-aware sale workflow avoids unlinked invoices and ignored discounts.
    PosScreen(vm, modifier)
}

@Composable
fun RepairsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val visible = vm.repairs.filter {
        val matchesSearch = it.customer.contains(search, true) || it.phone.contains(search, true) || it.device.contains(search, true) || it.id.contains(search, true)
        val matchesFilter = filter == "All" || it.status.label == filter
        matchesSearch && matchesFilter
    }
    Column(modifier = modifier.padding(ScreenPadding)) {
        SearchBox(search, { search = it }, "Search by customer, phone or IMEI")
        Spacer(Modifier.height(9.dp))
        FilterRow(listOf("All") + RepairStatus.entries.map { it.label }, filter, { filter = it })
        Spacer(Modifier.height(11.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible, key = { it.id }) { job -> RepairRow(job) }
            if (visible.isEmpty()) item { ListEmpty("No repair jobs found", "Try another status or create a new repair job.", "Add Repair Job") { vm.navigate(AppScreen.ADD_REPAIR) } }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Repair Job", { vm.navigate(AppScreen.ADD_REPAIR) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
    }
}

@Composable
private fun RepairRow(job: RepairJob) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Build, contentDescription = null, tint = BrandBlue) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    UiText(job.id, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    RepairStatusPill(job.status)
                }
                Spacer(Modifier.height(3.dp))
                UiText(job.customer, translate = false, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                UiText("${job.device} · ${job.issue}", color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    UiText(job.date, color = MutedInk, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    UiText("Rs. ${job.amount}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RepairStatusPill(status: RepairStatus) {
    val tone = when (status) {
        RepairStatus.PENDING -> StatusTone.WARNING
        RepairStatus.IN_PROGRESS -> StatusTone.INFO
        RepairStatus.READY -> StatusTone.SUCCESS
        RepairStatus.DELIVERED -> StatusTone.PURPLE
    }
    StatusPill(status.label, tone)
}

@Composable
fun AddRepairScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var customer by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("Samsung") }
    var model by rememberSaveable { mutableStateOf("") }
    var issue by rememberSaveable { mutableStateOf("Display Issue") }
    var cost by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Pending") }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Add Repair Job", "Track every device from intake to delivery.") }
            item { AppDropdown("Customer *", customer, vm.customers.map { it.name }, { customer = it }) }
            item { AppTextField("Customer Mobile *", phone, { phone = it }, leadingIcon = Icons.Outlined.PhoneAndroid) }
            item { AppDropdown("Brand *", brand, listOf("Samsung", "Apple", "Xiaomi", "Oppo", "Vivo", "Other"), { brand = it }) }
            item { AppTextField("Model *", model, { model = it }) }
            item { AppDropdown("Problem Type *", issue, listOf("Display Issue", "Battery Drain", "Charging Port Issue", "Speaker Not Working", "Back Panel Damage", "Software Issue"), { issue = it }) }
            item { AppTextField("Estimated Cost *", cost, { cost = it }, leadingIcon = Icons.Outlined.Payments) }
            item { AppDropdown("Status *", status, RepairStatus.entries.map { it.label }, { status = it }) }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save Repair Job", { vm.saveRepair(customer, phone, "$brand $model", issue, cost, RepairStatus.entries.first { it.label == status }) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun CustomersScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val visible = vm.customers.filter { it.name.contains(search, true) || it.phone.contains(search, true) }
    if (showAdd) AddCustomerDialog(onDismiss = { showAdd = false }, onSave = { name, phone -> vm.addCustomer(name, phone); showAdd = false })
    Column(modifier = modifier.padding(ScreenPadding)) {
        SearchBox(search, { search = it }, "Search customers")
        Spacer(Modifier.height(11.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { customer -> CustomerRow(customer, vm) }
            if (visible.isEmpty()) item { ListEmpty("No customers found", "Add a new customer to create an account ledger.") }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Customer", { showAdd = true }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
    }
}

@Composable
private fun CustomerRow(customer: Customer, vm: AppViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(customer.name, BrandBlue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(customer.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                UiText(customer.phone, translate = false, color = MutedInk, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                UiText(vm.formatMoney(kotlin.math.abs(customer.balance)), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val tone = when (customer.status) { CustomerStatus.CREDIT -> StatusTone.SUCCESS; CustomerStatus.PAID -> StatusTone.INFO; CustomerStatus.OVERDUE -> StatusTone.DANGER }
                StatusPill(customer.status.label, tone)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MutedInk)
        }
    }
}

@Composable
private fun AddCustomerDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { UiText("Add Customer", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { AppTextField("Customer Name", name, { name = it }); AppTextField("Mobile Number", phone, { phone = it }) } },
        confirmButton = { TextButton(onClick = { onSave(name, phone) }) { UiText("Save", color = BrandBlue, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { UiText("Cancel", color = MutedInk) } }
    )
}

@Composable
fun SuppliersScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = vm.suppliers.filter { it.name.contains(search, true) || it.phone.contains(search, true) }
    Column(modifier = modifier.padding(ScreenPadding)) {
        SearchBox(search, { search = it }, "Search suppliers")
        Spacer(Modifier.height(11.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { supplier -> SupplierRow(supplier, vm) }
            if (visible.isEmpty()) item { ListEmpty("No suppliers found", "Try a supplier name or mobile number.") }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Supplier Ledger", { vm.showMessage("Supplier ledger opened with payable balances and purchase history.") }, Modifier.fillMaxWidth(), Icons.Outlined.ReceiptLong)
    }
}

@Composable
private fun SupplierRow(supplier: Supplier, vm: AppViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(supplier.name, Purple)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(supplier.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                UiText(supplier.phone, translate = false, color = MutedInk, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                UiText(vm.formatMoney(supplier.payable), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                StatusPill("Payable", StatusTone.DANGER)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MutedInk)
        }
    }
}
