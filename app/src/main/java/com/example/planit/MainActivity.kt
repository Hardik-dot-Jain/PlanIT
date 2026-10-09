package com.example.planit

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planit.data.local.entity.Activity
import com.example.planit.data.local.entity.ScheduledBlock
import com.example.planit.data.local.entity.relation.ScheduledBlockWithActivity
import com.example.planit.notification.NotificationService
import com.example.planit.ui.components.ActivityDock
import com.example.planit.ui.components.HourHeight
import com.example.planit.ui.components.MinuteScale
import com.example.planit.ui.components.TimelineView
import com.example.planit.ui.theme.CanvasBackground
import com.example.planit.ui.theme.ClashDisplay
import com.example.planit.ui.theme.PastelCoral
import com.example.planit.ui.theme.PastelMint
import com.example.planit.ui.theme.PastelYellow
import com.example.planit.ui.theme.PlanITTheme
import com.example.planit.ui.theme.Satoshi
import com.example.planit.ui.theme.SoftPastelPalette
import com.example.planit.ui.viewmodel.PlanItViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PlanItApplication
        val notificationService = NotificationService(app)

        setContent {
            PlanITTheme {
                val viewModel: PlanItViewModel = viewModel(
                    factory = PlanItViewModel.Factory(
                        repository = app.repository,
                        notificationService = notificationService
                    )
                )
                PlanItScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Phase 3 Screen: Interactive Timeline Block Manipulation & Dock Scheduling.
 *
 * - HorizontalPager enables robust swiping between days using java.time.LocalDate
 * - Vertically scrollable 24-hour timeline with #E0E0D4 dotted grid
 * - Dock-to-Timeline scheduling: Tapping an activity chip schedules it onto the timeline
 * - Movable and resizable blocks with 45-min short block threshold and pinned drag handle
 * - Robust Android 13+ POST_NOTIFICATIONS and Android 14+ SCHEDULE_EXACT_ALARM handling
 */
@Composable
fun PlanItScreen(viewModel: PlanItViewModel) {
    val context = LocalContext.current
    val activities by viewModel.activities.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var activityToEdit by remember { mutableStateOf<Activity?>(null) }

    // Android 13+ POST_NOTIFICATIONS permission request
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Android 14+ / Android 12+ Exact Alarm Permission Check
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            showExactAlarmDialog = !alarmManager.canScheduleExactAlarms()
        }
    }

    LaunchedEffect(Unit) {
        checkExactAlarmPermission()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkExactAlarmPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Robust Date Swiping using java.time.LocalDate
    val initialPage = 5000
    val baseDate = remember { LocalDate.now() }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 10000 })

    // Hoisted scroll state for timeline viewport
    val timelineScrollState = rememberScrollState()

    // Global Drag-and-Drop State
    var draggingActivity by remember { mutableStateOf<Activity?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var timelineBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var lastSnappedCandidateMinutes by remember { mutableIntStateOf(-1) }
    var optimisticBlocks by remember { mutableStateOf<List<ScheduledBlockWithActivity>>(emptyList()) }

    // Synchronize selectedDate when user swipes pages
    LaunchedEffect(pagerState.currentPage) {
        val newDate = baseDate.plusDays((pagerState.currentPage - initialPage).toLong())
        if (viewModel.selectedDate.value != newDate) {
            viewModel.selectDate(newDate)
        }
    }

    // Candidate drop time calculation using 1.2.dp minute scale
    val candidateTime = if (draggingActivity != null &&
        dragPosition.x in timelineBoundsInRoot.left..timelineBoundsInRoot.right &&
        dragPosition.y in timelineBoundsInRoot.top..timelineBoundsInRoot.bottom
    ) {
        val minuteScalePx = with(density) { MinuteScale.toPx() }
        val localY = (dragPosition.y - timelineBoundsInRoot.top) + timelineScrollState.value
        val rawMinutes = localY / minuteScalePx
        val duration = draggingActivity?.defaultDuration ?: 30
        val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, (1440 - duration).coerceAtLeast(0))
        LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        Scaffold(
            containerColor = CanvasBackground,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(CanvasBackground)
            ) {
                // Simple Horizontal Top Header
                TimelineTopHeader(
                    currentDate = selectedDate,
                    onAddActivityClick = { showAddActivityDialog = true }
                )

                // HorizontalPager: Swiping left/right moves between days and loads respective blocks
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val pageDate = baseDate.plusDays((page - initialPage).toLong())
                    val pageBlocks by viewModel.getScheduledBlocksForDate(pageDate).collectAsState(initial = emptyList())

                    // Merge persisted blocks from Room with any pending optimistic blocks for this pageDate
                    val effectiveOptimistic = remember(optimisticBlocks, pageBlocks, pageDate) {
                        optimisticBlocks.filter { opt ->
                            opt.scheduledBlock.date == pageDate &&
                            pageBlocks.none { real ->
                                real.scheduledBlock.activityId == opt.scheduledBlock.activityId &&
                                real.scheduledBlock.date == opt.scheduledBlock.date &&
                                real.scheduledBlock.startTime == opt.scheduledBlock.startTime
                            }
                        }
                    }
                    val combinedBlocks = remember(pageBlocks, effectiveOptimistic) {
                        pageBlocks + effectiveOptimistic
                    }

                    // Clean up persisted optimistic blocks once Room has emitted them
                    LaunchedEffect(pageBlocks) {
                        if (optimisticBlocks.isNotEmpty()) {
                            optimisticBlocks = optimisticBlocks.filter { opt ->
                                pageBlocks.none { real ->
                                    real.scheduledBlock.activityId == opt.scheduledBlock.activityId &&
                                    real.scheduledBlock.date == opt.scheduledBlock.date &&
                                    real.scheduledBlock.startTime == opt.scheduledBlock.startTime
                                }
                            }
                        }
                    }

                    TimelineView(
                        scheduledBlocks = combinedBlocks,
                        selectedDate = pageDate,
                        scrollState = timelineScrollState,
                        onUpdateBlock = { updatedBlock ->
                            viewModel.updateScheduledBlock(updatedBlock)
                        },
                        onDeleteBlock = { blockToDelete ->
                            if (blockToDelete.id < 0) {
                                optimisticBlocks = optimisticBlocks.filter { it.scheduledBlock.id != blockToDelete.id }
                            } else {
                                viewModel.deleteScheduledBlock(blockToDelete)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                if (page == pagerState.currentPage) {
                                    timelineBoundsInRoot = coordinates.boundsInRoot()
                                }
                            },
                        initialScrollHour = 8,
                        draggingActivity = if (page == pagerState.currentPage) draggingActivity else null,
                        dragPosition = dragPosition
                    )
                }

                // Bottom Activity Dock: Tapping opens edit dialog, long-press dragging schedules on timeline
                ActivityDock(
                    activities = activities,
                    onActivityClick = { activity ->
                        activityToEdit = activity
                    },
                    onDragStarted = { activity, startPos ->
                        draggingActivity = activity
                        dragPosition = startPos
                        lastSnappedCandidateMinutes = -1
                    },
                    onDragDelta = { delta ->
                        dragPosition += delta
                    },
                    onDragEnded = {
                        val activity = draggingActivity
                        if (activity != null &&
                            dragPosition.x in timelineBoundsInRoot.left..timelineBoundsInRoot.right &&
                            dragPosition.y in timelineBoundsInRoot.top..timelineBoundsInRoot.bottom
                        ) {
                            val minuteScalePx = with(density) { MinuteScale.toPx() }
                            val localY = (dragPosition.y - timelineBoundsInRoot.top) + timelineScrollState.value
                            val rawMinutes = localY / minuteScalePx
                            val duration = activity.defaultDuration
                            val snappedMinutes = ((rawMinutes / 15.0).roundToInt() * 15).coerceIn(0, (1440 - duration).coerceAtLeast(0))
                            val startTime = LocalTime.of(snappedMinutes / 60, snappedMinutes % 60)
                            val endTime = if (activity.isFlexible) {
                                if (snappedMinutes + duration >= 1440) {
                                    LocalTime.of(23, 59)
                                } else {
                                    startTime.plusMinutes(duration.toLong())
                                }
                            } else {
                                // If the activity is Rigid, the endTime MUST be exactly startTime.plusMinutes(activity.defaultDuration).
                                // Do not apply 15-minute rounding to the duration of Rigid blocks.
                                startTime.plusMinutes(activity.defaultDuration.toLong())
                            }

                            // Optimistic State: immediately append temporary optimistic block to local UI state
                            val tempId = -System.currentTimeMillis()
                            val optimisticBlock = ScheduledBlock(
                                id = tempId,
                                activityId = activity.id,
                                date = selectedDate,
                                startTime = startTime,
                                endTime = endTime
                            )
                            val optimisticBlockWithActivity = ScheduledBlockWithActivity(
                                scheduledBlock = optimisticBlock,
                                activity = activity
                            )
                            optimisticBlocks = optimisticBlocks + optimisticBlockWithActivity

                            // Persist to Room database & schedule notifications
                            viewModel.insertScheduledBlock(optimisticBlock.copy(id = 0))

                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        draggingActivity = null
                        lastSnappedCandidateMinutes = -1
                    },
                    onDragCanceled = {
                        draggingActivity = null
                        lastSnappedCandidateMinutes = -1
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dialog for adding activities (updates database and triggers reactive Flow update in dock)
            if (showAddActivityDialog) {
                AddActivityNeoDialog(
                    onDismiss = { showAddActivityDialog = false },
                    onConfirm = { name, icon, duration, colorVal, isFlexible ->
                        viewModel.createActivity(
                            name = name,
                            icon = icon,
                            color = colorVal,
                            defaultDuration = duration,
                            isFlexible = isFlexible
                        )
                        showAddActivityDialog = false
                    }
                )
            }

            // Dialog for editing/deleting activity from dock
            activityToEdit?.let { activity ->
                EditActivityNeoDialog(
                    activity = activity,
                    onDismiss = { activityToEdit = null },
                    onSave = { updatedActivity ->
                        viewModel.updateActivity(updatedActivity)
                        activityToEdit = null
                    },
                    onDelete = { actToDelete ->
                        viewModel.deleteActivity(actToDelete)
                        activityToEdit = null
                    }
                )
            }

            // Neo-Brutalist Exact Alarm Permission Dialog (Android 14+ / Android 12+)
            if (showExactAlarmDialog) {
                AlertDialog(
                    onDismissRequest = { showExactAlarmDialog = false },
                    containerColor = Color(0xFFFDFDF7),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(16.dp)),
                    title = {
                        Text(
                            text = "Enable Exact Alarms ⏰",
                            fontFamily = ClashDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                    },
                    text = {
                        Text(
                            text = "PlanIT needs permission to set exact alarms so notifications fire precisely when your scheduled activities begin.\n\nPlease enable 'Alarms & reminders' in system settings.",
                            fontFamily = Satoshi,
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExactAlarmDialog = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Open Settings",
                                fontFamily = ClashDisplay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showExactAlarmDialog = false }
                        ) {
                            Text(
                                text = "Later",
                                fontFamily = Satoshi,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        // Global Drag Overlay following user's finger
        draggingActivity?.let { activity ->
            FloatingDraggedBlockOverlay(
                activity = activity,
                dragPosition = dragPosition,
                candidateTime = candidateTime
            )
        }
    }
}

/**
 * Top app bar designed as a clean horizontal Row in Soft Neo-Brutalism aesthetic.
 */
@Composable
fun TimelineTopHeader(
    currentDate: LocalDate,
    onAddActivityClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasBackground)
            .border(BorderStroke(2.dp, Color.Black))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 12.dp)
        ) {
            Text(
                text = "PlanIT",
                fontFamily = ClashDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.5).sp,
                color = Color.Black
            )
            Text(
                text = currentDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                fontFamily = Satoshi,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0xFF71716A),
                maxLines = 1
            )
        }

        NeoBrutalistButton(
            text = "+ Activity",
            backgroundColor = PastelYellow,
            onClick = onAddActivityClick
        )
    }
}

/**
 * Neo-Brutalist button with solid border and hard offset drop shadow.
 */
@Composable
fun NeoBrutalistButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 3.dp, bottom = 3.dp)
            .clickable(onClick = onClick)
    ) {
        // Shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(Color.Black, shape = RoundedCornerShape(12.dp))
        )

        // Foreground
        Box(
            modifier = Modifier
                .background(backgroundColor, shape = RoundedCornerShape(12.dp))
                .border(BorderStroke(2.dp, Color.Black), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = ClashDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.Black,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Neo-Brutalist toggle for choosing between 'Flexible' and 'Rigid' tile modes.
 *
 * Replaces overlapping segmented buttons with a Row of two distinct, selectable neo-brutalist chips
 * separated by Arrangement.spacedBy(12.dp). Both have RoundedCornerShape(12.dp) and a 2.dp black border.
 * The selected one gets a black background with white text; the unselected one gets a white background
 * with black text and a 2.dp hard drop shadow.
 */
@Composable
fun NeoBrutalistSegmentedToggle(
    isFlexible: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Tile Mode:",
            fontFamily = Satoshi,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = Color.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flexible chip
            NeoBrutalistModeChip(
                text = "Flexible",
                isSelected = isFlexible,
                onClick = { onToggle(true) },
                modifier = Modifier.weight(1f)
            )

            // Rigid chip
            NeoBrutalistModeChip(
                text = "Rigid",
                isSelected = !isFlexible,
                onClick = { onToggle(false) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = if (isFlexible) "• Stretchable & resizable on the timeline" else "• Fixed duration; movable only",
            fontFamily = Satoshi,
            fontSize = 11.sp,
            color = Color(0xFF71716A)
        )
    }
}

@Composable
fun NeoBrutalistModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier.padding(bottom = 2.dp, end = 2.dp)
    ) {
        if (!isSelected) {
            // 2.dp hard drop shadow for unselected chip
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 2.dp, y = 2.dp)
                    .background(Color.Black, cornerShape)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cornerShape)
                .background(if (isSelected) Color.Black else Color.White)
                .border(BorderStroke(2.dp, Color.Black), cornerShape)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = ClashDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else Color.Black
            )
        }
    }
}

