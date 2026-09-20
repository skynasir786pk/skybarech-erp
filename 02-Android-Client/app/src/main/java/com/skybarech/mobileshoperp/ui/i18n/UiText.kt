package com.skybarech.mobileshoperp.ui.i18n

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import com.skybarech.mobileshoperp.R

private val UrduFont = FontFamily(Font(R.font.noto_naskh_arabic))
@Composable
fun UiText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified, textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current, translate: Boolean = true
) {
    val urdu = UiLanguage.code == "ur"
    Text(text = if (translate) tr(text) else text, modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily ?: if (urdu) UrduFont else null,
        letterSpacing = if (urdu) TextUnit.Unspecified else letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = if (urdu) TextUnit.Unspecified else lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style)
}

@Composable
fun LanguageSelector(modifier: Modifier = Modifier, light: Boolean = false) {
    val context = LocalContext.current
    TextButton(onClick = { UiLanguage.set(context, if (UiLanguage.code == "ur") "en" else "ur") },
        modifier = modifier.heightIn(min = 48.dp)) {
        UiText(if (UiLanguage.code == "ur") "English" else "اردو", translate = false,
            color = if (light) Color.White else MaterialTheme.colorScheme.primary,
            fontFamily = UrduFont, fontWeight = FontWeight.Bold)
    }
}
