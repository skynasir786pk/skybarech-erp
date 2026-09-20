package com.skybarech.mobileshoperp.ui.components

import com.skybarech.mobileshoperp.ui.i18n.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skybarech.mobileshoperp.model.AppScreen
import com.skybarech.mobileshoperp.model.CartLine
import com.skybarech.mobileshoperp.ui.AppViewModel
import com.skybarech.mobileshoperp.ui.theme.*

val ScreenPadding = 16.dp
val CardRadius = 18.dp

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppCanvas)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = BrandBlueSoft.copy(alpha = 0.50f), radius = size.minDimension * 0.72f, center = Offset(size.width * 1.10f, -size.height * 0.10f))
            drawCircle(color = BrandBlueSoft.copy(alpha = 0.34f), radius = size.minDimension * 0.58f, center = Offset(-size.width * 0.10f, size.height * 1.12f))
            drawLine(color = BrandBlue.copy(alpha = 0.10f), start = Offset(0f, size.height * 0.78f), end = Offset(size.width, size.height * 0.60f), strokeWidth = 3f)
        }
        content()
    }
}

@Composable
fun BrandMark(size: Dp = 56.dp, light: Boolean = false, initials: String = "SB") {
    val color = if (light) Color.White else BrandBlue
    val label = initials.ifBlank { "SB" }.take(2).uppercase()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(if (light) Color.White.copy(alpha = 0.12f) else BrandBlueSoft),
        contentAlignment = Alignment.Center
    ) {
        if (label == "SB") androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.skybarech.mobileshoperp.R.drawable.ic_skybarech),
            contentDescription = tr("SkyBarech"), modifier = Modifier.fillMaxSize())
        else UiText(
            text = label,
            translate = false,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
            letterSpacing = (-2).sp
        )
    }
}

@Composable
fun BrandHeader(compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(if (compact) 40.dp else 52.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            UiText("SkyBarech", color = Ink, fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.ExtraBold)
            UiText("Mobile Shop ERP", color = BrandBlue, fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.Bold)
            if (!compact) UiText("by SkyBarech Technology", color = MutedInk, fontSize = 12.sp)
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Outlined.ArrowForward,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        UiText(label, fontWeight = FontWeight.Bold)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandBlue),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = .65f))
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        UiText(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    visualPassword: Boolean = false,
    singleLine: Boolean = true,
    supportingText: String? = null,
    error: String? = null,
    keyboardType: KeyboardType? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    var showPassword by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyType = keyboardType ?: when {
        visualPassword -> KeyboardType.Password
        label.contains("Email", true) -> KeyboardType.Email
        label.contains("Number", true) || label.contains("Phone", true) || label.contains("Mobile", true) && !label.contains("Product", true) -> KeyboardType.Phone
        listOf("price", "amount", "cost", "fee", "advance", "quantity", "stock", "imei", "cnic", "cash", "purchase", "sale", "wholesale", "minimum").any { label.contains(it, true) } -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    val numberError = if (keyType == KeyboardType.Number && value.isNotBlank() &&
        !value.matches(Regex("[0-9, -]+"))) "Enter whole numbers only." else null
    val fieldError = error ?: numberError
    OutlinedTextField(
        value = value,
        textStyle = LocalTextStyle.current.copy(textDirection = if (keyType in setOf(KeyboardType.Number, KeyboardType.Phone, KeyboardType.Email, KeyboardType.Password, KeyboardType.NumberPassword)) androidx.compose.ui.text.style.TextDirection.Ltr else androidx.compose.ui.text.style.TextDirection.Content),
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { UiText(label) },
        placeholder = { if (placeholder.isNotBlank()) UiText(placeholder) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(19.dp)) } },
        trailingIcon = when {
            visualPassword -> {
                { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = tr("Toggle password visibility")) } }
            }
            trailingIcon != null -> { { Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(19.dp)) } }
            else -> null
        },
        isError = fieldError != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyType, imeAction = if (singleLine) imeAction else ImeAction.Default),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }),
        visualTransformation = if (visualPassword && !showPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(13.dp),
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandBlue,
            focusedLabelColor = BrandBlue,
            unfocusedBorderColor = CardStroke,
            unfocusedLabelColor = MutedInk,
            cursorColor = BrandBlue
        ),
        supportingText = (fieldError ?: supportingText)?.let { { UiText(it, color = if (fieldError != null) Danger else MutedInk, fontSize = 12.sp) } }
    )
}