/**
 * Dialog allowing the user to create an activity in the Room database,
 * with 12 distinct pastel colors and Flexible vs Rigid tile toggle.
 */
@Composable
fun AddActivityNeoDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, duration: Int, color: Long, isFlexible: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("code") }
    var durationText by remember { mutableStateOf("45") }
    var isFlexible by remember { mutableStateOf(true) }
    val colorOptions = SoftPastelPalette
    var selectedColor by remember { mutableLongStateOf((PastelMint.toArgb().toLong() and 0xFFFFFFFFL)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CanvasBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(16.dp)),
        title = {
            Text(
                text = "New Activity",
                fontFamily = ClashDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Activity Title", fontFamily = Satoshi) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon Keyword (code, fitness, book, etc.)", fontFamily = Satoshi) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Default Duration (minutes)", fontFamily = Satoshi) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Segmented Toggle for Flexible vs Rigid
                NeoBrutalistSegmentedToggle(
                    isFlexible = isFlexible,
                    onToggle = { isFlexible = it }
                )

                // 12-Pastel Color Palette
                Text(
                    text = "Color Palette (12 Pastels):",
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
                val chunkedColors = remember { colorOptions.chunked(6) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkedColors.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { color ->
                                val colorVal = (color.toArgb().toLong() and 0xFFFFFFFFL)
                                val isSelected = selectedColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = Color.Black,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val duration = durationText.toIntOrNull() ?: 30
                        onConfirm(name.trim(), icon.trim(), duration, selectedColor, isFlexible)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Add to Dock",
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = Satoshi,
                    color = Color.Black
                )
            }
        }
    )
}

