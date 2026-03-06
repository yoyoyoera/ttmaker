package com.yoyoyo.ttmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

const val snapIntervalMins = 15

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                YoyoTimetableScreen()
            }
        }
    }
}

// ==========================================
// 1. 데이터 모델
// ==========================================
data class EventData(
    val title: String, val description: String,
    val startHour: Int, val startMinute: Int,
    val endHour: Int, val endMinute: Int,
    val dayIndex: Int, val color: Color,
    val id: String = UUID.randomUUID().toString()
)

data class GridPopupInfo(val tapOffset: IntOffset, val dayIndex: Int, val startHour: Int, val startMinute: Int)
data class OverlapInfo(val newEvent: EventData, val overlappingEvents: List<EventData>)
data class PreviewResult(val events: List<EventData>, val freeX: Float? = null, val freeY: Float? = null, val isMagneticSnap: Boolean = true)

val initialSampleEvents = listOf(
    EventData("1차 회의", "3층 기획실\n앱 개발 목적", 10, 0, 14, 0, 0, Color(0xFF27AE60)),
    EventData("오전 세미나", "4층 세미나실\n프론트엔드와 백엔드...", 11, 0, 14, 0, 1, Color(0xFF8E44AD)),
    EventData("점심식사", "2층 구내식당", 14, 0, 15, 0, 0, Color(0xFF2980B9)),
    EventData("점심식사", "애슐리퀸즈 부산대점", 14, 0, 15, 0, 2, Color(0xFF2980B9)),
    EventData("2차 회의", "3층 기획실\n앱의 진행 방향에 관해", 15, 0, 17, 0, 0, Color(0xFF27AE60)),
    EventData("심야 회의", "24시 넘기기 테스트", 22, 0, 26, 0, 0, Color(0xFFE67E22))
)

val initialUnassignedEvents = listOf(
    EventData("19차 회의", "지하 1층 보일러실\n응가타임에 대하여", 0, 0, 0, 0, -1, Color(0xFF27AE60)),
    EventData("응가타임", "화장실", 0, 0, 0, 0, -1, Color(0xFFD35400)),
    EventData("기타치기 타임", "엠파이어 스테이트 빌딩", 0, 0, 0, 0, -1, Color(0xFFD35400))
)

