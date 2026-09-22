package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.skybarech.mobileshoperp.security.BiometricUnlock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.skybarech.mobileshoperp.R
import com.skybarech.mobileshoperp.model.AppScreen
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.components.*
import com.skybarech.mobileshoperp.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun AuroraBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF00071F), Color(0xFF00295B), Color(0xFF00071F))))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            repeat(5) { index ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(-size.width * .15f, size.height * (.66f + index * .022f))
                    cubicTo(size.width * .3f, size.height * .96f, size.width * .6f, size.height * .52f, size.width * 1.15f, size.height * (.78f + index * .025f))
                }
                drawPath(path, Brush.horizontalGradient(listOf(Color(0xFF6338FF).copy(alpha=.3f), Color(0xFF009EFF).copy(alpha=.55f), Color(0xFF0057FF).copy(alpha=.1f))), style=androidx.compose.ui.graphics.drawscope.Stroke(width=8f + index * 9f))
            }
        }
        content()
    }
}

@Composable
fun SplashScreen(vm: AppViewModel) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(1200), label="splash progress")
    LaunchedEffect(Unit) { started = true; delay(1400); if (!vm.resumeSavedSession()) vm.navigateRoot(if (vm.shouldShowOnboarding()) AppScreen.ONBOARDING else AppScreen.LOGIN) }
    AuroraBackdrop {
        Column(Modifier.align(Alignment.Center).padding(28.dp).graphicsLayer { alpha = .3f + progress * .7f; scaleX = .94f + progress * .06f; scaleY = scaleX }, horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(108.dp, light=true)
            Spacer(Modifier.height(20.dp))
            UiText("SkyBarech", color=Color.White, fontSize=34.sp, fontWeight=FontWeight.ExtraBold)
            UiText("ERP", color=Color(0xFF00A5FF), fontSize=28.sp, fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            UiText("Business Management System", color=Color.White, fontSize=14.sp)
            UiText("Manage  •  Grow  •  Succeed", color=Color(0xFFB9D3FF), fontSize=12.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            LinearProgressIndicator(progress={ progress }, modifier=Modifier.width(200.dp).clip(RoundedCornerShape(8.dp)), color=Color(0xFF7351FF), trackColor=Color(0xFF173769))
            Spacer(Modifier.height(12.dp))
            UiText("Opening your workspace…", color=Color(0xFFC4D7FA), fontSize=12.sp)
            Spacer(Modifier.height(24.dp))
            UiText("Version 1.3.20", color=Color(0xFF91A7C8), fontSize=10.sp)
        }
    }
}

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val motion = rememberInfiniteTransition(label = "onboarding motion")
    val floatScale by motion.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow pulse")
    var step by rememberSaveable { mutableStateOf(0) }
    val titles = listOf("Welcome & Language", "Activate Your Shop", "Fresh Shop Protection", "Device Security", "Cloud Sync Setup", "Ready to Grow")
    val bodies = listOf(
        "Choose English or اردو. SkyBarech ERP keeps your shop tools simple and connected.",
        "Use your registered mobile number and create the secure 4-digit shop PIN. The same PIN works on Desktop and Android.",
        "Every new shop starts with zero records. Your Shop-ID keeps products, sales, customers and staff isolated from every other shop.",
        "After your first successful PIN login, enable fingerprint for quick secure access. Your PIN always remains available as fallback.",
        "Verify the HTTPS connection, restore only your verified shop data, and continue safely in offline mode when needed.",
        "Next steps: Shop Profile → Products & Stock → First Sale. You can revisit these tools from the dashboard anytime."
    )
    val icons = listOf(Icons.Outlined.Language, Icons.Outlined.VerifiedUser, Icons.Outlined.Shield, Icons.Outlined.Fingerprint, Icons.Outlined.CloudSync, Icons.Outlined.CheckCircle)
    AuroraBackdrop {
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = floatScale; scaleY = floatScale }.background(Brush.radialGradient(listOf(Color(0x226A00FF), Color.Transparent))))
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(72.dp, light = false)
        Spacer(Modifier.height(12.dp))
        UiText("SkyBarech ERP", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
        UiText("Set up your secure business workspace", color = MutedInk, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        SoftCard(Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(26.dp)), contentPadding = 20.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(BrandBlueSoft), contentAlignment = Alignment.Center) { Icon(icons[step], null, tint = BrandBlue, modifier = Modifier.size(36.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    titles.indices.forEach { index ->
                        Box(Modifier.width(if (index == step) 28.dp else 8.dp).height(6.dp).clip(RoundedCornerShape(50)).background(if (index == step) BrandBlue else BrandBlueSoft))
                    }
                }
                UiText("STEP ${step + 1} OF ${titles.size}", color = BrandBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                UiText(titles[step], color = Ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                UiText(bodies[step], color = MutedInk, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                if (step == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = UiLanguage.code == "en", onClick = { UiLanguage.set(context, "en") }, label = { UiText("English") })
                        FilterChip(selected = UiLanguage.code == "ur", onClick = { UiLanguage.set(context, "ur") }, label = { UiText("اردو", translate = false) })
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) OutlineButton("Back", { step -= 1 }, Modifier.weight(1f), Icons.Outlined.ArrowBack)
            OnboardingGradientButton(if (step == titles.lastIndex) "Start Secure Setup" else "Continue", { if (step == titles.lastIndex) { vm.completeOnboarding(); vm.navigateRoot(AppScreen.ACTIVATION) } else step += 1 }, Modifier.weight(1.5f))
        }
        TextButton(onClick = { vm.completeOnboarding(); vm.navigateRoot(AppScreen.ACTIVATION) }) { UiText("Skip instructions", color = MutedInk, fontSize = 12.sp) }
    }
    }
}

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (visible) 1f else .72f, tween(700), label = "welcome scale")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "welcome alpha")
    LaunchedEffect(Unit) { visible = true; delay(1800); vm.navigateRoot(AppScreen.DASHBOARD) }
    AuroraBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.shadow(28.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Color(0xFF0CE39A), Color(0xFF3A2AA8), Color(0xFFFC0987)))).padding(3.dp)) {
                Box(Modifier.size(138.dp).clip(RoundedCornerShape(31.dp)).background(Color(0xFF071B42)), contentAlignment = Alignment.Center) { BrandMark(92.dp, light = true) }
            }
            Spacer(Modifier.height(28.dp))
            UiText("Welcome back", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            UiText(vm.shopName.ifBlank { "SkyBarech ERP" }, translate = false, color = Color(0xFF9DD8FF), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            UiText("Your secure workspace is ready", color = Color(0xFFD7E6FF), fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))
            LinearProgressIndicator(progress = { if (visible) 1f else 0f }, modifier = Modifier.width(190.dp).clip(RoundedCornerShape(50)), color = Color(0xFF0CE39A), trackColor = Color.White.copy(alpha = .14f))
        }
    }
}

