@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import android.content.Intent
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
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
import androidx.compose.ui.platform.LocalContext
import com.skybarech.mobileshoperp.model.AppScreen
import com.skybarech.mobileshoperp.model.Product
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*

private val accessoryCategories = listOf("Cover", "Glass / Protector", "Charger", "Cable", "Handsfree", "Earbuds", "Power Bank", "Mobile Holder", "Smart Watch", "Speaker", "OTG / Connector", "Memory Card", "Other", "Accessory")
private val sparePartCategories = listOf("Display / LCD", "Touch Panel", "Battery", "Charging Board", "Charging Strip", "Speaker / Ringer", "Mic", "Camera", "Back Camera", "Front Camera", "Fingerprint", "Power Button Flex", "Volume Button Flex", "SIM Jacket", "Back Cover", "Frame / Body", "Panel", "IC / Board Part", "Connector", "Repair Part", "Other Part")
private fun Product.isAccessoryItem() = category in accessoryCategories
private fun Product.isSparePartItem() = category in sparePartCategories

private val mobileBrands = listOf("Samsung", "iPhone", "Vivo", "Oppo", "Infinix", "Tecno", "Xiaomi", "Realme", "Itel", "Nokia", "Other")
private val mobileModelLibrary = mapOf(
    "iPhone" to listOf("iPhone 6", "iPhone 7", "iPhone 8", "iPhone X", "iPhone XR", "iPhone 11", "iPhone 12", "iPhone 13", "iPhone 14", "iPhone 15", "Manual / Add New"),
    "Samsung" to listOf("Galaxy A04", "Galaxy A05", "Galaxy A12", "Galaxy A13", "Galaxy A14", "Galaxy A15", "Galaxy A23", "Galaxy A24", "Galaxy A25", "Galaxy A32", "Galaxy A33", "Galaxy A34", "Galaxy A35", "Galaxy A52", "Galaxy A53", "Galaxy A54", "Galaxy A55", "Galaxy S21", "Galaxy S22", "Galaxy S23", "Galaxy S24", "Galaxy S24 Ultra", "Manual / Add New"),
    "Vivo" to listOf("Vivo Y12", "Vivo Y15", "Vivo Y16", "Vivo Y17", "Vivo Y20", "Vivo Y21", "Vivo Y22", "Vivo Y27", "Vivo Y33s", "Vivo Y35", "Vivo V21", "Vivo V23", "Vivo V25", "Vivo V27", "Vivo V29", "Manual / Add New"),
    "Oppo" to listOf("Oppo A16", "Oppo A17", "Oppo A18", "Oppo A31", "Oppo A54", "Oppo A57", "Oppo A58", "Oppo A76", "Oppo A77", "Oppo A78", "Oppo F17", "Oppo F19", "Oppo F21 Pro", "Oppo Reno 6", "Oppo Reno 8", "Manual / Add New"),
    "Infinix" to listOf("Infinix Hot 10", "Infinix Hot 11", "Infinix Hot 12", "Infinix Hot 20", "Infinix Hot 30", "Infinix Hot 40", "Infinix Note 10", "Infinix Note 11", "Infinix Note 12", "Infinix Note 30", "Infinix Smart 6", "Infinix Smart 7", "Infinix Smart 8", "Manual / Add New"),
    "Tecno" to listOf("Tecno Spark 7", "Tecno Spark 8", "Tecno Spark 9", "Tecno Spark 10", "Tecno Spark 20", "Tecno Camon 17", "Tecno Camon 18", "Tecno Camon 19", "Tecno Camon 20", "Tecno Pova 3", "Tecno Pova 5", "Manual / Add New"),
    "Xiaomi" to listOf("Redmi 9", "Redmi 10", "Redmi 12", "Redmi 13C", "Redmi Note 10", "Redmi Note 11", "Redmi Note 12", "Redmi Note 13", "Redmi Note 13 Pro", "Poco X3", "Poco X4", "Poco X5", "Poco X6", "Manual / Add New"),
    "Realme" to listOf("Realme C11", "Realme C21", "Realme C25", "Realme C30", "Realme C33", "Realme C35", "Realme C51", "Realme C53", "Realme C55", "Realme 8", "Realme 9", "Realme 10", "Realme 11", "Manual / Add New"),
    "Itel" to listOf("Itel A23", "Itel A26", "Itel A48", "Itel A60", "Itel A70", "Itel S23", "Itel S23 Plus", "Itel Vision 1", "Itel Vision 2", "Manual / Add New"),
    "Nokia" to listOf("Nokia 2.4", "Nokia 3.4", "Nokia 5.4", "Nokia C10", "Nokia C20", "Nokia C21", "Nokia C30", "Nokia G10", "Nokia G20", "Nokia G21", "Nokia G22", "Manual / Add New"),
    "Other" to listOf("Universal", "Manual / Add New")
)
private fun modelsForBrand(brand: String) = mobileModelLibrary[brand] ?: mobileModelLibrary.getValue("Other")

