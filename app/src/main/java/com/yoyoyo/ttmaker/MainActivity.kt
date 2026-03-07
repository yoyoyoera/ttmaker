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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

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
data class OverlapInfo(val newEvents: List<EventData>, val overlappingEvents: List<EventData>)
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
// ==========================================
// 2. 메인 화면
// ==========================================
@Composable
fun YoyoTimetableScreen() {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    var events by remember { mutableStateOf(initialSampleEvents) }
    var unassignedList by remember { mutableStateOf(initialUnassignedEvents) }

    var historyStack by remember { mutableStateOf(listOf<Pair<List<EventData>, List<EventData>>>()) }

    var selectedEvent by remember { mutableStateOf<EventData?>(null) }
    var overlapDialogInfo by remember { mutableStateOf<OverlapInfo?>(null) }

    var selectedEventIds by remember { mutableStateOf(emptySet<String>()) }
    var copiedEventsList by remember { mutableStateOf(emptyList<EventData>()) }

    // 🔥 수정 1: 전체화면 Dialog 대신 좌표 기반 미니 팝업을 띄우기 위한 상태
    var multiSelectPopupOffset by remember { mutableStateOf<IntOffset?>(null) }

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

    fun saveState() {
        historyStack = (historyStack + Pair(events.toList(), unassignedList.toList())).takeLast(30)
    }

    fun undo() {
        if (historyStack.isNotEmpty()) {
            val prevState = historyStack.last()
            historyStack = historyStack.dropLast(1)
            events = prevState.first
            unassignedList = prevState.second
            currentMode = "NORMAL"
            activeEvent = null
            selectedEvent = null
            selectedEventIds = emptySet()
            multiSelectPopupOffset = null
        }
    }

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
                    events = events, selectedEvent = selectedEvent, copiedEvents = copiedEventsList,
                    currentMode = currentMode, activeEvent = activeEvent, selectedEventIds = selectedEventIds,
                    gridGlobalX = gridGlobalX, gridGlobalY = gridGlobalY, gridHeightPx = gridHeightPx,
                    verticalScrollState = verticalScrollState, horizontalScrollState = horizontalScrollState,
                    scale = scale, onScaleChange = { scale = it }, hourHeight = hourHeight,
                    isExternalDragging = isExternalDragging, externalDragEvent = externalDragEvent, externalDragPos = externalDragPos, externalDropSignal = externalDropSignal,
                    onExternalMagneticSnapChange = { isExternalMagneticSnap = it },
                    onMultiSelectChange = { newSelection -> selectedEventIds = newSelection },
                    onShowMultiSelectPopup = { offset -> multiSelectPopupOffset = offset },
                    onExternalDrop = { updatedEvents, droppedId ->
                        saveState()
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
                        saveState()
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
                    onEventCopy = { eventToCopy -> copiedEventsList = listOf(eventToCopy); selectedEvent = null },
                    onEventMoveStart = { eventToMove -> currentMode = if (currentMode == "MULTI_MOVE") "MULTI_MOVE" else "MOVE"; activeEvent = eventToMove; selectedEvent = null },
                    onEventStretchStart = { eventToStretch -> currentMode = "STRETCH"; activeEvent = eventToStretch; selectedEvent = null },
                    onEventActionComplete = { updatedEvents ->
                        saveState()
                        events = updatedEvents
                        if (currentMode == "MOVE" || currentMode == "MULTI_MOVE") {
                            currentMode = "NORMAL"
                            activeEvent = null
                            selectedEventIds = emptySet()
                        } else if (currentMode == "STRETCH") {
                            activeEvent = updatedEvents.find { it.id == activeEvent?.id }
                        }
                    },
                    onEventActionInvalid = { activeEvent = activeEvent?.copy() },
                    onActionCancel = {
                        currentMode = "NORMAL"
                        activeEvent = null
                        selectedEvent = null
                        selectedEventIds = emptySet()
                        multiSelectPopupOffset = null
                    },
                    onEventPaste = { dayIndex, startHour, startMinute ->
                        if (copiedEventsList.isNotEmpty()) {
                            val minOldAbs = copiedEventsList.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
                            val newAnchorAbs = dayIndex * 1440 + startHour * 60 + startMinute
                            val deltaAbs = newAnchorAbs - minOldAbs

                            val pasted = copiedEventsList.map { ev ->
                                val oldStartAbs = ev.dayIndex * 1440 + ev.startHour * 60 + ev.startMinute
                                val oldEndAbs = ev.dayIndex * 1440 + ev.endHour * 60 + ev.endMinute
                                val newStartAbs = oldStartAbs + deltaAbs
                                val dur = oldEndAbs - oldStartAbs
                                ev.copy(
                                    id = UUID.randomUUID().toString(),
                                    dayIndex = newStartAbs / 1440,
                                    startHour = (newStartAbs % 1440) / 60,
                                    startMinute = newStartAbs % 60,
                                    endHour = ((newStartAbs % 1440) + dur) / 60,
                                    endMinute = ((newStartAbs % 1440) + dur) % 60
                                )
                            }

                            // 🔥 수정 3: 다중 붙여넣기 시 모든 블록에 대해 겹침 검사
                            val overlaps = events.filter { old ->
                                pasted.any { newEv ->
                                    val nS = newEv.dayIndex * 1440 + newEv.startHour * 60 + newEv.startMinute
                                    val nE = newEv.dayIndex * 1440 + newEv.endHour * 60 + newEv.endMinute
                                    val oS = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute
                                    val oE = old.dayIndex * 1440 + old.endHour * 60 + old.endMinute
                                    nS < oE && nE > oS
                                }
                            }

                            if (overlaps.isNotEmpty()) {
                                overlapDialogInfo = OverlapInfo(pasted, overlaps)
                            } else {
                                saveState()
                                events = events + pasted
                            }
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
                Icon(painter = painterResource(id = R.drawable.under_undo), contentDescription = "되돌리기", tint = if (historyStack.isNotEmpty()) Color.DarkGray else Color.LightGray, modifier = Modifier.size(22.dp).clickable(enabled = historyStack.isNotEmpty()) { undo() })

                val isSelectMode = currentMode == "SELECT" || currentMode == "MULTI_MOVE"
                Icon(painter = painterResource(id = R.drawable.under_select), contentDescription = "다중 선택", tint = if (isSelectMode) Color(0xFF3498DB) else Color.DarkGray, modifier = Modifier.size(22.dp).clickable {
                    if (currentMode == "NORMAL") { currentMode = "SELECT"; selectedEventIds = emptySet() }
                    else { currentMode = "NORMAL"; selectedEventIds = emptySet(); multiSelectPopupOffset = null; activeEvent = null }
                })

                Icon(painter = painterResource(id = R.drawable.under_ai), contentDescription = "AI 챗봇", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_setting), contentDescription = "설정", modifier = Modifier.size(22.dp))
                Icon(painter = painterResource(id = R.drawable.under_done), contentDescription = "완료", modifier = Modifier.size(22.dp).clickable { currentMode = "NORMAL"; activeEvent = null; selectedEvent = null; selectedEventIds = emptySet(); multiSelectPopupOffset = null })
            }
        }

        AnimatedVisibility(visible = currentMode != "NORMAL" || isExternalDragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp).zIndex(50f)) {
            Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(if(currentMode == "SELECT") Color(0xFF3498DB) else Color(0xEE333333)).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val text = when {
                        currentMode == "SELECT" -> "드래그하여 선택하세요"
                        currentMode == "MULTI_MOVE" -> "잡고 이동하여 전체를 옮기세요"
                        currentMode == "MOVE" || isExternalDragging -> "원하는 요일/시간에 부드럽게 가져다 놓으세요"
                        else -> "상하단 핸들을 잡아 길이를 조절하세요"
                    }
                    Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (currentMode == "STRETCH") {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "완료", color = Color(0xFF3498DB), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.clickable { currentMode = "NORMAL"; activeEvent = null; selectedEvent = null }.padding(4.dp))
                    }
                }
            }
        }

        // 🔥 수정 1: 미니 형태의 다중 선택 컨텍스트 팝업
        if (multiSelectPopupOffset != null && selectedEventIds.isNotEmpty()) {
            Popup(alignment = Alignment.TopStart, offset = multiSelectPopupOffset!!, onDismissRequest = { multiSelectPopupOffset = null; currentMode = "NORMAL"; selectedEventIds = emptySet() }, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
                Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.popup_copy), contentDescription = "복사", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable {
                            copiedEventsList = events.filter { it.id in selectedEventIds }
                            multiSelectPopupOffset = null; currentMode = "NORMAL"; selectedEventIds = emptySet()
                        })
                        Icon(painter = painterResource(id = R.drawable.popup_move), contentDescription = "이동", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable {
                            currentMode = "MULTI_MOVE"; multiSelectPopupOffset = null
                            activeEvent = events.firstOrNull { it.id in selectedEventIds }
                        })
                        Icon(painter = painterResource(id = R.drawable.popup_remove), contentDescription = "삭제", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable {
                            saveState()
                            events = events.filter { it.id !in selectedEventIds }
                            multiSelectPopupOffset = null; currentMode = "NORMAL"; selectedEventIds = emptySet()
                        })
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
                            saveState()
                            unassignedList = unassignedList.filter { it.id != savedEvent.id } + savedEvent
                            events = events.filter { it.id != savedEvent.id }
                            editingEvent = null
                        } else {
                            val newAbsStart = savedEvent.dayIndex * 1440 + savedEvent.startHour * 60 + savedEvent.startMinute
                            val newAbsEnd = savedEvent.dayIndex * 1440 + savedEvent.endHour * 60 + savedEvent.endMinute

                            val overlaps = events.filter { old ->
                                val oldAbsStart = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute
                                val oldAbsEnd = old.dayIndex * 1440 + old.endHour * 60 + old.endMinute
                                old.id != savedEvent.id && newAbsStart < oldAbsEnd && newAbsEnd > oldAbsStart
                            }

                            if (overlaps.isNotEmpty()) {
                                overlapDialogInfo = OverlapInfo(listOf(savedEvent), overlaps)
                                editingEvent = null
                            } else {
                                saveState()
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
                // 🔥 수정 3: 다중 이벤트 덮어쓰기 로직 완벽 반영
                TextButton(onClick = {
                    saveState()
                    val updated = events.toMutableList()
                    updated.removeAll { old -> info.newEvents.any { it.id == old.id } }

                    if (info.newEvents.size == 1) {
                        val newEv = info.newEvents.first()
                        val newAbsStart = newEv.dayIndex * 1440 + newEv.startHour * 60 + newEv.startMinute
                        val newAbsEnd = newEv.dayIndex * 1440 + newEv.endHour * 60 + newEv.endMinute
                        updated.removeAll { old -> info.overlappingEvents.any { it.id == old.id } }

                        info.overlappingEvents.forEach { old ->
                            val oldAbsStart = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute
                            val oldAbsEnd = old.dayIndex * 1440 + old.endHour * 60 + old.endMinute

                            if (oldAbsStart in newAbsStart until newAbsEnd && oldAbsEnd > newAbsEnd) {
                                val relStartMins = newAbsEnd - (old.dayIndex * 1440)
                                updated.add(old.copy(startHour = relStartMins / 60, startMinute = relStartMins % 60, id = UUID.randomUUID().toString()))
                            } else if (oldAbsStart < newAbsStart && oldAbsEnd in (newAbsStart + 1)..newAbsEnd) {
                                val relEndMins = newAbsStart - (old.dayIndex * 1440)
                                updated.add(old.copy(endHour = relEndMins / 60, endMinute = relEndMins % 60, id = UUID.randomUUID().toString()))
                            } else if (oldAbsStart < newAbsStart && oldAbsEnd > newAbsEnd) {
                                val relEndMins = newAbsStart - (old.dayIndex * 1440)
                                val relStartMins = newAbsEnd - (old.dayIndex * 1440)
                                updated.add(old.copy(endHour = relEndMins / 60, endMinute = relEndMins % 60, id = UUID.randomUUID().toString()))
                                updated.add(old.copy(startHour = relStartMins / 60, startMinute = relStartMins % 60, id = UUID.randomUUID().toString()))
                            }
                        }
                    } else {
                        // 다중 붙여넣기 덮어쓰기의 경우, 겹친 기존 블록은 깔끔하게 통째로 삭제
                        updated.removeAll { old -> info.overlappingEvents.any { it.id == old.id } }
                    }

                    updated.addAll(info.newEvents)
                    events = updated
                    unassignedList = unassignedList.filter { old -> info.newEvents.none { it.id == old.id } }
                    overlapDialogInfo = null
                }) { Text("덮어쓰기", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { overlapDialogInfo = null; currentMode = "NORMAL"; activeEvent = null }) { Text("취소", color = Color.Gray) }
                    // 🔥 수정 3: 다중 이벤트 밀어넣기(Push) 로직
                    TextButton(onClick = {
                        saveState()
                        if (info.newEvents.size == 1) {
                            val newEv = info.newEvents.first()
                            val newStart = newEv.startHour * 60 + newEv.startMinute
                            events = finalizeDropWithPush(events, newEv, newStart, newEv.dayIndex)
                        } else {
                            // 다중 붙여넣기 시 붙여넣은 블록들의 전체 범위를 하나로 취급해 밀어냅니다!
                            val minAbs = info.newEvents.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
                            val maxAbs = info.newEvents.maxOf { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
                            val duration = maxAbs - minAbs
                            val movingCenter = minAbs + duration / 2.0f

                            val others = events.filter { old -> info.newEvents.none { it.id == old.id } }
                            val pushUpList = others.filter { old ->
                                val oldCenter = (old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) + ((old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)) / 2.0f
                                oldCenter < movingCenter
                            }.sortedByDescending { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }

                            val pushDownList = others.filter { old ->
                                val oldCenter = (old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) + ((old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)) / 2.0f
                                oldCenter >= movingCenter
                            }.sortedBy { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }

                            var currentUpperBoundary = minAbs
                            val finalizedEvents = mutableListOf<EventData>()
                            for (old in pushUpList) {
                                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                                val sEnd = minOf(old.dayIndex * 1440 + old.endHour * 60 + old.endMinute, currentUpperBoundary)
                                val sStart = maxOf(0, sEnd - oDur)
                                val dIdx = sStart / 1440
                                finalizedEvents.add(old.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60))
                                currentUpperBoundary = sStart
                            }

                            var currentLowerBoundary = maxAbs
                            for (old in pushDownList) {
                                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                                val sStart = maxOf(old.dayIndex * 1440 + old.startHour * 60 + old.startMinute, currentLowerBoundary)
                                val sEnd = minOf(4320, sStart + oDur)
                                val dIdx = sStart / 1440
                                finalizedEvents.add(old.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60))
                                currentLowerBoundary = sEnd
                            }
                            finalizedEvents.addAll(info.newEvents)
                            events = finalizedEvents
                        }

                        unassignedList = unassignedList.filter { old -> info.newEvents.none { it.id == old.id } }
                        overlapDialogInfo = null
                    }) { Text("밀어넣기", color = Color(0xFF2980B9), fontWeight = FontWeight.Bold) }
                }
            }
        )
    }
}

