package com.skybarech.mobileshoperp.model

enum class AppScreen(val title: String) {
    SPLASH(""),
    ONBOARDING("Welcome to SkyBarech ERP"),
    ACTIVATION("Activate Account"),
    LOGIN("Login"),
    CHANGE_PASSWORD("Change Password"),
    DASHBOARD("SkyBarech Mobile Shop ERP"),
    POS("POS Billing"),
    PRODUCT_SEARCH("Product Search"),
    CART_PAYMENT("Cart & Payment"),
    INVOICE("Invoice Preview"),
    INVENTORY("Inventory"),
    MOBILE_ACCESSORIES("Mobile Accessories"),
    ADD_ACCESSORY("Add Accessory"),
    MOBILE_SPARE_PARTS("Spare Parts"),
    ADD_SPARE_PART("Add Spare Part"),
    LAPTOP("Laptop"),
    ADD_LAPTOP("Add Laptop"),
    ADD_PRODUCT("Add Product"),
    MOBILE_PURCHASE("Mobile Purchase"),
    MOBILE_SALE("Mobile Sale"),
    REPAIRS("Repair Jobs"),
    ADD_REPAIR("Add Repair Job"),
    CUSTOMERS("Customers"),
    SUPPLIERS("Suppliers"),
    INSTALLMENTS("Installments"),
    ADD_INSTALLMENT("Add Installment"),
    RECEIVE_PAYMENT("Receive Payment"),
    REPORTS("Reports"),
    EXPENSES("Expenses"),
    CASH_LEDGER("Cash Ledger"),
    EWALLET("Easypaisa/Jazz"),
    STOCK_ALERTS("Stock Alerts"),
    STAFF_ACTIVITY("Staff Activity"),
    SETTINGS("Settings"),
    HELP("Help Center")
}

data class Product(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val variant: String,
    val salePrice: Int,
    val purchasePrice: Int,
    val stock: Int,
    val category: String = "Mobile",
    val sku: String = "",
    val shopId: String = "SHOP-LOCAL-001",
    val compatibleModels: String = "",
    val color: String = "",
    val quality: String = "",
    val wholesalePrice: Int = 0,
    val minStock: Int = 0,
    val rack: String = "",
    val warranty: String = "No Warranty",
    val notes: String = ""
)

data class CartLine(
    val product: Product,
    val quantity: Int = 1
)

data class RepairJob(
    val id: String,
    val customer: String,
    val phone: String,
    val device: String,
    val issue: String,
    val amount: Int,
    val status: RepairStatus,
    val date: String
)

enum class RepairStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    READY("Ready"),
    DELIVERED("Delivered")
}

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val balance: Int,
    val status: CustomerStatus
)

enum class CustomerStatus(val label: String) {
    CREDIT("Credit"),
    PAID("Paid"),
    OVERDUE("Overdue")
}

data class Supplier(
    val id: String,
    val name: String,
    val phone: String,
    val payable: Int
)

data class Installment(
    val id: String,
    val customer: String,
    val phone: String,
    val product: String,
    val amount: Int,
    val dueLabel: String,
    val dueSoon: Boolean = false,
    val paidMonths: Int = 0,
    val months: Int = 6
)

data class SaleRecord(
    val id: String,
    val customer: String,
    val item: String,
    val total: Int,
    val payment: String,
    val time: String
)

data class SupportRequest(
    val id: String,
    val title: String,
    val status: String,
    val date: String,
    val message: String = "",
    val priority: String = ""
)


data class WalletRecord(
    val id: String,
    val wallet: String,
    val direction: String,
    val amount: Int,
    val fee: Int = 0,
    val mobile: String = "",
    val party: String = "Walk-in Customer",
    val reference: String = "",
    val date: String = java.time.LocalDate.now().toString()
)

data class ExpenseRecord(
    val id: String,
    val title: String,
    val category: String,
    val amount: Int,
    val date: String = java.time.LocalDate.now().toString()
)

data class CashClosingRecord(
    val id: String,
    val openingCash: Int,
    val cashSales: Int,
    val expenses: Int,
    val expectedCash: Int,
    val actualCash: Int,
    val date: String = java.time.LocalDate.now().toString()
) {
    val difference: Int get() = actualCash - expectedCash
}
