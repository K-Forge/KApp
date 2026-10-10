package co.edu.konradlorenz.kapp.ui.schedule

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.schedule.ClassOccurrence
import co.edu.konradlorenz.kapp.data.schedule.ScheduleResult
import co.edu.konradlorenz.kapp.data.schedule.SpaceDetail
import co.edu.konradlorenz.kapp.data.schedule.WeekAgenda
import co.edu.konradlorenz.kapp.data.schedule.WeekDays
import co.edu.konradlorenz.kapp.ui.common.BandContentHeight
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.common.hexColor
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.InProgress
import co.edu.konradlorenz.kapp.ui.theme.Subject
import java.time.LocalDate

private val CardShape = RoundedCornerShape(18.dp)
private val ClassShape = RoundedCornerShape(14.dp)

/**
 * Horario (issue #45), from docs/api/schedule.openapi.yaml 2.0.0.
 *
 * No mockup covers it; it is Inicio's band, card and class rows. One week is read and drawn three
 * ways: a day, two days side by side, or the seven days one under the other - a seven-column grid
 * would be too narrow to read on a phone. A class with no room says so, in its row, rather than
 * leaving the place blank. Tapping a class shows its room from the map.
 */
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory)) {
    val schedule by viewModel.schedule.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrandBand(title = stringResource(R.string.home_shortcut_schedule))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = BandContentHeight - 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = BarContentPadding)
                .navigationBarsPadding(),
        ) {
            when (val week = viewModel.week) {
                null -> Card {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 32.dp),
                        color = Brand,
                    )
                }
                ScheduleResult.NoSchedule -> Card { Message(R.string.schedule_none) }
                ScheduleResult.Failed -> Card {
                    Message(R.string.schedule_failed)
                    TextButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(R.string.schedule_retry)) }
                }
                is ScheduleResult.Ready -> {
                    WeekCard(week.value, viewModel)
                    val source = (schedule as? ScheduleResult.Ready)?.value?.source
                    if (source == "TEST") TestNotice()
                    Week(week.value, viewModel)
                }
            }
        }

        viewModel.openClass?.let { ClassSheet(it, viewModel.room, onDismiss = viewModel::close) }
    }
}