@Composable
fun PinCodeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    length: Int = 4,
    allowFourOrSix: Boolean = false,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {}
) {
    require(length == 4)
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        UiText(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { char -> char in '0'..'9' }.take(length)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onDone() }),
            decorationBox = { innerTextField ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = if (maxWidth < 310.dp) 6.dp else 9.dp
                    val cell = ((maxWidth - gap * (length - 1)) / length.toFloat()).coerceAtMost(56.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(length) { index ->
                            val filled = index < value.length
                            val active = focused && index == value.length.coerceAtMost(length - 1)
                            val borderColor = when {
                                error != null -> Danger
                                value.length == length -> Success
                                active -> BrandBlue
                                filled -> BrandBlue.copy(alpha = .55f)
                                else -> CardStroke
                            }
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(14.dp))
                                    .background(if (value.length == length) SuccessSoft else if (active) BrandBlueSoft else CardSurface)
                                    .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (filled) Text("•", color = if (value.length == length) Success else BrandBlueDark, fontSize = 29.sp, fontWeight = FontWeight.Black)
                            }
                            if (index < length - 1) Spacer(Modifier.width(gap))
                        }
                    }
                    Box(Modifier.matchParentSize()) { innerTextField() }
                }
            }
        )
        UiText(error ?: if (allowFourOrSix) "Enter your 4 digit shop PIN" else "Enter exactly $length digits",
            color = if (error != null) Danger else MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun AppDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        UiText(label, color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.dp, CardStroke, RoundedCornerShape(13.dp))
                    .clickable { expanded = true },
                color = CardSurface
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiText(value, translate = label !in setOf("Customer", "Supplier", "Mobile / Product", "Product", "Brand", "Mobile Brand", "Model", "Mobile Model"), modifier = Modifier.weight(1f), color = if (value.startsWith("Select")) MutedInk else Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MutedInk)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.94f)) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { UiText(option, translate = label !in setOf("Customer", "Supplier", "Mobile / Product", "Product", "Brand", "Mobile Brand", "Model", "Mobile Model")) },
                        onClick = { onSelect(option); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        UiText(title, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)

    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    UiText(text, modifier = modifier, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(CardRadius), clip = false),
        shape = RoundedCornerShape(CardRadius),
        color = CardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke)
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color = BrandBlue,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(15.dp),
        color = accent,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke)
    ) {
        Column(Modifier.background(Brush.linearGradient(listOf(accent, androidx.compose.ui.graphics.lerp(accent, Color.Black, .22f)))).padding(14.dp)) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(10.dp))
            UiText(label, color = Color.White.copy(alpha = .9f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            UiText(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        color = CardSurface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardStroke)
    ) {
        Column(
            Modifier.padding(vertical = 13.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(35.dp).clip(RoundedCornerShape(12.dp)).background(BrandBlueSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(7.dp))
            UiText(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (fill, ink) = when (tone) {
        StatusTone.SUCCESS -> SuccessSoft to Success
        StatusTone.WARNING -> WarningSoft to Warning
        StatusTone.DANGER -> DangerSoft to Danger
        StatusTone.INFO -> BrandBlueSoft to BrandBlue
        StatusTone.PURPLE -> PurpleSoft to Purple
        StatusTone.NEUTRAL -> Color(0xFFF0F3F8) to MutedInk
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = fill) {
        UiText(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

enum class StatusTone { SUCCESS, WARNING, DANGER, INFO, PURPLE, NEUTRAL }

@Composable
fun ProductAvatar(name: String, color: Color = BrandBlue) {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)),
        contentAlignment = Alignment.Center
    ) {
        UiText(name.take(1).uppercase(), translate = false, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
fun MiniBarChart(modifier: Modifier = Modifier, values: List<Int> = emptyList()) {
    val peak = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bars = values.map { (it.toFloat() / peak).coerceIn(0.02f, 1f) }
    Row(modifier = modifier.height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(if (index == bars.lastIndex) BrandBlue else BrandBlue.copy(alpha = .23f))
            )
        }
    }
}

@Composable
fun CartLineRow(line: CartLine, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProductAvatar(line.product.name)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            UiText(line.product.name, translate = false, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText(line.product.variant, color = MutedInk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText("${line.quantity} × ${line.product.salePrice}", color = MutedInk, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            UiText("Rs. ${line.product.salePrice * line.quantity}", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = tr("Decrease quantity"), tint = BrandBlue) }
                UiText("${line.quantity}", color = Ink, fontWeight = FontWeight.Bold)
                IconButton(onClick = onPlus, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.AddCircleOutline, contentDescription = tr("Increase quantity"), tint = BrandBlue) }
            }
        }
    }
}

@Composable
fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { UiText(placeholder, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, contentDescription = tr("Clear search"), tint = BrandBlue) } },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandBlue,
            unfocusedBorderColor = CardStroke
        )
    )
}

@Composable
fun FilterRow(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { UiText(option, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandBlue, selectedLabelColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@Composable
fun ListEmpty(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(BrandBlueSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(13.dp))
        UiText(title, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        UiText(body, color = MutedInk, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlineButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ConfirmationDialog(vm: AppViewModel) {
    vm.confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirmation,
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Warning) },
            title = { UiText(action.title, translate = !action.literalTitle, fontWeight = FontWeight.Bold) },
            text = { UiText(action.body, color = MutedInk) },
            confirmButton = { TextButton(onClick = vm::confirmCurrentAction) { UiText("Confirm", color = BrandBlue, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = vm::dismissConfirmation) { UiText("Cancel", color = MutedInk) } }
        )
    }
}

/** Form columns wrap on narrow screens and at large accessibility font scales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(10.dp),
    content: @Composable FlowRowScope.() -> Unit
) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = if (maxWidth < 480.dp || fontScale > 1.25f) 1 else 2,
            content = content)
    }
}