@Composable
private fun OnboardingGradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(52.dp).shadow(14.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF0CE39A), Color(0xFF69007F), Color(0xFFFC0987)))).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF272727)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiText(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun ActivationScreen(vm: AppViewModel) {
    var shopName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var temporaryPassword by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigateRoot(AppScreen.LOGIN) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Back"), tint = Ink) }
            UiText("Activate Account", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(BrandBlueSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(14.dp))
                UiText("Activate your shop account", color = Ink, fontWeight = FontWeight.Bold)
                UiText("to get started", color = MutedInk, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        AppTextField("Shop Name", shopName, { shopName = it }, leadingIcon = Icons.Outlined.Store, placeholder = "Ali Mobile Accessories")
        Spacer(Modifier.height(12.dp))
        AppTextField("Activation Code", code, { code = it }, leadingIcon = Icons.Outlined.Key, placeholder = "Your activation code")
        Spacer(Modifier.height(12.dp))
        AppTextField("Username / Owner Mobile", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PersonOutline, placeholder = "03001234567")
        Spacer(Modifier.height(12.dp))
        PinCodeField("Temporary PIN", temporaryPassword, { temporaryPassword = it }, length = 4)
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (vm.syncInProgress) "Verifying…" else "Activate", { vm.activate(code, mobile, temporaryPassword, shopName) }, Modifier.fillMaxWidth(), enabled = !vm.syncInProgress)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Support", { vm.navigateRoot(AppScreen.HELP) }, Modifier.fillMaxWidth(), Icons.Outlined.SupportAgent)
        Spacer(Modifier.height(16.dp))
        UiText("After verification, choose a 4 digit PIN.", color = MutedInk, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoginScreen(vm: AppViewModel) {
    var mobile by rememberSaveable { mutableStateOf(vm.ownerMobile) }
    var pin by remember { mutableStateOf("") }
    var loginPinLength by rememberSaveable { mutableStateOf(com.skybarech.mobileshoperp.security.ShopPin.DEFAULT_LENGTH) }
    var submitted by remember { mutableStateOf(false) }
    var connectionDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val binding = vm.biometricBinding
    val fingerprint = BiometricUnlock.enabled(context, binding)
    var showBiometric by rememberSaveable { mutableStateOf(fingerprint) }
    if (fingerprint && showBiometric) {
        AppBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                BrandHeader(compact = false)
                Spacer(Modifier.height(48.dp))
                Surface(shape=RoundedCornerShape(100.dp), color=Color.White, border=androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF008CFF)), shadowElevation=20.dp) {
                    IconButton(onClick={
                        BiometricUnlock.activity(context)?.let { activity -> BiometricUnlock.authenticate(activity, binding, false,
                            success={ vm.unlockWithBiometrics(binding) }, error={ vm.showMessage(it) }) }
                }, modifier=Modifier.size(152.dp)) { Icon(Icons.Outlined.Fingerprint, contentDescription=tr("Unlock with fingerprint"), tint=Color(0xFF1769DC), modifier=Modifier.size(94.dp)) }
                }
                Spacer(Modifier.height(28.dp))
                UiText("Use Fingerprint", color=Ink, fontSize=24.sp, fontWeight=FontWeight.Bold)
                UiText("Quick and secure login", color=MutedInk, modifier=Modifier.padding(top=10.dp,bottom=26.dp))
                OutlinedButton(onClick={ showBiometric=false }, modifier=Modifier.fillMaxWidth()) { UiText("Use PIN instead",color=BrandBlue) }
                Spacer(Modifier.height(60.dp))
                UiText("Fast   •   Secure   •   Your shop",color=MutedInk,fontSize=12.sp)
            }
        }
        return
    }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by animateFloatAsState(if (appeared) 1f else 0f, tween(500), label = "login reveal")
    AppBackground {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp)) {
        Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 24f }.widthIn(max = 440.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LanguageSelector(modifier = Modifier.align(Alignment.End))
            Spacer(Modifier.height(14.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6E8FA)), shadowElevation = 14.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(58.dp, light = false)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        UiText("SkyBarech ERP", color = Color(0xFF102858), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        UiText("Your connected shop workspace", color = Color(0xFF52709F), fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE9F3FF)) {
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = Color(0xFF1769DC), modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke), shadowElevation = 6.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            UiText("Welcome back", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            UiText("SECURE SHOP LOGIN", color = BrandBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = RoundedCornerShape(50), color = SuccessSoft) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Success))
                                Spacer(Modifier.width(6.dp)); UiText("PIN protected", color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    UiText(if (vm.activated) vm.shopName else "Sign in to your shop account.", translate = !vm.activated,
                        color = MutedInk, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
                    AppTextField("Owner mobile / email", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PersonOutline,
                        keyboardType = KeyboardType.Email, error = if (submitted && mobile.isBlank()) "Enter your mobile number or email." else null)
                    Spacer(Modifier.height(16.dp))
                    PinCodeField("4-digit Shop PIN", pin, { pin = it }, length = 4,
                        error = if (submitted && !com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) "Enter exactly 4 digits" else null,
                        onDone = { submitted = true; if (mobile.isNotBlank() && com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) vm.login(mobile, pin) })
                    TextButton(onClick = { vm.navigateRoot(AppScreen.HELP) }, modifier = Modifier.align(Alignment.End)) { UiText("Forgot PIN?", fontSize = 12.sp) }
                    PrimaryButton(if (vm.syncInProgress) "Signing in…" else "Sign in", {
                        submitted = true
                        if (mobile.isNotBlank() && com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) vm.login(mobile, pin)
                    }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !vm.syncInProgress)
                    if (fingerprint) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = {
                            val activity = BiometricUnlock.activity(context)
                            if (activity != null) BiometricUnlock.authenticate(activity, binding, false,
                                success = { vm.unlockWithBiometrics(binding) }, error = { vm.showMessage(it) })
                        }, enabled = !vm.syncInProgress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp)); UiText("Unlock with fingerprint")
                        }
                    }
                    TextButton(onClick = { vm.navigateRoot(AppScreen.ACTIVATION) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { UiText("New shop? Activate account") }
                }
            }
            TextButton(onClick = { connectionDetails = true; vm.checkConnection() }) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); UiText("Check Cloud connection", fontSize = 12.sp)
            }
        }
    }
    }
    if (connectionDetails) AlertDialog(onDismissRequest = { connectionDetails = false }, title = { UiText("Cloud connection") },
        text = { UiText(if (vm.checkingConnection) "Checking server…" else vm.connectionReport) },
        confirmButton = { TextButton(onClick = { connectionDetails = false }) { UiText("Done") } },
        dismissButton = { TextButton(onClick = { vm.checkConnection() }, enabled = !vm.checkingConnection) { UiText("Retry check") } })
}