/** The week shown, the arrows to the next and the last, and the three views. */
@Composable
private fun WeekCard(week: WeekAgenda, viewModel: ScheduleViewModel) {
    val months = stringArrayResource(R.array.months_short)
    val start = LocalDate.parse(week.weekStart)
    val end = LocalDate.parse(week.weekEnd)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = viewModel::previousWeek) { Text("‹", fontSize = 22.sp) }
            Text(
                text = stringResource(
                    R.string.schedule_week_range,
                    start.dayOfMonth,
                    months[start.monthValue - 1],
                    end.dayOfMonth,
                    months[end.monthValue - 1],
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = viewModel::nextWeek) { Text("›", fontSize = 22.sp) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ScheduleView.entries.forEach { view ->
                FilterChip(
                    selected = view == viewModel.view,
                    onClick = { viewModel.show(view) },
                    label = {
                        Text(
                            stringResource(
                                when (view) {
                                    ScheduleView.Day -> R.string.schedule_view_day
                                    ScheduleView.TwoDays -> R.string.schedule_view_two_days
                                    ScheduleView.Week -> R.string.schedule_view_week
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TestNotice() {
    Text(
        text = stringResource(R.string.schedule_test_notice),
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InProgress.copy(alpha = 0.35f))
            .padding(12.dp),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Brand,
    )
}

@Composable
private fun Week(week: WeekAgenda, viewModel: ScheduleViewModel) {
    val start = LocalDate.parse(week.weekStart)
    val names = stringArrayResource(R.array.weekdays)
    val classesOf = { day: Int -> week.days[WeekDays[day]].orEmpty() }
    val label = { day: Int -> "${names[day]} ${start.plusDays(day.toLong()).dayOfMonth}" }

    when (viewModel.view) {
        ScheduleView.Day -> {
            DayStrip(start, viewModel.selectedDay, onSelect = viewModel::select)
            DayColumn(classesOf(viewModel.selectedDay), compact = false, onOpen = viewModel::open)
        }
        ScheduleView.TwoDays -> {
            DayStrip(start, viewModel.selectedDay, onSelect = viewModel::select)
            // The chosen day and the next; from Sunday, Saturday and Sunday, so it stays in the week.
            val first = viewModel.selectedDay.coerceAtMost(5)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(first, first + 1).forEach { day ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label(day),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        DayColumn(classesOf(day), compact = true, onOpen = viewModel::open)
                    }
                }
            }
        }
        ScheduleView.Week -> (0..6).forEach { day ->
            Text(
                text = label(day),
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DayColumn(classesOf(day), compact = false, onOpen = viewModel::open)
        }
    }
}

/** The seven days of the week, Monday first, to pick the one the day views start on. */
@Composable
private fun DayStrip(start: LocalDate, selected: Int, onSelect: (Int) -> Unit) {
    val names = stringArrayResource(R.array.weekdays)
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..6).forEach { day ->
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(names[day], fontSize = 12.sp)
                        Text(
                            text = start.plusDays(day.toLong()).dayOfMonth.toString(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun DayColumn(classes: List<ClassOccurrence>, compact: Boolean, onOpen: (ClassOccurrence) -> Unit) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (classes.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_no_classes),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        classes.forEach { ClassRow(it, compact, onClick = { onOpen(it) }) }
    }
}

/**
 * One class on its subject's colour rail, the colour the API assigns. The room line is always
 * there: "Sin salon asignado" in italics when SINU has none for that stretch.
 */
@Composable
private fun ClassRow(occurrence: ClassOccurrence, compact: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, ClassShape, ambientColor = Brand, spotColor = Brand)
            .clip(ClassShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp),
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .heightIn(min = 64.dp)
                .background(hexColor(occurrence.color, Subject)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${occurrence.startTime} – ${occurrence.endTime}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = occurrence.courseName,
                fontSize = if (compact) 13.sp else 15.sp,
                lineHeight = if (compact) 16.sp else 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            RoomLine(occurrence, compact)
        }
    }
}

@Composable
private fun RoomLine(occurrence: ClassOccurrence, compact: Boolean) {
    val room = occurrence.room
    Text(
        text = when {
            room == null -> stringResource(R.string.room_none)
            compact -> stringResource(R.string.room_number, room)
            else -> stringResource(R.string.room_number, room) + " · " + occurrence.sede
        },
        fontSize = 12.sp,
        fontStyle = if (room == null) FontStyle.Italic else FontStyle.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A class in full, and where it is: the map's answer for its room, or why there is none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassSheet(occurrence: ClassOccurrence, room: RoomState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = occurrence.courseName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.schedule_code_group, occurrence.sinuCode, occurrence.group),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Detail(R.string.schedule_detail_time, "${occurrence.startTime} – ${occurrence.endTime}")
            Detail(
                R.string.schedule_detail_blocks,
                pluralStringResource(R.plurals.schedule_blocks, occurrence.blocks, occurrence.blocks),
            )
            Detail(R.string.schedule_detail_professor, occurrence.professor)
            Detail(R.string.schedule_detail_sede, occurrence.sede)

            Text(
                text = stringResource(R.string.schedule_where),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (room) {
                RoomState.NoRoom -> Note(R.string.schedule_room_none_detail)
                RoomState.NotMapped -> {
                    Detail(R.string.schedule_detail_room, occurrence.room.orEmpty())
                    Note(R.string.schedule_room_not_mapped)
                }
                RoomState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Brand)
                RoomState.Failed -> {
                    Detail(R.string.schedule_detail_room, occurrence.room.orEmpty())
                    Note(R.string.schedule_room_failed)
                }
                is RoomState.Ready -> Space(room.space)
            }
        }
    }
}

/** What the map knows about a room. Accessibility only when it has been checked. */
@Composable
private fun Space(space: SpaceDetail) {
    Detail(R.string.schedule_detail_room, space.code)
    space.name?.let { Detail(R.string.schedule_detail_space_name, it) }
    space.typeName?.let { Detail(R.string.schedule_detail_space_type, it) }
    Detail(R.string.schedule_detail_building, space.building.name)
    Detail(R.string.schedule_detail_floor, space.floor.name)
    space.capacity?.let { Detail(R.string.schedule_detail_capacity, it.toString()) }
    // UNKNOWN "must not be shown as either answer" (map.openapi.yaml): most rooms are unchecked.
    when (space.effectiveAccessibility) {
        "STEP_FREE" -> Detail(R.string.schedule_detail_access, stringResource(R.string.schedule_access_step_free))
        "STAIRS_ONLY" -> Detail(R.string.schedule_detail_access, stringResource(R.string.schedule_access_stairs))
    }
    space.note?.let { Text(it, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun Detail(@StringRes label: Int, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = stringResource(label),
            modifier = Modifier.padding(end = 16.dp),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Note(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Message(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, CardShape, ambientColor = Brand, spotColor = Brand)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        content = content,
    )
}
