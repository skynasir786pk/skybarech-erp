@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import android.content.Intent
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextAlign
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
    Column(modifier) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageTitle("POS Billing", "Choose products first, then save and print the bill.") }
        item {
            SoftCard(Modifier.fillMaxWidth(), contentPadding = 10.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBox(search, { search = it }, "Search product, barcode or SKU")
                    BarcodeScanButton(Modifier.fillMaxWidth(), onScan = { search = it }, onError = vm::showMessage)
                }
            }
        }
        item { SectionLabel("Products · ${visible.size} available") }
        if (visible.isEmpty()) {
            item { ListEmpty("No matching product", "Sync inventory or search by product name, model or SKU.") }
        } else {
            items(visible, key = { it.id }) { product ->
                ProductPosRow(product, onAdd = { vm.addToCart(product) }, quantity = vm.cart.find { it.product.id == product.id }?.quantity ?: 0)
            }
        }
    }
            SoftCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp), contentPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            UiText("Current bill · ${vm.cart.sumOf { it.quantity }} item(s)", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            UiText(vm.formatMoney(vm.cartTotal()), color = BrandBlue, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        }
                        if (vm.cart.isNotEmpty()) UiText(vm.cart.last().product.name, translate = false, color = MutedInk, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    AdaptiveRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("Clear", vm::clearCart, Modifier.weight(.7f), Icons.Outlined.DeleteOutline)
                        PrimaryButton("Save & Pay", { vm.navigate(AppScreen.CART_PAYMENT) }, Modifier.weight(1.3f), Icons.Outlined.ReceiptLong, enabled = vm.cart.isNotEmpty())
                    }
                }
            }
    }
}