@Composable
fun FingerprintEnrollment(vm: AppViewModel) {
    val context = LocalContext.current
    if (vm.loggedIn && vm.offerFingerprint) AlertDialog(
        onDismissRequest = { vm.dismissFingerprint() },
        icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(36.dp)) },
        title = { UiText("Enable fingerprint?") },
        text = { UiText("Unlock this shop on this phone without entering your PIN each time.") },
        confirmButton = { TextButton(onClick = {
            val binding = vm.biometricBinding
            BiometricUnlock.activity(context)?.let { activity ->
                BiometricUnlock.authenticate(activity, binding, true,
                    success = { if (vm.loggedIn && vm.biometricBinding == binding) vm.fingerprintEnrolled() else BiometricUnlock.disable(context) },
                    error = { vm.showMessage(it) })
            }
        }) { UiText("Enable fingerprint") } },
        dismissButton = { TextButton(onClick = { vm.dismissFingerprint() }) { UiText("Not now") } })
}

@Composable
fun FingerprintSetting(vm: AppViewModel) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    val enabled = remember(revision, vm.biometricBinding) { BiometricUnlock.enabled(context, vm.biometricBinding) }
    SoftCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            UiText("Fingerprint unlock", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
            Switch(checked = enabled, onCheckedChange = { checked ->
                if (!checked) { BiometricUnlock.disable(context); revision++ }
                else BiometricUnlock.activity(context)?.let { activity ->
                    val binding = vm.biometricBinding
                    BiometricUnlock.authenticate(activity, binding, true,
                        success = { if (vm.loggedIn && vm.biometricBinding == binding) { revision++; vm.fingerprintEnrolled() } else BiometricUnlock.disable(context) },
                        error = { vm.showMessage(it) })
                }
            })
        }
    }
}