// ==========================================
// 2. 메인 화면
// ==========================================
@Composable
fun YoyoTimetableScreen() {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    var events by remember { mutableStateOf(initialSampleEvents) }
    var unassignedList by remember { mutableStateOf(initialUnassignedEvents) }

    var selectedEvent by remember { mutableStateOf<EventData?>(null) }
    var copiedEvent by remember { mutableStateOf<EventData?>(null) }
    var overlapDialogInfo by remember { mutableStateOf<OverlapInfo?>(null) }

    var currentMode by remember { mutableStateOf("NORMAL") }
    var activeEvent by remember { mutableStateOf<EventData?>(null) }

    var editingEvent by remember { mutableStateOf<EventData?>(null) }

    var isExternalDragging by remember { mutableStateOf(false) }
    var externalDragEvent by remember { mutableStateOf<EventData?>(null) }
    var externalDragPos by remember { mutableStateOf<Offset?>(null) }
    var externalDropSignal by remember { mutableIntStateOf(0) }
    var isExternalMagneticSnap by remember { mutableStateOf(false) }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var scale by remember { mutableFloatStateOf(1f) }
    val hourHeight = (80f * scale).dp

    var gridGlobalX by remember { mutableFloatStateOf(0f) }
    var gridGlobalY by remember { mutableFloatStateOf(0f) }
    var gridHeightPx by remember { mutableFloatStateOf(0f) }

    var itemBounds by remember { mutableStateOf(mapOf<String, Rect>()) }
    val currentItemBounds by rememberUpdatedState(itemBounds)

    LaunchedEffect(currentMode, isExternalDragging) {
        if (currentMode != "NORMAL" || isExternalDragging) selectedEvent = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "제 1차 요요요컴퍼니 컨퍼런스", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f).onGloballyPositioned {
                gridGlobalX = it.positionInWindow().x
                gridGlobalY = it.positionInWindow().y
                gridHeightPx = it.size.height.toFloat()
            }) {
                TimetableGrid(
                    events = events, selectedEvent = selectedEvent, copiedEvent = copiedEvent,
                    currentMode = currentMode, activeEvent = activeEvent,
                    gridGlobalX = gridGlobalX, gridGlobalY = gridGlobalY, gridHeightPx = gridHeightPx,
                    verticalScrollState = verticalScrollState, horizontalScrollState = horizontalScrollState,
                    scale = scale, onScaleChange = { scale = it }, hourHeight = hourHeight,
                    isExternalDragging = isExternalDragging, externalDragEvent = externalDragEvent, externalDragPos = externalDragPos, externalDropSignal = externalDropSignal,
                    onExternalMagneticSnapChange = { isExternalMagneticSnap = it },
                    onExternalDrop = { updatedEvents, droppedId ->
                        events = updatedEvents
                        unassignedList = unassignedList.filter { it.id != droppedId }
                        isExternalDragging = false
                        externalDragEvent = null
                        externalDragPos = null
                    },
                    onExternalCancel = {
                        isExternalDragging = false
                        externalDragEvent = null
                        externalDragPos = null
                    },
                    onEventUnassigned = { eventToUnassign ->
                        events = events.filter { it.id != eventToUnassign.id }
                        val resetEvent = eventToUnassign.copy(startHour = 0, startMinute = 0, endHour = 1, endMinute = 0, dayIndex = -1)
                        unassignedList = unassignedList + resetEvent
                        currentMode = "NORMAL"
                        activeEvent = null
                        selectedEvent = null
                    },
                    onEventLongClick = { event -> selectedEvent = event },
                    onEventDeselect = { selectedEvent = null },
                    onEventClick = { event -> editingEvent = event },
                    onEventCopy = { eventToCopy -> copiedEvent = eventToCopy; selectedEvent = null },
                    onEventMoveStart = { eventToMove -> currentMode = "MOVE"; activeEvent = eventToMove; selectedEvent = null },
                    onEventStretchStart = { eventToStretch -> currentMode = "STRETCH"; activeEvent = eventToStretch; selectedEvent = null },
                    onEventActionComplete = { updatedEvents ->
                        events = updatedEvents
                        if (currentMode == "MOVE") { currentMode = "NORMAL"; activeEvent = null }
                        else if (currentMode == "STRETCH") { activeEvent = updatedEvents.find { it.id == activeEvent?.id } }
                    },
                    onEventActionInvalid = { activeEvent = activeEvent?.copy() },
                    onActionCancel = { currentMode = "NORMAL"; activeEvent = null; selectedEvent = null },
                    onEventPaste = { dayIndex, startHour, startMinute ->
                        copiedEvent?.let { copied ->
                            val durationMins = (copied.endHour * 60 + copied.endMinute) - (copied.startHour * 60 + copied.startMinute)
                            val newStartMins = startHour * 60 + startMinute
                            val newEndMins = newStartMins + durationMins

                            val newEvent = copied.copy(
                                startHour = newStartMins / 60, startMinute = newStartMins % 60,
                                endHour = newEndMins / 60, endMinute = newEndMins % 60,
                                dayIndex = dayIndex, id = UUID.randomUUID().toString()
                            )

                            val overlaps = events.filter { old ->
                                val oldStart = old.startHour * 60 + old.startMinute
                                val oldEnd = old.endHour * 60 + old.endMinute
                                old.dayIndex == dayIndex && newStartMins < oldEnd && newEndMins > oldStart
                            }

                            if (overlaps.isNotEmpty()) overlapDialogInfo = OverlapInfo(newEvent, overlaps)
                            else events = events + newEvent
                        }
                    },
                    onEventAdd = { dayIndex, startHour, startMinute ->
                        editingEvent = EventData("새 일정", "", startHour, startMinute, startHour + 1, startMinute, dayIndex, Color(0xFF95A5A6))
                    }
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(110.dp).background(Color(0xFFEBEBEB))) {
                LazyRow(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    items(unassignedList, key = { it.id }) { event ->
                        val isBeingDragged = isExternalDragging && externalDragEvent?.id == event.id
                        UnassignedEventBlock(
                            event = event, isBeingDragged = isBeingDragged,
                            modifier = Modifier.animateItem().onGloballyPositioned { coords -> itemBounds = itemBounds + (event.id to coords.boundsInWindow()) },
                            onDragStart = { globalOffset ->
                                externalDragEvent = event.copy(startHour = 0, startMinute = 0, endHour = 1, endMinute = 0)
                                externalDragPos = globalOffset
                                isExternalDragging = true
                                selectedEvent = null
                            },
                            onDrag = { dragAmount ->
                                val currentPos = externalDragPos
                                if (currentPos != null) externalDragPos = currentPos + dragAmount
                            },
                            onDragEnd = { externalDropSignal++ },
                            onClick = { editingEvent = event }
                        )
                    }
                    item { AddEventButton(onClick = { editingEvent = EventData("새 일정", "", 0, 0, 1, 0, -1, Color(0xFF95A5A6)) }) }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(bottom = 8.dp).height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(id = R.drawable.under_undo), contentDescription = "되돌리기", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_select), contentDescription = "드래그 모드", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_ai), contentDescription = "AI 챗봇", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_setting), contentDescription = "설정", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_done), contentDescription = "완료", modifier = Modifier.size(22.dp).clickable { currentMode = "NORMAL"; activeEvent = null; selectedEvent = null })
            }
        }

        AnimatedVisibility(visible = currentMode != "NORMAL" || isExternalDragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).zIndex(50f)) {
            Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color(0xEE333333)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val text = if (currentMode == "MOVE" || isExternalDragging) "원하는 요일/시간에 부드럽게 가져다 놓으세요" else "상하단 핸들을 잡아 길이를 조절하세요"
                    Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (currentMode == "STRETCH") {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "완료", color = Color(0xFF3498DB), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.clickable { currentMode = "NORMAL"; activeEvent = null; selectedEvent = null }.padding(4.dp))
                    }
                }
            }
        }

        val ePos = externalDragPos
        val eEvent = externalDragEvent
        val isPointerInsideGrid = ePos != null && (ePos.y - gridGlobalY) in -50f..(gridHeightPx + 50f)
        val showOverlay = isExternalDragging && ePos != null && !isPointerInsideGrid

        if (showOverlay && eEvent != null) {
            val blockWidth = 120.dp
            val blockHeight = 80.dp
            val offsetX = with(density) { ePos.x.toDp() } - (blockWidth / 2)
            val offsetY = with(density) { ePos.y.toDp() } - (blockHeight / 2)

            val animX by animateDpAsState(offsetX, tween(0), label = "ovX")
            val animY by animateDpAsState(offsetY, tween(0), label = "ovY")

            Box(modifier = Modifier.absoluteOffset(x = animX, y = animY).width(blockWidth).height(blockHeight).padding(horizontal = 6.dp, vertical = 2.dp).shadow(12.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(eEvent.color).padding(8.dp).zIndex(100f)) {
                Column {
                    Text(text = eEvent.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (eEvent.description.isNotEmpty()) Text(text = eEvent.description, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = editingEvent != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) + fadeIn(tween(400)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(tween(300)),
            modifier = Modifier.zIndex(1000f)
        ) {
            editingEvent?.let { ev ->
                EventEditScreen(
                    initialEvent = ev,
                    onDismiss = { editingEvent = null },
                    onSave = { savedEvent ->
                        if (savedEvent.dayIndex == -1) {
                            unassignedList = unassignedList.filter { it.id != savedEvent.id } + savedEvent
                            events = events.filter { it.id != savedEvent.id }
                            editingEvent = null
                        } else {
                            val newStartMins = savedEvent.startHour * 60 + savedEvent.startMinute
                            val newEndMins = savedEvent.endHour * 60 + savedEvent.endMinute

                            val overlaps = events.filter { old ->
                                val oldStart = old.startHour * 60 + old.startMinute
                                val oldEnd = old.endHour * 60 + old.endMinute
                                old.id != savedEvent.id && old.dayIndex == savedEvent.dayIndex && newStartMins < oldEnd && newEndMins > oldStart
                            }

                            if (overlaps.isNotEmpty()) {
                                overlapDialogInfo = OverlapInfo(savedEvent, overlaps)
                                editingEvent = null
                            } else {
                                events = events.filter { it.id != savedEvent.id } + savedEvent
                                unassignedList = unassignedList.filter { it.id != savedEvent.id }
                                editingEvent = null
                            }
                        }
                    }
                )
            }
        }
    }

    if (overlapDialogInfo != null) {
        val info = overlapDialogInfo!!
        AlertDialog(
            onDismissRequest = { overlapDialogInfo = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text("이벤트가 겹칩니다", fontWeight = FontWeight.Bold) },
            text = { Text("기존 일정을 어떻게 처리할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    val updated = events.toMutableList()
                    updated.removeAll { it.id == info.newEvent.id }
                    updated.removeAll { old -> info.overlappingEvents.any { it.id == old.id } }
                    val newStart = info.newEvent.startHour * 60 + info.newEvent.startMinute
                    val newEnd = info.newEvent.endHour * 60 + info.newEvent.endMinute

                    info.overlappingEvents.forEach { old ->
                        val oldStart = old.startHour * 60 + old.startMinute
                        val oldEnd = old.endHour * 60 + old.endMinute
                        if (oldStart in newStart until newEnd && oldEnd > newEnd) {
                            updated.add(old.copy(startHour = newEnd / 60, startMinute = newEnd % 60, id = UUID.randomUUID().toString()))
                        } else if (oldStart < newStart && oldEnd in (newStart + 1)..newEnd) {
                            updated.add(old.copy(endHour = newStart / 60, endMinute = newStart % 60, id = UUID.randomUUID().toString()))
                        } else if (oldStart < newStart && oldEnd > newEnd) {
                            updated.add(old.copy(endHour = newStart / 60, endMinute = newStart % 60, id = UUID.randomUUID().toString()))
                            updated.add(old.copy(startHour = newEnd / 60, startMinute = newEnd % 60, id = UUID.randomUUID().toString()))
                        }
                    }
                    updated.add(info.newEvent)
                    events = updated
                    unassignedList = unassignedList.filter { it.id != info.newEvent.id }
                    overlapDialogInfo = null
                }) { Text("덮어쓰기", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { overlapDialogInfo = null; currentMode = "NORMAL"; activeEvent = null }) { Text("취소", color = Color.Gray) }
                    TextButton(onClick = {
                        val newStart = info.newEvent.startHour * 60 + info.newEvent.startMinute
                        events = finalizeDropWithPush(events, info.newEvent, newStart, info.newEvent.dayIndex)
                        unassignedList = unassignedList.filter { it.id != info.newEvent.id }
                        overlapDialogInfo = null
                    }) { Text("밀어넣기", color = Color(0xFF2980B9), fontWeight = FontWeight.Bold) }
                }
            }
        )
    }
}

// ==========================================
// 1. 이동/길이조절 계산 함수
// ==========================================
fun calculateLivePreview(events: List<EventData>, movingEvent: EventData, pointer: Offset, dragTouchOffset: Offset, hourHeightPx: Float, columnWidthPx: Float, isExternalDrag: Boolean = false, isInsideGrid: Boolean = true): PreviewResult {
    val minuteHeightPx = hourHeightPx / 60f
    val hoverDayIndex = (pointer.x / columnWidthPx).toInt().coerceIn(0, 2)
    val topPaddingPx = hourHeightPx / 2f
    val exactStartMins = ((pointer.y - dragTouchOffset.y - topPaddingPx) / minuteHeightPx).toInt()
    val duration = (movingEvent.endHour * 60 + movingEvent.endMinute) - (movingEvent.startHour * 60 + movingEvent.startMinute)

    val nearestHourMins = (exactStartMins / 60f).roundToInt() * 60
    val isMagneticSnap = abs(exactStartMins - nearestHourMins) <= 18
    val displayStartMins = if (isMagneticSnap) nearestHourMins else exactStartMins

    val clampedStartMins = displayStartMins.coerceIn(0, 24 * 60 - duration)
    val clampedEndMins = clampedStartMins + duration

    val freeX = pointer.x - dragTouchOffset.x

    val rawFreeY = pointer.y - dragTouchOffset.y
    val minFreeY = topPaddingPx
    val maxFreeY = topPaddingPx + (24 * 60 - duration) * minuteHeightPx
    val freeY = rawFreeY.coerceIn(minFreeY, maxFreeY)

    val others = events.filter { it.id != movingEvent.id }
    val dayOthers = others.filter { it.dayIndex == hoverDayIndex }.sortedBy { it.startHour * 60 + it.startMinute }
    val previewDayEvents = mutableListOf<EventData>()

    if (isMagneticSnap) {
        val hasOverlap = dayOthers.any { maxOf(clampedStartMins, it.startHour * 60 + it.startMinute) < minOf(clampedEndMins, it.endHour * 60 + it.endMinute) }
        val movingCenter = clampedStartMins + duration / 2.0f

        if (!hasOverlap) {
            previewDayEvents.addAll(dayOthers)
        } else {
            val pushUpList = dayOthers.filter {
                val center = it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f
                center < movingCenter
            }.sortedByDescending { it.endHour * 60 + it.endMinute }

            val pushDownList = dayOthers.filter {
                val center = it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f
                center >= movingCenter
            }.sortedBy { it.startHour * 60 + it.startMinute }

            pushUpList.fold(clampedStartMins) { boundary, old ->
                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                val sEnd = minOf(old.endHour * 60 + old.endMinute, boundary)
                val sStart = maxOf(0, sEnd - oDur)
                previewDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = (sStart + oDur) / 60, endMinute = (sStart + oDur) % 60))
                sStart
            }
            pushDownList.fold(clampedEndMins) { boundary, old ->
                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                val sStart = maxOf((old.startHour * 60 + old.startMinute), boundary)
                previewDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = (sStart + oDur) / 60, endMinute = (sStart + oDur) % 60))
                sStart + oDur
            }
        }
    } else {
        previewDayEvents.addAll(dayOthers)
    }

    // 🔥 UUID를 유지하여 이벤트가 끊기지 않게 함
    val previewMoving = movingEvent.copy(startHour = clampedStartMins / 60, startMinute = clampedStartMins % 60, endHour = clampedEndMins / 60, endMinute = clampedEndMins % 60, dayIndex = hoverDayIndex)
    previewDayEvents.add(previewMoving)

    val finalEvents = others.filter { it.dayIndex != hoverDayIndex }.toMutableList()
    finalEvents.addAll(previewDayEvents)
    return PreviewResult(finalEvents, freeX, freeY, isMagneticSnap)
}

