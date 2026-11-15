/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.content.Intent
import app.opentv.ui.focusVeil
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.core.isIgnoringBatteryOptimizations
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.recording.RecordingStorage
import app.opentv.recording.SmbClient
import app.opentv.recording.SmbConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.opentv.ui.theme.focusLift

/**
 * Where recordings are written: this box's internal storage, a NAS over SMB (Synology and the
 * like), or a plugged-in USB / external drive. The NAS credentials live only on this device,
 * exactly like a provider's login; the USB drive is addressed through a folder the user grants
 * with the system picker (Storage Access Framework), which needs no storage permission.
 */
@Composable
fun RecordingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ServiceLocator.get(context).settings }
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf(settings.recordingTarget.value) }
    var host by remember { mutableStateOf(settings.smbHost.value) }
    var share by remember { mutableStateOf(settings.smbShare.value) }
    var folder by remember { mutableStateOf(settings.smbFolder.value) }
    var user by remember { mutableStateOf(settings.smbUser.value) }
    var password by remember { mutableStateOf(settings.smbPassword.value) }
    var usbTree by remember { mutableStateOf(settings.usbTreeUri.value) }
    var usbLabel by remember { mutableStateOf(settings.usbFolderLabel.value) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    // Recording behaviour. These apply the moment they're changed so they hold whether the user
    // leaves via Done or Save.
    var padStart by remember { mutableStateOf(settings.recordPadStartMinutes.value) }
    var padEnd by remember { mutableStateOf(settings.recordPadEndMinutes.value) }
    var autoSwitch by remember { mutableStateOf(settings.recordAutoSwitch.value) }
    var livePause by remember { mutableStateOf(settings.livePauseEnabled.value) }

    // The SAF folder picker. On a granted tree we take a persistable read/write permission so the
    // grant (and thus playback of what we record there) survives restarts, then remember the folder.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val label = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment ?: context.getString(R.string.recset_usb_label)
            settings.setUsbTree(uri.toString(), label)
            usbTree = uri.toString()
            usbLabel = label
            target = AppSettings.RecordingTarget.USB
            status = context.getString(R.string.recset_usb_saving_to, label)
        }
    }

    fun persist() {
        settings.setSmbConfig(host, share, folder, user, password)
        // Don't commit USB as the destination unless a folder has actually been granted, so
        // leaving the screen without picking one can never strand recordings with nowhere to go.
        val safeTarget =
            if (target == AppSettings.RecordingTarget.USB && usbTree == null) settings.recordingTarget.value
            else target
        settings.setRecordingTarget(safeTarget)
    }

    // No Save button: every choice applies at once, what was typed in the NAS window when it closes.
    var nasWindow by remember { mutableStateOf(false) }
    val targets = listOf(AppSettings.RecordingTarget.INTERNAL, AppSettings.RecordingTarget.SMB, AppSettings.RecordingTarget.USB)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SetGroup(stringResource(R.string.recset_save_to))
        SetPillsRow(
            stringResource(R.string.set_rec_save_to),
            null,
            listOf(stringResource(R.string.set_rec_internal), stringResource(R.string.set_rec_nas), stringResource(R.string.set_rec_usb)),
            targets.indexOf(target),
            first = true,
        ) { i ->
            target = targets[i]
            when {
                // USB needs a folder first: the picker opens, the choice is saved once one is granted.
                target == AppSettings.RecordingTarget.USB && usbTree == null ->
                    runCatching { folderPicker.launch(null) }.onFailure { status = context.getString(R.string.recset_usb_no_picker) }
                else -> settings.setRecordingTarget(target)
            }
        }
        when (target) {
            AppSettings.RecordingTarget.SMB -> SetActionRow(
                stringResource(R.string.recset_nas_connection),
                status,
                value = if (host.isBlank()) stringResource(R.string.set_rec_nas_setup) else "$host/$share",
                icon = R.drawable.ic_set_hard_drives,
            ) { nasWindow = true }
            AppSettings.RecordingTarget.USB -> SetActionRow(
                stringResource(R.string.recset_usb_section),
                if (usbTree == null) stringResource(R.string.recset_usb_need_folder) else stringResource(R.string.recset_usb_hint),
                value = usbLabel ?: stringResource(R.string.recset_usb_choose),
                icon = R.drawable.ic_set_folder,
            ) {
                runCatching { folderPicker.launch(null) }.onFailure { status = context.getString(R.string.recset_usb_no_picker) }
            }
            else -> StorageInfo()
        }

        SetGroup(stringResource(R.string.recset_behaviour_title))
        val min = stringResource(R.string.recset_minutes_short)
        SetStepRow(
            stringResource(R.string.recset_pad_start), stringResource(R.string.recset_pad_start_desc), "$padStart $min",
            canDown = padStart > 0, canUp = padStart < 30,
            onDown = { padStart = (padStart - 1).coerceAtLeast(0); settings.setRecordPadding(padStart, padEnd) },
            onUp = { padStart = (padStart + 1).coerceAtMost(30); settings.setRecordPadding(padStart, padEnd) },
        )
        SetStepRow(
            stringResource(R.string.recset_pad_end), stringResource(R.string.recset_pad_end_desc), "$padEnd $min",
            canDown = padEnd > 0, canUp = padEnd < 60,
            onDown = { padEnd = (padEnd - 1).coerceAtLeast(0); settings.setRecordPadding(padStart, padEnd) },
            onUp = { padEnd = (padEnd + 1).coerceAtMost(60); settings.setRecordPadding(padStart, padEnd) },
        )
        SetSwitchRow(stringResource(R.string.recset_autoswitch), stringResource(R.string.recset_autoswitch_desc), autoSwitch) {
            autoSwitch = it; settings.setRecordAutoSwitch(it)
        }
        SetSwitchRow(stringResource(R.string.recset_livepause), stringResource(R.string.recset_livepause_desc), livePause) {
            livePause = it; settings.setLivePauseEnabled(it)
        }

        SetGroup(stringResource(R.string.recset_background_title))
        BackgroundStatus()
    }

    if (nasWindow) {
        SetDialog(stringResource(R.string.recset_nas_connection), onDismiss = { persist(); nasWindow = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(stringResource(R.string.recset_field_server), host, "192.168.1.10") { host = it }
                Field(stringResource(R.string.recset_field_share), share, "video") { share = it }
                Field(stringResource(R.string.recset_field_folder), folder, "OpenTV") { folder = it }
                Field(stringResource(R.string.recset_field_username), user, "") { user = it }
                Field(stringResource(R.string.recset_field_password), password, "", isPassword = true) { password = it }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(
                        if (testing) stringResource(R.string.recset_status_testing) else stringResource(R.string.recset_test_connection),
                        on = false,
                        enabled = !testing && host.isNotBlank() && share.isNotBlank(),
                    ) {
                        testing = true
                        status = context.getString(R.string.recset_status_testing)
                        val cfg = SmbConfig(host.trim(), share.trim(), folder.trim(), user, password)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { SmbClient.test(cfg) } }
                            testing = false
                            status = result.fold(
                                onSuccess = { context.getString(R.string.recset_status_connected) },
                                onFailure = { context.getString(R.string.recset_status_failed, it.message ?: it.javaClass.simpleName) },
                            )
                        }
                    }
                    Pill(stringResource(R.string.common_save), on = true) {
                        persist(); status = context.getString(R.string.recset_status_saved); nasWindow = false
                    }
                }
            }
        }
    }
}