// 🔥 단일 이동: 70% 프리하게 따라오고 30% 자석처럼 착착 붙는 감성 (가로/세로 모두 적용)
fun calculateLivePreview(events: List<EventData>, movingEvent: EventData, pointer: Offset, dragTouchOffset: Offset, hourHeightPx: Float, columnWidthPx: Float, isExternalDrag: Boolean = false, isInsideGrid: Boolean = true): PreviewResult {
    val minuteHeightPx = hourHeightPx / 60f
    val hoverDayIndex = (pointer.x / columnWidthPx).toInt().coerceIn(0, 2)
    val topPaddingPx = hourHeightPx / 2f
    val exactStartMins = ((pointer.y - dragTouchOffset.y - topPaddingPx) / minuteHeightPx).toInt()
    val duration = (movingEvent.endHour * 60 + movingEvent.endMinute) - (movingEvent.startHour * 60 + movingEvent.startMinute)

    val nearestHourMins = (exactStartMins / 60f).roundToInt() * 60
    val isMagneticSnap = abs(exactStartMins - nearestHourMins) <= 18 // Y축 30% 스냅
    val displayStartMins = if (isMagneticSnap) nearestHourMins else exactStartMins

    val clampedStartMins = displayStartMins.coerceIn(0, 1440 - duration)
    val clampedEndMins = clampedStartMins + duration

    // 🔥 핵심: X축(가로)도 정위치 근처(30%)에 가면 자석처럼 들러붙게 null을 반환하여 gridX로 스냅시킴
    val exactX = pointer.x - dragTouchOffset.x
    val targetGridX = hoverDayIndex * columnWidthPx
    val isMagneticSnapX = abs(exactX - targetGridX) < (columnWidthPx * 0.3f)
    val finalFreeX = if (isMagneticSnapX) null else exactX

    val rawFreeY = pointer.y - dragTouchOffset.y
    val minFreeY = topPaddingPx
    val maxFreeY = topPaddingPx + (1440 - duration) * minuteHeightPx
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
            val pushUpList = dayOthers.filter { (it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f) < movingCenter }.sortedByDescending { it.endHour * 60 + it.endMinute }
            val pushDownList = dayOthers.filter { (it.startHour * 60 + it.startMinute + ((it.endHour * 60 + it.endMinute) - (it.startHour * 60 + it.startMinute)) / 2.0f) >= movingCenter }.sortedBy { it.startHour * 60 + it.startMinute }

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

    val previewMoving = movingEvent.copy(startHour = clampedStartMins / 60, startMinute = clampedStartMins % 60, endHour = clampedEndMins / 60, endMinute = clampedEndMins % 60, dayIndex = hoverDayIndex)
    previewDayEvents.add(previewMoving)

    val finalEvents = others.filter { it.dayIndex != hoverDayIndex }.toMutableList()
    finalEvents.addAll(previewDayEvents)
    return PreviewResult(finalEvents, finalFreeX, freeY, isMagneticSnap)
}