// 🔥 v0.52b: 분할 블록 STRETCH 오작동 완벽 해결
fun calculateStretchPreview(
    events: List<EventData>, activeEvent: EventData, pointerY: Float, stretchType: String, hourHeightPx: Float,
    dragHitPart: Int // 🔥 어떤 조각(part1=1, part2=2)을 터치했는지 판별값 추가
): PreviewResult {
    val minuteHeightPx = hourHeightPx / 60f
    val topPaddingPx = hourHeightPx / 2f
    val pointerMins = ((pointerY - topPaddingPx) / minuteHeightPx).toInt()
    val snappedMins = ((pointerMins / snapIntervalMins.toFloat()).roundToInt() * snapIntervalMins)

    val oldStart = activeEvent.startHour * 60 + activeEvent.startMinute
    val oldEnd = activeEvent.endHour * 60 + activeEvent.endMinute

    // 원본이 분할된 블록인지 확인
    val isOriginalSplit = oldEnd > 24 * 60

    val others = events.filter { it.id != activeEvent.id }
    val dayOthers = others.filter { it.dayIndex == activeEvent.dayIndex }
    val previewDayEvents = mutableListOf<EventData>()

    var newStart = oldStart
    var newEnd = oldEnd

    if (stretchType == "TOP") {
        val blocksAbove = dayOthers.filter { (it.endHour * 60 + it.endMinute) <= oldStart }.sortedByDescending { it.endHour * 60 + it.endMinute }
        val unaffected = dayOthers.filter { (it.endHour * 60 + it.endMinute) > oldStart }
        val limitByBlocks = blocksAbove.sumOf { (it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute) }

        // 🔥 이미 분할된 블록은 더 이상 '이전 날'로 확장할 수 없음 (무조건 0시 방어)
        val absoluteMin = if (activeEvent.dayIndex > 0 && !isOriginalSplit) -30 else 0

        // part2를 위로 당기더라도 다음날 0:00(24:00) 밑으로는 못 내려가게 방어
        val limitStart = if (isOriginalSplit && dragHitPart == 2) maxOf(limitByBlocks, 24 * 60) else limitByBlocks

        if (pointerMins < 0 && activeEvent.dayIndex > 0 && !isOriginalSplit) {
            newStart = maxOf(absoluteMin, pointerMins)
        } else {
            newStart = maxOf(limitStart, minOf(snappedMins.coerceAtLeast(absoluteMin), oldEnd - snapIntervalMins))
        }

        blocksAbove.fold(newStart) { boundary, old ->
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sEnd = minOf((old.endHour * 60 + old.endMinute), boundary)
            val sStart = sEnd - oDur
            previewDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = sEnd / 60, endMinute = sEnd % 60))
            sStart
        }
        previewDayEvents.addAll(unaffected)
    } else {
        val blocksBelow = dayOthers.filter { (it.startHour * 60 + it.startMinute) >= oldEnd }.sortedBy { it.startHour * 60 + it.startMinute }
        val unaffected = dayOthers.filter { (it.startHour * 60 + it.startMinute) < oldEnd }

        // 분할 블록은 최대 48시(A+1일 24시)까지 연산 허용
        val maxLimit = if (isOriginalSplit) 48 * 60 else 24 * 60
        val maxEndAllowed = maxLimit - blocksBelow.sumOf { (it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute) }

        // part1을 아래로 당기더라도 A일 자정(24:00)을 넘어가지 못하게 방어
        val limitEnd = if (isOriginalSplit && dragHitPart == 1) minOf(maxEndAllowed, 24 * 60) else maxEndAllowed

        // 🔥 이미 분할된 블록은 더 이상 '다음 날'로 확장할 수 없음 (+30분 점프 불가)
        if (pointerMins > 24 * 60 && activeEvent.dayIndex < 2 && !isOriginalSplit) {
            newEnd = minOf(pointerMins, 24 * 60 + 30)
        } else {
            newEnd = minOf(limitEnd, maxOf(snappedMins.coerceAtMost(maxLimit), oldStart + snapIntervalMins))
        }

        blocksBelow.fold(newEnd) { boundary, old ->
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sStart = maxOf((old.startHour * 60 + old.startMinute), boundary)
            val sEnd = sStart + oDur
            previewDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = sEnd / 60, endMinute = sEnd % 60))
            sEnd
        }
        previewDayEvents.addAll(unaffected)
    }

    val previewStretchEvent = activeEvent.copy(startHour = newStart / 60, startMinute = newStart % 60, endHour = newEnd / 60, endMinute = newEnd % 60)
    previewDayEvents.add(previewStretchEvent)

    val finalEvents = others.filter { it.dayIndex != activeEvent.dayIndex }.toMutableList()
    finalEvents.addAll(previewDayEvents)
    return PreviewResult(finalEvents, null, null, true)
}

