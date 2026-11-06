package com.blackout.communications.ui.calls

import android.telecom.CallAudioState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackout.communications.calls.CallInfo
import com.blackout.communications.calls.CallManager
import com.blackout.communications.data.CallPrefs
import com.blackout.communications.data.DeclineAudience
import com.blackout.communications.ui.components.Avatar
import com.blackout.communications.ui.contacts.formatNumber
import com.blackout.communications.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AnswerGreen = Color(0xFF16A34A)
private val DeclineRed = Color(0xFFDC2626)

// ---------------------------------------------------------------- Incoming call

@Composable
fun IncomingCallScreen(info: CallInfo, prefs: CallPrefs, hasOtherCall: Boolean) {
    var showReplies by remember { mutableStateOf(false) }
    val known = info.contactId != null
    val showMessage = info.number.isNotBlank() && when (prefs.declineAudience) {
        DeclineAudience.Unknown -> !known
        DeclineAudience.Everyone -> true
        DeclineAudience.Selected -> info.contactId != null && info.contactId in prefs.declineSelected
        DeclineAudience.Off -> false
    }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Text(if (hasOtherCall) "Incoming call (another call is on hold if you answer)" else "Incoming call", color = AppColors.SubText, fontSize = 14.sp)
            Spacer(Modifier.height(40.dp))
            Avatar(info.name, info.photoPath, 180.dp)
            Text(info.name, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp))
            if (info.number.isNotBlank() && info.name != info.number) {
                Text(formatNumber(info.number), color = AppColors.SubText, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            SwipeToAnswer(
                onAnswer = { CallManager.answer(info.call) },
                onDecline = { CallManager.decline(info.call) },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Answer", color = AppColors.SubText, fontSize = 13.sp)
                Text("Decline", color = AppColors.SubText, fontSize = 13.sp)
            }
            if (showMessage) {
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, AppColors.Line, RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button) { showReplies = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(18.dp))
                    Text("Message", fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showReplies) {
        AlertDialog(
            onDismissRequest = { showReplies = false },
            title = { Text("Decline and send") },
            text = {
                Column {
                    prefs.quickReplies.forEachIndexed { i, reply ->
                        if (i > 0) HorizontalDivider(color = AppColors.Line)
                        Text(
                            reply,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showReplies = false
                                CallManager.declineWithMessage(info.call, reply)
                            }.padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReplies = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Green phone on the left, red phone on the right, a handle in the middle.
 * Drag the handle to the left to answer or to the right to decline. The phones can also be tapped.
 */
@Composable
private fun SwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(42.dp)).background(AppColors.Card),
        contentAlignment = Alignment.Center,
    ) {
        val limit = with(LocalDensity.current) { (maxWidth / 2 - 42.dp).toPx() }
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(Icons.Filled.Call, "Answer", AnswerGreen, 64.dp) { if (!done) { done = true; onAnswer() } }
            Spacer(Modifier.weight(1f))
            RoundAction(Icons.Filled.CallEnd, "Decline", DeclineRed, 64.dp) { if (!done) { done = true; onDecline() } }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(56.dp)
                .clip(CircleShape)
                .background(AppColors.Text)
                .semantics { contentDescription = "Drag left to answer, right to decline" }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-limit, limit)) }
                    },
                    onDragStopped = {
                        when {
                            offset.value <= -limit * 0.8f && !done -> { done = true; onAnswer() }
                            offset.value >= limit * 0.8f && !done -> { done = true; onDecline() }
                            else -> offset.animateTo(0f)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.UnfoldMore, null, tint = AppColors.Background, modifier = Modifier.rotate(90f))
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

// ---------------------------------------------------------------- In call

@Composable
fun InCallScreen(
    calls: List<CallInfo>,
    main: CallInfo,
    audio: CallAudioState?,
    recording: Boolean,
    onToggleRecord: () -> Unit,
    onAddCall: () -> Unit,
) {
    var showKeypad by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    val other = calls.firstOrNull { it.call != main.call }
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val hasBluetooth = (audio?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_BLUETOOTH != 0

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The other call, on hold. Tap to switch.
            if (other != null) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.Card)
                        .clickable { CallManager.swap() }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(other.name, other.photoPath, 32.dp)
                    Text(other.name, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text(if (other.isHeld) "On hold · tap to switch" else "Tap to switch", color = AppColors.SubText, fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }

            Avatar(main.name, main.photoPath, 120.dp, modifier = Modifier.padding(top = 16.dp))
            Text(main.name, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            if (main.number.isNotBlank() && main.name != main.number) {
                Text(formatNumber(main.number), color = AppColors.SubText, fontSize = 15.sp)
            }
            CallStatus(main, recording)

            Spacer(Modifier.weight(1f))

            if (showKeypad) {
                InCallKeypad(onDigit = { CallManager.playDtmf(main.call, it) }, onHide = { showKeypad = false })
            } else {
                val buttons = buildList {
                    add(CallButton("Mute", Icons.Outlined.MicOff, active = muted) { CallManager.setMuted(!muted) })
                    add(CallButton("Speaker", Icons.AutoMirrored.Outlined.VolumeUp, active = route == CallAudioState.ROUTE_SPEAKER) {
                        CallManager.setRoute(if (route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
                    })
                    if (hasBluetooth) add(CallButton("Bluetooth", Icons.Outlined.Bluetooth, active = route == CallAudioState.ROUTE_BLUETOOTH) {
                        CallManager.setRoute(if (route == CallAudioState.ROUTE_BLUETOOTH) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH)
                    })
                    add(CallButton("Keypad", Icons.Outlined.Dialpad) { showKeypad = true })
                    add(CallButton("Hold", Icons.Outlined.Pause, active = main.isHeld, enabled = main.canHold || main.isHeld) {
                        if (main.isHeld) CallManager.unhold(main.call) else CallManager.hold(main.call)
                    })
                    add(CallButton("Add call", Icons.Outlined.PersonAddAlt, onClick = onAddCall))
                    add(CallButton("Switch", Icons.Outlined.SwapVert, enabled = other != null || main.canSwapConference) { CallManager.swap() })
                    add(CallButton("Merge", Icons.Filled.CallMerge, enabled = other != null || main.canMergeConference) { CallManager.merge() })
                    add(CallButton("Record", Icons.Filled.FiberManualRecord, active = recording, tint = AppColors.Missed, onClick = onToggleRecord))
                    if (main.isConference) add(CallButton("Manage", Icons.Outlined.Groups) { showManage = true })
                }
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    buttons.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { CallButtonView(it) }
                            repeat(3 - row.size) { Spacer(Modifier.width(76.dp)) }
                        }
                    }
                }
            }

            Box(Modifier.padding(top = 28.dp, bottom = 32.dp)) {
                RoundAction(Icons.Filled.CallEnd, "End call", DeclineRed, 76.dp) { CallManager.hangUp(main.call) }
            }
        }
    }

    if (showManage && main.isConference) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            title = { Text("Conference call") },
            text = {
                Column {
                    main.children.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = AppColors.Line)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(c.name, c.photoPath, 36.dp)
                            Text(c.name, modifier = Modifier.padding(start = 10.dp).weight(1f))
                            if (c.canSeparate) TextButton(onClick = { c.call.splitFromConference(); showManage = false }) { Text("Private") }
                            TextButton(onClick = { c.call.disconnect() }) { Text("Hang up", color = AppColors.Missed) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManage = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun CallStatus(main: CallInfo, recording: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(main.call) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = when {
        main.isDialing -> "Calling…"
        main.isHeld -> "On hold"
        main.connectTime > 0 -> {
            val s = ((now - main.connectTime) / 1000).coerceAtLeast(0)
            if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
        }
        else -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        if (recording) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Missed))
            Text("REC", color = AppColors.Missed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private class CallButton(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
private fun CallButtonView(b: CallButton) {
    val alpha = if (b.enabled) 1f else 0.35f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = b.enabled, role = Role.Button, onClick = b.onClick)
            .semantics { contentDescription = b.label + if (b.active) ", on" else "" },
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (b.active) AppColors.Accent else AppColors.Card),
            contentAlignment = Alignment.Center,
        ) {
            val color = when {
                b.active -> Color.White
                b.tint != null -> b.tint
                else -> AppColors.Text
            }
            Icon(b.icon, null, tint = color.copy(alpha = alpha))
        }
        Text(b.label, fontSize = 13.sp, color = AppColors.Text.copy(alpha = alpha), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun InCallKeypad(onDigit: (Char) -> Unit, onHide: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed, fontSize = 28.sp, modifier = Modifier.height(40.dp))
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier.size(68.dp).clip(CircleShape).background(AppColors.Card)
                            .clickable { typed += d; onDigit(d) },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.toString(), fontSize = 26.sp) }
                }
            }
        }
        TextButton(onClick = onHide) { Text("Hide keypad", color = AppColors.Accent) }
    }
}