/** Used-by-recordings and free-space readout for the box's internal storage. */
@Composable
private fun StorageInfo() {
    val context = LocalContext.current
    var used by remember { mutableLongStateOf(-1L) }
    var free by remember { mutableLongStateOf(-1L) }
    var count by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val (u, c, f) = withContext(Dispatchers.IO) {
            val dir = RecordingStorage.internalDir(context)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            Triple(files.sumOf { it.length() }, files.size, dir.usableSpace)
        }
        used = u; count = c; free = f
    }

    SetRow(
        stringResource(R.string.recset_storage_title),
        if (used < 0) stringResource(R.string.recset_storage_reading)
        else stringResource(R.string.recset_storage_used, formatBytes(used), count) + " · " + stringResource(R.string.recset_storage_free, formatBytes(free)),
        R.drawable.ic_set_hard_drives,
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

/**
 * Whether OpenTV is allowed to keep recording in the background (exempt from battery optimisation).
 * Reads *Allowed* or *Not allowed — Fix*; tapping Fix opens the system dialog to grant it. The
 * status is re-read every time the screen resumes, so it flips to Allowed the moment the user
 * comes back from granting it.
 */
@Composable
private fun BackgroundStatus() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = context.isIgnoringBatteryOptimizations()
                // Just came back from granting it: say so out loud so it's unmistakable.
                if (now && !allowed) {
                    Toast.makeText(context, context.getString(R.string.recset_background_toast), Toast.LENGTH_LONG).show()
                }
                allowed = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (allowed) {
        SetRow(
            stringResource(R.string.recset_background_on_title),
            stringResource(R.string.recset_background_on_desc),
            R.drawable.ic_set_shield_check,
            titleColor = Color(0xFF4ADE80),
        )
    } else {
        SetRow(
            stringResource(R.string.recset_background_not_allowed),
            stringResource(R.string.recset_background_desc),
            R.drawable.ic_set_warning,
            titleColor = MaterialTheme.colorScheme.error,
        ) {
            Pill(stringResource(R.string.recset_background_fix), on = true) { context.requestIgnoreBatteryOptimizations() }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        // A recording password is a real credential (a NAS login) — don't paint it on a living-room
        // screen. Masked by default, with an eye to reveal it if you need to check what you typed.
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!isPassword) null else {
            {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.recset_pw_hide else R.string.recset_pw_show,
                        ),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
