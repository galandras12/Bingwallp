package com.galandras.bingwallpaper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onAuto = vm::setAuto,
                    onTarget = vm::setTarget,
                    onInterval = vm::setInterval,
                    onSetNow = vm::setNow,
                )
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: UiState,
    onAuto: (Boolean) -> Unit,
    onTarget: (Target) -> Unit,
    onInterval: (Int) -> Unit,
    onSetNow: () -> Unit,
) {
    val s = state.settings
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewCard(state)

            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_update), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.auto_update_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.autoEnabled, onCheckedChange = onAuto)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).selectableGroup()) {
                    Text(stringResource(R.string.target_title), style = MaterialTheme.typography.titleMedium)
                    RadioRow(stringResource(R.string.target_home), s.target == Target.HOME) { onTarget(Target.HOME) }
                    RadioRow(stringResource(R.string.target_lock), s.target == Target.LOCK) { onTarget(Target.LOCK) }
                    RadioRow(stringResource(R.string.target_both), s.target == Target.BOTH) { onTarget(Target.BOTH) }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.interval_title), style = MaterialTheme.typography.titleMedium)
                    RadioRow(stringResource(R.string.interval_12), s.intervalHours == 12) { onInterval(12) }
                    RadioRow(stringResource(R.string.interval_24), s.intervalHours == 24) { onInterval(24) }
                }
            }

            Button(onClick = onSetNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.working))
                } else {
                    Text(stringResource(R.string.set_now))
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.last_update), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (s.lastSuccessMillis > 0) formatTime(s.lastSuccessMillis) else stringResource(R.string.never),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.status), style = MaterialTheme.typography.labelLarge)
                    Text(
                        s.status.ifBlank { stringResource(R.string.status_idle) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            BatteryCard()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreviewCard(state: UiState) {
    val s = state.settings
    Card(Modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = state.preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = s.title.ifBlank { stringResource(R.string.today_image) },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                    )
                } else if (state.loadingPreview) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.no_preview),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    s.title.ifBlank { stringResource(R.string.today_image) },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (s.copyright.isNotBlank()) {
                    Text(
                        s.copyright,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BatteryCard() {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = { openBatterySettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.battery_button))
            }
            Text(stringResource(R.string.battery_help), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun openBatterySettings(ctx: Context) {
    val primary = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val fallback = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    try {
        ctx.startActivity(primary)
    } catch (_: ActivityNotFoundException) {
        try {
            ctx.startActivity(fallback)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.battery_open_failed, Toast.LENGTH_LONG).show()
        }
    }
}

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.forLanguageTag("hu-HU"))
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