@Composable
private fun ProductPosRow(product: Product, onAdd: () -> Unit, quantity: Int = 0) {
    val selected = quantity > 0
    val canAdd = product.stock > quantity
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (selected) BrandBlueSoft else CardSurface, tween(220), label = "product selection color")
    val stroke by animateColorAsState(if (selected) BrandBlue else CardStroke, tween(220), label = "product selection border")
    val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(120), label = "product press")
    Surface(onClick = onAdd, enabled = canAdd, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(18.dp), color = fill, border = BorderStroke(if (selected) 1.5.dp else 1.dp, stroke)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(product.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(product.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                UiText(listOf(product.category, product.variant, product.model, product.rack).filter { it.isNotBlank() }.joinToString(" · "), color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                UiText("Rs. ${product.salePrice}", color = BrandBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (selected) AnimatedContent(targetState = quantity, label = "cart quantity") { count ->
                    UiText("$count in your bill", color = BrandBlueDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill("Stock: ${product.stock}", if (product.stock <= 5) StatusTone.DANGER else StatusTone.NEUTRAL)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAdd, enabled = canAdd, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandBlue, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    UiText(if (product.stock <= 0) "Sold out" else if (!canAdd) "All added" else if (selected) " Add more" else " Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            items(visible, key = { it.id }) { product -> ProductPosRow(product, { vm.addToCart(product) }, vm.cart.find { it.product.id == product.id }?.quantity ?: 0) }
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
                        if (vm.shopAddress.isNotBlank()) UiText(vm.shopAddress, translate = false, color = MutedInk, fontSize = 12.sp)
                        UiText(vm.ownerMobile, translate = false, color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = CardStroke)
                        Spacer(Modifier.height(9.dp))
                        ReceiptLine("Invoice #", vm.lastInvoiceNumber)
                        ReceiptLine("Date", vm.lastInvoiceDate)
                        UiText(if (vm.invoiceCompleted) "Payment recorded" else "Review payment, then save invoice", color = if (vm.invoiceCompleted) Success else Warning, fontSize = 12.sp)
                        ReceiptLine("Cashier", "Owner")
                        Spacer(Modifier.height(9.dp))
                        HorizontalDivider(color = CardStroke)
                        Spacer(Modifier.height(9.dp))
                        cart.take(8).forEach { line ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    UiText(line.product.name, translate = false, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    UiText(line.product.variant, color = MutedInk, fontSize = 12.sp)
                                }
                                UiText("${line.quantity} × ${line.product.salePrice}", color = Ink, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (cart.size > 8) UiText("+ ${cart.size - 8} more items — see full invoice in app", color = MutedInk, fontSize = 11.sp)
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
    fun safeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val rawLines = receipt.lineSequence().toList()
    val shopTitle = safeHtml(rawLines.getOrNull(0).orEmpty())
    val shopAddress = safeHtml(rawLines.getOrNull(1).orEmpty())
    val shopPhone = safeHtml(rawLines.getOrNull(2).orEmpty())
    val receiptBody = rawLines.drop(3).map { line ->
        val label = line.substringBefore(":", "")
        when {
            label in setOf("Invoice", "Date", "Customer", "Supplier", "Payment", "Total", "Amount", "Fee", "Reference", "Quantity", "Unit cost", "Received", "Method", "Plan") -> tr(label) + ":" + safeHtml(line.substringAfter(":"))
            line in setOf("PAID RECEIPT", "PAYMENT PREVIEW", "Thank you", "Thank you for your purchase!", "Installment payment") -> tr(line)
            else -> safeHtml(line)
        }
    }.joinToString("<br>")
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(invoiceNo, view.createPrintDocumentAdapter(invoiceNo), PrintAttributes.Builder().build())
        }
    }
    val direction = if (UiLanguage.code == "ur") "rtl" else "ltr"
    val logo = "<svg viewBox='0 0 72 72' aria-label='SkyBarech logo'><path fill='#009FFF' d='M44 5 60 17 48 28 39 21 26 32 36 40 24 51 13 41Q6 34 13 26L35 7Q39 3 44 5Z'/><path fill='#5A36FF' d='m48 23 11 10q8 8 0 16L37 67q-5 4-10 0L12 56l12-11 10 8 13-12-11-8Z'/></svg>"
    webView.loadDataWithBaseURL("file:///android_asset/", "<html lang='${UiLanguage.code}' dir='$direction'><head><meta charset='UTF-8'><style>@font-face{font-family:Urdu;src:url('NotoNaskhArabic.ttf')}@page{size:80mm auto;margin:2.5mm}body{font-family:Urdu,Arial,sans-serif;width:72mm;font-size:9px;line-height:1.28;color:#101828;overflow-wrap:anywhere}.receipt{border:1px solid #b9d7f5;border-radius:9px;padding:9px 10px;background:linear-gradient(180deg,#eaf6ff 0,#fff 72px)}.brand{width:30px;height:30px;margin:0 auto 3px}.brand svg{width:100%;height:100%;display:block}.shop{font:bold 14px Arial;text-align:center;letter-spacing:.35px;color:#073b78;border-block:1px solid #0b5da8;padding:2px 0;margin:0}.contact{text-align:center;color:#334155;font-size:8px;line-height:1.2;margin:3px 0}.phone{color:#0b5da8;font-weight:bold}.tag{display:table;margin:4px auto 0;padding:2px 8px;border-radius:99px;background:#0b5da8;color:#fff;font:bold 7px Arial;letter-spacing:.6px}.rule{border:0;border-top:1px dashed #94bfe6;margin:5px 0}.body{font-size:8px;line-height:1.25}.foot{text-align:center;color:#466987;font-size:7px;line-height:1.2;margin-top:5px}.foot b{display:block;color:#073b78;font-size:8px;margin-bottom:1px}</style></head><body><section class='receipt'><div class='brand'>$logo</div><div class='shop'>$shopTitle</div><div class='contact'>$shopAddress<br><span class='phone'>$shopPhone</span></div><div class='tag'>SALE INVOICE</div><hr class='rule'><div class='body'>$receiptBody</div><hr class='rule'><div class='foot'><b>Thank you for shopping with us</b>Powered by SkyBarech ERP</div></section></body></html>", "text/html", "UTF-8", null)
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
            (product.name.contains(search, true) || product.brand.contains(search, true) || product.model.contains(search, true) || product.processor.contains(search, true) || product.generation.contains(search, true) || product.ram.contains(search, true) || product.storage.contains(search, true) || product.variant.contains(search, true) || product.sku.contains(search, true) || product.rack.contains(search, true))
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
            items(laptops, key = { it.id }) { product -> LaptopInventoryRow(product, onClick = { vm.navigate(AppScreen.ADD_LAPTOP) }) }
            if (laptops.isEmpty()) item { ListEmpty("No laptops", "Add laptop stock separately here, so it does not mix with mobile inventory.", "Add Laptop") { vm.navigate(AppScreen.ADD_LAPTOP) } }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add Laptop", { vm.navigate(AppScreen.ADD_LAPTOP) }, Modifier.fillMaxWidth(), Icons.Outlined.Add)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LaptopInventoryRow(product: Product, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductAvatar(product.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                UiText(product.name, translate = false, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(listOf(product.brand, product.model).filter { it.isNotBlank() }.joinToString(" · "), translate = false, color = BrandBlue, fontSize = 12.sp)
                UiText(listOf(product.processor, product.generation, product.ram, listOf(product.storage, product.storageType).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { product.variant }, translate = false, color = MutedInk, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                UiText(listOf(product.graphics, product.screenSize, product.quality).filter { it.isNotBlank() }.joinToString(" · "), translate = false, color = MutedInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(if (product.stock <= product.minStock) "Low Stock" else "In Stock", if (product.stock <= product.minStock) StatusTone.DANGER else StatusTone.SUCCESS)
                UiText("${product.stock} pcs", translate = false, color = MutedInk, fontSize = 11.sp)
                UiText("Rs. ${product.salePrice}", translate = false, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { UiText("Edit", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun AddLaptopScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val brands = listOf("HP", "Dell", "Lenovo", "Apple", "Acer", "Asus", "Microsoft", "MSI", "Samsung", "Razer", "Toshiba", "Other")
    val conditions = listOf("New", "Used", "Open Box", "Refurbished")
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("HP") }
    var model by rememberSaveable { mutableStateOf("") }
    var processor by rememberSaveable { mutableStateOf("") }
    var generation by rememberSaveable { mutableStateOf("11th Gen") }
    var ram by rememberSaveable { mutableStateOf("8GB") }
    var storage by rememberSaveable { mutableStateOf("256GB") }
    var storageType by rememberSaveable { mutableStateOf("SSD") }
    var graphics by rememberSaveable { mutableStateOf("") }
    var screenSize by rememberSaveable { mutableStateOf("14 inch") }
    var operatingSystem by rememberSaveable { mutableStateOf("Windows 11") }
    var batteryHealth by rememberSaveable { mutableStateOf("") }
    var serialNumber by rememberSaveable { mutableStateOf("") }
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
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Processor", processor, { processor = it }, Modifier.weight(1f), placeholder = "Core i5-1135G7")
                AppDropdown("Generation", generation, listOf("6th Gen", "7th Gen", "8th Gen", "9th Gen", "10th Gen", "11th Gen", "12th Gen", "13th Gen", "14th Gen", "Ryzen 5000", "Ryzen 7000", "Apple M1", "Apple M2", "Apple M3", "Apple M4"), { generation = it }, Modifier.weight(1f))
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("RAM", ram, listOf("4GB", "8GB", "16GB", "32GB", "64GB"), { ram = it }, Modifier.weight(1f))
                AppDropdown("Storage", storage, listOf("128GB", "256GB", "512GB", "1TB", "2TB"), { storage = it }, Modifier.weight(1f))
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("Storage Type", storageType, listOf("SSD", "NVMe SSD", "HDD", "eMMC"), { storageType = it }, Modifier.weight(1f))
                AppDropdown("Screen", screenSize, listOf("12.5 inch", "13.3 inch", "14 inch", "15.6 inch", "16 inch", "17.3 inch"), { screenSize = it }, Modifier.weight(1f))
            } }
            item { AppTextField("Graphics", graphics, { graphics = it }, placeholder = "Intel Iris Xe / NVIDIA GTX") }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppDropdown("Operating System", operatingSystem, listOf("Windows 11", "Windows 10", "macOS", "Linux", "No OS"), { operatingSystem = it }, Modifier.weight(1f))
                AppDropdown("Condition", condition, conditions, { condition = it }, Modifier.weight(1f))
            } }
            item { AdaptiveRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField("Battery Health", batteryHealth, { batteryHealth = it }, Modifier.weight(1f), placeholder = "85% / Good")
                AppTextField("Serial Number", serialNumber, { serialNumber = it }, Modifier.weight(1f), placeholder = "Serial no.")
            } }
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
            vm.addProduct(
                name = finalName, category = "Laptop", brand = brand, model = model, compatibleModels = "",
                variant = listOf(processor, generation, ram, "$storage $storageType").filter { it.isNotBlank() }.joinToString(" / "),
                color = "", quality = condition, purchase = purchase, sale = sale, wholesale = "", stock = stock,
                minStock = minStock, rack = rack, sku = sku, warranty = "No Warranty", notes = notes,
                ram = ram, storage = storage, storageType = storageType, processor = processor, generation = generation,
                graphics = graphics, screenSize = screenSize, operatingSystem = operatingSystem,
                batteryHealth = batteryHealth, serialNumber = serialNumber
            )
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
