package co.edu.konradlorenz.kapp.ui.semaphore

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.semaphore.AcademicPlan
import co.edu.konradlorenz.kapp.data.semaphore.CourseStatus
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreData
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreItem
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreLevel
import co.edu.konradlorenz.kapp.data.semaphore.SemaphoreState
import co.edu.konradlorenz.kapp.data.semaphore.buildSemaphore
import co.edu.konradlorenz.kapp.data.semaphore.formatGrade
import co.edu.konradlorenz.kapp.data.semaphore.formatHours
import co.edu.konradlorenz.kapp.data.semaphore.formatPeriod
import co.edu.konradlorenz.kapp.ui.common.BandContentHeight
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.common.hexColor
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.ErrorRed
import co.edu.konradlorenz.kapp.ui.theme.InProgress
import co.edu.konradlorenz.kapp.ui.theme.OnPostponed
import co.edu.konradlorenz.kapp.ui.theme.Passed
import co.edu.konradlorenz.kapp.ui.theme.Pending
import co.edu.konradlorenz.kapp.ui.theme.Postponed
import co.edu.konradlorenz.kapp.ui.theme.TextSoft
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(18.dp)
private val ItemShape = RoundedCornerShape(12.dp)

/**
 * Semáforo (issue #44), from docs/api/semaphore.openapi.yaml 2.0.0.
 *
 * No mockup covers it. On a phone the pensum's grid - levels across, areas down - would be nine
 * columns of unreadable tiles, so it is drawn one semester under the other: each item a row painted
 * by its status, with its area's colour on its edge. Tapping one opens what SINU says about it and,
 * with a plan open, where the student means to take it.
 *
 * Read-only by contract: no status and no grade is ever asked for. The plans are the one thing
 * written, and the grid switches between the pensum as published and any plan.
 */
@Composable
fun SemaphoreScreen(viewModel: SemaphoreViewModel = viewModel(factory = SemaphoreViewModel.Factory)) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrandBand(title = stringResource(R.string.home_shortcut_semaphore))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = BandContentHeight - 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, bottom = BarContentPadding)
                .navigationBarsPadding(),
        ) {
            when (val current = state) {
                SemaphoreState.Loading -> Card {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 32.dp),
                        color = Brand,
                    )
                }
                SemaphoreState.NoProgram -> Card { Message(R.string.semaphore_no_program) }
                SemaphoreState.Failed -> Card {
                    Message(R.string.semaphore_failed)
                    TextButton(
                        onClick = viewModel::retry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(R.string.semaphore_retry)) }
                }
                is SemaphoreState.Ready -> Ready(current.data, viewModel)
            }
        }
    }
}

@Composable
private fun Ready(data: SemaphoreData, viewModel: SemaphoreViewModel) {
    val plan = viewModel.planOf(data)
    val levels = remember(data, plan) {
        buildSemaphore(data.pensum, data.semaphore, data.eligible, plan)
    }

    Summary(data)
    if (data.semaphore.source == "TEST") TestNotice()
    PlanBar(data, plan, viewModel)
    if (viewModel.saveFailed && viewModel.openItem == null) SaveFailed()
    Legend()
    // A level with nothing in it says nothing: a pensum fills every level, so an empty one is a
    // plan that moved everything out of it, and the sheet is where things are moved back.
    levels.filter { it.items.isNotEmpty() }.forEach { level ->
        LevelSection(level, current = level.level == data.semaphore.currentLevel, onOpen = viewModel::open)
    }

    val open = levels.flatMap { it.items }.firstOrNull { it.course.pensumItemCode == viewModel.openItem }
    if (open != null) {
        ItemSheet(item = open, data = data, plan = plan, viewModel = viewModel)
    }
    if (viewModel.naming) {
        NamePlanDialog(onCreate = viewModel::createPlan, onDismiss = viewModel::stopNaming)
    }
}