// 🔥 다중 이동: 묶음을 하나로 취급하여 밀어넣고 착착 붙는 엔진
fun calculateMultiLivePreview(events: List<EventData>, selectedIds: Set<String>, anchorEvent: EventData, pointer: Offset, dragTouchOffset: Offset, hourHeightPx: Float, columnWidthPx: Float): PreviewResult {
    val minuteHeightPx = hourHeightPx / 60f
    val topPaddingPx = hourHeightPx / 2f
    val exactStartMins = ((pointer.y - dragTouchOffset.y - topPaddingPx) / minuteHeightPx).toInt()
    val hoverDayIndex = (pointer.x / columnWidthPx).toInt().coerceIn(0, 2)

    val nearestHourMins = (exactStartMins / 60f).roundToInt() * 60
    val isMagneticSnap = abs(exactStartMins - nearestHourMins) <= 18
    val displayStartMins = if (isMagneticSnap) nearestHourMins else exactStartMins

    val anchorOldAbs = anchorEvent.dayIndex * 1440 + anchorEvent.startHour * 60 + anchorEvent.startMinute
    val anchorNewAbs = hoverDayIndex * 1440 + displayStartMins
    val deltaAbs = anchorNewAbs - anchorOldAbs

    val selectedEvents = events.filter { it.id in selectedIds }
    val minOldAbs = selectedEvents.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
    val maxOldAbs = selectedEvents.maxOf { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
    val duration = maxOldAbs - minOldAbs

    val clampedMinNewAbs = (minOldAbs + deltaAbs).coerceIn(hoverDayIndex * 1440, hoverDayIndex * 1440 + 1440 - duration)
    val finalDeltaAbs = clampedMinNewAbs - minOldAbs

    val movedGroup = selectedEvents.map { ev ->
        val oldStartAbs = ev.dayIndex * 1440 + ev.startHour * 60 + ev.startMinute
        val oldEndAbs = ev.dayIndex * 1440 + ev.endHour * 60 + ev.endMinute
        val newStartAbs = oldStartAbs + finalDeltaAbs
        val dur = oldEndAbs - oldStartAbs
        ev.copy(dayIndex = newStartAbs / 1440, startHour = (newStartAbs % 1440) / 60, startMinute = newStartAbs % 1440 % 60, endHour = (newStartAbs % 1440 + dur) / 60, endMinute = (newStartAbs % 1440 + dur) % 60)
    }

    val unselectedEvents = events.filter { it.id !in selectedIds }
    val previewEvents = mutableListOf<EventData>()

    if (isMagneticSnap) {
        val groupNewMinAbs = movedGroup.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
        val groupNewMaxAbs = movedGroup.maxOf { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
        val groupCenter = groupNewMinAbs + (groupNewMaxAbs - groupNewMinAbs) / 2f

        val dayOthers = unselectedEvents.filter { it.dayIndex == hoverDayIndex }.sortedBy { it.startHour * 60 + it.startMinute }
        val hasOverlap = dayOthers.any { old -> maxOf(groupNewMinAbs, old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) < minOf(groupNewMaxAbs, old.dayIndex * 1440 + old.endHour * 60 + old.endMinute) }

        val pushedOthers = mutableListOf<EventData>()
        if (!hasOverlap) {
            pushedOthers.addAll(dayOthers)
        } else {
            val pushUpList = dayOthers.filter { old -> val oldCenter = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute + (old.endHour * 60 + old.endMinute - old.startHour * 60 - old.startMinute) / 2f; oldCenter < groupCenter }.sortedByDescending { it.endHour * 60 + it.endMinute }
            val pushDownList = dayOthers.filter { old -> val oldCenter = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute + (old.endHour * 60 + old.endMinute - old.startHour * 60 - old.startMinute) / 2f; oldCenter >= groupCenter }.sortedBy { it.startHour * 60 + it.startMinute }

            var currentUpperBoundary = groupNewMinAbs
            for (old in pushUpList) {
                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                val sEnd = minOf(old.dayIndex * 1440 + old.endHour * 60 + old.endMinute, currentUpperBoundary)
                val sStart = maxOf(hoverDayIndex * 1440, sEnd - oDur)
                pushedOthers.add(old.copy(dayIndex = sStart / 1440, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sStart % 1440 + oDur) / 60, endMinute = (sStart % 1440 + oDur) % 60))
                currentUpperBoundary = sStart
            }

            var currentLowerBoundary = groupNewMaxAbs
            for (old in pushDownList) {
                val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
                val sStart = maxOf(old.dayIndex * 1440 + old.startHour * 60 + old.startMinute, currentLowerBoundary)
                val sEnd = minOf(hoverDayIndex * 1440 + 1440, sStart + oDur)
                pushedOthers.add(old.copy(dayIndex = sStart / 1440, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sStart % 1440 + oDur) / 60, endMinute = (sStart % 1440 + oDur) % 60))
                currentLowerBoundary = sStart + oDur
            }
        }
        previewEvents.addAll(unselectedEvents.filter { it.dayIndex != hoverDayIndex })
        previewEvents.addAll(pushedOthers)
    } else {
        previewEvents.addAll(unselectedEvents)
    }

    previewEvents.addAll(movedGroup)

    val exactX = pointer.x - dragTouchOffset.x
    val targetGridX = hoverDayIndex * columnWidthPx
    val isMagneticSnapX = abs(exactX - targetGridX) < (columnWidthPx * 0.3f)
    val finalFreeX = if (isMagneticSnapX) null else exactX

    return PreviewResult(previewEvents, finalFreeX, pointer.y - dragTouchOffset.y, isMagneticSnap)
}

// 🔥 다중 이동 손 뗐을 때 밀어내며 확정 짓는 기능
fun finalizeMultiDropWithPush(events: List<EventData>, selectedIds: Set<String>, anchorEvent: EventData, exactPointerMins: Int, hoverDayIndex: Int): List<EventData> {
    val selectedEvents = events.filter { it.id in selectedIds }
    val minOldAbs = selectedEvents.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
    val maxOldAbs = selectedEvents.maxOf { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
    val duration = maxOldAbs - minOldAbs

    val anchorOldAbs = anchorEvent.dayIndex * 1440 + anchorEvent.startHour * 60 + anchorEvent.startMinute
    val nearestHourMins = (exactPointerMins / 60f).roundToInt() * 60
    val isMagneticSnap = abs(exactPointerMins - nearestHourMins) <= 18
    val finalStartMins = if (isMagneticSnap) nearestHourMins else exactPointerMins

    val deltaAbs = (hoverDayIndex * 1440 + finalStartMins) - anchorOldAbs
    val finalDeltaAbs = (minOldAbs + deltaAbs).coerceIn(hoverDayIndex * 1440, hoverDayIndex * 1440 + 1440 - duration) - minOldAbs

    val movedGroup = selectedEvents.map { ev ->
        val oldStartAbs = ev.dayIndex * 1440 + ev.startHour * 60 + ev.startMinute
        val oldEndAbs = ev.dayIndex * 1440 + ev.endHour * 60 + ev.endMinute
        val newStartAbs = oldStartAbs + finalDeltaAbs
        val dur = oldEndAbs - oldStartAbs
        ev.copy(dayIndex = newStartAbs / 1440, startHour = (newStartAbs % 1440) / 60, startMinute = newStartAbs % 1440 % 60, endHour = (newStartAbs % 1440 + dur) / 60, endMinute = (newStartAbs % 1440 + dur) % 60)
    }

    val unselectedEvents = events.filter { it.id !in selectedIds }
    val groupNewMinAbs = movedGroup.minOf { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
    val groupNewMaxAbs = movedGroup.maxOf { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
    val groupCenter = groupNewMinAbs + (groupNewMaxAbs - groupNewMinAbs) / 2f

    val dayOthers = unselectedEvents.filter { it.dayIndex == hoverDayIndex }.sortedBy { it.startHour * 60 + it.startMinute }
    val hasOverlap = dayOthers.any { old -> maxOf(groupNewMinAbs, old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) < minOf(groupNewMaxAbs, old.dayIndex * 1440 + old.endHour * 60 + old.endMinute) }

    val finalizedEvents = mutableListOf<EventData>()
    finalizedEvents.addAll(unselectedEvents.filter { it.dayIndex != hoverDayIndex })

    if (!hasOverlap) {
        finalizedEvents.addAll(dayOthers)
    } else {
        val pushUpList = dayOthers.filter { old -> val oldCenter = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute + (old.endHour * 60 + old.endMinute - old.startHour * 60 - old.startMinute) / 2f; oldCenter < groupCenter }.sortedByDescending { it.endHour * 60 + it.endMinute }
        val pushDownList = dayOthers.filter { old -> val oldCenter = old.dayIndex * 1440 + old.startHour * 60 + old.startMinute + (old.endHour * 60 + old.endMinute - old.startHour * 60 - old.startMinute) / 2f; oldCenter >= groupCenter }.sortedBy { it.startHour * 60 + it.startMinute }

        var currentUpperBoundary = groupNewMinAbs
        for (old in pushUpList) {
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sEnd = minOf(old.dayIndex * 1440 + old.endHour * 60 + old.endMinute, currentUpperBoundary)
            val sStart = maxOf(hoverDayIndex * 1440, sEnd - oDur)
            finalizedEvents.add(old.copy(dayIndex = sStart / 1440, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sStart % 1440 + oDur) / 60, endMinute = (sStart % 1440 + oDur) % 60))
            currentUpperBoundary = sStart
        }

        var currentLowerBoundary = groupNewMaxAbs
        for (old in pushDownList) {
            val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)
            val sStart = maxOf(old.dayIndex * 1440 + old.startHour * 60 + old.startMinute, currentLowerBoundary)
            val sEnd = minOf(hoverDayIndex * 1440 + 1440, sStart + oDur)
            finalizedEvents.add(old.copy(dayIndex = sStart / 1440, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sStart % 1440 + oDur) / 60, endMinute = (sStart % 1440 + oDur) % 60))
            currentLowerBoundary = sStart + oDur
        }
    }
    finalizedEvents.addAll(movedGroup)
    return finalizedEvents
}