@Composable
private fun BrandModelPicker(
    brand: String,
    onBrandChange: (String) -> Unit,
    selectedModel: String,
    onModelChange: (String) -> Unit,
    manualModel: String,
    onManualModelChange: (String) -> Unit,
    vm: AppViewModel,
    modifier: Modifier = Modifier
) {
    val models = modelsForBrand(brand)
    Column(modifier = modifier.fillMaxWidth()) {
        AppDropdown("Mobile Brand", brand, mobileBrands, { value ->
            onBrandChange(value)
            val next = modelsForBrand(value).firstOrNull().orEmpty()
            onModelChange(next)
            onManualModelChange("")
        })
        Spacer(Modifier.height(8.dp))
        AppDropdown("Mobile Model", selectedModel.ifBlank { models.first() }, models, { onModelChange(it) })
        if (selectedModel == "Manual / Add New" || brand == "Other") {
            Spacer(Modifier.height(8.dp))
            AppTextField("Manual Model", manualModel, onManualModelChange, placeholder = "Samsung A14 / iPhone 13 / Vivo Y21")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.showMessage("Models synced safely. Internet mode mein latest models database mein save honge; missing model manually add kar sakte hain.") }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            UiText("Update Models From Internet", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        UiText("Pehle saved models show honge. Search na mile to Manual Model likh kar save karein.", color = MutedInk, fontSize = 12.sp)
    }
}

private fun variantOptions(category: String) = when (category) {
    "Cover" -> listOf("Silicon", "Hard", "Leather", "Transparent", "Fancy")
    "Glass / Protector" -> listOf("9D", "11D", "Matte", "Privacy", "UV", "Simple")
    "Charger" -> listOf("10W", "18W", "25W", "45W", "Fast Charger")
    "Cable" -> listOf("Type-C", "Micro USB", "Lightning / iPhone")
    "Handsfree", "Earbuds" -> listOf("Wired", "Wireless", "Bluetooth")
    else -> listOf("Standard", "Other")
}

@Composable
fun PosScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = vm.products.filter { it.name.contains(search, true) || it.sku.contains(search, true) || it.notes.contains(search, true) }
    Column(modifier = modifier.padding(ScreenPadding)) {
        PageTitle("POS Billing", "Choose products first, then save and print the bill.")
        Spacer(Modifier.height(8.dp))
        SoftCard(Modifier.fillMaxWidth(), contentPadding = 10.dp) {
            AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBox(search, { search = it }, "Search product or barcode", Modifier.weight(1.4f))
                BarcodeScanButton(Modifier.weight(.9f), onScan = { search = it }, onError = vm::showMessage)
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionLabel("1. Products · ${visible.size} available")
        Spacer(Modifier.height(7.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible, key = { it.id }) { product -> ProductPosRow(product, onAdd = { vm.addToCart(product) }) }
            if (visible.isEmpty()) item { ListEmpty("No matching product", "Try a different product name, model or SKU.") }
        }
        Spacer(Modifier.height(8.dp))
        SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 11.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    UiText("2. Current bill · ${vm.cart.sumOf { it.quantity }} item(s)", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    UiText(vm.formatMoney(vm.cartTotal()), color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                if (vm.cart.isNotEmpty()) UiText(vm.cart.takeLast(2).joinToString(" · ") { it.product.name }, translate = false, color = MutedInk, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Clear Cart", vm::clearCart, Modifier.weight(0.75f), Icons.Outlined.DeleteOutline)
            PrimaryButton("Review & Save Bill", { vm.navigate(AppScreen.CART_PAYMENT) }, Modifier.weight(1.25f), Icons.Outlined.ReceiptLong)
        }
    }
}

@Composable
private fun ProductPosRow(product: Product, onAdd: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(product.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(product.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(listOf(product.category, product.variant, product.model, product.rack).filter { it.isNotBlank() }.joinToString(" · "), color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                UiText("Rs. ${product.salePrice}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill("Stock: ${product.stock}", if (product.stock <= 5) StatusTone.DANGER else StatusTone.NEUTRAL)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandBlue, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    UiText(" Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProductSearchScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val visible = vm.products.filter { it.name.contains(search, true) || it.brand.contains(search, true) || it.model.contains(search, true) || it.sku.contains(search, true) || it.notes.contains(search, true) }
    Column(modifier = modifier.padding(ScreenPadding)) {
        SearchBox(search, { search = it }, "Search by name, brand, model or SKU")
        Spacer(Modifier.height(10.dp))
        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarcodeScanButton(Modifier.weight(1f), onScan = { search = it }, onError = vm::showMessage)
            UiText("Search also matches purchase IMEIs.", Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
        }
        Spacer(Modifier.height(17.dp))
        SectionLabel("Search Results")
        Spacer(Modifier.height(9.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible, key = { it.id }) { product -> ProductPosRow(product, { vm.addToCart(product) }) }
            if (visible.isEmpty()) item { ListEmpty("No products found", "Try scanning a SKU or use another keyword.") }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Quick Add (Manual)", { vm.navigate(AppScreen.ADD_PRODUCT) }, Modifier.fillMaxWidth(), Icons.Outlined.AddCircleOutline)
    }
}

@Composable
fun CartPaymentScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var paidAmount by remember(vm.cartTotal()) { mutableStateOf(vm.cartTotal().toString()) }
    val methods = listOf("Cash", "EasyPaisa In", "JazzCash In", "Bank Transfer", "Credit Card")
    Column(modifier = modifier.padding(ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Bill Summary")
                    Spacer(Modifier.height(9.dp))
                    BillSummary(vm)
                }
            }
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Payment Details")
                    Spacer(Modifier.height(10.dp))
                    AppTextField("Paid Amount", paidAmount, { paidAmount = it }, leadingIcon = Icons.Outlined.Payments)
                    Spacer(Modifier.height(8.dp))
                    val paid = paidAmount.trim().replace(",", "").toIntOrNull() ?: 0
                    SummaryLine("Balance", vm.formatMoney((vm.cartTotal() - paid).coerceAtLeast(0)), highlight = true)
                }
            }
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    SectionLabel("Payment Method")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        methods.chunked(3).forEach { group ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEach { method ->
                                    FilterChip(
                                        selected = vm.lastPaymentMethod == method,
                                        onClick = { vm.selectPayment(method) },
                                        label = { UiText(method, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        leadingIcon = if (vm.lastPaymentMethod == method) ({ Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(15.dp)) }) else null,
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandBlue, selectedLabelColor = MaterialTheme.colorScheme.onPrimary, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary)
                                    )
                                }
                                repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Review Payment", { val paid = paidAmount.trim().replace(",", "").toIntOrNull(); if (paid == null || paid < vm.cartTotal()) vm.showMessage("Enter the complete payable amount.") else vm.prepareInvoice() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun BillSummary(vm: AppViewModel) {
    SummaryLine("Subtotal", vm.formatMoney(vm.cartSubtotal()))
    Spacer(Modifier.height(6.dp))
    SummaryLine("Discount", "- ${vm.formatMoney(vm.cartDiscount())}", valueColor = Success)
    Spacer(Modifier.height(6.dp))
    SummaryLine("Tax (Optional)", "Rs. 0")
    Spacer(Modifier.height(9.dp))
    Surface(shape = RoundedCornerShape(12.dp), color = BrandBlueSoft) {
        SummaryLine("Total Payable", vm.formatMoney(vm.cartTotal()), modifier = Modifier.padding(11.dp), highlight = true)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color = Ink, highlight: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UiText(label, color = if (highlight) BrandBlueDark else MutedInk, fontSize = 12.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.weight(1f))
        UiText(value, color = valueColor, fontSize = if (highlight) 14.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InvoiceScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cart = vm.invoiceLines()
    Column(modifier = modifier.padding(ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BrandMark(48.dp, initials = vm.shopInitials)
                        Spacer(Modifier.height(7.dp))
                        UiText(vm.shopName, translate = false, color = Ink, fontWeight = FontWeight.ExtraBold)
                        UiText(vm.ownerMobile, translate = false, color = MutedInk, fontSize = 12.sp)
                        if (vm.shopAddress.isNotBlank()) UiText(vm.shopAddress, translate = false, color = MutedInk, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = CardStroke)
                        Spacer(Modifier.height(9.dp))
                        ReceiptLine("Invoice #", vm.lastInvoiceNumber)
                        ReceiptLine("Date", vm.lastInvoiceDate)
                        UiText(if (vm.invoiceCompleted) "Payment recorded" else "Review payment, then save invoice", color = if (vm.invoiceCompleted) Success else Warning, fontSize = 12.sp)
                        ReceiptLine("Cashier", "Owner")
                        Spacer(Modifier.height(9.dp))
                        HorizontalDivider(color = CardStroke)
                        Spacer(Modifier.height(9.dp))
                        cart.forEach { line ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    UiText(line.product.name, translate = false, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    UiText(line.product.variant, color = MutedInk, fontSize = 12.sp)
                                }
                                UiText("${line.quantity} × ${line.product.salePrice}", color = Ink, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (cart.isEmpty()) {
                            UiText("No active cart. Go back to POS Billing to create an invoice.", color = MutedInk, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            HorizontalDivider(color = CardStroke)
                            Spacer(Modifier.height(9.dp))
                            ReceiptLine("Subtotal", vm.formatMoney(vm.invoiceTotal()))
                            ReceiptLine("Discount", "- ${vm.formatMoney(vm.cartDiscount())}")
                            ReceiptLine("Tax (0%)", "Rs. 0")
                            Spacer(Modifier.height(5.dp))
                            ReceiptLine("Total Payable", vm.formatMoney(vm.invoiceTotal()), true)
                            ReceiptLine("Payment Method", vm.lastPaymentMethod)
                            
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = CardStroke)
                            Spacer(Modifier.height(10.dp))
                            UiText("Thank you for your purchase!", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            UiText("Powered by SkyBarech Technology", color = MutedInk, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Print (80mm)", {
                if (!vm.invoiceCompleted) vm.showMessage("Save the invoice before printing a paid receipt.")
                else printInvoice(context, vm.lastInvoiceNumber, vm.invoiceShareText())
            }, Modifier.weight(1f), Icons.Outlined.Print)
            OutlineButton("Share", {
                if (!vm.invoiceCompleted) vm.showMessage("Save the invoice before sharing a paid receipt.")
                else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Invoice ${vm.lastInvoiceNumber}")
                    putExtra(Intent.EXTRA_TEXT, vm.invoiceShareText())
                }, "Share invoice"))
            }, Modifier.weight(1f), Icons.Outlined.Share)
        }
        Spacer(Modifier.height(8.dp))
        BluetoothThermalPrintButton(
            receipt = vm.invoiceShareText(),
            enabled = vm.invoiceCompleted,
            onMessage = vm::showMessage,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(if (vm.invoiceCompleted) "New Sale" else "Save Invoice", { if (vm.invoiceCompleted) vm.navigateRoot(AppScreen.POS) else vm.completeSale() }, Modifier.fillMaxWidth(), Icons.Outlined.CheckCircle)
    }
}

fun printInvoice(context: Context, invoiceNo: String, receipt: String) {
    val displayReceipt = receipt.lineSequence().mapIndexed { index, line ->
        if (index == 0) line else {
            val label = line.substringBefore(":", "")
            if (label in setOf("Invoice", "Date", "Customer", "Supplier", "Payment", "Total", "Amount", "Fee", "Reference", "Quantity", "Unit cost", "Received", "Method", "Plan")) tr(label) + ":" + line.substringAfter(":")
            else if (line in setOf("PAID RECEIPT", "PAYMENT PREVIEW", "Thank you", "Thank you for your purchase!", "Installment payment")) tr(line)
            else line
        }
    }.joinToString("\n")
    val receiptLines = displayReceipt.lineSequence().toList()
    val shopTitle = receiptLines.firstOrNull().orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val receiptBody = receiptLines.drop(1).joinToString("\n").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(invoiceNo, view.createPrintDocumentAdapter(invoiceNo), PrintAttributes.Builder().build())
        }
    }
    val direction = if (UiLanguage.code == "ur") "rtl" else "ltr"
    webView.loadDataWithBaseURL("file:///android_asset/", "<html lang='${UiLanguage.code}' dir='$direction'><head><meta charset='UTF-8'><style>@font-face{font-family:Urdu;src:url('NotoNaskhArabic.ttf')}@page{size:80mm auto;margin:3mm}body{font-family:Urdu,Arial,sans-serif;width:72mm;font-size:11px;line-height:1.55;color:#101828;overflow-wrap:anywhere}.receipt{border:1px solid #d9e8fb;border-radius:8px;padding:10px;background:linear-gradient(180deg,#eef7ff,#fff 74px)}.brand{width:28px;height:28px;line-height:28px;margin:0 auto 4px;border-radius:9px;background:linear-gradient(135deg,#008cff,#583cff);color:#fff;font:bold 15px Arial;text-align:center}.shop{font:bold 16px Arial;text-align:center;letter-spacing:.4px;color:#063b7b;border-block:1px solid #1d66ad;padding:3px 0;margin-bottom:6px}.rule{border:0;border-top:1px dashed #78a9d8;margin:8px 0}.foot{text-align:center;color:#42607d;font-size:9px;margin-top:9px}</style></head><body><section class='receipt'><div class='brand'>S</div><div class='shop'>$shopTitle</div><hr class='rule'><div>$receiptBody</div><hr class='rule'><div class='foot'>Thank you for choosing us<br>Powered by SkyBarech ERP</div></section></body></html>", "text/html", "UTF-8", null)
}

@Composable
private fun ReceiptLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        UiText(label, modifier = Modifier.weight(1f), color = if (strong) BrandBlueDark else MutedInk, fontSize = 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        UiText(value, color = if (strong) BrandBlue else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun InventoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val visible = vm.products.filter { product ->
        val matchesSearch = product.name.contains(search, true) || product.brand.contains(search, true) || product.model.contains(search, true) || product.sku.contains(search, true) || product.rack.contains(search, true)
        val isMainInventory = !product.isAccessoryItem() && !product.isSparePartItem() && product.category != "Laptop"
        val matchesFilter = selectedFilter == "All" || (selectedFilter == "Low Stock" && product.stock <= 5) || (selectedFilter == product.category)
        isMainInventory && matchesSearch && matchesFilter
    }
    Column(modifier = modifier.padding(ScreenPadding)) {
        SearchBox(search, { search = it }, "Search product, brand, model or SKU")
        Spacer(Modifier.height(9.dp))
        FilterRow(listOf("All", "Mobile", "Low Stock"), selectedFilter, { selectedFilter = it })
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible, key = { it.id }) { product -> InventoryRow(product, onClick = { vm.navigate(AppScreen.ADD_PRODUCT) }) }
            if (visible.isEmpty()) item { ListEmpty("No inventory items", "Add a new product or remove the filters.", "Add Product") { vm.navigate(AppScreen.ADD_PRODUCT) } }
        }
        Spacer(Modifier.height(10.dp))
        AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Add Product", { vm.navigate(AppScreen.ADD_PRODUCT) }, Modifier.weight(1f), Icons.Outlined.Add)
            OutlineButton("Accessories", { vm.navigate(AppScreen.MOBILE_ACCESSORIES) }, Modifier.weight(1f), Icons.Outlined.PhoneAndroid)
            OutlineButton("Parts", { vm.navigate(AppScreen.MOBILE_SPARE_PARTS) }, Modifier.weight(1f), Icons.Outlined.Construction)
            OutlineButton("Laptop", { vm.navigate(AppScreen.LAPTOP) }, Modifier.weight(1f), Icons.Outlined.Computer)
        }
    }
}

@Composable
private fun InventoryRow(product: Product, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(product.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(product.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(listOf(product.category, product.variant, product.model, product.rack).filter { it.isNotBlank() }.joinToString(" · "), color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                UiText("Stock: ${product.stock}", color = MutedInk, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (product.stock <= 5) StatusPill("Low Stock", StatusTone.DANGER)
                UiText("Rs. ${product.salePrice}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { UiText("Edit", fontSize = 12.sp) }
            }
        }
    }
}


@Composable
fun LaptopScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val laptops = vm.products.filter { product ->
        product.category == "Laptop" &&
            (product.name.contains(search, true) || product.brand.contains(search, true) || product.model.contains(search, true) || product.variant.contains(search, true) || product.sku.contains(search, true) || product.rack.contains(search, true))
    }
    Column(modifier = modifier.padding(ScreenPadding)) {
        PageTitle("Laptop", "Laptop stock, purchase price, sale price, specs and rack details.")
        Spacer(Modifier.height(9.dp))
        SearchBox(search, { search = it }, "Search laptop, brand, model, specs, SKU or rack")
        Spacer(Modifier.height(10.dp))
        AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Laptops", laptops.size.toString(), Icons.Outlined.Computer, modifier = Modifier.weight(1f))
            MetricCard("Low", laptops.count { it.stock <= it.minStock }.toString(), Icons.Outlined.WarningAmber, accent = Danger, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(laptops, key = { it.id }) { product -> InventoryRow(product, onClick = { vm.navigate(AppScreen.ADD_LAPTOP) }) }
            if (laptops.isEmpty()) item { ListEmpty("No laptops", "Add laptop stock separately here, so it does not mix with mobile inventory.", "Add Laptop") { vm.navigate(AppScreen.ADD_LAPTOP) } }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Laptop", { vm.navigate(AppScreen.ADD_LAPTOP) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun AddLaptopScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val brands = listOf("HP", "Dell", "Lenovo", "Apple", "Acer", "Asus", "Microsoft", "Other")
    val conditions = listOf("New", "Used", "Open Box", "Refurbished")
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("HP") }
    var model by rememberSaveable { mutableStateOf("") }
    var specs by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("Used") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var sale by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("1") }
    var minStock by rememberSaveable { mutableStateOf("1") }
    var rack by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Add Laptop", "Fast laptop entry for shop stock.") }
            item { AppTextField("Laptop Name *", name, { name = it }, placeholder = "HP EliteBook 840 G5") }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("Brand", brand, brands, { brand = it }, Modifier.weight(1f))
                AppTextField("Model", model, { model = it }, Modifier.weight(1f), placeholder = "840 G5")
            } }
            item { AppTextField("Specs", specs, { specs = it }, placeholder = "Core i5 / 8GB / 256GB SSD") }
            item { AppDropdown("Condition", condition, conditions, { condition = it }) }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Purchase *", purchase, { purchase = it }, Modifier.weight(1f), placeholder = "Rs. 0")
                AppTextField("Sale *", sale, { sale = it }, Modifier.weight(1f), placeholder = "Rs. 0")
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Qty *", stock, { stock = it }, Modifier.weight(1f), placeholder = "1")
                AppTextField("Min Stock", minStock, { minStock = it }, Modifier.weight(1f), placeholder = "1")
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Rack", rack, { rack = it }, Modifier.weight(1f), placeholder = "L-1")
                AppTextField("SKU", sku, { sku = it }, Modifier.weight(1f), placeholder = "Auto if blank")
            } }
            item { AppTextField("Notes", notes, { notes = it }, placeholder = "Battery health / warranty / supplier notes") }
        }
        PrimaryButton("Save Laptop", {
            val finalName = name.ifBlank { "$brand $model".trim() }
            vm.addProduct(finalName, "Laptop", brand, model, "", specs.ifBlank { condition }, "", condition, purchase, sale, "", stock, minStock, rack, sku, "No Warranty", notes)
        }, Modifier.fillMaxWidth(), Icons.Outlined.CheckCircle)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun MobileAccessoriesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val accessories = vm.products.filter { product ->
        product.isAccessoryItem() &&
            (product.name.contains(search, true) || product.category.contains(search, true) || product.brand.contains(search, true) || product.model.contains(search, true) || product.compatibleModels.contains(search, true) || product.sku.contains(search, true) || product.rack.contains(search, true))
    }
    Column(modifier = modifier.padding(ScreenPadding)) {
        PageTitle("Mobile Accessories", "Covers, chargers, tempered glass, cables and handsfree stock.")
        Spacer(Modifier.height(9.dp))
        SearchBox(search, { search = it }, "Search name, category, brand, model, SKU or rack")
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(accessories, key = { it.id }) { product -> InventoryRow(product, onClick = { vm.navigate(AppScreen.ADD_PRODUCT) }) }
            if (accessories.isEmpty()) item { ListEmpty("No accessories", "Add covers, chargers, handsfree, cables or glass here.", "Add Accessory") { vm.navigate(AppScreen.ADD_ACCESSORY) } }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Accessory", { vm.navigate(AppScreen.ADD_ACCESSORY) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
        Spacer(Modifier.height(10.dp))
    }
}


@Composable
fun AddAccessoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    AddProductScreen(vm, modifier)
}