fun finalizeDropWithPush(events: List<EventData>, movingEvent: EventData, finalStartMins: Int, dayIndex: Int): List<EventData> {
    val duration = (movingEvent.endHour * 60 + movingEvent.endMinute) - (movingEvent.startHour * 60 + movingEvent.startMinute)
    val snappedEndMins = finalStartMins + duration
    val movingCenter = finalStartMins + duration / 2.0f

    val others = events.filter { it.id != movingEvent.id }
    val dayOthers = others.filter { it.dayIndex == dayIndex }

    val hasOverlap = dayOthers.any { maxOf(finalStartMins, it.startHour * 60 + it.startMinute) < minOf(snappedEndMins, it.endHour * 60 + it.endMinute) }
    val finalizedDayEvents = mutableListOf<EventData>()

    if (!hasOverlap) {
        finalizedDayEvents.addAll(dayOthers)
    } else {
        val pushUpList = dayOthers.filter {
            val center = it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f
            center < movingCenter
        }.sortedByDescending { it.endHour * 60 + it.endMinute }

        val pushDownList = dayOthers.filter {
            val center = it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f
            center >= movingCenter
        }.sortedBy { it.startHour * 60 + it.startMinute }

        pushUpList.fold(finalStartMins) { boundary, old ->
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sEnd = minOf(old.endHour * 60 + old.endMinute, boundary)
            val sStart = maxOf(0, sEnd - oDur)
            finalizedDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = (sStart + oDur) / 60, endMinute = (sStart + oDur) % 60))
            sStart
        }

        pushDownList.fold(snappedEndMins) { boundary, old ->
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sStart = maxOf(old.startHour * 60 + old.startMinute, boundary)
            val sEnd = sStart + oDur
            finalizedDayEvents.add(old.copy(startHour = sStart / 60, startMinute = sStart % 60, endHour = sEnd / 60, endMinute = sEnd % 60))
            sEnd
        }
    }

    val finalizedMoving = movingEvent.copy(startHour = finalStartMins / 60, startMinute = finalStartMins % 60, endHour = snappedEndMins / 60, endMinute = snappedEndMins % 60, dayIndex = dayIndex)
    finalizedDayEvents.add(finalizedMoving)

    val finalEvents = others.filter { it.dayIndex != dayIndex }.toMutableList()
    finalEvents.addAll(finalizedDayEvents)
    return finalEvents
}