fun calculateStretchPreview(
    events: List<EventData>, activeEvent: EventData, pointerY: Float, stretchType: String, hourHeightPx: Float, dragHitPart: Int = 3, isFinalDrop: Boolean = false
): PreviewResult {
    val minuteHeightPx = hourHeightPx / 60f
    val topPaddingPx = hourHeightPx / 2f
    val pointerMins = ((pointerY - topPaddingPx) / minuteHeightPx).toInt()
    val snappedMins = ((pointerMins / snapIntervalMins.toFloat()).roundToInt() * snapIntervalMins)

    val oldStartRel = activeEvent.startHour * 60 + activeEvent.startMinute
    val oldEndRel = activeEvent.endHour * 60 + activeEvent.endMinute
    val isOriginalSplit = (oldEndRel > 1440) || (oldStartRel < 0)

    val activeAbsStart = activeEvent.dayIndex * 1440 + oldStartRel
    val activeAbsEnd = activeEvent.dayIndex * 1440 + oldEndRel
    val snappedAbsMins = activeEvent.dayIndex * 1440 + snappedMins
    val pointerAbsMins = activeEvent.dayIndex * 1440 + pointerMins

    val others = events.filter { it.id != activeEvent.id }.map { old ->
        Triple(old, old.dayIndex * 1440 + old.startHour * 60 + old.startMinute, old.dayIndex * 1440 + old.endHour * 60 + old.endMinute)
    }

    val previewEvents = mutableListOf<EventData>()
    val pushedIds = mutableSetOf<String>()
    var newAbsStart = activeAbsStart
    var newAbsEnd = activeAbsEnd

    if (stretchType == "TOP") {
        val blocksAbove = others.filter { it.third <= activeAbsStart }.sortedByDescending { it.third }
        val limitStart = blocksAbove.sumOf { it.third - it.second }
        val maxStartAllowed = if (oldEndRel > 1440 && dragHitPart == 1) activeEvent.dayIndex * 1440 + 1440 else if (oldStartRel < 0 && dragHitPart == 1) activeEvent.dayIndex * 1440 else activeAbsEnd - snapIntervalMins

        if (pointerMins < 0 && activeEvent.dayIndex > 0 && !isOriginalSplit) {
            if (isFinalDrop) newAbsStart = maxOf(limitStart, activeEvent.dayIndex * 1440 - 60)
            else newAbsStart = maxOf(limitStart, maxOf(activeEvent.dayIndex * 1440 - 30, pointerAbsMins))
        } else {
            newAbsStart = maxOf(limitStart, minOf(snappedAbsMins.coerceAtLeast(0), maxStartAllowed))
        }

        blocksAbove.fold(newAbsStart) { boundary, (oldEvent, oStart, oEnd) ->
            val sEnd = minOf(oEnd, boundary)
            val oDur = oEnd - oStart
            val sStart = maxOf(0, sEnd - oDur)
            val dIdx = sStart / 1440
            previewEvents.add(oldEvent.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60))
            pushedIds.add(oldEvent.id)
            sStart
        }
    } else {
        val blocksBelow = others.filter { it.second >= activeAbsEnd }.sortedBy { it.second }
        val limitEnd = 4320 - blocksBelow.sumOf { it.third - it.second }
        val minEndAllowed = if (oldEndRel > 1440 && dragHitPart == 2) activeEvent.dayIndex * 1440 + 1440 else if (oldStartRel < 0 && dragHitPart == 2) activeEvent.dayIndex * 1440 else activeAbsStart + snapIntervalMins

        if (pointerMins > 1440 && activeEvent.dayIndex < 2 && !isOriginalSplit) {
            if (isFinalDrop) newAbsEnd = minOf(limitEnd, activeEvent.dayIndex * 1440 + 1500)
            else newAbsEnd = minOf(limitEnd, minOf(pointerAbsMins, activeEvent.dayIndex * 1440 + 1470))
        } else {
            newAbsEnd = minOf(limitEnd, maxOf(snappedAbsMins.coerceAtMost(4320), minEndAllowed))
        }

        blocksBelow.fold(newAbsEnd) { boundary, (oldEvent, oStart, oEnd) ->
            val sStart = maxOf(oStart, boundary)
            val oDur = oEnd - oStart
            val sEnd = minOf(4320, sStart + oDur)
            val dIdx = sStart / 1440
            previewEvents.add(oldEvent.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60))
            pushedIds.add(oldEvent.id)
            sEnd
        }
    }

    others.filter { !pushedIds.contains(it.first.id) }.forEach { previewEvents.add(it.first) }

    val newRelStart = newAbsStart - (activeEvent.dayIndex * 1440)
    val newRelEnd = newAbsEnd - (activeEvent.dayIndex * 1440)
    previewEvents.add(activeEvent.copy(startHour = newRelStart / 60, startMinute = newRelStart % 60, endHour = newRelEnd / 60, endMinute = newRelEnd % 60))

    return PreviewResult(previewEvents, null, null, true)
}

fun finalizeDropWithPush(events: List<EventData>, movingEvent: EventData, finalStartMins: Int, dayIndex: Int): List<EventData> {
    val absStart = dayIndex * 1440 + finalStartMins
    val duration = (movingEvent.endHour * 60 + movingEvent.endMinute) - (movingEvent.startHour * 60 + movingEvent.startMinute)
    val absEnd = absStart + duration
    val movingCenter = absStart + duration / 2.0f
    val others = events.filter { it.id != movingEvent.id }
    val hasOverlap = others.any { old -> maxOf(absStart, old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) < minOf(absEnd, old.dayIndex * 1440 + old.endHour * 60 + old.endMinute) }
    val finalizedEvents = mutableListOf<EventData>()

    if (!hasOverlap) { finalizedEvents.addAll(others) } else {
        val pushUpList = others.filter { old -> val oldCenter = (old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) + ((old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)) / 2.0f; oldCenter < movingCenter }.sortedByDescending { it.dayIndex * 1440 + it.endHour * 60 + it.endMinute }
        val pushDownList = others.filter { old -> val oldCenter = (old.dayIndex * 1440 + old.startHour * 60 + old.startMinute) + ((old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute)) / 2.0f; oldCenter >= movingCenter }.sortedBy { it.dayIndex * 1440 + it.startHour * 60 + it.startMinute }
        var currentUpperBoundary = absStart
        for (old in pushUpList) { val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute); val sEnd = minOf(old.dayIndex * 1440 + old.endHour * 60 + old.endMinute, currentUpperBoundary); val sStart = maxOf(0, sEnd - oDur); val dIdx = sStart / 1440; finalizedEvents.add(old.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60)); currentUpperBoundary = sStart }
        var currentLowerBoundary = absEnd
        for (old in pushDownList) { val oDur = (old.endHour * 60 + old.endMinute) - (old.startHour * 60 + old.startMinute); val sStart = maxOf(old.dayIndex * 1440 + old.startHour * 60 + old.startMinute, currentLowerBoundary); val sEnd = minOf(4320, sStart + oDur); val dIdx = sStart / 1440; finalizedEvents.add(old.copy(dayIndex = dIdx, startHour = (sStart % 1440) / 60, startMinute = sStart % 60, endHour = (sEnd - dIdx * 1440) / 60, endMinute = sEnd % 60)); currentLowerBoundary = sEnd }
    }
    finalizedEvents.add(movingEvent.copy(dayIndex = absStart / 1440, startHour = (absStart % 1440) / 60, startMinute = absStart % 60, endHour = (absEnd - (absStart / 1440) * 1440) / 60, endMinute = absEnd % 60))
    return finalizedEvents
}