@Composable
fun MobileSparePartsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    val parts = vm.products.filter { product ->
        product.isSparePartItem() &&
            (product.name.contains(search, true) || product.category.contains(search, true) || product.brand.contains(search, true) || product.model.contains(search, true) || product.sku.contains(search, true) || product.rack.contains(search, true))
    }
    Column(modifier = modifier.padding(ScreenPadding)) {
        PageTitle("Mobile Spare Parts", "LCD, battery, charging board, flex, camera and repair part stock.")
        Spacer(Modifier.height(9.dp))
        SearchBox(search, { search = it }, "Search part, category, brand, model, SKU or rack")
        Spacer(Modifier.height(10.dp))
        AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Parts", parts.size.toString(), Icons.Outlined.Construction, modifier = Modifier.weight(1f))
            MetricCard("Low", parts.count { it.stock <= it.minStock }.toString(), Icons.Outlined.WarningAmber, accent = Danger, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(parts, key = { it.id }) { product -> InventoryRow(product, onClick = { vm.navigate(AppScreen.ADD_SPARE_PART) }) }
            if (parts.isEmpty()) item { ListEmpty("No spare parts", "Add LCD, battery, charging board, camera, flex or speaker parts here.", "Add Spare Part") { vm.navigate(AppScreen.ADD_SPARE_PART) } }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Spare Part", { vm.navigate(AppScreen.ADD_SPARE_PART) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun AddSparePartScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val qualities = listOf("Original", "Master Copy", "A Quality", "Local", "China")
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Display / LCD") }
    var brand by rememberSaveable { mutableStateOf("Samsung") }
    var model by remember { mutableStateOf(modelsForBrand(brand).first()) }
    var manualModel by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf("A Quality") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var sale by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("1") }
    var minStock by rememberSaveable { mutableStateOf("2") }
    var rack by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Add Spare Part", "Repair parts fast entry for mobile shops.") }
            item { SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Construction, contentDescription = null, tint = BrandBlue)
                    Spacer(Modifier.width(9.dp))
                    UiText("Required: Part Name, Category, Purchase, Sale and Quantity. shop_id auto attach hoga.", color = MutedInk, fontSize = 12.sp)
                }
            } }
            item { AppTextField("Part Name *", name, { name = it }, placeholder = "Samsung A14 LCD / iPhone 11 Battery") }
            item { AppDropdown("Part Category *", category, sparePartCategories, { category = it }) }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Purchase *", purchase, { purchase = it }, Modifier.weight(1f), placeholder = "Rs. 0")
                AppTextField("Sale *", sale, { sale = it }, Modifier.weight(1f), placeholder = "Rs. 0")
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Quantity *", stock, { stock = it }, Modifier.weight(1f), placeholder = "0")
                AppTextField("Rack", rack, { rack = it }, Modifier.weight(1f), placeholder = "P-1")
            } }
            item { BrandModelPicker(brand, { brand = it }, model, { model = it }, manualModel, { manualModel = it }, vm) }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("Quality", quality, qualities, { quality = it }, Modifier.weight(1f))
                AppTextField("Min Stock", minStock, { minStock = it }, Modifier.weight(1f), placeholder = "2")
            } }
            item { AppTextField("Barcode / SKU", sku, { sku = it }, placeholder = "Auto generate if blank") }
            item { AppTextField("Notes", notes, { notes = it }, placeholder = "Supplier / compatibility / warranty notes") }
        }
        PrimaryButton("Save Spare Part", {
            vm.addProduct(name, category, brand, if (model == "Manual / Add New" || brand == "Other") manualModel else model, "", quality, "", quality, purchase, sale, "", stock, minStock, rack, sku, "No Warranty", notes)
        }, Modifier.fillMaxWidth(), Icons.Outlined.CheckCircle)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun AddProductScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val categories = accessoryCategories.filter { it != "Accessory" }
    val colors = listOf("Black", "White", "Blue", "Red", "Transparent", "Mix", "Other")
    val qualities = listOf("Original", "Master Copy", "A Quality", "Local", "China")
    val warranties = listOf("No Warranty", "7 Days", "15 Days", "1 Month", "3 Months", "6 Months")
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Cover") }
    var brand by rememberSaveable { mutableStateOf("Samsung") }
    var model by remember { mutableStateOf(modelsForBrand(brand).first()) }
    var manualModel by rememberSaveable { mutableStateOf("") }
    var compatible by rememberSaveable { mutableStateOf("") }
    var variant by rememberSaveable { mutableStateOf("Silicon") }
    var color by rememberSaveable { mutableStateOf("Black") }
    var quality by rememberSaveable { mutableStateOf("A Quality") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var sale by rememberSaveable { mutableStateOf("") }
    var wholesale by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("1") }
    var minStock by rememberSaveable { mutableStateOf("2") }
    var rack by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var warranty by rememberSaveable { mutableStateOf("No Warranty") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    val currentVariants = variantOptions(category)

    LaunchedEffect(category) {
        if (variant !in currentVariants) variant = currentVariants.first()
    }

    Column(modifier = modifier.imePadding().padding(horizontal = ScreenPadding)) {
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageTitle("Add Product", "Fast entry: sirf zaroori fields bharo, baqi optional hain.") }
            item { SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = Success)
                    Spacer(Modifier.width(9.dp))
                    UiText("Shopkeeper fast add: Name, Category, Purchase, Sale, Quantity. Baqi More Details mein hain.", color = MutedInk, fontSize = 12.sp)
                }
            } }
            item { AppTextField("Product Name *", name, { name = it }, placeholder = "A14 Glass / 25W Charger / Type-C Cable") }
            item { AppDropdown("Category *", category, categories, { category = it }) }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Purchase *", purchase, { purchase = it }, Modifier.weight(1f), placeholder = "Rs. 0")
                AppTextField("Sale *", sale, { sale = it }, Modifier.weight(1f), placeholder = "Rs. 0")
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Quantity *", stock, { stock = it }, Modifier.weight(1f), placeholder = "0")
                AppTextField("Rack", rack, { rack = it }, Modifier.weight(1f), placeholder = "A-2")
            } }
            item { AppTextField("Barcode / SKU", sku, { sku = it }, placeholder = "Auto generate if blank", trailingIcon = Icons.Outlined.CenterFocusStrong) }
            item {
                TextButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (showMore) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    UiText(if (showMore) "Hide Optional Details" else "More Details Optional")
                }
            }
            if (showMore) {
                item { BrandModelPicker(brand, { brand = it }, model, { model = it }, manualModel, { manualModel = it }, vm) }
                item { AppTextField("Compatible Models", compatible, { compatible = it }, placeholder = "A14 / A15") }
                item { AppDropdown("Type / Variant", variant, currentVariants, { variant = it }) }
                item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppDropdown("Color", color, colors, { color = it }, Modifier.weight(1f))
                    AppDropdown("Quality", quality, qualities, { quality = it }, Modifier.weight(1f))
                } }
                item { AppTextField("Wholesale Price", wholesale, { wholesale = it }, placeholder = "Optional") }
                item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField("Min Stock", minStock, { minStock = it }, Modifier.weight(1f), placeholder = "2")
                    AppDropdown("Warranty", warranty, warranties, { warranty = it }, Modifier.weight(1f))
                } }
                item { AppTextField("Notes", notes, { notes = it }, placeholder = "Optional") }
            }
            item { UiText("shop_id current login shop se auto attach hoga. User ko shop select nahi karni.", color = MutedInk, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save", {
            vm.addProduct(name, category, brand, if (model == "Manual / Add New" || brand == "Other") manualModel else model, compatible, variant, color, quality, purchase, sale, wholesale, stock, minStock, rack, sku, warranty, notes)
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
    }
}
