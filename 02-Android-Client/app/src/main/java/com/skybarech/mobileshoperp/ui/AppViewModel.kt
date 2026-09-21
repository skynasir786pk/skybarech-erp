package com.skybarech.mobileshoperp.ui

import com.skybarech.mobileshoperp.ui.i18n.tr
import com.skybarech.mobileshoperp.security.BiometricUnlock
import com.skybarech.mobileshoperp.ui.theme.UiAppearance
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skybarech.mobileshoperp.data.local.LocalData
import com.skybarech.mobileshoperp.data.local.LocalDataStore
import com.skybarech.mobileshoperp.data.local.ShopProfile
import com.skybarech.mobileshoperp.data.local.ShopSessionStore
import com.skybarech.mobileshoperp.data.offline.OfflineRepository
import com.skybarech.mobileshoperp.data.offline.RemoteActivationClient
import com.skybarech.mobileshoperp.data.offline.RemoteAuthClient
import com.skybarech.mobileshoperp.data.offline.RemoteAuthException
import com.skybarech.mobileshoperp.data.offline.SyncScheduler
import com.skybarech.mobileshoperp.data.offline.VerifiedActivation
import com.skybarech.mobileshoperp.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.text.NumberFormat
import java.util.Locale

/**
 * Local-first state layer. Every change is committed locally before it is queued
 * for background delivery to the Cloud API.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    var screen by mutableStateOf(AppScreen.SPLASH)
        private set
    private val history = mutableStateListOf<AppScreen>()

    private val sessionStore = ShopSessionStore(application)
    private val onboardingPrefs = application.getSharedPreferences("skybarech_onboarding", android.content.Context.MODE_PRIVATE)
    private val localDataStore = LocalDataStore(application)
    private val offlineRepository = OfflineRepository(application)
    private var session = sessionStore.read()
    private var pendingActivation: VerifiedActivation? = null
    val needsActivationPassword: Boolean get() = pendingActivation != null

    fun shouldShowOnboarding(): Boolean = !onboardingPrefs.getBoolean("completed", false)
    fun completeOnboarding() { onboardingPrefs.edit().putBoolean("completed", true).apply() }

    var activated by mutableStateOf(session.isActivated)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var shopName by mutableStateOf(session.shopName)
    var shopInitials by mutableStateOf(makeInitials(session.shopName))
    var subscriptionPlan by mutableStateOf(session.plan)
        private set
    var subscriptionStatus by mutableStateOf(session.status)
        private set
    var expiryLabel by mutableStateOf(session.expiryLabel)
        private set
    var storageStatus by mutableStateOf("Local storage active")
        private set
    var syncInProgress by mutableStateOf(false)
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
        private set
    var connectionReport by mutableStateOf("")
        private set
    var checkingConnection by mutableStateOf(false)
        private set
    fun checkConnection() {
        if (checkingConnection) return
        checkingConnection = true
        viewModelScope.launch {
            try { connectionReport = com.skybarech.mobileshoperp.data.offline.ApiEndpoint.checkConnection() }
            catch (error: Exception) { connectionReport = error.message ?: "Connection check failed." }
            finally { checkingConnection = false }
        }
    }
    var confirmAction by mutableStateOf<ConfirmAction?>(null)
        private set

    val products = mutableStateListOf<Product>()
    val cart = mutableStateListOf<CartLine>()
    val repairs = mutableStateListOf<RepairJob>()
    val customers = mutableStateListOf<Customer>()
    val suppliers = mutableStateListOf<Supplier>()
    val installments = mutableStateListOf<Installment>()
    val sales = mutableStateListOf<SaleRecord>()
    val supportRequests = mutableStateListOf<SupportRequest>()
    val ewallets = mutableStateListOf<WalletRecord>()
    val expenses = mutableStateListOf<ExpenseRecord>()
    val cashClosings = mutableStateListOf<CashClosingRecord>()

    var invoiceCompleted by mutableStateOf(false)
        private set
    private var savedInvoiceLines: List<CartLine> = emptyList()
    fun invoiceLines(): List<CartLine> = if (invoiceCompleted) savedInvoiceLines else cart.toList()
    fun invoiceTotal(): Int = invoiceLines().sumOf { it.product.salePrice * it.quantity }
    var lastInvoiceNumber by mutableStateOf("")
        private set
    var lastPaymentMethod by mutableStateOf("Cash")
        private set

    private val appearancePrefs = application.getSharedPreferences("skybarech_appearance", android.content.Context.MODE_PRIVATE)
    fun toggleTheme() {
        if (UiAppearance.managed) { showMessage("Colors are managed by Super Admin."); return }
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.dark = !com.skybarech.mobileshoperp.ui.theme.UiAppearance.dark
        appearancePrefs.edit().putBoolean("dark", com.skybarech.mobileshoperp.ui.theme.UiAppearance.dark).apply()
    }
    init {
        com.skybarech.mobileshoperp.ui.theme.UiAppearance.dark = appearancePrefs.getBoolean("dark", false)
        loadLocalData()
        SyncScheduler.schedule(application)
        viewModelScope.launch {
            offlineRepository.initialize()
            offlineRepository.loadSnapshot()?.let { replaceData(it) }
            storageStatus = "Local-first ready · ${offlineRepository.pendingCount()} pending"
            offlineRepository.observeSnapshot().collect { latest ->
                replaceData(latest)
                localDataStore.save(latest)
            }
        }
        viewModelScope.launch {
            offlineRepository.observeSyncConfig().collect { config ->
                session = sessionStore.read()
                shopName = session.shopName; shopInitials = makeInitials(session.shopName)
                subscriptionPlan = session.plan; subscriptionStatus = session.status; expiryLabel = session.expiryLabel
                shopAddress = getApplication<Application>().getSharedPreferences("skybarech_shop_profile", android.content.Context.MODE_PRIVATE).getString("address", "").orEmpty()
                val error = config.lastSyncError.orEmpty()
                storageStatus = when {
                    error.isNotBlank() -> error.substringAfter('|')
                    config.lastSyncAt != null -> "Last sync: ${java.text.SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(java.util.Date(config.lastSyncAt!!))} · ${offlineRepository.pendingCount()} pending"
                    else -> "Saved on device · ${offlineRepository.pendingCount()} pending"
                }
                when {
                    error.startsWith("ACCESS_REVOKED|") -> markShopRevoked(error.substringAfter('|').ifBlank { "Shop access was removed." })
                    error.startsWith("DEVICE_BLOCKED|") -> lockCurrentDevice(error.substringAfter('|').ifBlank { "This device is blocked by Super Admin." })
                }
            }
        }
    }

    val ownerMobile: String get() = session.ownerMobile
    var shopAddress by mutableStateOf("")
        private set
    var lastPurchaseReceipt by mutableStateOf("")
        private set
    var lastInstallmentReceipt by mutableStateOf("")
        private set
    var selectedInstallmentId by mutableStateOf<String?>(null)
    var lastInvoiceDate by mutableStateOf("")
        private set
    fun nowLabel(): String = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    fun selectInstallment(id: String) { selectedInstallmentId = id; navigate(AppScreen.RECEIVE_PAYMENT) }
    fun localSalesTotal(): Int = sales.sumOf { it.total }
    fun lowStockCount(): Int = products.count { it.stock <= it.minStock }
    fun outstandingInstallments(): Int = installments.sumOf { it.amount * (it.months - it.paidMonths).coerceAtLeast(0) }
    fun salesForDays(days: Long): List<SaleRecord> {
        val start = java.time.LocalDate.now().minusDays(days - 1)
        return sales.filter { sale -> runCatching { java.time.LocalDate.parse(sale.time.take(10)) >= start }.getOrDefault(false) }
    }
    fun recentSalesBars(): List<Int> = (6 downTo 0).map { offset ->
        val day = java.time.LocalDate.now().minusDays(offset.toLong()).toString()
        sales.filter { it.time.startsWith(day) }.sumOf { it.total }
    }
    fun formatMoney(value: Int): String = "Rs. " + NumberFormat.getIntegerInstance(Locale.US).format(value)

    fun navigate(target: AppScreen, addToBackStack: Boolean = true) {
        if (screen == target) return
        if (addToBackStack && screen !in authScreens) history.add(screen)
        screen = target
    }

    fun navigateRoot(target: AppScreen) {
        history.clear()
        screen = target
    }

    fun canGoBack(): Boolean = screen !in authScreens && screen != AppScreen.DASHBOARD && history.isNotEmpty()

    fun goBack() {
        if (history.isNotEmpty()) screen = history.removeAt(history.lastIndex) else screen = AppScreen.DASHBOARD
    }

    fun showMessage(message: String) { snackbarMessage = message }
    fun consumeMessage() { snackbarMessage = null }

    fun activate(code: String, mobile: String, temporaryPassword: String, shopNameInput: String = "") {
        if (syncInProgress) return
        if (!RemoteAuthClient.configured) { showMessage("Configure the HTTPS server before activation."); return }
        if (code.isBlank() || mobile.isBlank() || temporaryPassword.isBlank()) {
            showMessage("Complete activation code, owner mobile and temporary password.")
            return
        }
        if (!com.skybarech.mobileshoperp.security.ShopPin.valid(temporaryPassword)) {
            showMessage("Temporary PIN exactly 4 digits hona chahiye.")
            return
        }
        if (RemoteAuthClient.configured) {
            syncInProgress = true
            showMessage("Cloud se activation verify ho rahi hai…")
            viewModelScope.launch {
                runCatching { RemoteActivationClient.verify(code.trim(), mobile.trim(), temporaryPassword) }
                    .onSuccess { verified ->
                        if (session.shopId.isNotBlank() && session.shopId != verified.shopId) {
                            syncInProgress = false
                            showMessage("Use a separate device/profile for another shop; existing local data is protected.")
                            return@onSuccess
                        }
                        pendingActivation = verified
                        activated = true
                        shopName = verified.shopName
                        shopInitials = makeInitials(verified.shopName)
                        subscriptionPlan = "Online"
                        subscriptionStatus = "active"
                        expiryLabel = "Cloud Linked"
                        syncInProgress = false
                        showMessage("Activation verified. Ab apna 4-digit PIN banayein.")
                        screen = AppScreen.CHANGE_PASSWORD
                    }
                    .onFailure { error ->
                        syncInProgress = false
                        showMessage(error.message ?: "Activation verify nahi ho saki.")
                    }
            }
            return
        }
        activated = true
        val cleanCode = code.trim()
        val shopLabel = shopNameInput.trim().ifBlank { if (cleanCode.startsWith("SB", ignoreCase = true)) "Mobile Shop ${mobile.takeLast(4)}" else cleanCode }
        session = ShopSessionStore.Session(
            shopId = cleanCode.ifBlank { "local-shop" },
            shopName = shopLabel,
            ownerMobile = mobile.trim(),
            plan = "Local Linked",
            status = "active",
            expiryLabel = "Linked Local Mode"
        )
        sessionStore.save(session)
        sessionStore.setPassword(temporaryPassword)
        session = sessionStore.read()
        shopName = session.shopName
        shopInitials = makeInitials(session.shopName)
        subscriptionPlan = session.plan
        subscriptionStatus = session.status
        expiryLabel = session.expiryLabel
        saveLocalData()
        loggedIn = false
        showMessage("Shop activated. Ab apna 4-digit PIN banayein.")
        screen = AppScreen.CHANGE_PASSWORD
    }

    var offerFingerprint by mutableStateOf(false)
        private set
    val biometricBinding: String get() = sessionStore.credentialBinding()
    fun dismissFingerprint() { BiometricUnlock.dismiss(getApplication<Application>(), biometricBinding); offerFingerprint = false }
    fun fingerprintEnrolled() { offerFingerprint = false; showMessage("Fingerprint enabled on this device.") }
    private fun offerFingerprintAfterLogin() {
        offerFingerprint = BiometricUnlock.shouldOffer(getApplication<Application>(), biometricBinding)
    }
    private fun localAccessAllowed(): Boolean {
        if (!session.isActivated || session.status.lowercase() in setOf("blocked", "suspended", "expired", "deleted", "revoked")) return false
        val expires = runCatching { java.time.Instant.parse(session.expiryLabel.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" }) }.getOrNull()
        return expires == null || expires.isAfter(java.time.Instant.now())
    }
    fun unlockWithBiometrics(binding: String) {
        if (syncInProgress || binding.isBlank() || binding != biometricBinding || !BiometricUnlock.enabled(getApplication<Application>(), binding)) return
        if (!localAccessAllowed()) { showMessage("Shop access stopped. Sign in online with your PIN."); return }
        syncInProgress = true
        viewModelScope.launch {
            try {
                val tokens = offlineRepository.currentTokens() ?: throw RemoteAuthException(401, "session_invalid", "Sign in with your PIN to reconnect this device.")
                try {
                    val refreshed = RemoteAuthClient.validateSession(tokens)
                    val active = refreshed ?: tokens
                    if (refreshed != null) offlineRepository.configureSession(refreshed.accessToken, refreshed.refreshToken)
                    val shop = RemoteAuthClient.biometricBootstrap(active, offlineRepository.deviceId())
                    check(shop.optString("id") == session.shopId) { "Shop session changed. Sign in with your PIN." }
                    UiAppearance.saveRemote(getApplication<Application>(), shop.optJSONObject("appearance"))
                    if (binding == biometricBinding && localAccessAllowed()) finishOwnerLogin(online = true)
                } catch (error: java.io.IOException) {
                    if (binding == biometricBinding && localAccessAllowed()) finishOwnerLogin(online = false)
                }
            } catch (error: Exception) {
                if (error is RemoteAuthException && error.isAuthoritativeDenial) {
                    BiometricUnlock.disable(getApplication<Application>())
                    offlineRepository.clearTokens("ACCESS_REVOKED|${error.message}")
                }
                showMessage(error.message ?: "Sign in with your PIN to reconnect this device.")
            } finally { syncInProgress = false }
        }
    }

    fun login(mobile: String, password: String) {
        if (syncInProgress) return
        if (!com.skybarech.mobileshoperp.security.ShopPin.valid(password)) { showMessage("Enter exactly 4 digits."); return }
        if (sessionStore.pinLocked()) { showMessage("Too many failed PIN attempts. Try again in 15 minutes."); return }
        val identity = mobile.trim()
        if (identity.isBlank() || password.isBlank()) {
            showMessage("Enter your owner mobile and password.")
            return
        }

        val localCredentialValid = session.isActivated && sessionStore.hasPassword()
            && sameMobileIdentity(identity, session.ownerMobile) && sessionStore.verifyPassword(password)
        val locallyRevoked = !localAccessAllowed()

        if (!RemoteAuthClient.configured) {
            if (!localCredentialValid) {
                sessionStore.recordPinFailure()
                showMessage("Owner mobile ya password incorrect hai. New shop ho to Activate use karein.")
                return
            }
            if (locallyRevoked) {
                storageStatus = "Shop access stopped"
                showMessage("Shop access Super Admin ne stop kar di hai. Internet verification required hai.")
                return
            }
            finishOwnerLogin(online = false)
            return
        }

        syncInProgress = true
        storageStatus = "Connecting to central ERP…"
        viewModelScope.launch {
            runCatching { RemoteAuthClient.loginSession(identity, password) }
                .onSuccess { remote ->
                    if (session.shopId.isNotBlank() && session.shopId != remote.shopId) {
                        syncInProgress = false
                        showMessage("This installation belongs to another shop. Use a separate Android profile/device to protect unsynced records.")
                        return@onSuccess
                    }
                    val joiningNewShop = session.shopId.isBlank() || session.shopId != remote.shopId
                    if (joiningNewShop) {
                        offlineRepository.resetForRemoteShop()
                        replaceData(LocalData())
                        localDataStore.save(LocalData())
                    }
                    session = ShopSessionStore.Session(
                        shopId = remote.shopId.ifBlank { session.shopId.ifBlank { "online-shop" } },
                        shopName = remote.shopName,
                        ownerMobile = remote.ownerMobile.ifBlank { identity },
                        plan = remote.plan,
                        status = remote.status,
                        expiryLabel = remote.expiryLabel,
                        passwordHash = session.passwordHash, passwordSalt = session.passwordSalt
                    )
                    sessionStore.save(session)
                    if (!localCredentialValid) sessionStore.setPassword(password)
                    session = sessionStore.read()
                    UiAppearance.saveRemote(getApplication<Application>(), remote.appearance)
                    activated = true
                    shopName = session.shopName
                    shopInitials = makeInitials(session.shopName)
                    subscriptionPlan = session.plan
                    subscriptionStatus = session.status
                    expiryLabel = session.expiryLabel
                    offlineRepository.configureSession(remote.tokens.accessToken, remote.tokens.refreshToken)
                    syncInProgress = false
                    SyncScheduler.runOnce(getApplication<Application>())
                    finishOwnerLogin(online = true)
                    offerFingerprintAfterLogin()
                }
                .onFailure { error ->
                    syncInProgress = false
                    if (error is RemoteAuthException) {
                        if (error.statusCode in setOf(401,429)) sessionStore.recordPinFailure()
                        when (error.errorCode) {
                            "shop_inactive", "shop_expired", "user_inactive", "session_invalid" -> markShopRevoked(error.message ?: "Shop access was removed.")
                            else -> showMessage(error.message ?: "Online login failed.")
                        }
                    } else if (localCredentialValid && !locallyRevoked) {
                        finishOwnerLogin(online = false)
                    } else {
                        sessionStore.recordPinFailure()
                        storageStatus = "Online login required"
                        showMessage(com.skybarech.mobileshoperp.data.offline.ApiEndpoint.message(error).substringAfter('|'))
                    }
                }
        }
    }

    private fun sameMobileIdentity(first: String, second: String): Boolean {
        fun canonical(value: String): String {
            var digits = value.filter { it.isDigit() }
            if (digits.startsWith("0092") && digits.length == 14) digits = "0" + digits.drop(4)
            else if (digits.startsWith("92") && digits.length == 12) digits = "0" + digits.drop(2)
            return digits
        }
        val left = canonical(first)
        val right = canonical(second)
        return left.isNotBlank() && left == right
    }

    private fun finishOwnerLogin(online: Boolean) {
        sessionStore.resetPinFailures()
        sessionStore.setResumeSession(true)
        loggedIn = true
        storageStatus = if (online) "Online access verified · sync connected" else "Offline mode · local data ready"
        showMessage(if (online) "Welcome back. Online access verified." else "Internet unavailable. Local data offline mode mein ready hai.")
        navigateRoot(AppScreen.DASHBOARD)
    }

    fun resumeSavedSession(): Boolean {
        if (!sessionStore.shouldResumeSession() || !sessionStore.hasPassword() || !localAccessAllowed()) return false
        loggedIn = true
        storageStatus = "Saved session restored · sync will continue"
        navigateRoot(AppScreen.DASHBOARD)
        return true
    }

    private fun markShopRevoked(reason: String) {
        BiometricUnlock.disable(getApplication<Application>())
        session = session.copy(status = "revoked", expiryLabel = "Access removed")
        sessionStore.save(session)
        loggedIn = false
        activated = true
        subscriptionStatus = "revoked"
        expiryLabel = "Access removed"
        storageStatus = "Shop access stopped"
        showMessage("$reason New activation credentials ke baghair login allowed nahi hai.")
        navigateRoot(AppScreen.ACTIVATION)
    }

    private fun lockCurrentDevice(reason: String) {
        loggedIn = false
        storageStatus = "This device is blocked"
        syncInProgress = false
        showMessage("$reason Super Admin se device unblock karwa kar dobara login karein.")
        navigateRoot(AppScreen.LOGIN)
    }

    fun updatePassword(current: String, fresh: String, confirm: String) {
        if (syncInProgress) return
        val firstActivationPassword = pendingActivation != null
        if (!loggedIn && !firstActivationPassword) { showMessage("Sign in first or ask the administrator to reset your activation."); return }
        if (RemoteAuthClient.configured && !firstActivationPassword) {
            if (!com.skybarech.mobileshoperp.security.ShopPin.valid(fresh) || fresh != confirm || current.isBlank()) {
                showMessage("Enter your current PIN and matching new 4 digit PIN.")
                return
            }
            syncInProgress = true
            viewModelScope.launch {
                runCatching { RemoteAuthClient.changePassword(session.ownerMobile, current, fresh) }
                    .onSuccess { tokens ->
                        offlineRepository.configureSession(tokens.accessToken, tokens.refreshToken)
                        sessionStore.setPassword(fresh)
                        session = sessionStore.read()
                        showMessage("PIN updated. Use the new PIN on your other device.")
                        offerFingerprintAfterLogin()
                        navigateRoot(AppScreen.SETTINGS)
                    }
                    .onFailure { showMessage(it.message ?: "Password update failed; local password unchanged.") }
                syncInProgress = false
            }
            return
        }
        when {
            fresh.isBlank() || confirm.isBlank() -> showMessage("Enter the PIN and confirm it.")
            !com.skybarech.mobileshoperp.security.ShopPin.valid(fresh) -> showMessage("Choose a 4 digit PIN.")
            fresh != confirm -> showMessage("PINs do not match.")
            !firstActivationPassword && current.isBlank() -> showMessage("Enter current password.")
            !firstActivationPassword && !sessionStore.verifyPassword(current) -> showMessage("Current password is incorrect.")
            else -> {
                val verified = pendingActivation
                if (firstActivationPassword && RemoteAuthClient.configured && verified != null) {
                    syncInProgress = true
                    viewModelScope.launch {
                        runCatching { RemoteActivationClient.complete(verified, fresh) }
                            .onSuccess { tokens ->
                                if (session.shopId.isBlank()) {
                                    offlineRepository.resetForRemoteShop()
                                    replaceData(LocalData())
                                    localDataStore.save(LocalData())
                                }
                                session = ShopSessionStore.Session(
                                    shopId = verified.shopId,
                                    shopName = verified.shopName,
                                    ownerMobile = verified.mobile,
                                    plan = "Online",
                                    status = "active",
                                    expiryLabel = "Cloud Linked"
                                )
                                sessionStore.save(session)
                                sessionStore.setPassword(fresh)
                                session = sessionStore.read()
                                offlineRepository.configureSession(tokens.accessToken, tokens.refreshToken)
                                pendingActivation = null
                                sessionStore.setResumeSession(true)
                                loggedIn = true
                                offerFingerprintAfterLogin()
                                syncInProgress = false
                                storageStatus = "Online sync connected"
                                showMessage("Activation complete. Local-first sync ready hai.")
                                navigateRoot(AppScreen.DASHBOARD)
                            }
                            .onFailure { error ->
                                syncInProgress = false
                                showMessage(error.message ?: "Activation complete nahi ho saki.")
                            }
                    }
                } else {
                    sessionStore.setPassword(fresh)
                    session = sessionStore.read()
                    sessionStore.setResumeSession(true)
                    showMessage("PIN saved. Dashboard ready.")
                    loggedIn = true
                    screen = AppScreen.DASHBOARD
                }
            }
        }
    }

    fun signOut() {
        offerFingerprint = false
        sessionStore.setResumeSession(false)
        loggedIn = false
        navigateRoot(AppScreen.LOGIN)
        showMessage("Signed out successfully.")
    }

    fun syncNow(showConfirmation: Boolean = true) {
        if (syncInProgress) return
        viewModelScope.launch {
            com.skybarech.mobileshoperp.data.offline.SkyBarechDatabase.get(getApplication<Application>()).offlineDao().retryFailuresNow()
            SyncScheduler.runOnce(getApplication<Application>())
            if (showConfirmation) showMessage("Sync requested. The status updates when the server responds.")
        }
    }

    /** Refreshes server controls without creating an unnecessary full snapshot. */
    fun refreshServerControl() {
        if (!loggedIn || syncInProgress || !RemoteAuthClient.configured) return
        SyncScheduler.runOnce(getApplication<Application>())
    }

    fun exportBackup(): String {
        saveLocalData()
        return localDataStore.exportJson()
    }

    fun restoreBackup(raw: String): Boolean {
        if (!localDataStore.restoreJson(raw)) {
            showMessage("Invalid ya unsupported SkyBarech backup file.")
            return false
        }
        products.clear(); repairs.clear(); customers.clear(); suppliers.clear(); installments.clear()
        sales.clear(); supportRequests.clear(); ewallets.clear(); expenses.clear(); cashClosings.clear()
        loadLocalData()
        saveLocalData()
        showMessage("Backup restored successfully.")
        return true
    }

    fun invoiceShareText(): String = buildString {
        appendLine(shopName)
        appendLine(shopAddress)
        appendLine(ownerMobile)
        appendLine("${tr("Invoice")}: $lastInvoiceNumber")
        appendLine("${tr("Date")}: $lastInvoiceDate")
        appendLine(tr(if (invoiceCompleted) "PAID RECEIPT" else "PAYMENT PREVIEW"))
        appendLine("--------------------------------")
        invoiceLines().forEach { appendLine("${it.product.name}  ${it.quantity} x ${formatMoney(it.product.salePrice)}") }
        appendLine("--------------------------------")
        appendLine("${tr("Total")}: ${formatMoney(invoiceTotal())}")
        appendLine("${tr("Payment")}: $lastPaymentMethod")
        appendLine(tr("Thank you"))
    }

    fun addToCart(product: Product) {
        if (product.stock <= 0) {
            showMessage("${product.name} out of stock hai.")
            return
        }
        val index = cart.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = cart[index]
            if (existing.quantity >= product.stock) {
                showMessage("Available stock sirf ${product.stock} hai.")
                return
            }
            cart[index] = existing.copy(quantity = existing.quantity + 1)
        } else cart.add(CartLine(product))
        showMessage("${product.name} added to cart.")
    }

    fun changeCartQuantity(productId: String, delta: Int) {
        val index = cart.indexOfFirst { it.product.id == productId }
        if (index == -1) return
        val existing = cart[index]
        val quantity = existing.quantity + delta
        if (quantity > existing.product.stock) {
            showMessage("Available stock sirf ${existing.product.stock} hai.")
            return
        }
        if (quantity <= 0) cart.removeAt(index) else cart[index] = existing.copy(quantity = quantity)
    }

    fun clearCart() {
        if (cart.isEmpty()) return
        confirmAction = ConfirmAction(
            title = "Clear cart?",
            body = "All items in this bill will be removed.",
            confirm = {
                cart.clear()
                showMessage("Cart cleared.")
            }
        )
    }

    fun cartSubtotal(): Int = cart.sumOf { it.product.salePrice * it.quantity }
    fun cartDiscount(): Int = 0
    fun cartTotal(): Int = (cartSubtotal() - cartDiscount()).coerceAtLeast(0)

    fun selectPayment(method: String) { lastPaymentMethod = method }

    fun prepareInvoice() {
        if (cart.isEmpty()) {
            showMessage("Add at least one product before collecting payment.")
            return
        }
        invoiceCompleted = false
        savedInvoiceLines = emptyList()
        lastInvoiceNumber = "INV-" + java.util.UUID.randomUUID().toString()
        lastInvoiceDate = nowLabel()
        navigate(AppScreen.INVOICE)
    }

    fun completeSale() {
        if (cart.isEmpty() || invoiceCompleted) return
        val unavailable = cart.firstOrNull { line -> products.firstOrNull { it.id == line.product.id }?.stock?.let { it < line.quantity } != false }
        if (unavailable != null) {
            showMessage("${unavailable.product.name} ka stock change ho gaya. Cart quantity check karein.")
            return
        }
        val productLabel = cart.joinToString { it.product.name }
        val sale = SaleRecord(lastInvoiceNumber, "Walk-in Customer", productLabel, cartTotal(), lastPaymentMethod, lastInvoiceDate)
        sales.add(0, sale)
        cart.forEach { line ->
            val index = products.indexOfFirst { it.id == line.product.id }
            if (index >= 0) {
                products[index] = products[index].copy(stock = (products[index].stock - line.quantity).coerceAtLeast(0))
                sync("products", products[index].id, products[index].toMap())
            }
        }
        sync("sales", sale.id, sale.toMap())
        savedInvoiceLines = cart.toList()
        invoiceCompleted = true
        cart.clear()
        syncDashboardSummary()
        showMessage("Invoice saved. Print or share your receipt.")
    }

    fun addProduct(
        name: String,
        category: String,
        brand: String,
        model: String,
        compatibleModels: String,
        variant: String,
        color: String,
        quality: String,
        purchase: String,
        sale: String,
        wholesale: String,
        stock: String,
        minStock: String,
        rack: String,
        sku: String,
        warranty: String,
        notes: String
    ) {
        val purchaseValue = purchase.trim().replace(",", "").toIntOrNull()
        val saleValue = sale.trim().replace(",", "").toIntOrNull()
        val stockValue = stock.trim().replace(",", "").toIntOrNull()
        val wholesaleValue = wholesale.trim().replace(",", "").toIntOrNull() ?: 0
        val minStockValue = minStock.trim().replace(",", "").toIntOrNull() ?: 0
        when {
            name.isBlank() || category.isBlank() -> showMessage("Product name and category are required.")
            purchaseValue == null || saleValue == null || stockValue == null || purchaseValue < 0 || saleValue < 0 || stockValue < 0 -> showMessage("Enter valid purchase price, sale price and stock quantity.")
            else -> {
                val finalSku = sku.ifBlank { generateSku(category, brand.ifBlank { name }) }
                val product = Product(
                    id = "p${System.currentTimeMillis()}",
                    name = name,
                    brand = brand.ifBlank { "Other" },
                    model = model,
                    variant = variant.ifBlank { "Standard" },
                    salePrice = saleValue,
                    purchasePrice = purchaseValue,
                    stock = stockValue,
                    category = category,
                    sku = finalSku,
                    shopId = session.shopId.ifBlank { "SHOP-LOCAL-001" },
                    compatibleModels = compatibleModels,
                    color = color,
                    quality = quality,
                    wholesalePrice = wholesaleValue,
                    minStock = minStockValue,
                    rack = rack,
                    warranty = warranty,
                    notes = notes
                )
                products.add(0, product)
                sync("products", product.id, product.toMap())
                syncDashboardSummary()
                showMessage("$name added to Mobile Accessories.")
                navigate(AppScreen.MOBILE_ACCESSORIES)
            }
        }
    }

    private fun generateSku(category: String, brand: String): String {
        val c = category.filter { it.isLetterOrDigit() }.uppercase().take(4).ifBlank { "SKU" }
        val b = brand.filter { it.isLetterOrDigit() }.uppercase().take(4).ifBlank { "ITEM" }
        return "$c-$b-${System.currentTimeMillis().toString().takeLast(5)}"
    }

    fun addAccessory(
        name: String,
        type: String,
        brand: String,
        model: String,
        purchase: String,
        sale: String,
        stock: String,
        sku: String
    ) {
        val purchaseValue = purchase.trim().replace(",", "").toIntOrNull()
        val saleValue = sale.trim().replace(",", "").toIntOrNull()
        val stockValue = stock.trim().replace(",", "").toIntOrNull()
        when {
            name.isBlank() || brand.isBlank() -> showMessage("Accessory name and brand are required.")
            purchaseValue == null || saleValue == null || stockValue == null || purchaseValue < 0 || saleValue < 0 || stockValue < 0 -> showMessage("Enter valid purchase price, sale price and stock quantity.")
            else -> {
                val product = Product(
                    id = "acc${System.currentTimeMillis()}",
                    name = name,
                    brand = brand,
                    model = model.ifBlank { type },
                    variant = type,
                    salePrice = saleValue,
                    purchasePrice = purchaseValue,
                    stock = stockValue,
                    category = "Accessory",
                    sku = sku.ifBlank { "ACC-${(100 + products.count { it.category == "Accessory" })}" }
                )
                products.add(0, product)
                sync("products", product.id, product.toMap())
                syncDashboardSummary()
                showMessage("$name added to accessories.")
                navigate(AppScreen.MOBILE_ACCESSORIES)
            }
        }
    }

    fun savePurchase(brand: String, model: String, imei: String, purchase: String, sale: String, supplier: String, supplierMobile: String = "", cnic: String = "", frontImage: String = "", backImage: String = "") {
        val purchaseValue = purchase.trim().replace(",", "").toIntOrNull()
        val saleValue = sale.trim().replace(",", "").toIntOrNull()
        val rawImeis = imei.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val imeis = rawImeis.distinct()
        val existingImeis = products.flatMap { p ->
            listOf(p.sku, p.notes) + Regex("\\d{10,18}").findAll("${p.sku} ${p.notes}").map { it.value }.toList()
        }.map { it.filter { ch -> ch.isDigit() } }.filter { it.length in 10..18 }.toSet()
        val duplicate = imeis.firstOrNull { it in existingImeis }
        when {
            brand.isBlank() || model.isBlank() || supplier.isBlank() -> showMessage("Brand, model and supplier are required.")
            imeis.isEmpty() || imeis.any { !it.matches(Regex("[0-9]{15}")) } -> showMessage("Each IMEI must contain exactly 15 digits.")
            duplicate != null -> showMessage("Duplicate IMEI found: $duplicate already exists in stock.")
            purchaseValue == null || saleValue == null || purchaseValue < 0 || saleValue < 0 -> showMessage("Enter valid price values.")
            else -> {
                val qty = imeis.size.coerceAtLeast(1)
                val supplierContact = supplierMobile.takeIf { it.isNotBlank() }?.let { " · Mobile: $it" }.orEmpty()
                val product = Product("p${System.currentTimeMillis()}", "$brand $model", brand, model, "New purchase", saleValue, purchaseValue, qty, sku = imeis.first(), notes = "IMEI: ${imeis.joinToString(", ")} · Supplier: $supplier$supplierContact")
                products.add(0, product)
                sync("products", product.id, product.toMap() + mapOf("supplier" to supplier, "supplierMobile" to supplierMobile, "imeis" to imeis, "purchaseType" to "mobile"))
                syncDashboardSummary()
                getApplication<Application>().getSharedPreferences("skybarech_purchase_documents", android.content.Context.MODE_PRIVATE).edit()
                    .putString(product.id, org.json.JSONObject().put("cnic", cnic).put("supplierMobile", supplierMobile).put("front", frontImage).put("back", backImage).toString()).apply()
                lastPurchaseReceipt = "$shopName\n${shopAddress.ifBlank { ownerMobile }}\nPurchase ${product.id}\n${nowLabel()}\nSupplier: $supplier\nSupplier Mobile: $supplierMobile\n${product.name}\nIMEI: ${imeis.joinToString()}\nQuantity: $qty\nUnit cost: ${formatMoney(purchaseValue)}\nTotal: ${formatMoney(purchaseValue * qty)}"
                showMessage("Purchase saved. You can print the saved receipt below.")
            }
        }
    }

    fun saveRepair(customer: String, phone: String, device: String, issue: String, amount: String, status: RepairStatus) {
        val parsedAmount = amount.trim().replace(",", "").toIntOrNull()
        when {
            customer.isBlank() || phone.isBlank() || device.isBlank() || issue.isBlank() -> showMessage("Customer, phone, device and problem type are required.")
            parsedAmount == null || parsedAmount < 0 -> showMessage("Enter a valid estimated cost.")
            else -> {
                val repair = RepairJob("RJ-${java.util.UUID.randomUUID()}", customer, phone, device, issue, parsedAmount, status, nowLabel())
                repairs.add(0, repair)
                sync("repairs", repair.id, repair.toMap())
                syncDashboardSummary()
                showMessage("Repair job saved successfully.")
                navigate(AppScreen.REPAIRS)
            }
        }
    }

    fun addCustomer(name: String, phone: String) {
        when {
            name.isBlank() || phone.isBlank() -> showMessage("Customer name and mobile are required.")
            else -> {
                val customer = Customer("c-${java.util.UUID.randomUUID()}", name, phone, 0, CustomerStatus.PAID)
                customers.add(0, customer)
                sync("customers", customer.id, customer.toMap())
                showMessage("Customer added successfully.")
            }
        }
    }

    fun addInstallment(customer: String, phone: String, product: String, totalPrice: String, advance: String, months: String, firstDueDate: String = java.time.LocalDate.now().plusMonths(1).toString()) {
        val total = totalPrice.trim().replace(",", "").toIntOrNull()
        val advanceValue = if (advance.isBlank()) 0 else advance.trim().replace(",", "").toIntOrNull()
        val monthValue = months.substringBefore(" ").toIntOrNull() ?: 6
        val dueDate = runCatching { java.time.LocalDate.parse(firstDueDate) }.getOrNull()
        when {
            customer.isBlank() || phone.isBlank() || product.isBlank() -> showMessage("Customer, mobile and product are required.")
            dueDate == null -> showMessage("Enter first due date as YYYY-MM-DD.")
            monthValue !in 1..60 -> showMessage("Choose a valid number of months.")
            total == null || total <= 0 -> showMessage("Enter a valid total price.")
            advanceValue == null || advanceValue < 0 || advanceValue >= total -> showMessage("Advance must be less than the total price.")
            (total - advanceValue) % monthValue != 0 -> showMessage("Remaining amount must divide evenly across the selected months. Adjust advance or months.")
            else -> {
                val perMonth = ((total - advanceValue).coerceAtLeast(0) / monthValue.coerceAtLeast(1))
                val installment = Installment("i-${java.util.UUID.randomUUID()}", customer, phone, product, perMonth, dueDate.toString(), !dueDate.isAfter(java.time.LocalDate.now()), 0, monthValue)
                installments.add(0, installment)
                sync("installments", installment.id, installment.toMap() + mapOf("totalPrice" to total, "advance" to advanceValue))
                syncDashboardSummary()
                showMessage("Installment plan created successfully.")
                navigate(AppScreen.INSTALLMENTS)
            }
        }
    }

    fun receiveInstallment(installment: Installment, amount: String, method: String) {
        val paid = amount.trim().replace(",", "").toIntOrNull()
        when {
            paid == null || paid <= 0 -> showMessage("Enter a valid received amount.")
            installment.paidMonths >= installment.months -> showMessage("This plan is already paid.")
            paid != installment.amount -> showMessage("Enter the scheduled monthly amount: ${formatMoney(installment.amount)}.")
            else -> {
                val index = installments.indexOfFirst { it.id == installment.id }
                if (index >= 0) {
                    val updated = installment.copy(paidMonths = (installment.paidMonths + 1).coerceAtMost(installment.months), dueLabel = runCatching { java.time.LocalDate.parse(installment.dueLabel).plusMonths(1).toString() }.getOrElse { java.time.LocalDate.now().plusMonths(1).toString() }, dueSoon = false)
                    installments[index] = updated
                    sync("installments", updated.id, updated.toMap())
                    sync("installmentPayments", "${updated.id}-${updated.paidMonths}", mapOf(
                        "installmentId" to updated.id,
                        "customer" to updated.customer,
                        "amount" to paid,
                        "method" to method,
                        "receivedAtLabel" to nowLabel()
                    ))
                    syncDashboardSummary()
                }
                lastInstallmentReceipt = "$shopName\nInstallment payment\n${nowLabel()}\nCustomer: ${installment.customer}\nPlan: ${installment.id}\nPayment: ${installment.paidMonths + 1}/${installment.months}\nReceived: ${formatMoney(paid)}\nMethod: $method"
                showMessage("${formatMoney(paid)} received by $method.")
                navigate(AppScreen.INSTALLMENTS)
            }
        }
    }

    fun saveWalletEntry(wallet: String, direction: String, amount: String, mobile: String, party: String, reference: String, fee: String = "0"): Boolean {
        val amountValue = amount.trim().replace(",", "").toIntOrNull()
        val feeValue = if (fee.isBlank()) 0 else fee.trim().replace(",", "").toIntOrNull()
        return when {
            amountValue == null || amountValue <= 0 -> { showMessage("Enter a valid wallet amount."); false }
            feeValue == null || feeValue < 0 -> { showMessage("Enter a valid non-negative fee."); false }
            wallet !in listOf("EasyPaisa", "JazzCash") -> { showMessage("Select EasyPaisa or JazzCash."); false }
            direction !in listOf("In", "Out") -> { showMessage("Select In or Out."); false }
            else -> {
                val record = WalletRecord(
                    id = "EW-${java.util.UUID.randomUUID()}",
                    wallet = wallet,
                    direction = direction,
                    amount = amountValue,
                    fee = feeValue,
                    mobile = mobile,
                    party = party.ifBlank { "Walk-in Customer" },
                    reference = reference,
                    date = nowLabel()
                )
                ewallets.add(0, record)
                sync("ewallets", record.id, record.toMap())
                showMessage("$wallet $direction saved.")
                true
            }
        }
    }

    fun submitSupport(title: String, priority: String, message: String) {
        when {
            title.isBlank() || priority.isBlank() || message.length < 10 -> showMessage("Choose an issue type, priority and write at least 10 characters.")
            else -> {
                val request = SupportRequest("SR-${java.util.UUID.randomUUID()}", title, "Open", nowLabel(), message, priority)
                supportRequests.add(0, request)
                sync("supportRequests", request.id, request.toMap() + mapOf("priority" to priority, "message" to message))
                showMessage("Support request saved locally.")
            }
        }
    }

    fun addExpense(title: String, category: String, amount: String): Boolean {
        val value = amount.trim().replace(",", "").toIntOrNull()
        if (title.isBlank() || value == null || value <= 0) {
            showMessage("Expense title aur valid amount enter karein.")
            return false
        }
        val expense = ExpenseRecord("EXP-${System.currentTimeMillis()}", title.trim(), category, value)
        expenses.add(0, expense)
        sync("expenses", expense.id, mapOf("title" to expense.title, "category" to expense.category, "amount" to expense.amount, "dateLabel" to expense.date))
        showMessage("Expense saved and cash ledger updated.")
        return true
    }

    fun saveCashClosing(opening: Int, actual: String): Boolean {
        val actualValue = actual.trim().replace(",", "").toIntOrNull()
        if (actualValue == null) {
            showMessage("Actual closing cash enter karein.")
            return false
        }
        val cashSales = salesForDays(1).filter { it.payment == "Cash" }.sumOf { it.total }
        val expenseTotal = expenses.filter { it.date.take(10) == java.time.LocalDate.now().toString() }.sumOf { it.amount }
        val expected = opening + cashSales - expenseTotal
        val closing = CashClosingRecord("CC-${System.currentTimeMillis()}", opening, cashSales, expenseTotal, expected, actualValue)
        cashClosings.add(0, closing)
        sync("cashClosings", closing.id, mapOf(
            "openingCash" to closing.openingCash, "cashSales" to closing.cashSales,
            "expenses" to closing.expenses, "expectedCash" to closing.expectedCash,
            "actualCash" to closing.actualCash, "dateLabel" to closing.date
        ))
        showMessage("Cash closing saved. Difference: ${formatMoney(actualValue - expected)}")
        return true
    }


    fun requestConfirmation(action: ConfirmAction) { confirmAction = action }
    fun dismissConfirmation() { confirmAction = null }
    fun confirmCurrentAction() { confirmAction?.confirm?.invoke(); confirmAction = null }

    private fun loadLocalData() {
        replaceData(localDataStore.read())
    }

    private fun replaceData(data: LocalData) {
        products.clear(); products.addAll(data.products)
        repairs.clear(); repairs.addAll(data.repairs)
        customers.clear(); customers.addAll(data.customers)
        suppliers.clear(); suppliers.addAll(data.suppliers)
        installments.clear(); installments.addAll(data.installments)
        sales.clear(); sales.addAll(data.sales)
        supportRequests.clear(); supportRequests.addAll(data.supportRequests)
        ewallets.clear(); ewallets.addAll(data.ewallets)
        expenses.clear(); expenses.addAll(data.expenses)
        cashClosings.clear(); cashClosings.addAll(data.cashClosings)
    }


    private fun makeInitials(name: String): String = name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "SB" }

    private fun persistSession(profile: ShopProfile) {
        session = ShopSessionStore.Session(
            shopId = profile.shopId,
            shopName = profile.shopName,
            ownerMobile = profile.ownerMobile,
            plan = profile.plan,
            status = profile.status,
            expiryLabel = profile.expiryLabel,
            passwordHash = session.passwordHash,
            passwordSalt = session.passwordSalt
        )
        sessionStore.save(session)
        activated = session.isActivated
        shopName = session.shopName
        shopInitials = makeInitials(session.shopName)
        subscriptionPlan = session.plan
        subscriptionStatus = session.status
        expiryLabel = session.expiryLabel
    }

    private fun sync(collection: String, id: String, data: Map<String, Any?>) {
        val snapshot = currentData()
        localDataStore.save(snapshot)
        viewModelScope.launch {
            offlineRepository.saveAndQueue(snapshot, collection, id, data)
            storageStatus = "Saved locally · ${offlineRepository.pendingCount()} queued for sync"
        }
    }

    private fun syncDashboardSummary() {
        val snapshot = currentData()
        localDataStore.save(snapshot)
        viewModelScope.launch { offlineRepository.saveSnapshot(snapshot) }
    }

    private fun saveLocalData() {
        val snapshot = currentData()
        localDataStore.save(snapshot)
        viewModelScope.launch { offlineRepository.saveSnapshot(snapshot) }
    }

    private fun currentData() = LocalData(
        products = products.toList(),
        repairs = repairs.toList(),
        customers = customers.toList(),
        suppliers = suppliers.toList(),
        installments = installments.toList(),
        sales = sales.toList(),
        supportRequests = supportRequests.toList(),
        ewallets = ewallets.toList(),
        expenses = expenses.toList(),
        cashClosings = cashClosings.toList()
    )

    companion object {
        private val authScreens = setOf(AppScreen.SPLASH, AppScreen.ACTIVATION, AppScreen.LOGIN, AppScreen.CHANGE_PASSWORD)
    }
}