// ==========================================
// 3. 시간표 그리드
// ==========================================
@Composable
fun TimetableGrid(
    events: List<EventData>, selectedEvent: EventData?, copiedEvent: EventData?,
    currentMode: String, activeEvent: EventData?, gridGlobalX: Float, gridGlobalY: Float, gridHeightPx: Float,
    verticalScrollState: ScrollState, horizontalScrollState: ScrollState,
    scale: Float, onScaleChange: (Float) -> Unit, hourHeight: Dp,
    isExternalDragging: Boolean, externalDragEvent: EventData?, externalDragPos: Offset?, externalDropSignal: Int,
    onExternalMagneticSnapChange: (Boolean) -> Unit, onExternalDrop: (List<EventData>, String) -> Unit, onExternalCancel: () -> Unit,
    onEventUnassigned: (EventData) -> Unit, onEventLongClick: (EventData) -> Unit, onEventDeselect: () -> Unit,
    onEventClick: (EventData) -> Unit,
    onEventCopy: (EventData) -> Unit, onEventMoveStart: (EventData) -> Unit, onEventStretchStart: (EventData) -> Unit,
    onEventActionComplete: (List<EventData>) -> Unit, onEventActionInvalid: () -> Unit, onActionCancel: () -> Unit,
    onEventPaste: (Int, Int, Int) -> Unit, onEventAdd: (Int, Int, Int) -> Unit
) {
    val days = listOf("27(토)", "28(일)", "29(월)")
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()

    var gridPopupInfo by remember { mutableStateOf<GridPopupInfo?>(null) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var dragTouchOffset by remember { mutableStateOf<Offset?>(null) }
    var stretchType by remember { mutableStateOf<String?>(null) }

    // 🔥 v0.52b: 팝업 중복 방지용 선택된 파트 식별자 저장
    var selectedPartSuffix by remember { mutableStateOf<String?>(null) }
    // 🔥 v0.52b: 어느 조각을 터치했는지 기억 (1=part1, 2=part2, 3=main)
    var dragHitPart by remember { mutableIntStateOf(0) }

    val previewResult = remember(events, currentMode, activeEvent, dragPointer, dragTouchOffset, stretchType, isExternalDragging, externalDragEvent, externalDragPos, verticalScrollState.value, horizontalScrollState.value, gridGlobalX, gridGlobalY, gridHeightPx, dragHitPart, hourHeight.value) {
        val pPtr = dragPointer
        val dOffset = dragTouchOffset
        val sType = stretchType
        val ePos = externalDragPos
        val hHPx = with(density) { hourHeight.toPx() }
        val wPx = with(density) { 120.dp.toPx() }

        if (currentMode == "MOVE" && activeEvent != null && pPtr != null && dOffset != null) {
            val visualY = pPtr.y - verticalScrollState.value
            if (visualY > gridHeightPx + 600f) {
                PreviewResult(events.filter { it.id != activeEvent.id }, null, null, false)
            } else {
                calculateLivePreview(events, activeEvent, pPtr, dOffset, hHPx, wPx, isExternalDrag = false, isInsideGrid = true)
            }
        } else if (currentMode == "STRETCH" && activeEvent != null && pPtr != null && sType != null) {
            // 🔥 v0.52b: part2(아랫부분)을 터치했다면, 로직이 이어지도록 Y좌표를 24시간 밑으로 가상 투영함
            val isSplit = activeEvent.endHour * 60 + activeEvent.endMinute > 24 * 60
            val mappedY = if (isSplit && dragHitPart == 2) pPtr.y + 24 * 60 * (hHPx / 60f) else pPtr.y
            calculateStretchPreview(events, activeEvent, mappedY, sType, hHPx, dragHitPart)
        } else if (isExternalDragging && externalDragEvent != null && ePos != null) {
            val visualY = ePos.y - gridGlobalY
            val isInsideGrid = visualY in -50f..(gridHeightPx + 50f)
            val localX = ePos.x - gridGlobalX + horizontalScrollState.value
            val localY = ePos.y - gridGlobalY + verticalScrollState.value
            val extDragTouchOffset = Offset(wPx / 2f, hHPx / 2f)

            calculateLivePreview(events, externalDragEvent, Offset(localX, localY), extDragTouchOffset, hHPx, wPx, isExternalDrag = true, isInsideGrid = isInsideGrid)
        } else null
    }

    val displayEvents = if ((currentMode != "NORMAL" || isExternalDragging) && previewResult != null) previewResult.events else events

    LaunchedEffect(previewResult) {
        if (isExternalDragging) onExternalMagneticSnapChange(previewResult?.isMagneticSnap == true)
    }

    LaunchedEffect(currentMode, isExternalDragging, dragPointer, externalDragPos) {
        while (currentMode != "NORMAL" || isExternalDragging) {
            val visualY = if (isExternalDragging) externalDragPos?.let { it.y - gridGlobalY }
            else if (currentMode != "NORMAL") dragPointer?.let { it.y - verticalScrollState.value }
            else null

            if (visualY != null && gridHeightPx > 0f) {
                var scrollDelta = 0f
                if (visualY > 0f && visualY < 100f && verticalScrollState.value > 0) scrollDelta = -35f
                else if (visualY > gridHeightPx - 100f && verticalScrollState.value < verticalScrollState.maxValue) scrollDelta = 35f

                if (scrollDelta != 0f) {
                    verticalScrollState.dispatchRawDelta(scrollDelta)
                    if (currentMode != "NORMAL" && dragPointer != null) dragPointer = dragPointer!!.copy(y = dragPointer!!.y + scrollDelta)
                }
            }
            delay(16)
        }
    }

    LaunchedEffect(externalDropSignal) {
        if (externalDropSignal > 0 && isExternalDragging && externalDragEvent != null && externalDragPos != null) {
            val visualY = externalDragPos.y - gridGlobalY
            if (visualY in -50f..(gridHeightPx + 50f)) {
                val hHPx = with(density) { hourHeight.toPx() }
                val wPx = with(density) { 120.dp.toPx() }
                val localX = externalDragPos.x - gridGlobalX + horizontalScrollState.value
                val localY = externalDragPos.y - gridGlobalY + verticalScrollState.value
                val dayIndex = (localX / wPx).toInt().coerceIn(0, 2)
                val exactStartMins = ((localY - hHPx / 2f) / (hHPx / 60f)).toInt()

                val finalStartMins = (exactStartMins / 60f).roundToInt() * 60

                val duration = (externalDragEvent.endHour * 60 + externalDragEvent.endMinute) - (externalDragEvent.startHour * 60 + externalDragEvent.startMinute)
                val clampedStartMins = finalStartMins.coerceIn(0, 24 * 60 - duration)

                val finalizedEvents = finalizeDropWithPush(events, externalDragEvent, clampedStartMins, dayIndex)
                onExternalDrop(finalizedEvents, externalDragEvent.id)
            } else {
                onExternalCancel()
            }
            onExternalMagneticSnapChange(false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp).horizontalScroll(horizontalScrollState)) {
            days.forEach { day -> Box(modifier = Modifier.width(120.dp).padding(horizontal = 4.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Text(text = day, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) } }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> onScaleChange((scale * (1f + (zoom - 1f) * 2.5f)).coerceIn(0.3f, 5f)) } }
                    .verticalScroll(verticalScrollState)
            ) {
                Column(modifier = Modifier.width(40.dp)) {
                    Spacer(modifier = Modifier.height(hourHeight / 2f)) // 상단 30분 패딩
                    (0..24).toList().forEach { hour -> Text(text = "$hour", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(hourHeight).padding(top = 8.dp)) }
                }

                Box(
                    modifier = Modifier
                        .width(120.dp * 3).height(hourHeight * 25.5f) // 총 도화지 25시간 길이
                        .horizontalScroll(horizontalScrollState)
                        .pointerInput(currentMode, activeEvent) {
                            if (currentMode != "NORMAL" && activeEvent != null) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val wPx = with(density) { 120.dp.toPx() }
                                        val hPxPerMin = with(density) { hourHeight.toPx() / 60f }
                                        val hHPx = with(density) { hourHeight.toPx() }
                                        val topPaddingPx = hHPx / 2f

                                        val startMins = activeEvent.startHour * 60 + activeEvent.startMinute
                                        val endMins = activeEvent.endHour * 60 + activeEvent.endMinute
                                        val isSplit = endMins > 24 * 60

                                        val left1 = activeEvent.dayIndex * wPx
                                        val top1 = startMins * hPxPerMin + topPaddingPx
                                        val bottom1 = minOf(endMins, 24 * 60) * hPxPerMin + topPaddingPx
                                        val right1 = left1 + wPx

                                        val touchBuffer = 40f
                                        val hit1 = offset.x in (left1 - touchBuffer)..(right1 + touchBuffer) && offset.y in (top1 - touchBuffer)..(bottom1 + touchBuffer)

                                        var hit2 = false
                                        var top2 = 0f
                                        var bottom2 = 0f
                                        if (isSplit && activeEvent.dayIndex < 2) {
                                            val left2 = (activeEvent.dayIndex + 1) * wPx
                                            top2 = 0f * hPxPerMin + topPaddingPx
                                            bottom2 = (endMins - 24 * 60) * hPxPerMin + topPaddingPx
                                            val right2 = left2 + wPx
                                            hit2 = offset.x in (left2 - touchBuffer)..(right2 + touchBuffer) && offset.y in (top2 - touchBuffer)..(bottom2 + touchBuffer)
                                        }

                                        if (hit1 || hit2) {
                                            // 🔥 겹치는 터치 영역에서 더 가까운 곳을 선택
                                            val actualHit = if (isSplit) {
                                                if (hit2 && !hit1) 2 else if (hit1 && !hit2) 1 else {
                                                    val dist1 = minOf(abs(offset.y - top1), abs(offset.y - bottom1))
                                                    val dist2 = minOf(abs(offset.y - top2), abs(offset.y - bottom2))
                                                    if (dist2 < dist1) 2 else 1
                                                }
                                            } else 3

                                            dragHitPart = actualHit
                                            dragPointer = offset

                                            if (currentMode == "MOVE") {
                                                // 분할 블록의 이동은 UI에서 이미 막혔으므로 일반 블록에서만 실행됨
                                                dragTouchOffset = Offset(offset.x - left1, offset.y - top1)
                                            } else if (currentMode == "STRETCH") {
                                                // 🔥 분할된 블록은 part1은 위로만(TOP), part2는 아래로만(BOTTOM) 당기도록 강제 고정!
                                                if (isSplit) {
                                                    stretchType = if (actualHit == 1) "TOP" else "BOTTOM"
                                                } else {
                                                    val centerY = top1 + (bottom1 - top1) / 2f
                                                    stretchType = if (offset.y < centerY) "TOP" else "BOTTOM"
                                                }
                                            }
                                        } else onEventActionInvalid()
                                    },
                                    onDrag = { _, dragAmount ->
                                        if (dragHitPart != 0) {
                                            val pPtr = dragPointer
                                            if (pPtr != null) dragPointer = pPtr + dragAmount
                                        }
                                    },
                                    onDragEnd = {
                                        val pPtr = dragPointer
                                        val dOffset = dragTouchOffset
                                        val sType = stretchType
                                        val hitPart = dragHitPart

                                        if (hitPart != 0 && pPtr != null && activeEvent != null) {
                                            val hHPx = with(density) { hourHeight.toPx() }
                                            val hPxPerMin = hHPx / 60f

                                            if (currentMode == "MOVE" && dOffset != null) {
                                                val visualY = pPtr.y - verticalScrollState.value
                                                if (visualY > gridHeightPx + 600f) {
                                                    onEventUnassigned(activeEvent)
                                                } else {
                                                    val cWPx = with(density) { 120.dp.toPx() }
                                                    val finalHoverDayIndex = (pPtr.x / cWPx).toInt().coerceIn(0, 2)
                                                    val exactStartMins = ((pPtr.y - dOffset.y - hHPx / 2f) / hPxPerMin).toInt()
                                                    val duration = (activeEvent.endHour * 60 + activeEvent.endMinute) - (activeEvent.startHour * 60 + activeEvent.startMinute)

                                                    val finalStartMins = (exactStartMins / 60f).roundToInt() * 60
                                                    val roundedStartMins = finalStartMins.coerceIn(0, 24 * 60 - duration)

                                                    val finalizedEvents = finalizeDropWithPush(events, activeEvent, roundedStartMins, finalHoverDayIndex)
                                                    onEventActionComplete(finalizedEvents)
                                                }
                                            } else if (currentMode == "STRETCH" && sType != null) {
                                                val isSplit = activeEvent.endHour * 60 + activeEvent.endMinute > 24 * 60
                                                val mappedY = if (isSplit && hitPart == 2) pPtr.y + 24 * 60 * hPxPerMin else pPtr.y

                                                val finalRes = calculateStretchPreview(events, activeEvent, mappedY, sType, hHPx, hitPart)
                                                var resultingEvents = finalRes.events
                                                val stretchedEvent = resultingEvents.find { it.id == activeEvent.id }

                                                var isExtendedToNextDay = false
                                                var isExtendedToPrevDay = false

                                                if (stretchedEvent != null) {
                                                    val isOriginalSplit = activeEvent.endHour * 60 + activeEvent.endMinute > 24 * 60

                                                    if (sType == "BOTTOM" && !isOriginalSplit) { // 🔥 이미 분할된 블록은 다음 날로 확장 점프 안 함
                                                        val endMins = stretchedEvent.endHour * 60 + stretchedEvent.endMinute
                                                        if (endMins > 24 * 60) {
                                                            val nextDayEvent = stretchedEvent.copy(endHour = 25, endMinute = 0)
                                                            resultingEvents = resultingEvents.filter { it.id != activeEvent.id } + nextDayEvent
                                                            isExtendedToNextDay = true
                                                            coroutineScope.launch { delay(100); verticalScrollState.animateScrollTo(0) }
                                                        }
                                                    } else if (sType == "TOP" && !isOriginalSplit) { // 🔥 이미 분할된 블록은 이전 날로 확장 점프 안 함
                                                        val startMins = stretchedEvent.startHour * 60 + stretchedEvent.startMinute
                                                        if (startMins < 0 && activeEvent.dayIndex > 0) {
                                                            val prevDayEvent = stretchedEvent.copy(startHour = -1, startMinute = 0)
                                                            resultingEvents = resultingEvents.filter { it.id != activeEvent.id } + prevDayEvent
                                                            isExtendedToPrevDay = true
                                                            coroutineScope.launch { delay(100); verticalScrollState.animateScrollTo(verticalScrollState.maxValue) }
                                                        }
                                                    }
                                                }

                                                // 이전 날로 밀려난 블록들 음수 좌표 보정
                                                resultingEvents = resultingEvents.map { ev ->
                                                    val evStartMins = ev.startHour * 60 + ev.startMinute
                                                    if (evStartMins < 0 && ev.dayIndex > 0 && ev.id != activeEvent.id) {
                                                        val shiftEndMins = ev.endHour * 60 + ev.endMinute + 24 * 60
                                                        ev.copy(dayIndex = ev.dayIndex - 1, startHour = (evStartMins + 24 * 60) / 60, startMinute = (evStartMins + 24 * 60) % 60, endHour = shiftEndMins / 60, endMinute = shiftEndMins % 60)
                                                    } else ev
                                                }

                                                onEventActionComplete(resultingEvents)
                                                if (isExtendedToNextDay || isExtendedToPrevDay) onActionCancel()
                                            }
                                        } else onEventActionInvalid()

                                        dragPointer = null; dragTouchOffset = null; stretchType = null; dragHitPart = 0
                                    },
                                    onDragCancel = {
                                        onEventActionInvalid()
                                        dragPointer = null; dragTouchOffset = null; stretchType = null; dragHitPart = 0
                                    }
                                )
                            }
                        }
                        .pointerInput(hourHeight, currentMode) {
                            detectTapGestures(
                                onTap = {
                                    if (currentMode != "NORMAL") {
                                        onActionCancel()
                                    } else {
                                        gridPopupInfo = null
                                        selectedPartSuffix = null // 🔥 팝업 초기화
                                    }
                                },
                                onLongPress = { offset ->
                                    if (currentMode == "NORMAL") {
                                        onEventDeselect()
                                        selectedPartSuffix = null
                                        val dIdx = (offset.x / with(density) { 120.dp.toPx() }).toInt().coerceIn(0, 2)
                                        val h = ((offset.y - with(density){hourHeight.toPx() / 2f}) / (with(density) { hourHeight.toPx() } / 60f)).toInt() / 60

                                        // 🔥 v0.52b: 붙여넣기 팝업 화면 이탈 방지 (Clamp X)
                                        val popupWidthPx = with(density) { 140.dp.toPx() }
                                        val touchXInWindow = gridGlobalX - horizontalScrollState.value + offset.x
                                        val screenWidthPx = windowInfo.containerSize.width.toFloat()
                                        val maxLeft = screenWidthPx - popupWidthPx - with(density) { 16.dp.toPx() }
                                        val idealLeft = touchXInWindow - with(density) { 40.dp.toPx() }
                                        val shiftX = if (idealLeft > maxLeft) maxLeft - idealLeft else 0f

                                        val finalOffsetX = offset.x - with(density){40.dp.toPx()} + shiftX
                                        val finalOffsetY = offset.y - with(density){60.dp.toPx()}

                                        gridPopupInfo = GridPopupInfo(IntOffset(finalOffsetX.toInt(), finalOffsetY.toInt()), dIdx, h.coerceIn(0, 23), 0)
                                    }
                                }
                            )
                        }
                ) {
                    Row { days.forEachIndexed { _, _ -> Box(modifier = Modifier.width(120.dp).fillMaxHeight()) { Column { Spacer(modifier = Modifier.height(hourHeight / 2f)); (0..24).toList().forEach { _ -> Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp) } } } } } }

                    val visuallySplitEvents = displayEvents.flatMap { event ->
                        val startMins = event.startHour * 60 + event.startMinute
                        val endMins = event.endHour * 60 + event.endMinute

                        // 🔥 원본이 이미 분할되어 있었는지 검사
                        val originalEvent = events.find { it.id == event.id }
                        val isOriginalSplit = originalEvent != null && (originalEvent.endHour * 60 + originalEvent.endMinute > 24 * 60)

                        val isStretchingBottom = currentMode == "STRETCH" && stretchType == "BOTTOM" && event.id == activeEvent?.id && endMins > 24 * 60 && !isOriginalSplit
                        val isStretchingTop = currentMode == "STRETCH" && stretchType == "TOP" && event.id == activeEvent?.id && startMins < 0 && !isOriginalSplit

                        if (endMins > 24 * 60 && !isStretchingBottom) {
                            val part1 = event.copy(endHour = 24, endMinute = 0)
                            val nextDayIndex = minOf(event.dayIndex + 1, 2)
                            val part2 = event.copy(
                                startHour = 0, startMinute = 0,
                                endHour = (endMins - 24 * 60) / 60,
                                endMinute = (endMins - 24 * 60) % 60,
                                dayIndex = nextDayIndex
                            )
                            listOf(Pair(part1, "part1"), Pair(part2, "part2"))
                        } else if (startMins < 0 && !isStretchingTop) {
                            val actualStartMins = 24 * 60 + startMins
                            val part1 = event.copy(
                                dayIndex = maxOf(event.dayIndex - 1, 0),
                                startHour = actualStartMins / 60,
                                startMinute = actualStartMins % 60,
                                endHour = 24,
                                endMinute = 0
                            )
                            val part2 = event.copy(startHour = 0, startMinute = 0)
                            listOf(Pair(part1, "part1"), Pair(part2, "part2"))
                        } else {
                            listOf(Pair(event, "main"))
                        }
                    }

                    visuallySplitEvents.forEach { (event, partSuffix) ->
                        key("${event.id}_$partSuffix") {
                            val isInternalActive = currentMode != "NORMAL" && event.id == activeEvent?.id
                            val isExternalActive = isExternalDragging && event.id == externalDragEvent?.id
                            val isActiveTarget = isInternalActive || isExternalActive

                            val isDimmed = (currentMode != "NORMAL" || isExternalDragging) && !isActiveTarget
                            val blockAlpha = if (isDimmed) 0.85f else 1.0f

                            val isMagneticSnap = if (isInternalActive && currentMode == "MOVE") previewResult?.isMagneticSnap ?: false else if (isExternalActive) previewResult?.isMagneticSnap ?: false else false

                            val isNextDayPart = partSuffix == "part2" && event.id == activeEvent?.id
                            val freeX = if (isInternalActive && currentMode == "MOVE") previewResult?.freeX else if (isExternalActive) previewResult?.freeX else null
                            val freeY = if (isInternalActive && currentMode == "MOVE" && !isNextDayPart) previewResult?.freeY else if (isExternalActive) previewResult?.freeY else null

                            val isEventSplit = partSuffix != "main"

                            // 🔥 v0.52b: 터치된 특정 조각(part)만 팝업을 띄우도록 설정! (듀얼 팝업 방지)
                            val isSelected = (selectedEvent?.id == event.id) && (selectedPartSuffix == partSuffix)

                            EventBlockItem(
                                event = event, hourHeightDp = hourHeight.value,
                                isSelected = isSelected, currentMode = currentMode, isActiveTarget = isActiveTarget,
                                isMagneticSnap = isMagneticSnap, freeX = freeX, freeY = freeY, alpha = blockAlpha,
                                isEventSplit = isEventSplit, partSuffix = partSuffix,
                                onLongClick = {
                                    gridPopupInfo = null
                                    selectedPartSuffix = partSuffix // 🔥 누른 파트 기억
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventLongClick(originalEvent)
                                },
                                onDeselect = {
                                    selectedPartSuffix = null
                                    onEventDeselect()
                                },
                                onClick = {
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventClick(originalEvent)
                                },
                                onCopy = {
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventCopy(originalEvent)
                                },
                                onMoveStart = {
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventMoveStart(originalEvent)
                                },
                                onStretchStart = {
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventStretchStart(originalEvent)
                                },
                                onActionCancel = onActionCancel,
                                onEventUnassigned = {
                                    val originalEvent = events.find { it.id == event.id } ?: event
                                    onEventUnassigned(originalEvent)
                                }
                            )
                        }
                    }

                    if (gridPopupInfo != null) {
                        Popup(alignment = Alignment.TopStart, offset = gridPopupInfo!!.tapOffset, onDismissRequest = { gridPopupInfo = null }, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
                            Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isPasteEnabled = (copiedEvent != null)
                                    Icon(painter = painterResource(id = R.drawable.popup_paste), contentDescription = "붙여넣기", tint = if (isPasteEnabled) Color.DarkGray else Color.LightGray, modifier = Modifier.size(24.dp).clickable(enabled = isPasteEnabled) { onEventPaste(gridPopupInfo!!.dayIndex, gridPopupInfo!!.startHour, gridPopupInfo!!.startMinute); gridPopupInfo = null })
                                    Icon(painter = painterResource(id = R.drawable.popup_add), contentDescription = "추가", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onEventAdd(gridPopupInfo!!.dayIndex, gridPopupInfo!!.startHour, gridPopupInfo!!.startMinute); gridPopupInfo = null })
                                }
                            }
                        }
                    }
                }
            }

            val pPtr = dragPointer
            val dOffset = dragTouchOffset
            val isInternalOutside = currentMode == "MOVE" && pPtr != null && dOffset != null && activeEvent != null && (pPtr.y - verticalScrollState.value > gridHeightPx + 600f)

            if (isInternalOutside && activeEvent != null) {
                val localX = pPtr.x - horizontalScrollState.value - dOffset.x
                val localY = pPtr.y - verticalScrollState.value - dOffset.y
                val blockHeightDp = (((activeEvent.endHour * 60 + activeEvent.endMinute) - (activeEvent.startHour * 60 + activeEvent.startMinute)) * (hourHeight.value / 60f)).dp

                Box(modifier = Modifier
                    .offset(x = with(density) { localX.toDp() }, y = with(density) { localY.toDp() })
                    .width(120.dp).height(blockHeightDp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .shadow(12.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(activeEvent.color)
                    .padding(8.dp)
                    .zIndex(100f)
                ) {
                    Column {
                        Text(text = activeEvent.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (activeEvent.description.isNotEmpty()) Text(text = activeEvent.description, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. 개별 이벤트 블록
// ==========================================
@Composable
fun EventBlockItem(
    event: EventData, hourHeightDp: Float, isSelected: Boolean, currentMode: String, isActiveTarget: Boolean, alpha: Float,
    isMagneticSnap: Boolean = true, freeX: Float? = null, freeY: Float? = null,
    isEventSplit: Boolean = false, partSuffix: String = "main", // 🔥 UI 제어용 파라미터 적용
    onLongClick: () -> Unit, onDeselect: () -> Unit, onClick: () -> Unit,
    onCopy: () -> Unit, onMoveStart: () -> Unit, onStretchStart: () -> Unit, onActionCancel: () -> Unit,
    onEventUnassigned: () -> Unit
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val minuteHeightDp = hourHeightDp / 60f

    val gridX = (event.dayIndex * 120).dp
    val gridY = ((event.startHour * 60 + event.startMinute) * minuteHeightDp).dp + (hourHeightDp / 2f).dp
    val calculatedHeight = (((event.endHour * 60 + event.endMinute) - (event.startHour * 60 + event.startMinute)) * minuteHeightDp)
    val safeBlockHeightDp = maxOf(28f, calculatedHeight).dp

    val isMoving = isActiveTarget && freeX != null
    val isStretching = currentMode == "STRETCH" && isActiveTarget

    val targetX = if (isMoving && freeX != null) with(density) { freeX.toDp() } else gridX
    val targetTop = if (isMoving && freeY != null && !isMagneticSnap) with(density) { freeY.toDp() } else gridY
    val targetBottom = targetTop + safeBlockHeightDp

    val animDurationX = if (isMoving) 0 else 300
    val animDurationY = if (isMoving) { if (isMagneticSnap) 200 else 0 } else if (isStretching) 0 else 300

    val animSpecX = if (animDurationX == 0) snap() else tween<Dp>(animDurationX, easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f))
    val animSpecY = if (animDurationY == 0) snap() else tween<Dp>(animDurationY, easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f))

    val animatedX by animateDpAsState(targetX, animSpecX, label = "x")
    val animatedTop by animateDpAsState(targetTop, animSpecY, label = "top")
    val animatedBottom by animateDpAsState(targetBottom, animSpecY, label = "bottom")

    val animatedY = animatedTop
    val animatedHeight = maxOf(28.dp, animatedBottom - animatedTop)

    var tapOffset by remember { mutableStateOf(IntOffset.Zero) }
    var blockXInWindow by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(x = animatedX, y = animatedY)
            .width(120.dp).height(animatedHeight)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .shadow(if (isSelected || isActiveTarget) 12.dp else 0.dp, RoundedCornerShape(8.dp))
            .alpha(alpha)
            .clip(RoundedCornerShape(8.dp))
            .background(event.color)
            .onGloballyPositioned { coordinates -> blockXInWindow = coordinates.positionInWindow().x }
            .pointerInput(event, currentMode) {
                detectTapGestures(
                    onTap = {
                        if (currentMode == "NORMAL") onClick() else onActionCancel()
                    },
                    onLongPress = { offset ->
                        if (currentMode == "NORMAL") {
                            val screenWidthPx = windowInfo.containerSize.width.toFloat()
                            val popupWidthPx = with(density) { 210.dp.toPx() }
                            val touchXInWindow = blockXInWindow + offset.x
                            val idealLeft = touchXInWindow - with(density) { 60.dp.toPx() }
                            val minLeft = with(density) { 16.dp.toPx() }
                            val maxLeft = screenWidthPx - popupWidthPx - with(density) { 16.dp.toPx() }
                            val shiftX = if (idealLeft > maxLeft) maxLeft - idealLeft else if (idealLeft < minLeft) minLeft - idealLeft else 0f

                            val xAdjust = with(density) { (-60).dp.toPx() } + shiftX
                            val yAdjust = with(density) { (-60).dp.toPx() }
                            tapOffset = IntOffset((offset.x + xAdjust).toInt(), (offset.y + yAdjust).toInt())

                            onLongClick()
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text = event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (event.description.isNotEmpty()) Text(text = event.description, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        }

        if (isStretching) {
            val startMins = event.startHour * 60 + event.startMinute
            val endMins = event.endHour * 60 + event.endMinute

            if (endMins > 24 * 60 && !isEventSplit) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("놓아서 다음 날로 확장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White, CircleShape))
                }
            } else if (startMins < 0 && !isEventSplit) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White, CircleShape))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("놓아서 이전 날로 확장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                // 🔥 v0.52b: 분할 블록일 경우 해당되는 핸들만 렌더링
                if (isEventSplit) {
                    if (partSuffix == "part1") {
                        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
                    } else if (partSuffix == "part2") {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
                    }
                } else {
                    Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
                }
            }
        }

        if (isSelected && currentMode == "NORMAL") {
            Popup(alignment = Alignment.TopStart, offset = tapOffset, onDismissRequest = { onDeselect() }, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
                Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.popup_copy), contentDescription = "복사", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onCopy() })
                        if (isEventSplit) {
                            Icon(painter = painterResource(id = R.drawable.popup_move), contentDescription = "이동불가", tint = Color.LightGray, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(painter = painterResource(id = R.drawable.popup_move), contentDescription = "이동", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onMoveStart() })
                        }
                        Icon(painter = painterResource(id = R.drawable.popup_stretch), contentDescription = "수정", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onStretchStart() })
                        Icon(painter = painterResource(id = R.drawable.popup_remove), contentDescription = "삭제", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onEventUnassigned() })
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. 미배정 블록 UI
// ==========================================
@Composable
fun UnassignedEventBlock(
    event: EventData, modifier: Modifier = Modifier, isBeingDragged: Boolean,
    onDragStart: (Offset) -> Unit = {}, onDrag: (Offset) -> Unit = {}, onDragEnd: () -> Unit = {}, onClick: () -> Unit = {}
) {
    val itemWidth by animateDpAsState(targetValue = if (isBeingDragged) 0.dp else 130.dp, animationSpec = tween(300), label = "w")
    val itemPadding by animateDpAsState(targetValue = if (isBeingDragged) 0.dp else 8.dp, animationSpec = tween(300), label = "p")
    val itemAlpha by animateFloatAsState(targetValue = if (isBeingDragged) 0f else 1f, animationSpec = tween(150), label = "a")

    var itemGlobalPos by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier
        .onGloballyPositioned { itemGlobalPos = it.positionInWindow() }
        .width(itemWidth)
        .fillMaxHeight()
        .padding(end = itemPadding)
        .clip(RoundedCornerShape(8.dp))
        .alpha(itemAlpha)
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset -> onDragStart(itemGlobalPos + localOffset) },
                onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount) },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() }
            )
        }
        .clickable { onClick() }
    ) {
        Box(modifier = Modifier.width(122.dp).fillMaxHeight().background(event.color).padding(12.dp)) {
            Column {
                Text(text = event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                if (event.description.isNotEmpty()) Text(text = event.description, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun AddEventButton(onClick: () -> Unit) {
    Box(modifier = Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.3f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text = "+", fontSize = 40.sp, fontWeight = FontWeight.Light, color = Color.DarkGray)
    }
}

// ==========================================
// 6. 일정 편집/생성 다이얼로그 (오버레이)
// ==========================================
@Composable
fun EventEditScreen(initialEvent: EventData, onDismiss: () -> Unit, onSave: (EventData) -> Unit) {
    val descParts = initialEvent.description.split("\n", limit = 2)

    var title by remember(initialEvent) { mutableStateOf(initialEvent.title) }
    var location by remember(initialEvent) { mutableStateOf(descParts.getOrNull(0) ?: "") }
    var memo by remember(initialEvent) { mutableStateOf(descParts.getOrNull(1) ?: "") }
    var color by remember(initialEvent) { mutableStateOf(initialEvent.color) }

    var dayIndex by remember(initialEvent) { mutableStateOf(if (initialEvent.dayIndex == -1) 0 else initialEvent.dayIndex) }
    var startMins by remember(initialEvent) { mutableStateOf(initialEvent.startHour * 60 + initialEvent.startMinute) }
    var durationMins by remember(initialEvent) { mutableStateOf(maxOf(5, (initialEvent.endHour * 60 + initialEvent.endMinute) - startMins)) }

    var errorMessage by remember(initialEvent) { mutableStateOf("") }
    var showColorPicker by remember(initialEvent) { mutableStateOf(false) }

    val days = listOf("27일(토)", "28일(일)", "29일(월)")
    val endMins = startMins + durationMins

    val startH = String.format("%02d", startMins / 60)
    val startM = String.format("%02d", startMins % 60)

    val durH = (durationMins / 60).toString()
    val durM = String.format("%02d", durationMins % 60)

    val displayEndMins = endMins
    val endH = String.format("%02d", displayEndMins / 60)
    val endM = String.format("%02d", displayEndMins % 60)

    val hourOptions = (0..23).map { String.format("%02d", it) }
    val endHourOptions = (0..36).map { String.format("%02d", it) }
    val durHourOptions = (0..36).map { it.toString() }
    val minuteOptions = (0..55 step 5).map { String.format("%02d", it) }

    val colorPalette = listOf(
        Color(0xFF95A5A6), Color(0xFFE74C3C), Color(0xFFE67E22), Color(0xFFF1C40F),
        Color(0xFF2ECC71), Color(0xFF1ABC9C), Color(0xFF3498DB), Color(0xFF9B59B6),
        Color(0xFF34495E), Color(0xFFFF4081), Color(0xFF69F0AE), Color(0xFFBCAAA4)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) { detectTapGestures { } }) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 40.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = title,
                    onValueChange = { if (it.length <= 20) title = it },
                    textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (title.isEmpty()) Text("일정 제목", color = Color.LightGray, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                innerTextField()
                            }
                            Icon(painterResource(id = R.drawable.popup_stretch), contentDescription = "수정", tint = Color.LightGray, modifier = Modifier.size(20.dp).padding(start = 4.dp))
                        }
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(32.dp).background(color, CircleShape).border(1.dp, Color.LightGray, CircleShape).clickable { showColorPicker = true })
            }
            Text(text = "(${title.length}/20)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))

            Spacer(modifier = Modifier.height(32.dp))

            Text("진행 시간", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownSelector(value = days[dayIndex], options = days, onSelect = { dayIndex = days.indexOf(it) }, modifier = Modifier.weight(1.2f))
                Spacer(modifier = Modifier.width(8.dp))
                Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                    DropdownSelector(value = startH, options = hourOptions, onSelect = { h ->
                        startMins = h.toInt() * 60 + (startMins % 60)
                        errorMessage = ""
                    }, modifier = Modifier.weight(1f))
                    Text(":", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold)
                    DropdownSelector(value = startM, options = minuteOptions, onSelect = { m ->
                        startMins = (startMins / 60) * 60 + m.toInt()
                        errorMessage = ""
                    }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("부터", fontSize = 16.sp, modifier = Modifier.width(40.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1.2f))
                Spacer(modifier = Modifier.width(8.dp))
                Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                    DropdownSelector(value = durH, options = durHourOptions, onSelect = { h ->
                        val newDur = h.toInt() * 60 + (durationMins % 60)
                        durationMins = maxOf(5, newDur)
                        errorMessage = ""
                    }, modifier = Modifier.weight(1f))
                    Text(":", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold)
                    DropdownSelector(value = durM, options = minuteOptions, onSelect = { m ->
                        val newDur = (durationMins / 60) * 60 + m.toInt()
                        durationMins = maxOf(5, newDur)
                        errorMessage = ""
                    }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("동안", fontSize = 16.sp, modifier = Modifier.width(40.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1.2f).background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(text = days[dayIndex], fontSize = 16.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                    DropdownSelector(value = endH, options = endHourOptions, onSelect = { h ->
                        val proposed = h.toInt() * 60 + (endMins % 60)
                        if (proposed <= startMins) errorMessage = "종료 시간은 시작 시간보다 늦어야 합니다!"
                        else { durationMins = proposed - startMins; errorMessage = "" }
                    }, modifier = Modifier.weight(1f))
                    Text(":", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold)
                    DropdownSelector(value = endM, options = minuteOptions, onSelect = { m ->
                        val proposed = (endMins / 60) * 60 + m.toInt()
                        if (proposed <= startMins) errorMessage = "종료 시간은 시작 시간보다 늦어야 합니다!"
                        else { durationMins = proposed - startMins; errorMessage = "" }
                    }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("까지", fontSize = 16.sp, modifier = Modifier.width(40.dp))
            }

            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("장소", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = location, onValueChange = { if (it.length <= 20) location = it },
                textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(16.dp),
                decorationBox = { inner -> if (location.isEmpty()) Text("장소를 입력하세요", color = Color.Gray) else inner() }
            )
            Text("(${location.length}/20)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text("메모", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = memo, onValueChange = { if (it.length <= 20) memo = it },
                textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(16.dp),
                decorationBox = { inner -> if (memo.isEmpty()) Text("간단한 메모를 입력하세요", color = Color.Gray) else inner() }
            )
            Text("(${memo.length}/20)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f).background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).clickable { onDismiss() }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("취소", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Box(modifier = Modifier.weight(1f).background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).clickable {
                    val finalDesc = listOf(location.trim(), memo.trim()).filter { it.isNotEmpty() }.joinToString("\n")
                    val clampedEnd = endMins
                    val finalEvent = initialEvent.copy(
                        title = title.ifEmpty { "새 일정" }, description = finalDesc,
                        dayIndex = if(initialEvent.dayIndex == -1) -1 else dayIndex,
                        startHour = startMins / 60, startMinute = startMins % 60,
                        endHour = clampedEnd / 60, endMinute = clampedEnd % 60,
                        color = color
                    )
                    onSave(finalEvent)
                }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("적용", color = Color(0xFF3498DB), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        if (showColorPicker) {
            Dialog(onDismissRequest = { showColorPicker = false }) {
                Box(modifier = Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(24.dp)) {
                    Column {
                        Text("색상 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(colorPalette) { c ->
                                Box(modifier = Modifier.size(48.dp).background(c, CircleShape).border(if (c == color) 3.dp else 0.dp, if (c == color) Color.DarkGray else Color.Transparent, CircleShape).clickable { color = c; showColorPicker = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownSelector(value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).clickable { expanded = true }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text = value, fontSize = 16.sp, color = Color.Black)
        }
        if (expanded) {
            Popup(alignment = Alignment.TopCenter, onDismissRequest = { expanded = false }) {
                Box(modifier = Modifier
                    .width(70.dp)
                    .heightIn(max = 250.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(options) { opt ->
                            Text(text = opt, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onSelect(opt); expanded = false }.padding(12.dp), textAlign = TextAlign.Center)
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}