/** The card climbing into the band: semester, percentage and the credits bar of Inicio. */
@Composable
private fun Summary(data: SemaphoreData) {
    val summary = data.summary
    Card {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.semaphore_level, summary.currentLevel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.semaphore_summary,
                        data.pensum.programName,
                        summary.creditsPassed,
                        summary.totalCredits,
                    ),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${summary.percentComplete.roundToInt()}%",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Brand,
            )
        }
        Spacer(Modifier.height(12.dp))
        CreditsBar(
            passed = summary.creditsPassed,
            inProgress = summary.creditsInProgress,
            total = summary.totalCredits,
        )
    }
}

@Composable
private fun CreditsBar(passed: Int, inProgress: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Pending),
    ) {
        if (total > 0) {
            if (passed > 0) Box(Modifier.weight(passed.toFloat()).fillMaxHeight().background(Passed))
            if (inProgress > 0) {
                Box(Modifier.weight(inProgress.toFloat()).fillMaxHeight().background(InProgress))
            }
            val rest = total - passed - inProgress
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
    }
}

/** `source: TEST` (issue #44): say plainly that none of this is the student's record. */
@Composable
private fun TestNotice() {
    Text(
        text = stringResource(R.string.semaphore_test_notice),
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

/** The pensum as published, each plan, and a new one. The chosen one is what the grid shows. */
@Composable
private fun PlanBar(data: SemaphoreData, plan: AcademicPlan?, viewModel: SemaphoreViewModel) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = plan == null,
            onClick = viewModel::showOriginal,
            label = { Text(stringResource(R.string.semaphore_original)) },
        )
        data.plans.forEach { each ->
            FilterChip(
                selected = each.id == plan?.id,
                onClick = { viewModel.showPlan(each.id) },
                label = { Text(each.name, maxLines = 1) },
            )
        }
        FilterChip(
            selected = false,
            onClick = viewModel::startNaming,
            enabled = !viewModel.saving,
            label = { Text("+ " + stringResource(R.string.semaphore_new_plan)) },
        )
    }
    if (plan != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.semaphore_plans_hint),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { viewModel.deletePlan(plan) }, enabled = !viewModel.saving) {
                Text(stringResource(R.string.semaphore_delete_plan))
            }
        }
    }
}

/** The six colours, once, so the grid does not need a label on every row to be read. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            CourseStatus.Passed,
            CourseStatus.InProgress,
            CourseStatus.Eligible,
            CourseStatus.Blocked,
            CourseStatus.Failed,
            CourseStatus.Postponed,
        ).forEach { status ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(status, Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(labelOf(status)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LevelSection(level: SemaphoreLevel, current: Boolean, onOpen: (SemaphoreItem) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(
                if (current) R.string.semaphore_current_level else R.string.semaphore_level,
                level.level,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.semaphore_credits, level.credits),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        level.items.forEach { item -> ItemRow(item, onClick = { onOpen(item) }) }
    }
}

/**
 * One item, painted by its status. The area's colour is its left edge: the API sends it, and the
 * client only paints it - the same rule as a subject's colour on Inicio.
 */