private fun Product.toMap() = mapOf(
    "name" to name, "brand" to brand, "model" to model, "variant" to variant,
    "salePrice" to salePrice, "purchasePrice" to purchasePrice, "price" to salePrice, "cost" to purchasePrice, "stock" to stock,
    "category" to category, "sku" to sku, "shopId" to shopId,
    "compatibleModels" to compatibleModels, "color" to color, "quality" to quality,
    "wholesalePrice" to wholesalePrice, "minStock" to minStock, "rack" to rack,
    "warranty" to warranty, "notes" to notes
)

private fun RepairJob.toMap() = mapOf(
    "customer" to customer, "phone" to phone, "device" to device, "issue" to issue,
    "amount" to amount, "cost" to amount, "status" to status.name, "statusLabel" to status.label, "dateLabel" to date, "date" to date
)

private fun Customer.toMap() = mapOf(
    "name" to name, "phone" to phone, "balance" to balance, "status" to status.name, "statusLabel" to status.label
)

private fun Supplier.toMap() = mapOf("name" to name, "phone" to phone, "payable" to payable)

private fun Installment.toMap() = mapOf(
    "customer" to customer, "phone" to phone, "product" to product, "amount" to amount,
    "dueLabel" to dueLabel, "dueSoon" to dueSoon, "paidMonths" to paidMonths, "months" to months
)

private fun SaleRecord.toMap() = mapOf(
    "customer" to customer, "item" to item, "items" to listOf(mapOf("name" to item, "qty" to 1, "price" to total)),
    "total" to total, "payment" to payment, "timeLabel" to time, "time" to time
)

private fun SupportRequest.toMap() = mapOf("title" to title, "type" to title, "status" to status, "dateLabel" to date, "message" to message, "priority" to priority)

private fun WalletRecord.toMap() = mapOf(
    "wallet" to wallet, "direction" to direction, "amount" to amount, "fee" to fee,
    "mobile" to mobile, "party" to party, "reference" to reference, "dateLabel" to date
)

data class ConfirmAction(
    val title: String,
    val body: String,
    val confirm: () -> Unit,
    val literalTitle: Boolean = false
)
