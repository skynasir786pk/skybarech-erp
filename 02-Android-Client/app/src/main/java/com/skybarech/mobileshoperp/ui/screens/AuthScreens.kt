package com.skybarech.mobileshoperp.ui.screens

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE7F6FF), Color(0xFFF9FBFF), Color(0xFFDCEEFF))))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF7CD8FF).copy(alpha = .23f), radius = size.minDimension * .47f, center = Offset(size.width * .88f, size.height * .20f))
            drawCircle(Color(0xFFB8ACFF).copy(alpha = .16f), radius = size.minDimension * .40f, center = Offset(size.width * .12f, size.height * .82f))
            repeat(5) { index ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(-size.width * .15f, size.height * (.66f + index * .022f))
                    cubicTo(size.width * .3f, size.height * .96f, size.width * .6f, size.height * .52f, size.width * 1.15f, size.height * (.78f + index * .025f))
                }
                drawPath(path, Brush.horizontalGradient(listOf(Color(0xFF6D57FF).copy(alpha=.12f), Color(0xFF00A4E8).copy(alpha=.26f), Color(0xFF2B70FF).copy(alpha=.08f))), style=androidx.compose.ui.graphics.drawscope.Stroke(width=8f + index * 9f))
            }
        }
        content()
    }
}

@Composable
fun SplashScreen(vm: AppViewModel) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (started) 1f else 0f, tween(1200), label="splash progress")
    val motion = rememberInfiniteTransition(label = "splash logo motion")
    val logoPulse by motion.animateFloat(.96f, 1.05f, infiniteRepeatable(tween(1050), RepeatMode.Reverse), label = "logo pulse")
    val logoTurn by motion.animateFloat(-3f, 3f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "logo turn")
    LaunchedEffect(Unit) {
        started = true
        delay(1400)
        if (!vm.resumeSavedSession()) {
            vm.completeOnboarding()
            vm.navigateRoot(if (vm.activated) AppScreen.LOGIN else AppScreen.ACTIVATION)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Image(painterResource(R.drawable.splash_reference), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .25f + progress * .75f; scaleX = .985f + progress * .015f; scaleY = scaleX })
        Box(Modifier.align(Alignment.Center).offset(y = (-28).dp).size(230.dp).graphicsLayer { rotationZ = logoTurn; scaleX = logoPulse; scaleY = logoPulse }) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFF22BDF0).copy(alpha = .12f), radius = size.minDimension * .49f, style = androidx.compose.ui.graphics.drawscope.Stroke(3f)) }
        }
        LinearProgressIndicator(progress={ progress }, modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 42.dp).width(150.dp).height(4.dp).clip(RoundedCornerShape(8.dp)), color=Color(0xFF0B83F6), trackColor=Color(0xFFD9F1FF))
    }
}

@Composable
private fun AuthHero(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    compact: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCDE8FF))
    ) {
        Box(Modifier.fillMaxWidth().height(if (compact) 112.dp else 174.dp).background(Brush.linearGradient(listOf(Color(0xFFF9FDFF), Color(0xFFE8F7FF), Color(0xFFF0EDFF))))) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF33C2F1).copy(alpha = .17f), radius = size.minDimension * .50f, center = Offset(size.width * .92f, size.height * .25f))
                drawCircle(Color(0xFF725FF4).copy(alpha = .10f), radius = size.minDimension * .42f, center = Offset(size.width * .78f, size.height * 1.02f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    UiText("SkyBarech ERP", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    UiText(badge, color = BrandBlue, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).shadow(8.dp, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 92.dp, bottom = if (compact) 9.dp else 17.dp)) {
                UiText(title, color = Ink, fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.ExtraBold)
                UiText(subtitle, color = MutedInk, fontSize = if (compact) 10.sp else 12.sp, lineHeight = if (compact) 13.sp else 17.sp, maxLines = if (compact) 1 else 2)
            }
            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = BrandBlue.copy(alpha = .15f), modifier = Modifier.align(Alignment.BottomEnd).padding(15.dp).size(68.dp))
        }
    }
}

@Composable
private fun ReferenceAuthBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8FDFF), Color.White, Color(0xFFE9F8FF))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF75D7FA).copy(alpha = .20f), size.minDimension * .55f, Offset(size.width * -.05f, size.height * -.02f))
            drawCircle(Color(0xFF2FBFFC).copy(alpha = .14f), size.minDimension * .62f, Offset(size.width * 1.02f, size.height * 1.02f))
            repeat(4) { index ->
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(-size.width * .1f, size.height * (.88f + index * .018f))
                    cubicTo(size.width * .28f, size.height * (.74f + index * .018f), size.width * .62f, size.height * (1.02f + index * .01f), size.width * 1.1f, size.height * (.84f + index * .02f))
                }
                drawPath(path, Color(0xFF24B8F1).copy(alpha = .12f - index * .018f), style = androidx.compose.ui.graphics.drawscope.Stroke(5f + index * 3f))
            }
        }
        content()
    }
}

