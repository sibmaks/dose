package io.github.sibmaks.app.dose.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.localization.AppLanguages

@Composable
internal fun LanguagePicker(firstStart: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(AppLanguages.supported.firstOrNull { it.tag == AppLanguages.selectedTag(context).substringBefore('-') }?.tag.orEmpty()) }
    fun dismiss() { AppLanguages.markOffered(context); onDismiss() }
    AlertDialog(onDismissRequest = ::dismiss, title = { Text(stringResource(R.string.app_language)) }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            if (firstStart) Text(stringResource(R.string.first_language_offer), modifier = Modifier.padding(bottom = 12.dp))
            val options = listOf("" to stringResource(R.string.system_language)) + AppLanguages.supported.map { it.tag to it.nativeName }
            options.forEach { (tag, name) ->
                Row(Modifier.fillMaxWidth().clickable { selected = tag }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == tag, onClick = { selected = tag })
                    Text(name, modifier = Modifier.weight(1f))
                }
            }
            if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = {
                dismiss()
                AppLanguages.openSettings(context)
            }) { Text(stringResource(R.string.android_language_settings)) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val changed = selected != AppLanguages.selectedTag(context)
            AppLanguages.select(context, selected)
            onDismiss()
            if (changed && Build.VERSION.SDK_INT < 33) context.activity()?.recreate()
        }) { Text(stringResource(R.string.apply_language)) }
    }, dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.cancel)) } })
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