@Composable
private fun ItemRow(item: SemaphoreItem, onClick: () -> Unit) {
    val (background, foreground) = coloursOf(item.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(background)
            .then(
                if (item.status == CourseStatus.Eligible) {
                    Modifier.border(BorderStroke(1.5.dp, Passed), ItemShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(56.dp)
                .background(areaColour(item)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondLine(item),
                fontSize = 12.sp,
                color = foreground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.entry?.sinuStatus?.takeIf { item.status == CourseStatus.Other }
                ?: stringResource(labelOf(item.status)),
            modifier = Modifier.padding(end = 12.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = foreground,
        )
    }
}

/** Code (or nothing, never `pensumItemCode`), credits, and where a plan moved it from. */
@Composable
private fun secondLine(item: SemaphoreItem): String = listOfNotNull(
    item.displayCode,
    stringResource(R.string.semaphore_credits, item.course.credits),
    item.movedFrom?.let { stringResource(R.string.semaphore_moved_from, it) },
    item.chosenElective?.let { elective ->
        if (elective.offered == false) stringResource(R.string.semaphore_elective_not_offered) else null
    },
).joinToString(" · ")

/** Everything SINU says about one item, and - with a plan open - where it goes in it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemSheet(
    item: SemaphoreItem,
    data: SemaphoreData,
    plan: AcademicPlan?,
    viewModel: SemaphoreViewModel,
) {
    ModalBottomSheet(onDismissRequest = viewModel::close) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.displayCode?.let {
                Text(text = it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(item.status, Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(labelOf(item.status)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))
            item.entry?.sinuStatus?.let { Detail(R.string.semaphore_detail_sinu_status, it) }
            item.entry?.period?.let { Detail(R.string.semaphore_detail_period, formatPeriod(it)) }
            item.entry?.grade?.let { Detail(R.string.semaphore_detail_grade, formatGrade(it)) }
            Detail(R.string.semaphore_detail_credits, item.course.credits.toString())
            Detail(R.string.semaphore_detail_hours, formatHours(item.course.weeklyHours))
            data.pensum.areas.firstOrNull { it.code == item.course.area }?.let {
                Detail(R.string.semaphore_detail_area, it.name)
            }
            // Only once SINU filled the slot: the title is then the course, and this names the slot
            // it went into. Before that the title already is the slot.
            if (item.course.isElectiveSlot && item.entry?.resolvedName != null) {
                Detail(R.string.semaphore_detail_elective_slot, item.course.name)
            }
            Prerequisites(item, data)

            Text(
                text = stringResource(R.string.semaphore_detail_from_sinu),
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.status.plannable) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (plan == null) {
                    Text(
                        text = stringResource(R.string.semaphore_plan_needs_plan),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PlanSection(item, data, plan, viewModel)
                }
            }
            if (viewModel.saveFailed) SaveFailed()
        }
    }
}

/** Each prerequisite by its name, with its own status: what is holding a blocked item back. */
@Composable
private fun Prerequisites(item: SemaphoreItem, data: SemaphoreData) {
    if (item.course.prerequisites.isEmpty()) return
    val courses = data.pensum.courses.associateBy { it.pensumItemCode }
    val entries = data.semaphore.courses.associateBy { it.pensumItemCode }
    Text(
        text = stringResource(R.string.semaphore_detail_prerequisites),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    item.course.prerequisites.forEach { code ->
        val course = courses[code] ?: return@forEach
        val status = CourseStatus.of(entries[code], code in data.eligible)
        Row(
            modifier = Modifier.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatch(status, Modifier.size(10.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = course.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Where the item goes in [plan]: any level the pensum has, back to its own, and for an elective
 * slot which course of this semester's bank. Moving it unlocks nothing - the contract is explicit
 * that eligibility ignores plans - so the colour stays what SINU says.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSection(
    item: SemaphoreItem,
    data: SemaphoreData,
    plan: AcademicPlan,
    viewModel: SemaphoreViewModel,
) {
    Text(
        text = stringResource(R.string.semaphore_plan_level),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..data.pensum.levels).forEach { level ->
            FilterChip(
                selected = level == item.level,
                onClick = { if (level != item.level) viewModel.move(plan, item, level) },
                enabled = !viewModel.saving,
                label = { Text(level.toString()) },
            )
        }
    }
    item.movedFrom?.let { original ->
        TextButton(onClick = { viewModel.reset(plan, item) }, enabled = !viewModel.saving) {
            Text(stringResource(R.string.semaphore_plan_reset, original))
        }
    }

    if (item.course.isElectiveSlot && item.entry?.resolvedSinuCode == null) {
        ElectiveSection(item, plan, viewModel)
    }
}

@Composable
private fun ElectiveSection(item: SemaphoreItem, plan: AcademicPlan, viewModel: SemaphoreViewModel) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val chosen = item.chosenElective
    val bank = viewModel.bank
    val chosenName = (bank as? ElectiveBank.Ready)?.offerings?.firstOrNull { it.sinuCode == chosen?.sinuCode }?.name

    Text(
        text = stringResource(R.string.semaphore_elective),
        modifier = Modifier.padding(top = 16.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = when {
            chosen == null -> stringResource(R.string.semaphore_elective_none)
            chosenName != null -> "$chosenName · ${chosen.sinuCode}"
            else -> chosen.sinuCode
        },
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (chosen?.offered == false) {
        Text(
            text = stringResource(R.string.semaphore_elective_not_offered),
            fontSize = 13.sp,
            color = ErrorRed,
        )
    }
    Row {
        TextButton(
            onClick = {
                choosing = true
                viewModel.loadBank()
            },
            enabled = !viewModel.saving,
        ) { Text(stringResource(R.string.semaphore_elective_choose)) }
        if (chosen != null) {
            TextButton(
                onClick = { viewModel.chooseElective(plan, item, null) },
                enabled = !viewModel.saving,
            ) { Text(stringResource(R.string.semaphore_elective_clear)) }
        }
    }

    if (choosing) {
        Text(
            text = stringResource(R.string.semaphore_elective_bank),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (bank) {
            ElectiveBank.NotAsked, ElectiveBank.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Brand,
            )
            ElectiveBank.Failed -> Text(
                text = stringResource(R.string.semaphore_elective_bank_failed),
                fontSize = 13.sp,
                color = ErrorRed,
            )
            is ElectiveBank.Ready -> {
                // An offering fills any slot unless it names the ones it fills.
                val offerings = bank.offerings.filter {
                    it.slots.isEmpty() || item.course.pensumItemCode in it.slots
                }
                if (offerings.isEmpty()) {
                    Text(
                        text = stringResource(R.string.semaphore_elective_bank_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                offerings.forEach { offering ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ItemShape)
                            .clickable(enabled = !viewModel.saving) {
                                choosing = false
                                viewModel.chooseElective(plan, item, offering.sinuCode)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (offering.sinuCode == chosen?.sinuCode) Passed else Pending,
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(offering.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = "${offering.sinuCode} · " +
                                    stringResource(R.string.semaphore_credits, offering.credits),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NamePlanDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val default = stringResource(R.string.semaphore_plan_default_name)
    var name by rememberSaveable { mutableStateOf(default) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.semaphore_new_plan)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.semaphore_plans_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    // The contract's limit: 1 to 60 characters.
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.semaphore_plan_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.semaphore_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.semaphore_cancel)) }
        },
    )
}

@Composable
private fun Detail(@StringRes label: Int, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SaveFailed() {
    Text(
        text = stringResource(R.string.semaphore_save_failed),
        modifier = Modifier.padding(top = 8.dp),
        fontSize = 13.sp,
        color = ErrorRed,
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
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/** A small square of a status's colour, outlined the way the row is for an eligible item. */
@Composable
private fun Swatch(status: CourseStatus, modifier: Modifier) {
    val (background, _) = coloursOf(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .then(
                if (status == CourseStatus.Eligible) {
                    Modifier.border(1.5.dp, Passed, RoundedCornerShape(3.dp))
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * Background and text colour per status. Green, lime, red and grey are the K's; white never goes
 * on the lime (1.5:1), purple does. *Eligible* is white with a green edge - free to take, not yet
 * taken - and *blocked* the grey of a course not taken, with softened text.
 */
private fun coloursOf(status: CourseStatus): Pair<Color, Color> = when (status) {
    CourseStatus.Passed -> Passed to Color.White
    CourseStatus.InProgress -> InProgress to Brand
    CourseStatus.Eligible -> Color.White to Color(0xFF1C1420)
    CourseStatus.Blocked -> Pending to TextSoft
    CourseStatus.Failed -> ErrorRed to Color.White
    CourseStatus.Postponed -> Postponed to OnPostponed
    CourseStatus.Other -> Pending to Color(0xFF1C1420)
}

@StringRes
private fun labelOf(status: CourseStatus): Int = when (status) {
    CourseStatus.Passed -> R.string.semaphore_status_passed
    CourseStatus.InProgress -> R.string.semaphore_status_in_progress
    CourseStatus.Eligible -> R.string.semaphore_status_eligible
    CourseStatus.Blocked -> R.string.semaphore_status_blocked
    CourseStatus.Failed -> R.string.semaphore_status_failed
    CourseStatus.Postponed -> R.string.semaphore_status_postponed
    CourseStatus.Other -> R.string.semaphore_status_other
}

/** The area's `#RRGGBB`, or the brand purple if the API sent something else. */
private fun areaColour(item: SemaphoreItem): Color = hexColor(item.areaColor, Brand)