@Composable
private fun ReferenceBrandIdentity(compact: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(if (compact) 68.dp else 88.dp)
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
        UiText("SkyBarech ERP", color = Color(0xFF123B80), fontSize = if (compact) 25.sp else 31.sp, fontWeight = FontWeight.ExtraBold, translate = false)
        UiText("Business Management System", color = Color(0xFF61656C), fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, translate = false)
    }
}

@Composable
private fun ReferencePrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(54.dp).shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF168AF4)).clip(RoundedCornerShape(16.dp))
        .background(if (enabled) Brush.horizontalGradient(listOf(Color(0xFF1559E8), Color(0xFF078CF7), Color(0xFF27C5E9))) else Brush.horizontalGradient(listOf(Color(0xFFB8C9DB), Color(0xFFCBD7E4))))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        UiText(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val motion = rememberInfiniteTransition(label = "onboarding motion")
    val floatScale by motion.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "glow pulse")
    val floatY by motion.animateFloat(-5f, 5f, infiniteRepeatable(tween(2300), RepeatMode.Reverse), label = "hero float")
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
    val benefits = listOf(
        listOf("English or Urdu", "Made for your shop"),
        listOf("Registered mobile", "4-digit secure PIN"),
        listOf("Zero demo records", "Private Shop-ID"),
        listOf("Fingerprint unlock", "PIN always available"),
        listOf("Verified cloud", "Safe offline work"),
        listOf("Shop profile", "Stock, then first sale")
    )
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.onboarding_liquid_store), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF031A4B).copy(alpha = .10f), Color(0xFF001C55).copy(alpha = .48f), Color(0xFF001333).copy(alpha = .15f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp, light = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    UiText("SkyBarech ERP", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    UiText("YOUR SHOP, READY TO GROW", color = Color(0xFFC8E7FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(42.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                titles.indices.forEach { index ->
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(if (index <= step) Color(0xFF67E5FF) else Color.White.copy(alpha = .30f)))
                }
            }
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth()) {
                AnimatedContent(targetState = step, transitionSpec = {
                    (fadeIn(tween(280, delayMillis = 100)) + slideInHorizontally { if (targetState > initialState) it / 8 else -it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally { if (targetState > initialState) -it / 10 else it / 10 })
                }, label = "setup instruction transition") { page ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icons[page], null, tint = Color(0xFF83EDFF), modifier = Modifier.size(54.dp).graphicsLayer { scaleX = floatScale; scaleY = floatScale; translationY = floatY })
                    Spacer(Modifier.height(16.dp))
                    UiText("STEP ${page + 1} OF ${titles.size}  ·  GET STARTED", color = Color(0xFF92EAFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    UiText(titles[page], color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    UiText(bodies[page], color = Color(0xFFE3F4FF), fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(15.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        benefits[page].forEach { benefit ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF159B78), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    UiText(benefit, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                        }
                    }
                    if (page == 0) {
                        Spacer(Modifier.height(14.dp))
                        UiText("Choose your language", color = Color(0xFFD9F2FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = UiLanguage.code == "en", onClick = { UiLanguage.set(context, "en") }, label = { UiText("English") })
                            FilterChip(selected = UiLanguage.code == "ur", onClick = { UiLanguage.set(context, "ur") }, label = { UiText("اردو", translate = false) })
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) OutlinedButton(onClick = {
                    step = (step - 1).coerceAtLeast(0)
                }, modifier = Modifier.weight(.85f).height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.ArrowBack, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); UiText("Back")
                }
                OnboardingGradientButton(if (step == titles.lastIndex) "Activate My Shop" else "Continue", {
                    if (step == titles.lastIndex) { vm.completeOnboarding(); vm.navigateRoot(AppScreen.ACTIVATION) }
                    else step = (step + 1).coerceAtMost(titles.lastIndex)
                }, Modifier.weight(1.5f))
            }
            TextButton(onClick = { vm.completeOnboarding(); vm.navigateRoot(AppScreen.ACTIVATION) }) { UiText("Skip setup guide", color = Color(0xFF7083A0), fontSize = 12.sp) }
        }
    }
}

/** Full-card onboarding art: a friendly shop, laptop and phone sit above flowing cloud waves. */
@Composable
private fun LiquidCartoonIllustration(step: Int, scale: Float, y: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF0D3EBC), Color(0xFF168CEB), Color(0xFF55D9E8))), cornerRadius = CornerRadius(30.dp.toPx()))
        drawCircle(Color.White.copy(alpha = .13f), radius = size.minDimension * .48f, center = Offset(size.width * .88f, size.height * .12f))
        drawCircle(Color(0xFF88F1FF).copy(alpha = .20f), radius = size.minDimension * .38f, center = Offset(size.width * .06f, size.height * .90f))
        val liquid = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, size.height * .70f)
            cubicTo(size.width * .22f, size.height * .49f, size.width * .52f, size.height * .96f, size.width, size.height * .62f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(liquid, Brush.linearGradient(listOf(Color(0xFF001C72).copy(alpha = .75f), Color(0xFF026AF2).copy(alpha = .45f))))
        // Cartoon shop façade
        drawRoundRect(Color.White.copy(alpha = .94f), topLeft = Offset(size.width * .09f, size.height * .30f), size = Size(size.width * .23f, size.height * .27f), cornerRadius = CornerRadius(12.dp.toPx()))
        drawRoundRect(Color(0xFF125CE4), topLeft = Offset(size.width * .075f, size.height * .25f), size = Size(size.width * .26f, size.height * .075f), cornerRadius = CornerRadius(8.dp.toPx()))
        drawRoundRect(Color(0xFF87D9FF), topLeft = Offset(size.width * .16f, size.height * .40f), size = Size(size.width * .085f, size.height * .17f), cornerRadius = CornerRadius(5.dp.toPx()))
        // Laptop and mobile device
        drawRoundRect(Color(0xFF092B79), topLeft = Offset(size.width * .66f, size.height * .39f), size = Size(size.width * .19f, size.height * .14f), cornerRadius = CornerRadius(7.dp.toPx()))
        drawRoundRect(Color(0xFF83E5FF), topLeft = Offset(size.width * .68f, size.height * .41f), size = Size(size.width * .15f, size.height * .09f), cornerRadius = CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFF052460), topLeft = Offset(size.width * .76f, size.height * .24f), size = Size(size.width * .09f, size.height * .23f), cornerRadius = CornerRadius(9.dp.toPx()))
        drawRoundRect(Color(0xFF7EEFFF), topLeft = Offset(size.width * .775f, size.height * .27f), size = Size(size.width * .06f, size.height * .16f), cornerRadius = CornerRadius(5.dp.toPx()))
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
                Box(Modifier.size(138.dp).clip(RoundedCornerShape(31.dp)).background(Color(0xFF071B42)), contentAlignment = Alignment.Center) {
                    BrandMark(84.dp, light = true)
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF17D99A)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Verified, null, tint = Color.White, modifier = Modifier.size(23.dp)) }
                }
            }
            Spacer(Modifier.height(28.dp))
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF0CE39A).copy(alpha = .16f), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF72F5C4).copy(alpha = .7f))) { UiText("SHOP ACTIVE NOW", color = Color(0xFF79F8C7), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            Spacer(Modifier.height(12.dp))
            UiText(vm.welcomeTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            UiText(vm.shopName.ifBlank { "SkyBarech ERP" }, translate = false, color = Color(0xFF9DD8FF), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            UiText(vm.welcomeSubtitle, color = Color(0xFFD7E6FF), fontSize = 13.sp)
            Spacer(Modifier.height(26.dp))
            LinearProgressIndicator(progress = { if (visible) 1f else 0f }, modifier = Modifier.width(190.dp).clip(RoundedCornerShape(50)), color = Color(0xFF0CE39A), trackColor = Color.White.copy(alpha = .14f))
        }
    }
}

@Composable
private fun OnboardingGradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(52.dp).shadow(14.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF0CE39A), Color(0xFF69007F), Color(0xFFFC0987)))).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
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
    ReferenceAuthBackdrop {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.navigateRoot(AppScreen.LOGIN) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = tr("Back"), tint = Ink) }
            UiText("Activate Account", color = Color(0xFF073A85), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        }
        Surface(Modifier.fillMaxWidth().height(165.dp), shape = RoundedCornerShape(25.dp), color = Color(0xFFEAF8FF), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC9EDFF)), shadowElevation = 5.dp) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF8FDFF), Color(0xFFDDF5FF))))) {
                Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(47.dp); Spacer(Modifier.width(8.dp)); UiText("SkyBarech ERP", translate = false, color = Color(0xFF063B88), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.height(14.dp)); UiText("Verified Shop Setup", color = Color(0xFF0785F5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    UiText("Activate your shop", color = Color(0xFF073A85), fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(86.dp).clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(listOf(Color.White, Color(0xFFBDEEFF)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF087FF0), modifier = Modifier.size(61.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(25.dp), color = Color.White.copy(alpha = .96f), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8EAF6)), shadowElevation = 8.dp) {
            Column(Modifier.padding(14.dp)) {
                AppTextField("Shop Name", shopName, { shopName = it }, leadingIcon = Icons.Outlined.Store, placeholder = "Ali Mobile Accessories")
                Spacer(Modifier.height(6.dp))
                AppTextField("Activation Code", code, { code = it }, leadingIcon = Icons.Outlined.Key, placeholder = "Your activation code")
                Spacer(Modifier.height(6.dp))
                AppTextField("Username / Owner Mobile", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PersonOutline, placeholder = "03001234567")
                Spacer(Modifier.height(7.dp))
                PinCodeField("Temporary PIN", temporaryPassword, { temporaryPassword = it }, length = 4)
                Spacer(Modifier.height(7.dp))
                ReferencePrimaryButton(if (vm.syncInProgress) "Verifying…" else "Activate Shop", enabled = !vm.syncInProgress) { vm.activate(code, mobile, temporaryPassword, shopName) }
            }
        }
    }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel) {
    var mobile by rememberSaveable { mutableStateOf(vm.ownerMobile) }
    var pin by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var connectionDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val binding = vm.biometricBinding
    val fingerprint = BiometricUnlock.enabled(context, binding)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by animateFloatAsState(if (appeared) 1f else 0f, tween(500), label = "login reveal")
    ReferenceAuthBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 10.dp)
            .graphicsLayer { alpha = reveal; translationY = (1f - reveal) * 16f }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            ReferenceBrandIdentity()
            Spacer(Modifier.height(15.dp))
            LanguageSelector()
            Spacer(Modifier.height(14.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                AppTextField("Shop Mobile Number", mobile, { mobile = it }, leadingIcon = Icons.Outlined.PhoneAndroid,
                    keyboardType = KeyboardType.Phone, placeholder = "03XX XXXXXXX", error = if (submitted && mobile.isBlank()) "Enter your mobile number." else null)
                Spacer(Modifier.height(13.dp))
                PinCodeField("4-digit Shop PIN", pin, { pin = it }, length = 4,
                    error = if (submitted && !com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) "Enter exactly 4 digits" else null,
                    onDone = { submitted = true; if (mobile.isNotBlank() && com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) vm.login(mobile, pin) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    var rememberShop by rememberSaveable { mutableStateOf(true) }
                    Checkbox(rememberShop, { rememberShop = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF078AF6)))
                    UiText("Remember Shop", color = Color(0xFF264E89), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.navigateRoot(AppScreen.HELP) }) { UiText("Forgot PIN?", color = Color(0xFF078AF6), fontSize = 12.sp) }
                }
                ReferencePrimaryButton(if (vm.syncInProgress) "Signing in…" else "Login", enabled = !vm.syncInProgress) {
                    submitted = true
                    if (mobile.isNotBlank() && com.skybarech.mobileshoperp.security.ShopPin.valid(pin)) vm.login(mobile, pin)
                }
                Spacer(Modifier.height(11.dp))
                if (fingerprint) OutlinedButton(onClick = {
                    BiometricUnlock.activity(context)?.let { activity -> BiometricUnlock.authenticate(activity, binding, false, success = { vm.unlockWithBiometrics(binding) }, error = { vm.showMessage(it) }) }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.3.dp, Color(0xFF078AF6))) {
                    Icon(Icons.Outlined.Fingerprint, null, tint = Color(0xFF078AF6)); Spacer(Modifier.width(9.dp)); UiText("Fingerprint / Biometric Login", color = Color(0xFF078AF6), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = { connectionDetails = true; vm.checkConnection() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF23AFC9))) {
                    Icon(Icons.Outlined.CloudDone, null, tint = Color(0xFF20A7C8)); Spacer(Modifier.width(9.dp)); UiText("Check Cloud", color = Color(0xFF078AF6), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { vm.navigateRoot(AppScreen.ACTIVATION) }, modifier = Modifier.fillMaxWidth()) { UiText("New shop? Activate account", color = Color(0xFF078AF6), fontSize = 12.sp) }
            }
            UiText("Secure  •  Reliable  •  Cloud Connected", color = Color(0xFF264E89), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            UiText("v${com.skybarech.mobileshoperp.BuildConfig.VERSION_NAME}", color = MutedInk, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
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
    if (vm.loggedIn && vm.screen == AppScreen.DASHBOARD && vm.offerFingerprint) AlertDialog(
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