// ==========================================
// 3. 시간표 그리드
// ==========================================
@Composable
fun TimetableGrid(
    events: List<EventData>, selectedEvent: EventData?, copiedEvents: List<EventData>,
    currentMode: String, activeEvent: EventData?, selectedEventIds: Set<String>, gridGlobalX: Float, gridGlobalY: Float, gridHeightPx: Float,
    verticalScrollState: ScrollState, horizontalScrollState: ScrollState,
    scale: Float, onScaleChange: (Float) -> Unit, hourHeight: Dp,
    isExternalDragging: Boolean, externalDragEvent: EventData?, externalDragPos: Offset?, externalDropSignal: Int,
    onExternalMagneticSnapChange: (Boolean) -> Unit, onExternalDrop: (List<EventData>, String) -> Unit, onExternalCancel: () -> Unit,
    onMultiSelectChange: (Set<String>) -> Unit, onShowMultiSelectPopup: (IntOffset) -> Unit,
    onEventUnassigned: (EventData) -> Unit, onEventLongClick: (EventData) -> Unit, onEventDeselect: () -> Unit,
    onEventClick: (EventData) -> Unit, onEventCopy: (EventData) -> Unit, onEventMoveStart: (EventData) -> Unit, onEventStretchStart: (EventData) -> Unit,
    onEventActionComplete: (List<EventData>) -> Unit, onEventActionInvalid: () -> Unit, onActionCancel: () -> Unit,
    onEventPaste: (Int, Int, Int) -> Unit, onEventAdd: (Int, Int, Int) -> Unit
) {
    val days = listOf("27(토)", "28(일)", "29(월)")
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var gridPopupInfo by remember { mutableStateOf<GridPopupInfo?>(null) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var dragTouchOffset by remember { mutableStateOf<Offset?>(null) }
    var stretchType by remember { mutableStateOf<String?>(null) }
    var selectedPartSuffix by remember { mutableStateOf<String?>(null) }
    var dragHitPart by remember { mutableIntStateOf(0) }

    var selectStart by remember { mutableStateOf<Offset?>(null) }
    var selectCurrent by remember { mutableStateOf<Offset?>(null) }

    val previewResult = remember(events, currentMode, activeEvent, dragPointer, dragTouchOffset, stretchType, isExternalDragging, externalDragEvent, externalDragPos, verticalScrollState.value, horizontalScrollState.value, gridGlobalX, gridGlobalY, gridHeightPx, dragHitPart, hourHeight.value) {
        val pPtr = dragPointer; val dOffset = dragTouchOffset; val sType = stretchType; val ePos = externalDragPos
        val hHPx = with(density) { hourHeight.toPx() }; val wPx = with(density) { 120.dp.toPx() }

        if (currentMode == "MULTI_MOVE" && activeEvent != null && pPtr != null && dOffset != null) {
            calculateMultiLivePreview(events, selectedEventIds, activeEvent, pPtr, dOffset, hHPx, wPx)
        } else if (currentMode == "MOVE" && activeEvent != null && pPtr != null && dOffset != null) {
            if (pPtr.y - verticalScrollState.value > gridHeightPx + 600f) PreviewResult(events.filter { it.id != activeEvent.id }, null, null, false)
            else calculateLivePreview(events, activeEvent, pPtr, dOffset, hHPx, wPx, isExternalDrag = false, isInsideGrid = true)
        } else if (currentMode == "STRETCH" && activeEvent != null && pPtr != null && sType != null) {
            val isSplit = activeEvent.endHour * 60 + activeEvent.endMinute > 1440
            val mappedY = if (isSplit && dragHitPart == 2) pPtr.y + 1440 * (hHPx / 60f) else pPtr.y
            calculateStretchPreview(events, activeEvent, mappedY, sType, hHPx, dragHitPart)
        } else if (isExternalDragging && externalDragEvent != null && ePos != null) {
            val isInsideGrid = (ePos.y - gridGlobalY) in -50f..(gridHeightPx + 50f)
            val localX = ePos.x - gridGlobalX + horizontalScrollState.value
            val localY = ePos.y - gridGlobalY + verticalScrollState.value
            calculateLivePreview(events, externalDragEvent, Offset(localX, localY), Offset(wPx / 2f, hHPx / 2f), hHPx, wPx, isExternalDrag = true, isInsideGrid = isInsideGrid)
        } else null
    }

    val displayEvents = if ((currentMode != "NORMAL" || isExternalDragging) && previewResult != null && currentMode != "SELECT") previewResult.events else events

    LaunchedEffect(previewResult) { if (isExternalDragging) onExternalMagneticSnapChange(previewResult?.isMagneticSnap == true) }

    LaunchedEffect(currentMode, isExternalDragging, dragPointer, externalDragPos) {
        while (currentMode != "NORMAL" || isExternalDragging) {
            val visualY = if (isExternalDragging) externalDragPos?.let { it.y - gridGlobalY } else if (currentMode != "NORMAL" && currentMode != "SELECT") dragPointer?.let { it.y - verticalScrollState.value } else null
            if (visualY != null && gridHeightPx > 0f) {
                var scrollDelta = 0f
                if (visualY > 0f && visualY < 100f && verticalScrollState.value > 0) scrollDelta = -35f
                else if (visualY > gridHeightPx - 100f && verticalScrollState.value < verticalScrollState.maxValue) scrollDelta = 35f
                if (scrollDelta != 0f) { verticalScrollState.dispatchRawDelta(scrollDelta); if (currentMode != "NORMAL" && dragPointer != null) dragPointer = dragPointer!!.copy(y = dragPointer!!.y + scrollDelta) }
            }
            delay(16)
        }
    }

    LaunchedEffect(externalDropSignal) {
        if (externalDropSignal > 0 && isExternalDragging && externalDragEvent != null && externalDragPos != null) {
            if ((externalDragPos.y - gridGlobalY) in -50f..(gridHeightPx + 50f)) {
                val hHPx = with(density) { hourHeight.toPx() }; val wPx = with(density) { 120.dp.toPx() }
                val localX = externalDragPos.x - gridGlobalX + horizontalScrollState.value; val localY = externalDragPos.y - gridGlobalY + verticalScrollState.value
                val dayIndex = (localX / wPx).toInt().coerceIn(0, 2); val exactStartMins = ((localY - hHPx / 2f) / (hHPx / 60f)).toInt()
                val finalStartMins = (exactStartMins / 60f).roundToInt() * 60
                val duration = (externalDragEvent.endHour * 60 + externalDragEvent.endMinute) - (externalDragEvent.startHour * 60 + externalDragEvent.startMinute)
                val clampedStartMins = finalStartMins.coerceIn(0, 1440 - duration)
                onExternalDrop(finalizeDropWithPush(events, externalDragEvent, clampedStartMins, dayIndex), externalDragEvent.id)
            } else onExternalCancel()
            onExternalMagneticSnapChange(false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp).horizontalScroll(horizontalScrollState)) {
            days.forEach { day -> Box(modifier = Modifier.width(120.dp).padding(horizontal = 4.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) { Text(text = day, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) } }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> onScaleChange((scale * (1f + (zoom - 1f) * 2.5f)).coerceIn(0.3f, 5f)) } }.verticalScroll(verticalScrollState)) {
                Column(modifier = Modifier.width(40.dp)) { Spacer(modifier = Modifier.height(hourHeight / 2f)); (0..24).toList().forEach { hour -> Text(text = "$hour", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(hourHeight).padding(top = 8.dp)) } }

                Box(modifier = Modifier.width(120.dp * 3).height(hourHeight * 25.5f).horizontalScroll(horizontalScrollState)
                    .pointerInput(currentMode) {
                        if (currentMode == "SELECT") {
                            var localSelection = emptySet<String>()

                            detectDragGestures(
                                onDragStart = { offset ->
                                    selectStart = offset
                                    selectCurrent = offset
                                    localSelection = emptySet()
                                    onMultiSelectChange(localSelection)
                                    gridPopupInfo = null
                                },
                                onDrag = { change, _ ->
                                    if (selectStart == null) return@detectDragGestures
                                    selectCurrent = change.position
                                    val left = minOf(selectStart!!.x, selectCurrent!!.x)
                                    val right = maxOf(selectStart!!.x, selectCurrent!!.x)
                                    val top = minOf(selectStart!!.y, selectCurrent!!.y)
                                    val bottom = maxOf(selectStart!!.y, selectCurrent!!.y)

                                    val hHPx = with(density){hourHeight.toPx()}; val wPx = with(density){120.dp.toPx()}; val tp = hHPx / 2f

                                    localSelection = events.filter { ev ->
                                        val eL = ev.dayIndex * wPx; val eR = eL + wPx
                                        val eT = (ev.startHour * 60 + ev.startMinute) * (hHPx / 60f) + tp
                                        val eB = (ev.endHour * 60 + ev.endMinute) * (hHPx / 60f) + tp
                                        left < eR && right > eL && top < eB && bottom > eT
                                    }.map { it.id }.toSet()

                                    onMultiSelectChange(localSelection)
                                },
                                onDragEnd = {
                                    if (localSelection.isNotEmpty() && selectCurrent != null) {
                                        // 🔥 수정 3: 손을 뗐을 때 요일 검사 및 토스트 경고, 그리고 모드 유지
                                        val selectedDays = events.filter { it.id in localSelection }.map { it.dayIndex }.distinct()
                                        if (selectedDays.size > 1) {
                                            Toast.makeText(context, "하나의 요일 내에서의 이벤트만 선택하세요", Toast.LENGTH_SHORT).show()
                                            onMultiSelectChange(emptySet()) // 선택 박스만 초기화
                                        } else {
                                            val globalX = gridGlobalX - horizontalScrollState.value + selectCurrent!!.x
                                            val globalY = gridGlobalY - verticalScrollState.value + selectCurrent!!.y

                                            val popupWidthPx = with(density) { 160.dp.toPx() }
                                            val popupHeightPx = with(density) { 60.dp.toPx() }
                                            val screenWidth = windowInfo.containerSize.width.toFloat()
                                            val screenHeight = windowInfo.containerSize.height.toFloat()

                                            var finalX = globalX - (popupWidthPx / 2f)
                                            var finalY = globalY - popupHeightPx - with(density) { 20.dp.toPx() }

                                            if (finalX + popupWidthPx > screenWidth) finalX = screenWidth - popupWidthPx - with(density) { 16.dp.toPx() }
                                            if (finalX < with(density) { 16.dp.toPx() }) finalX = with(density) { 16.dp.toPx() }.toFloat()
                                            if (finalY < with(density) { 80.dp.toPx() }) finalY = globalY + with(density) { 20.dp.toPx() }
                                            if (finalY + popupHeightPx > screenHeight) finalY = screenHeight - popupHeightPx - with(density) { 16.dp.toPx() }

                                            onShowMultiSelectPopup(IntOffset(finalX.toInt(), finalY.toInt()))
                                        }
                                    } else {
                                        onActionCancel()
                                    }
                                    selectStart = null; selectCurrent = null
                                },
                                onDragCancel = { onActionCancel(); selectStart = null; selectCurrent = null }
                            )
                        }
                    }
                    .pointerInput(currentMode, activeEvent, selectedEventIds) {
                        if (currentMode == "MULTI_MOVE" || (currentMode != "NORMAL" && currentMode != "SELECT" && activeEvent != null)) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val wPx = with(density) { 120.dp.toPx() }; val hPxPerMin = with(density) { hourHeight.toPx() / 60f }; val topPaddingPx = with(density) { (hourHeight.value / 2f).dp.toPx() }
                                    var targetEv = activeEvent; var hitPart = 0

                                    if (currentMode == "MULTI_MOVE") {
                                        val hit = events.find { ev ->
                                            if (ev.id !in selectedEventIds) return@find false
                                            val sMins = ev.startHour * 60 + ev.startMinute; val eMins = ev.endHour * 60 + ev.endMinute
                                            val left = ev.dayIndex * wPx; val top = sMins * hPxPerMin + topPaddingPx; val bottom = minOf(eMins, 1440) * hPxPerMin + topPaddingPx
                                            if (offset.x in left..(left+wPx) && offset.y in top..bottom) return@find true
                                            if (eMins > 1440 && ev.dayIndex < 2) {
                                                val left2 = (ev.dayIndex + 1) * wPx; val top2 = topPaddingPx; val bottom2 = (eMins - 1440) * hPxPerMin + topPaddingPx
                                                if (offset.x in left2..(left2+wPx) && offset.y in top2..bottom2) return@find true
                                            }
                                            false
                                        }
                                        if (hit != null) { targetEv = hit; hitPart = 3; onEventMoveStart(hit) }
                                    } else {
                                        val startMins = targetEv!!.startHour * 60 + targetEv.startMinute; val endMins = targetEv.endHour * 60 + targetEv.endMinute
                                        val isSplit = endMins > 1440
                                        val left1 = targetEv.dayIndex * wPx; val top1 = startMins * hPxPerMin + topPaddingPx; val bottom1 = minOf(endMins, 1440) * hPxPerMin + topPaddingPx
                                        val hit1 = offset.x in (left1 - 40f)..(left1 + wPx + 40f) && offset.y in (top1 - 40f)..(bottom1 + 40f)
                                        var hit2 = false; var top2 = 0f; var bottom2 = 0f
                                        if (isSplit && targetEv.dayIndex < 2) {
                                            val left2 = (targetEv.dayIndex + 1) * wPx; top2 = 0f * hPxPerMin + topPaddingPx; bottom2 = (endMins - 1440) * hPxPerMin + topPaddingPx
                                            hit2 = offset.x in (left2 - 40f)..(left2 + wPx + 40f) && offset.y in (top2 - 40f)..(bottom2 + 40f)
                                        }
                                        if (hit1 || hit2) hitPart = if (isSplit) (if (hit2 && !hit1) 2 else if (hit1 && !hit2) 1 else (if (minOf(abs(offset.y - top1), abs(offset.y - bottom1)) > minOf(abs(offset.y - top2), abs(offset.y - bottom2))) 2 else 1)) else 3
                                    }

                                    if (targetEv != null && hitPart != 0) {
                                        dragHitPart = hitPart; dragPointer = offset
                                        val sMins = targetEv.startHour * 60 + targetEv.startMinute
                                        dragTouchOffset = Offset(offset.x - (targetEv.dayIndex * wPx), offset.y - (sMins * hPxPerMin + topPaddingPx))
                                        if (currentMode == "STRETCH") stretchType = if (targetEv.endHour*60+targetEv.endMinute > 1440) (if (dragHitPart == 1) "TOP" else "BOTTOM") else (if (offset.y < (sMins * hPxPerMin + topPaddingPx) + ((targetEv.endHour*60+targetEv.endMinute - sMins)*hPxPerMin)/2f) "TOP" else "BOTTOM")
                                    } else onEventActionInvalid()
                                },
                                onDrag = { _, dragAmount -> if (dragHitPart != 0) dragPointer?.let { dragPointer = it + dragAmount } },
                                onDragEnd = {
                                    if (dragHitPart != 0 && dragPointer != null && activeEvent != null) {
                                        val hHPx = with(density) { hourHeight.toPx() }; val cWPx = with(density) { 120.dp.toPx() }

                                        // 🔥 수정 2: 다중 선택 이동 시 손 놓았을 때의 그룹 중심 밀어내기 확정 적용
                                        if (currentMode == "MULTI_MOVE" && dragTouchOffset != null) {
                                            val finalHoverDayIndex = (dragPointer!!.x / cWPx).toInt().coerceIn(0, 2)
                                            val exactStartMins = ((dragPointer!!.y - dragTouchOffset!!.y - hHPx / 2f) / (hHPx / 60f)).toInt()
                                            onEventActionComplete(finalizeMultiDropWithPush(events, selectedEventIds, activeEvent, exactStartMins, finalHoverDayIndex))
                                        } else if (currentMode == "MOVE" && dragTouchOffset != null) {
                                            if (dragPointer!!.y - verticalScrollState.value > gridHeightPx + 600f) onEventUnassigned(activeEvent)
                                            else {
                                                val finalHoverDayIndex = (dragPointer!!.x / cWPx).toInt().coerceIn(0, 2)
                                                val exactStartMins = ((dragPointer!!.y - dragTouchOffset!!.y - hHPx / 2f) / (hHPx / 60f)).toInt()
                                                val duration = (activeEvent.endHour * 60 + activeEvent.endMinute) - (activeEvent.startHour * 60 + activeEvent.startMinute)
                                                onEventActionComplete(finalizeDropWithPush(events, activeEvent, (exactStartMins / 60f).roundToInt() * 60.coerceIn(0, 1440 - duration), finalHoverDayIndex))
                                            }
                                        } else if (currentMode == "STRETCH" && stretchType != null) {
                                            val mappedY = if (activeEvent.endHour * 60 + activeEvent.endMinute > 1440 && dragHitPart == 2) dragPointer!!.y + 1440 * (hHPx / 60f) else dragPointer!!.y
                                            val res = calculateStretchPreview(events, activeEvent, mappedY, stretchType!!, hHPx, dragHitPart, isFinalDrop = true)
                                            val oldEnd = activeEvent.endHour * 60 + activeEvent.endMinute
                                            val newEnd = res.events.find { it.id == activeEvent.id }?.let { it.endHour * 60 + it.endMinute } ?: oldEnd
                                            val oldStart = activeEvent.startHour * 60 + activeEvent.startMinute
                                            val newStart = res.events.find { it.id == activeEvent.id }?.let { it.startHour * 60 + it.startMinute } ?: oldStart

                                            if (oldEnd <= 1440 && newEnd > 1440) coroutineScope.launch { verticalScrollState.animateScrollTo(0, tween(400)) }
                                            else if (oldStart >= 0 && newStart < 0) coroutineScope.launch { verticalScrollState.animateScrollTo(verticalScrollState.maxValue, tween(400)) }
                                            onEventActionComplete(res.events)
                                        }
                                    } else onEventActionInvalid()
                                    dragPointer = null; dragTouchOffset = null; stretchType = null; dragHitPart = 0
                                },
                                onDragCancel = { onEventActionInvalid(); dragPointer = null; dragTouchOffset = null; stretchType = null; dragHitPart = 0 }
                            )
                        }
                    }
                    .pointerInput(hourHeight, currentMode) {
                        detectTapGestures(
                            onTap = { if (currentMode != "NORMAL" && currentMode != "SELECT") onActionCancel() else { gridPopupInfo = null; selectedPartSuffix = null } },
                            onLongPress = { offset ->
                                if (currentMode == "NORMAL") {
                                    onEventDeselect(); selectedPartSuffix = null
                                    val dIdx = (offset.x / with(density) { 120.dp.toPx() }).toInt().coerceIn(0, 2)
                                    val h = ((offset.y - with(density){hourHeight.toPx() / 2f}) / (with(density) { hourHeight.toPx() } / 60f)).toInt() / 60
                                    val maxLeft = windowInfo.containerSize.width.toFloat() - with(density) { 140.dp.toPx() + 16.dp.toPx() }
                                    val idealLeft = gridGlobalX - horizontalScrollState.value + offset.x - with(density) { 40.dp.toPx() }
                                    val shiftX = if (idealLeft > maxLeft) maxLeft - idealLeft else 0f
                                    gridPopupInfo = GridPopupInfo(IntOffset((offset.x - with(density){40.dp.toPx()} + shiftX).toInt(), (offset.y - with(density){60.dp.toPx()}).toInt()), dIdx, h.coerceIn(0, 23), 0)
                                }
                            }
                        )
                    }
                ) {
                    Row { days.forEachIndexed { _, _ -> Box(modifier = Modifier.width(120.dp).fillMaxHeight()) { Column { Spacer(modifier = Modifier.height(hourHeight / 2f)); (0..24).toList().forEach { _ -> Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) { HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp) } } } } } }

                    val visuallySplitEvents = displayEvents.flatMap { event ->
                        val startMins = event.startHour * 60 + event.startMinute; val endMins = event.endHour * 60 + event.endMinute
                        val originalEvent = events.find { it.id == event.id }
                        val isOriginalSplit = originalEvent != null && (originalEvent.endHour * 60 + originalEvent.endMinute > 1440 || originalEvent.startHour * 60 + originalEvent.startMinute < 0)

                        val isStretchingBottom = currentMode == "STRETCH" && stretchType == "BOTTOM" && event.id == activeEvent?.id && !isOriginalSplit
                        val isStretchingTop = currentMode == "STRETCH" && stretchType == "TOP" && event.id == activeEvent?.id && !isOriginalSplit

                        val startsNextDay = startMins >= 1440; val endsNextDay = endMins > 1440 && !isStretchingBottom; val startsPrevDay = startMins < 0 && !isStretchingTop

                        if (startsNextDay) listOf(Pair(event.copy(dayIndex = minOf(event.dayIndex + 1, 2), startHour = (startMins - 1440) / 60, startMinute = (startMins - 1440) % 60, endHour = (endMins - 1440) / 60, endMinute = (endMins - 1440) % 60), "main"))
                        else if (endsNextDay) listOf(Pair(event.copy(endHour = 24, endMinute = 0), "part1"), Pair(event.copy(dayIndex = minOf(event.dayIndex + 1, 2), startHour = 0, startMinute = 0, endHour = (endMins - 1440) / 60, endMinute = (endMins - 1440) % 60), "part2"))
                        else if (startsPrevDay) listOf(Pair(event.copy(dayIndex = maxOf(event.dayIndex - 1, 0), startHour = (1440 + startMins) / 60, startMinute = (1440 + startMins) % 60, endHour = 24, endMinute = 0), "part1"), Pair(event.copy(startHour = 0, startMinute = 0), "part2"))
                        else listOf(Pair(event, "main"))
                    }

                    visuallySplitEvents.forEach { (event, partSuffix) ->
                        key("${event.id}_$partSuffix") {
                            val isActive = (currentMode != "NORMAL" && currentMode != "SELECT" && event.id == activeEvent?.id) || (isExternalDragging && event.id == externalDragEvent?.id) || (currentMode == "MULTI_MOVE" && event.id == activeEvent?.id)
                            val isMagneticSnap = (isActive && currentMode == "MOVE" && (previewResult?.isMagneticSnap == true)) || (isExternalDragging && isActive && (previewResult?.isMagneticSnap == true))
                            val isNextDayPart = partSuffix == "part2" && event.id == activeEvent?.id

                            val isSelectModeActive = currentMode == "SELECT" || currentMode == "MULTI_MOVE"
                            val isSelectedGroup = event.id in selectedEventIds
                            val targetAlpha = if (isSelectModeActive && !isSelectedGroup) 0.4f else 1.0f

                            EventBlockItem(
                                event = event, hourHeightDp = hourHeight.value,
                                isSelected = (selectedEvent?.id == event.id) && (selectedPartSuffix == partSuffix),
                                currentMode = currentMode, isActiveTarget = isActive,
                                isMagneticSnap = isMagneticSnap,
                                freeX = if (isActive && (currentMode == "MOVE" || currentMode == "MULTI_MOVE" || isExternalDragging)) previewResult?.freeX else null,
                                freeY = if (isActive && (currentMode == "MOVE" || currentMode == "MULTI_MOVE" || isExternalDragging) && !isNextDayPart) previewResult?.freeY else null,
                                alpha = if ((currentMode != "NORMAL" || isExternalDragging) && !isActive && !isSelectModeActive) 0.85f else targetAlpha,
                                isEventSplit = partSuffix != "main", partSuffix = partSuffix,
                                onLongClick = { gridPopupInfo = null; selectedPartSuffix = partSuffix; onEventLongClick(events.find { it.id == event.id } ?: event) },
                                onDeselect = { selectedPartSuffix = null; onEventDeselect() },
                                onClick = { onEventClick(events.find { it.id == event.id } ?: event) },
                                onCopy = { onEventCopy(events.find { it.id == event.id } ?: event) },
                                onMoveStart = { onEventMoveStart(events.find { it.id == event.id } ?: event) },
                                onStretchStart = { onEventStretchStart(events.find { it.id == event.id } ?: event) },
                                onActionCancel = onActionCancel,
                                onEventUnassigned = { onEventUnassigned(events.find { it.id == event.id } ?: event) }
                            )
                        }
                    }

                    if (currentMode == "SELECT" && selectStart != null && selectCurrent != null) {
                        val left = minOf(selectStart!!.x, selectCurrent!!.x)
                        val top = minOf(selectStart!!.y, selectCurrent!!.y)
                        val width = abs(selectStart!!.x - selectCurrent!!.x)
                        val height = abs(selectStart!!.y - selectCurrent!!.y)
                        Box(modifier = Modifier.offset { IntOffset(left.toInt(), top.toInt()) }.size(with(density){width.toDp()}, with(density){height.toDp()}).background(Color(0x403498DB)).border(2.dp, Color(0xFF3498DB)).zIndex(100f))
                    }

                    if (gridPopupInfo != null) {
                        Popup(alignment = Alignment.TopStart, offset = gridPopupInfo!!.tapOffset, onDismissRequest = { gridPopupInfo = null }, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
                            Box(modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(id = R.drawable.popup_paste), contentDescription = "붙여넣기", tint = if (copiedEvents.isNotEmpty()) Color.DarkGray else Color.LightGray, modifier = Modifier.size(24.dp).clickable(enabled = copiedEvents.isNotEmpty()) { onEventPaste(gridPopupInfo!!.dayIndex, gridPopupInfo!!.startHour, gridPopupInfo!!.startMinute); gridPopupInfo = null })
                                    Icon(painter = painterResource(id = R.drawable.popup_add), contentDescription = "추가", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onEventAdd(gridPopupInfo!!.dayIndex, gridPopupInfo!!.startHour, gridPopupInfo!!.startMinute); gridPopupInfo = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventBlockItem(
    event: EventData, hourHeightDp: Float, isSelected: Boolean, currentMode: String, isActiveTarget: Boolean, alpha: Float,
    isMagneticSnap: Boolean = true, freeX: Float? = null, freeY: Float? = null,
    isEventSplit: Boolean = false, partSuffix: String = "main",
    onLongClick: () -> Unit, onDeselect: () -> Unit, onClick: () -> Unit,
    onCopy: () -> Unit, onMoveStart: () -> Unit, onStretchStart: () -> Unit, onActionCancel: () -> Unit,
    onEventUnassigned: () -> Unit
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val minuteHeightDp = hourHeightDp / 60f

    val realDuration = (event.endHour * 60 + event.endMinute) - (event.startHour * 60 + event.startMinute)
    val displayAlpha = if (realDuration <= 0) 0f else alpha

    val gridX = (event.dayIndex * 120).dp
    val gridY = ((event.startHour * 60 + event.startMinute) * minuteHeightDp).dp + (hourHeightDp / 2f).dp
    val calculatedHeight = realDuration * minuteHeightDp

    val safeBlockHeightDp = if (isEventSplit || currentMode == "STRETCH") maxOf(0f, calculatedHeight).dp else maxOf(28f, calculatedHeight).dp

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
    val animatedHeight = if (isEventSplit || currentMode == "STRETCH") maxOf(0.dp, animatedBottom - animatedTop) else maxOf(28.dp, animatedBottom - animatedTop)

    var tapOffset by remember { mutableStateOf(IntOffset.Zero) }
    var blockXInWindow by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(x = animatedX, y = animatedY)
            .width(120.dp).height(animatedHeight)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .shadow(if (isSelected || isActiveTarget) 12.dp else 0.dp, RoundedCornerShape(8.dp))
            .alpha(displayAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(event.color)
            .onGloballyPositioned { coordinates -> blockXInWindow = coordinates.positionInWindow().x }
            .pointerInput(event, currentMode) {
                detectTapGestures(
                    onTap = { if (currentMode == "NORMAL") onClick() else onActionCancel() },
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
        val startMins = event.startHour * 60 + event.startMinute
        val endMins = event.endHour * 60 + event.endMinute

        val isStretchingPrevDay = isStretching && startMins <= 0 && !isEventSplit

        if (!isStretchingPrevDay) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(text = event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (event.description.isNotEmpty()) Text(text = event.description, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            }
        }

        if (isStretching && realDuration > 0) {
            if (endMins >= 1440 && !isEventSplit) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("다음 날로 확장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White, CircleShape))
                }
            } else if (startMins <= 0 && !isEventSplit) {
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White, CircleShape))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("이전 날로 확장", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                if (isEventSplit) {
                    if (partSuffix == "part1") Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
                    else if (partSuffix == "part2") Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.8f), CircleShape))
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
                        Icon(painter = painterResource(id = R.drawable.popup_move), contentDescription = "이동", tint = if (isEventSplit) Color.LightGray else Color.DarkGray, modifier = Modifier.size(24.dp).clickable(enabled = !isEventSplit) { onMoveStart() })
                        Icon(painter = painterResource(id = R.drawable.popup_stretch), contentDescription = "수정", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onStretchStart() })
                        Icon(painter = painterResource(id = R.drawable.popup_remove), contentDescription = "삭제", tint = Color.DarkGray, modifier = Modifier.size(24.dp).clickable { onEventUnassigned() })
                    }
                }
            }
        }
    }
}

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