@Composable
fun ChangePasswordScreen(vm: AppViewModel) {
    val firstActivationPassword = vm.needsActivationPassword
    var current by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var pinLength by rememberSaveable { mutableStateOf(com.skybarech.mobileshoperp.security.ShopPin.DEFAULT_LENGTH) }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigateRoot(if (vm.loggedIn) AppScreen.SETTINGS else AppScreen.LOGIN) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Back"), tint = Ink) }
            UiText(if (firstActivationPassword) "Create PIN" else "Change PIN", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(34.dp))
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(BrandBlueSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(18.dp))
        UiText(if (firstActivationPassword) "Choose a PIN for your shop." else "Change your shop PIN", color = Ink, fontWeight = FontWeight.Bold)
        UiText(if (firstActivationPassword) "Use the same PIN on desktop and Android." else "Use the same PIN on desktop and Android.", color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        if (!firstActivationPassword) {
            PinCodeField("Current PIN", current, { current = it }, length = 4)
            Spacer(Modifier.height(12.dp))
        }
        PinCodeField("New PIN", fresh, { fresh = it }, length = pinLength)
        Spacer(Modifier.height(12.dp))
        PinCodeField("Confirm PIN", confirm, { confirm = it }, length = pinLength)
        Spacer(Modifier.height(12.dp))
        SoftCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = BrandBlue)
                Spacer(Modifier.width(9.dp))
                UiText("Use the same PIN on desktop and Android.", color = MutedInk, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(if (firstActivationPassword) "Save PIN & Open Dashboard" else "Update PIN", { if (com.skybarech.mobileshoperp.security.ShopPin.valid(fresh, pinLength) && confirm.length == pinLength) vm.updatePassword(current, fresh, confirm) else vm.showMessage("Enter exactly the selected number of digits.") }, Modifier.fillMaxWidth(), enabled = !vm.syncInProgress)
    }
}
