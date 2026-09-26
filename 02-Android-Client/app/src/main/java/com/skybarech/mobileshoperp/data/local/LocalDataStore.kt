package com.skybarech.mobileshoperp.data.local

import android.content.Context
import android.util.Base64
import com.skybarech.mobileshoperp.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class ShopSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("skybarech_shop_session", Context.MODE_PRIVATE)

    data class Session(
        val shopId: String = "",
        val shopName: String = "SkyBarech Mobile Shop",
        val ownerMobile: String = "",
        val plan: String = "Local",
        val status: String = "active",
        val expiryLabel: String = "Local Mode",
        val passwordHash: String = "",
        val passwordSalt: String = ""
    ) {
        val isActivated: Boolean get() = shopId.isNotBlank()
    }

    fun read(): Session = Session(
        shopId = prefs.getString("shopId", "") ?: "",
        shopName = prefs.getString("shopName", "SkyBarech Mobile Shop") ?: "SkyBarech Mobile Shop",
        ownerMobile = prefs.getString("ownerMobile", "") ?: "",
        plan = prefs.getString("plan", "Local") ?: "Local",
        status = prefs.getString("status", "active") ?: "active",
        expiryLabel = prefs.getString("expiryLabel", "Local Mode") ?: "Local Mode",
        passwordHash = prefs.getString("passwordHash", "") ?: "",
        passwordSalt = prefs.getString("passwordSalt", "") ?: ""
    )

    fun save(session: Session) {
        prefs.edit()
            .putString("shopId", session.shopId)
            .putString("shopName", session.shopName)
            .putString("ownerMobile", session.ownerMobile)
            .putString("plan", session.plan)
            .putString("status", session.status)
            .putString("expiryLabel", session.expiryLabel)
            .putString("passwordHash", session.passwordHash)
            .putString("passwordSalt", session.passwordSalt)
            .apply()
    }

    fun hasPassword(): Boolean {
        migrateLegacyPasswordIfNeeded()
        val session = read()
        return session.passwordHash.isNotBlank() && session.passwordSalt.isNotBlank()
    }

    fun setPassword(password: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt)
        prefs.edit()
            .putString("passwordSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("passwordHash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove("ownerPassword")
            .apply()
    }

    fun verifyPassword(password: String): Boolean {
        migrateLegacyPasswordIfNeeded()
        val session = read()
        if (session.passwordHash.isBlank() || session.passwordSalt.isBlank()) return false
        return runCatching {
            val expected = Base64.decode(session.passwordHash, Base64.NO_WRAP)
            val salt = Base64.decode(session.passwordSalt, Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derive(password, salt))
        }.getOrDefault(false)
    }

    private fun migrateLegacyPasswordIfNeeded() {
        if (!prefs.getString("passwordHash", "").isNullOrBlank()) return
        val legacy = prefs.getString("ownerPassword", "") ?: ""
        if (legacy.isNotBlank()) setPassword(legacy)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun credentialBinding(): String {
        val session = read()
        if (!session.isActivated || session.passwordHash.isBlank()) return ""
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(
            "${session.shopId}|${session.ownerMobile}|${session.passwordHash}|${session.passwordSalt}".toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun pinLocked(): Boolean = prefs.getLong("pinUntil", 0) > System.currentTimeMillis() && prefs.getInt("pinFailures", 0) >= 5
    fun recordPinFailure() {
        val expired = prefs.getLong("pinUntil", 0) <= System.currentTimeMillis()
        prefs.edit().putInt("pinFailures", if (expired) 1 else prefs.getInt("pinFailures", 0) + 1)
            .putLong("pinUntil", if (expired) System.currentTimeMillis() + 15 * 60_000 else prefs.getLong("pinUntil", 0)).apply()
    }
    fun resetPinFailures() { prefs.edit().remove("pinFailures").remove("pinUntil").apply() }
    fun shouldResumeSession(): Boolean = prefs.getBoolean("resumeSession", false)
    fun setResumeSession(enabled: Boolean) { prefs.edit().putBoolean("resumeSession", enabled).apply() }
    fun clear() { prefs.edit().clear().apply() }

    private companion object {
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
    }
}

data class ShopProfile(
    val shopId: String,
    val shopName: String,
    val ownerMobile: String,
    val plan: String,
    val status: String,
    val expiryLabel: String
)

data class LocalData(
    val products: List<Product> = emptyList(),
    val repairs: List<RepairJob> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val installments: List<Installment> = emptyList(),
    val sales: List<SaleRecord> = emptyList(),
    val supportRequests: List<SupportRequest> = emptyList(),
    val ewallets: List<WalletRecord> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val cashClosings: List<CashClosingRecord> = emptyList()
)

class LocalDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("skybarech_local_data", Context.MODE_PRIVATE)

    fun read(): LocalData {
        val raw = prefs.getString(KEY, null) ?: return LocalData()
        return decode(raw)
    }

    fun save(data: LocalData) {
        prefs.edit().putString(KEY, encode(data)).apply()
    }

    fun encode(data: LocalData): String = JSONObject()
            .put("products", JSONArray(data.products.map { it.toJson() }))
            .put("repairs", JSONArray(data.repairs.map { it.toJson() }))
            .put("customers", JSONArray(data.customers.map { it.toJson() }))
            .put("suppliers", JSONArray(data.suppliers.map { it.toJson() }))
            .put("installments", JSONArray(data.installments.map { it.toJson() }))
            .put("sales", JSONArray(data.sales.map { it.toJson() }))
            .put("supportRequests", JSONArray(data.supportRequests.map { it.toJson() }))
            .put("ewallets", JSONArray(data.ewallets.map { it.toJson() }))
            .put("expenses", JSONArray(data.expenses.map { it.toJson() }))
            .put("cashClosings", JSONArray(data.cashClosings.map { it.toJson() }))
            .toString()

    fun decode(raw: String): LocalData = runCatching {
        val root = JSONObject(raw)
        LocalData(
            products = root.optJSONArray("products").toProducts(),
            repairs = root.optJSONArray("repairs").toRepairs(),
            customers = root.optJSONArray("customers").toCustomers(),
            suppliers = root.optJSONArray("suppliers").toSuppliers(),
            installments = root.optJSONArray("installments").toInstallments(),
            sales = root.optJSONArray("sales").toSales(),
            supportRequests = root.optJSONArray("supportRequests").toSupportRequests(),
            ewallets = root.optJSONArray("ewallets").toWalletRecords(),
            expenses = root.optJSONArray("expenses").toExpenses(),
            cashClosings = root.optJSONArray("cashClosings").toCashClosings()
        )
    }.getOrDefault(LocalData())

    fun clear() { prefs.edit().remove(KEY).apply() }

    fun exportJson(): String = JSONObject()
        .put("format", "skybarech-android-backup")
        .put("version", 2)
        .put("exportedAt", System.currentTimeMillis())
        .put("data", JSONObject(prefs.getString(KEY, "{}") ?: "{}"))
        .toString(2)

    fun restoreJson(raw: String): Boolean = runCatching {
        val backup = JSONObject(raw)
        require(backup.optString("format") == "skybarech-android-backup")
        require(backup.optInt("version") in 1..2)
        val data = backup.getJSONObject("data")
        prefs.edit().putString(KEY, data.toString()).commit()
    }.getOrDefault(false)

    companion object { private const val KEY = "local_data_v1" }
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.toProducts() = objects().map {
    Product(
        id = it.optString("id"),
        name = it.optString("name"),
        brand = it.optString("brand"),
        model = it.optString("model"),
        variant = it.optString("variant", "Standard"),
        salePrice = if (it.has("salePrice")) it.optInt("salePrice") else it.optInt("price"),
        purchasePrice = if (it.has("purchasePrice")) it.optInt("purchasePrice") else it.optInt("cost"),
        stock = it.optInt("stock"),
        category = it.optString("category", "Mobile"),
        sku = it.optString("sku"),
        shopId = it.optString("shopId", "SHOP-LOCAL-001"),
        compatibleModels = it.optString("compatibleModels"),
        color = it.optString("color"),
        quality = it.optString("quality"),
        wholesalePrice = it.optInt("wholesalePrice"),
        minStock = it.optInt("minStock"),
        rack = it.optString("rack"),
        warranty = it.optString("warranty", "No Warranty"),
        notes = it.optString("notes"),
        ram = it.optString("ram"), storage = it.optString("storage"), storageType = it.optString("storageType"),
        processor = it.optString("processor"), generation = it.optString("generation"), graphics = it.optString("graphics"),
        screenSize = it.optString("screenSize"), operatingSystem = it.optString("operatingSystem"),
        batteryHealth = it.optString("batteryHealth"), serialNumber = it.optString("serialNumber")
    )
}

private fun JSONArray?.toRepairs() = objects().map {
    RepairJob(
        id = it.optString("id"),
        customer = it.optString("customer"),
        phone = it.optString("phone"),
        device = it.optString("device"),
        issue = it.optString("issue"),
        amount = if (it.has("amount")) it.optInt("amount") else it.optInt("cost"),
        status = RepairStatus.entries.firstOrNull { s -> s.name == it.optString("status") } ?: RepairStatus.PENDING,
        date = it.optString("date")
    )
}

private fun JSONArray?.toCustomers() = objects().map {
    Customer(
        id = it.optString("id"),
        name = it.optString("name"),
        phone = it.optString("phone"),
        balance = it.optInt("balance"),
        status = CustomerStatus.entries.firstOrNull { s -> s.name == it.optString("status") } ?: CustomerStatus.PAID
    )
}

private fun JSONArray?.toSuppliers() = objects().map {
    Supplier(it.optString("id"), it.optString("name"), it.optString("phone"), it.optInt("payable"))
}

private fun JSONArray?.toInstallments() = objects().map {
    Installment(
        id = it.optString("id"),
        customer = it.optString("customer"),
        phone = it.optString("phone"),
        product = it.optString("product"),
        amount = it.optInt("amount"),
        dueLabel = it.optString("dueLabel"),
        dueSoon = it.optBoolean("dueSoon"),
        paidMonths = it.optInt("paidMonths"),
        months = it.optInt("months", 6)
    )
}

private fun JSONArray?.toSales() = objects().map {
    val items = it.optJSONArray("items")
    val firstItem = items?.optJSONObject(0)?.optString("name").orEmpty()
    SaleRecord(
        it.optString("id"),
        it.optString("customer", "Walk-in Customer"),
        it.optString("item").ifBlank { firstItem.ifBlank { "Sale" } },
        it.optInt("total"),
        it.optString("payment", "Cash"),
        it.optString("time").ifBlank { it.optString("timeLabel").ifBlank { it.optString("date", "Now") } }
    )
}

private fun JSONArray?.toSupportRequests() = objects().map {
    SupportRequest(it.optString("id"), it.optString("title", it.optString("type")), it.optString("status"), it.optString("date"), it.optString("message"), it.optString("priority"))
}

private fun JSONArray?.toWalletRecords() = objects().map {
    WalletRecord(
        id = it.optString("id"),
        wallet = it.optString("wallet", "EasyPaisa"),
        direction = it.optString("direction", "In"),
        amount = it.optInt("amount"),
        fee = it.optInt("fee"),
        mobile = it.optString("mobile"),
        party = it.optString("party", it.optString("customer", "Walk-in Customer")),
        reference = it.optString("reference", it.optString("note")),
        date = it.optString("date", "Today")
    )
}

private fun JSONArray?.toExpenses() = objects().map {
    ExpenseRecord(it.optString("id"), it.optString("title"), it.optString("category"), it.optInt("amount"), it.optString("date", "Today"))
}

private fun JSONArray?.toCashClosings() = objects().map {
    CashClosingRecord(
        it.optString("id"), it.optInt("openingCash"), it.optInt("cashSales"),
        it.optInt("expenses"), it.optInt("expectedCash"), if (it.has("actualCash")) it.optInt("actualCash") else it.optInt("closing"), it.optString("date", "Today")
    )
}

private fun Product.toJson() = JSONObject()
    .put("id", id).put("name", name).put("brand", brand).put("model", model).put("variant", variant)
    .put("salePrice", salePrice).put("purchasePrice", purchasePrice).put("stock", stock).put("category", category).put("sku", sku)
    .put("shopId", shopId).put("compatibleModels", compatibleModels).put("color", color).put("quality", quality)
    .put("wholesalePrice", wholesalePrice).put("minStock", minStock).put("rack", rack).put("warranty", warranty).put("notes", notes)
    .put("ram", ram).put("storage", storage).put("storageType", storageType).put("processor", processor).put("generation", generation)
    .put("graphics", graphics).put("screenSize", screenSize).put("operatingSystem", operatingSystem).put("batteryHealth", batteryHealth).put("serialNumber", serialNumber)

private fun RepairJob.toJson() = JSONObject()
    .put("id", id).put("customer", customer).put("phone", phone).put("device", device).put("issue", issue)
    .put("amount", amount).put("status", status.name).put("date", date)

private fun Customer.toJson() = JSONObject()
    .put("id", id).put("name", name).put("phone", phone).put("balance", balance).put("status", status.name)

private fun Supplier.toJson() = JSONObject()
    .put("id", id).put("name", name).put("phone", phone).put("payable", payable)

private fun Installment.toJson() = JSONObject()
    .put("id", id).put("customer", customer).put("phone", phone).put("product", product).put("amount", amount)
    .put("dueLabel", dueLabel).put("dueSoon", dueSoon).put("paidMonths", paidMonths).put("months", months)

private fun SaleRecord.toJson() = JSONObject()
    .put("id", id).put("customer", customer).put("item", item).put("total", total).put("payment", payment).put("time", time)

private fun SupportRequest.toJson() = JSONObject()
    .put("id", id).put("title", title).put("status", status).put("date", date).put("message", message).put("priority", priority)

private fun WalletRecord.toJson() = JSONObject()
    .put("id", id).put("wallet", wallet).put("direction", direction).put("amount", amount)
    .put("fee", fee).put("mobile", mobile).put("party", party).put("reference", reference).put("date", date)

private fun ExpenseRecord.toJson() = JSONObject()
    .put("id", id).put("title", title).put("category", category).put("amount", amount).put("date", date)

private fun CashClosingRecord.toJson() = JSONObject()
    .put("id", id).put("openingCash", openingCash).put("cashSales", cashSales).put("expenses", expenses)
    .put("expectedCash", expectedCash).put("actualCash", actualCash).put("date", date)