@Composable
fun EventEditScreen(initialEvent: EventData, onDismiss: () -> Unit, onSave: (EventData) -> Unit) {
    val descParts = initialEvent.description.split("\n", limit = 2)
    var title by remember(initialEvent) { mutableStateOf(initialEvent.title) }
    var location by remember(initialEvent) { mutableStateOf(descParts.getOrNull(0) ?: "") }
    var memo by remember(initialEvent) { mutableStateOf(descParts.getOrNull(1) ?: "") }
    var color by remember(initialEvent) { mutableStateOf(initialEvent.color) }

    var isTimeEnabled by remember(initialEvent) { mutableStateOf(initialEvent.dayIndex != -1) }

    var startDayIndex by remember(initialEvent) {
        var d = if (initialEvent.dayIndex == -1) 0 else initialEvent.dayIndex
        var s = initialEvent.startHour * 60 + initialEvent.startMinute
        while (s < 0) { d--; s += 1440 }
        mutableIntStateOf(maxOf(0, d))
    }
    var startMins by remember(initialEvent) {
        var s = initialEvent.startHour * 60 + initialEvent.startMinute
        while (s < 0) { s += 1440 }
        mutableIntStateOf(s)
    }

    var endDayIndex by remember(initialEvent) {
        var d = if (initialEvent.dayIndex == -1) 0 else initialEvent.dayIndex
        var e = initialEvent.endHour * 60 + initialEvent.endMinute
        while(e >= 1440){ d++; e -= 1440 }
        while(e < 0){ d--; e += 1440 }
        mutableIntStateOf(minOf(maxOf(0, d), 3))
    }
    var endMins by remember(initialEvent) {
        var e = initialEvent.endHour * 60 + initialEvent.endMinute
        while (e < 0) { e += 1440 }
        mutableIntStateOf(e % 1440)
    }

    var errorMessage by remember(initialEvent) { mutableStateOf("") }
    var showColorPicker by remember(initialEvent) { mutableStateOf(false) }

    val days = listOf("27일(토)", "28일(일)", "29일(월)")
    val totalS = startDayIndex * 1440 + startMins
    val totalE = endDayIndex * 1440 + endMins

    var dEIdx = endDayIndex
    var dEM = endMins
    if (endMins == 0 && endDayIndex > 0) { dEIdx = endDayIndex - 1; dEM = 1440 }

    val colorPalette = listOf(
        Color(0xFF95A5A6), Color(0xFFE74C3C), Color(0xFFE67E22), Color(0xFFF1C40F),
        Color(0xFF2ECC71), Color(0xFF1ABC9C), Color(0xFF3498DB), Color(0xFF9B59B6),
        Color(0xFF34495E), Color(0xFFFF4081), Color(0xFF69F0AE), Color(0xFFBCAAA4)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.White).pointerInput(Unit){}) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value = title, onValueChange = { if (it.length <= 20) title = it }, textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), decorationBox = { if (title.isEmpty()) Text("일정 제목", color = Color.LightGray, fontSize = 28.sp, fontWeight = FontWeight.Bold); it() })
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.size(32.dp).background(color, CircleShape).border(1.dp, Color.LightGray, CircleShape).clickable { showColorPicker = true })
            }
            Text("(${title.length}/20)", fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
            Spacer(modifier = Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("진행 시간", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isTimeEnabled = !isTimeEnabled }.padding(4.dp)) {
                    Box(modifier = Modifier.size(20.dp).border(2.dp, if (isTimeEnabled) Color(0xFF3498DB) else Color.Gray, RoundedCornerShape(4.dp)).background(if (isTimeEnabled) Color(0xFF3498DB) else Color.Transparent, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        if (isTimeEnabled) Text("✔", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("시간 활성화", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isTimeEnabled) Color(0xFF3498DB) else Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.alpha(if (isTimeEnabled) 1f else 0.4f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownSelector(days[startDayIndex], days, { val n = days.indexOf(it); if (n * 1440 + startMins >= totalE) errorMessage = "시작이 종료보다 늦을 수 없습니다!" else { startDayIndex = n; errorMessage = "" } }, Modifier.weight(1.2f))
                        Spacer(Modifier.width(8.dp))
                        DropdownSelector(String.format("%02d", startMins/60), (0..23).map { String.format("%02d", it) }, { val h = it.toInt(); if (startDayIndex*1440 + h*60 + startMins%60 >= totalE) errorMessage = "시작이 종료보다 늦을 수 없습니다!" else { startMins = h*60 + startMins%60; errorMessage = "" } }, Modifier.weight(0.7f))
                        Text(":", Modifier.padding(4.dp))
                        DropdownSelector(String.format("%02d", startMins%60), (0..55 step 5).map { String.format("%02d", it) }, { val m = it.toInt(); if (startDayIndex*1440 + (startMins/60)*60 + m >= totalE) errorMessage = "시작이 종료보다 늦을 수 없습니다!" else { startMins = (startMins/60)*60 + m; errorMessage = "" } }, Modifier.weight(0.7f))
                        Text("부터", Modifier.padding(start = 8.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownSelector(days[dEIdx], days, { val n = days.indexOf(it); val nE = n*1440 + dEM; if (nE <= totalS) errorMessage = "종료가 시작보다 빨라야 합니다!" else if ((nE-1)/1440 - startDayIndex >= 2) errorMessage = "최대 2일만 가능합니다!" else { endDayIndex = nE/1440; endMins = nE%1440; errorMessage = "" } }, Modifier.weight(1.2f))
                        Spacer(Modifier.width(8.dp))
                        DropdownSelector(String.format("%02d", dEM/60), (0..24).map { String.format("%02d", it) }, { val h = it.toInt(); val nE = dEIdx*1440 + h*60 + (if(h==24) 0 else dEM%60); if (nE <= totalS) errorMessage = "종료가 시작보다 빨라야 합니다!" else { endDayIndex = nE/1440; endMins = nE%1440; errorMessage = "" } }, Modifier.weight(0.7f))
                        Text(":", Modifier.padding(4.dp))
                        DropdownSelector(String.format("%02d", dEM%60), (if(dEM/60==24) listOf("00") else (0..55 step 5).map { String.format("%02d", it) }), { val m = it.toInt(); val nE = dEIdx*1440 + (dEM/60)*60 + m; if (nE <= totalS) errorMessage = "종료가 시작보다 빨라야 합니다!" else { endDayIndex = nE/1440; endMins = nE%1440; errorMessage = "" } }, Modifier.weight(0.7f))
                        Text("까지", Modifier.padding(start = 8.dp))
                    }
                    if (errorMessage.isNotEmpty() && isTimeEnabled) Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
                }

                if (!isTimeEnabled) {
                    Box(modifier = Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("장소", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BasicTextField(location, { if (it.length <= 20) location = it }, Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(16.dp), decorationBox = { if (location.isEmpty()) Text("장소를 입력하세요", color = Color.Gray); it() })
            Spacer(Modifier.height(16.dp))
            Text("메모", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BasicTextField(memo, { if (it.length <= 20) memo = it }, Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(16.dp), decorationBox = { if (memo.isEmpty()) Text("간단한 메모를 입력하세요", color = Color.Gray); it() })
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f).background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).clickable { onDismiss() }.padding(16.dp), Alignment.Center) { Text("취소", color = Color.Red, fontWeight = FontWeight.Bold) }

                Box(Modifier.weight(1f).background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).clickable {
                    val finalTitle = title.ifEmpty { "새 일정" }
                    val finalDesc = listOf(location.trim(), memo.trim()).filter { it.isNotEmpty() }.joinToString("\n")

                    if (isTimeEnabled) {
                        if (errorMessage.isEmpty()) {
                            onSave(initialEvent.copy(
                                title = finalTitle, description = finalDesc,
                                dayIndex = startDayIndex, startHour = startMins/60, startMinute = startMins%60,
                                endHour = (totalE - startDayIndex*1440)/60, endMinute = (totalE - startDayIndex*1440)%60,
                                color = color
                            ))
                        }
                    } else {
                        onSave(initialEvent.copy(
                            title = finalTitle, description = finalDesc,
                            dayIndex = -1, startHour = 0, startMinute = 0, endHour = 1, endMinute = 0,
                            color = color
                        ))
                    }
                }.padding(16.dp), Alignment.Center) { Text("적용", color = Color(0xFF3498DB), fontWeight = FontWeight.Bold) }
            }
        }
        if (showColorPicker) Dialog(onDismissRequest = { showColorPicker = false }) {
            Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(24.dp)) {
                Column {
                    Text("색상 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(colorPalette) { c -> Box(Modifier.size(48.dp).background(c, CircleShape).border(if (c == color) 3.dp else 0.dp, Color.DarkGray, CircleShape).clickable { color = c; showColorPicker = false }) }
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