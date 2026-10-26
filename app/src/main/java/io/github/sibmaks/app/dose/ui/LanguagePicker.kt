package io.github.sibmaks.app.dose.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.localization.AppLanguages

@Composable
internal fun LanguagePicker(
    firstStart: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selected by rememberSaveable {
        mutableStateOf(AppLanguages.supported.firstOrNull {
            it.tag == AppLanguages.selectedTag(
                context
            )
                .substringBefore('-')
        }?.tag.orEmpty())
    }

    fun dismiss() {
        AppLanguages.markOffered(context); onDismiss()
    }
    SetupScreen(
        title = stringResource(R.string.app_language), onClose = ::dismiss,
        primaryLabel = stringResource(R.string.apply_language), onPrimary = {
            val changed = selected != AppLanguages.selectedTag(context)
            AppLanguages.select(context, selected)
            onDismiss()
            if (changed && Build.VERSION.SDK_INT < 33) context.activity()
                ?.recreate()
        }, secondaryLabel = stringResource(R.string.cancel)
    ) {
        if (firstStart) Text(stringResource(R.string.first_language_offer), style = DoseText.body, color = TextMuted)
        val options =
            listOf("" to stringResource(R.string.system_language)) + AppLanguages.supported.map { it.tag to it.nativeName }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DoseSpacing.small)) {
            options.forEach { (tag, name) ->
                Surface(
                    shape = DoseShapes.button,
                    color = if (selected == tag) AccentPanel else SoftPanel,
                    modifier = Modifier.fillMaxWidth().selectionControl(selected == tag) { selected = tag }
                ) {
                    Row(
                        Modifier.padding(DoseSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DoseSpacing.small)
                    ) {
                        val flag = languageFlag(tag)
                        if (flag == null) Icon(Icons.Rounded.Language, null, tint = Violet, modifier = Modifier.size(36.dp))
                        else Image(painterResource(flag), null, modifier = Modifier.size(width = 36.dp, height = 24.dp))
                        Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        RadioButton(selected = selected == tag, onClick = null)
                    }
                }
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

internal fun languageFlag(tag: String): Int? = when (tag) {
    "en" -> R.drawable.flag_gb
    "ru" -> R.drawable.flag_ru
    "be" -> R.drawable.flag_by
    "hy" -> R.drawable.flag_am
    "az" -> R.drawable.flag_az
    "kk" -> R.drawable.flag_kz
    "ky" -> R.drawable.flag_kg
    "ro" -> R.drawable.flag_md
    "tg" -> R.drawable.flag_tj
    "tk" -> R.drawable.flag_tm
    "uz" -> R.drawable.flag_uz
    "uk" -> R.drawable.flag_ua
    "ka" -> R.drawable.flag_ge
    else -> null
}