/**
 * Edit Activity Dialog opened on dock long-press.
 * Allows modifying title, default duration, flexible/rigid toggle, color, or deleting the activity.
 */
@Composable
fun EditActivityNeoDialog(
    activity: Activity,
    onDismiss: () -> Unit,
    onSave: (Activity) -> Unit,
    onDelete: (Activity) -> Unit
) {
    var name by remember { mutableStateOf(activity.name) }
    var icon by remember { mutableStateOf(activity.icon) }
    var durationText by remember { mutableStateOf(activity.defaultDuration.toString()) }
    var selectedColor by remember { mutableLongStateOf(activity.color) }
    var isFlexible by remember { mutableStateOf(activity.isFlexible) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = CanvasBackground,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(16.dp)),
            title = {
                Text(
                    text = "Delete Activity?",
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Deleting \"${activity.name}\" will also remove all its scheduled blocks across all days.",
                    fontFamily = Satoshi,
                    fontSize = 13.sp,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onDelete(activity)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelCoral,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.5.dp, Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Delete Everything",
                        fontFamily = ClashDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", fontFamily = Satoshi, color = Color.Black)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = CanvasBackground,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(16.dp)),
            title = {
                Text(
                    text = "Edit Activity",
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Activity Title", fontFamily = Satoshi) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it },
                        label = { Text("Icon Keyword", fontFamily = Satoshi) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("Default Duration (minutes)", fontFamily = Satoshi) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Rigid vs Flexible Segmented Toggle
                    NeoBrutalistSegmentedToggle(
                        isFlexible = isFlexible,
                        onToggle = { isFlexible = it }
                    )

                    // 12-Pastel Color Palette Selector
                    Text(
                        text = "Color Palette (12 Pastels):",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                    val chunkedColors = remember { SoftPastelPalette.chunked(6) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunkedColors.forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowColors.forEach { color ->
                                    val colorVal = (color.toArgb().toLong() and 0xFFFFFFFFL)
                                    val isSelected = selectedColor == colorVal
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.5.dp,
                                                color = Color.Black,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = colorVal },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showConfirmDelete = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PastelCoral,
                            contentColor = Color.Black
                        ),
                        border = BorderStroke(1.5.dp, Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Delete",
                            fontFamily = ClashDisplay,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val duration = durationText.toIntOrNull() ?: 30
                                onSave(
                                    activity.copy(
                                        name = name.trim(),
                                        icon = icon.trim(),
                                        defaultDuration = duration,
                                        color = selectedColor,
                                        isFlexible = isFlexible
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = ClashDisplay,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel", fontFamily = Satoshi, color = Color.Black)
                }
            }
        )
    }
}

/**
 * Floating dragged block overlay following the user's finger during drag-and-drop.
 * Displays the activity chip, icon, title, and candidate drop time when hovering
 * over the timeline grid.
 */
@Composable
fun FloatingDraggedBlockOverlay(
    activity: Activity,
    dragPosition: Offset,
    candidateTime: LocalTime?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val overlayWidth = 200.dp
    val overlayHeight = 56.dp
    val widthPx = with(density) { overlayWidth.toPx() }
    val heightPx = with(density) { overlayHeight.toPx() }

    // Centered horizontally around finger, hovering 20.dp above the finger tip
    val offsetX = (dragPosition.x - widthPx / 2f).roundToInt()
    val offsetY = (dragPosition.y - heightPx - with(density) { 20.dp.toPx() }).roundToInt()

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(width = overlayWidth, height = overlayHeight)
    ) {
        // Hard drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(Color.Black, RoundedCornerShape(14.dp))
        )

        // Foreground Card
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color(activity.color.toInt()), RoundedCornerShape(14.dp))
                .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White, CircleShape)
                    .border(BorderStroke(1.5.dp, Color.Black), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = com.example.planit.ui.components.resolveActivityIcon(activity.icon),
                    fontSize = 16.sp
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = activity.name,
                    fontFamily = ClashDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 1
                )
                if (candidateTime != null) {
                    val timeStr = candidateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                    Text(
                        text = "$timeStr • ${activity.defaultDuration}m",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.Black
                    )
                } else {
                    Text(
                        text = "Drop on timeline",
                        fontFamily = Satoshi,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}