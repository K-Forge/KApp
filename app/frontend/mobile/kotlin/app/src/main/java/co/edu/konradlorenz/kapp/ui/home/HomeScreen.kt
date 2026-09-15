package co.edu.konradlorenz.kapp.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.ui.common.BandContentHeight
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding
import co.edu.konradlorenz.kapp.ui.navigation.BarContentPadding
import co.edu.konradlorenz.kapp.ui.navigation.KAppDestination
import co.edu.konradlorenz.kapp.ui.navigation.MainShell
import co.edu.konradlorenz.kapp.ui.theme.Border
import co.edu.konradlorenz.kapp.ui.theme.BorderSoft
import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.InProgress
import co.edu.konradlorenz.kapp.ui.theme.Passed
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme
import co.edu.konradlorenz.kapp.ui.theme.Placeholder
import co.edu.konradlorenz.kapp.ui.theme.Subject
import kotlin.math.roundToInt

// Every measurement below is read off docs/design/mobile/HomeAndroid.dc.html, which is drawn on a
// 360x800 canvas. The pixels there are dp here.
private val CardOverlap = 24.dp
private val HeadlineCardHeight = 166.dp
private val CardShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(16.dp)

/**
 * Inicio.
 *
 * Everything that names another screen opens it through [onOpen]: the four shortcuts, the two
 * section links, "Como llegar", and the button on each of the empty states. All four of those
 * screens are still placeholders, so what the student lands on is a card naming the contract that
 * will fill it - which is the honest version of the destination, not a dead tap.
 *
 * The bar itself is not here: it belongs to every tab, so it lives in MainShell and this screen
 * only leaves room for it.
 *
 * The data is [SampleHomeUiState] until there is a repository behind [HomeViewModel].
 */
@Composable
fun HomeScreen(
    onOpen: (KAppDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    HomeContent(state = viewModel.uiState, onOpen = onOpen, modifier = modifier)
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpen: (KAppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The band runs to the very top of the display and the system paints the status bar over
        // it, which is why MainActivity forces white status icons: they are always on purple.
        HomeBand(student = state.student)

        // The band is 80 dp of content below the status bar, and the headline card climbs 24 dp
        // back into it. Starting the scrolling column 24 dp short of the band's edge puts the card
        // exactly where the mockup draws it, and makes the column the thing on top - so the card's
        // corners and its shadow land on the purple rather than behind it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = BandContentHeight - CardOverlap)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = BarContentPadding,
                )
                .navigationBarsPadding(),
        ) {
            when (state.day) {
                DayState.Loading -> HeadlineSkeleton()
                DayState.NoSchedule -> NoScheduleCard(onOpen)
                DayState.NoClassesToday -> FreeDayCard(onOpen)
                is DayState.Classes -> NextClassCard(state.day.next, onOpen)
            }

            Spacer(Modifier.height(16.dp))
            Shortcuts(onOpen)

            SectionHeading(
                title = stringResource(R.string.home_semester_title),
                link = stringResource(R.string.home_semester_link),
                onLink = { onOpen(KAppDestination.Semaphore) },
            )
            when (state.semester) {
                SemesterState.Loading -> SemesterSkeleton()
                is SemesterState.Ready -> SemesterCard(state.semester)
            }

            // "Resto del dia" is the classes after the one on the card. With none of them there is
            // no heading either: an empty list under a title reads as something having failed.
            val later = (state.day as? DayState.Classes)?.later.orEmpty()
            if (later.isNotEmpty()) {
                SectionHeading(
                    title = stringResource(R.string.home_rest_title),
                    link = stringResource(R.string.home_rest_link),
                    onLink = { onOpen(KAppDestination.Schedule) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    later.forEach { UpcomingRow(it) }
                }
            }
        }
    }
}

/** The band of BrandBand, with the greeting in it and the student's initials on the end. */
@Composable
private fun HomeBand(student: Student) {
    BrandBand(title = stringResource(R.string.home_greeting, student.firstName)) {
        // Initials rather than a photo: user.openapi.yaml carries a name and no picture.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = student.initials,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/**
 * The card at the top of the screen, on its subject's colour.
 *
 * The rail colour is `ClassOccurrence.color`, which the API sends and the client only paints - see
 * the second note under "Pendientes de backend" in docs/design/mobile/README.md: if a subject can
 * come back pink, pink stops meaning "you can touch this" and the button below loses its meaning.
 */
@Composable
private fun NextClassCard(next: NextClass, onOpen: (KAppDestination) -> Unit) {
    RailCard(rail = next.color, railWidth = 6.dp, shape = CardShape, elevation = 10.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.home_next_class),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (next.startsInMinutes != null) {
                    // Purple on green, never white: the lime is 1.5:1 against it.
                    Text(
                        text = stringResource(R.string.home_next_starts_in, next.startsInMinutes),
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(InProgress.copy(alpha = 0.32f))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Brand,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = next.courseName,
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.home_next_when_where,
                    next.startTime,
                    next.endTime,
                    next.room,
                    next.building,
                ),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            DirectionsButton(onClick = { onOpen(KAppDestination.Map) })
        }
    }
}

/**
 * "Como llegar".
 *
 * The only pink thing on the card, so the only thing on the card that is touchable. It opens the
 * map, which is still a placeholder: the room it should centre on arrives with map-service.
 */
@Composable
private fun DirectionsButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RowShape)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pin),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = Color.White,
        )
        Text(
            text = stringResource(R.string.home_directions),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/**
 * A day with a schedule and nothing on it.
 *
 * Same height as the card it replaces, so the four shortcuts under it do not move between one
 * student's screen and another's.
 */
@Composable
private fun FreeDayCard(onOpen: (KAppDestination) -> Unit) {
    CenteredCard(height = HeadlineCardHeight) {
        IconBubble(R.drawable.ic_free_day, colour = InProgress, alpha = 0.30f, iconColour = Brand)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_free_day_title),
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_free_day_link),
            // The padding sits inside the clickable, so it widens the tap area rather than only
            // the text.
            modifier = Modifier
                .clickable { onOpen(KAppDestination.Schedule) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A student who has never built a schedule - `day` answers 404.
 *
 * Not an error: a 404 here means there is nothing to show yet, so the card asks for the missing
 * work instead of reporting a failure.
 */
@Composable
private fun NoScheduleCard(onOpen: (KAppDestination) -> Unit) {
    CenteredCard(height = 208.dp) {
        IconBubble(R.drawable.ic_calendar_plus, colour = Subject, alpha = 0.16f)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_no_schedule_title),
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_no_schedule_body),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RowShape)
                .clickable { onOpen(KAppDestination.Schedule) }
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_no_schedule_action),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

/** The headline card before schedule-service has answered. */
@Composable
private fun HeadlineSkeleton() {
    val loading = stringResource(R.string.home_loading)
    Panel(elevation = 10.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeadlineCardHeight)
                .padding(18.dp)
                // One label for the whole card: a screen reader has nothing to read out of four
                // grey bars.
                .semantics { contentDescription = loading },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBar(width = 104.dp, height = 11.dp, radius = 6.dp)
            SkeletonBar(width = 188.dp, height = 20.dp, radius = 7.dp)
            SkeletonBar(width = 236.dp, height = 13.dp, color = BorderSoft, radius = 6.dp)
            SkeletonBar(width = 142.dp, height = 44.dp, color = BorderSoft, radius = 14.dp)
        }
    }
}

/**
 * Semaforo, Horario, Mapa and Perfil: the same four destinations the bar has, in the order this
 * card draws them, each on its own colour of the K.
 *
 * The blue is at 0.14 rather than 0.12 because it is the lightest of the four and needs the extra
 * step to weigh the same as the others.
 */
@Composable
private fun Shortcuts(onOpen: (KAppDestination) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Shortcut(KAppDestination.Semaphore, alpha = 0.12f, onOpen = onOpen)
        Shortcut(KAppDestination.Schedule, alpha = 0.14f, onOpen = onOpen)
        Shortcut(KAppDestination.Map, alpha = 0.12f, onOpen = onOpen)
        Shortcut(KAppDestination.Profile, alpha = 0.12f, onOpen = onOpen)
    }
}

@Composable
private fun RowScope.Shortcut(
    destination: KAppDestination,
    alpha: Float,
    onOpen: (KAppDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(TileShape)
            .clickable { onOpen(destination) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(TileShape)
                .background(destination.colour.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(destination.icon),
                // The label below says the same thing.
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = destination.colour,
            )
        }
        Text(
            text = stringResource(destination.label),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * A section title with its link on the right.
 *
 * The mockup draws the row 22 dp tall with 22 above and 10 below. Here the link carries 8 dp of
 * padding so it is something a thumb can hit, which grows the row to about 34; the air above and
 * below is taken back down to keep the block the height the mockup gives it.
 */
@Composable
private fun SectionHeading(title: String, link: String, onLink: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = link,
            modifier = Modifier
                .clickable(onClick = onLink)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(6.dp))
}

/** Credits through the pensum: passed, being taken, and the rest of the track. */
@Composable
private fun SemesterCard(semester: SemesterState.Ready) {
    Panel(elevation = 2.dp) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_semester_level, semester.level),
                        style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_semester_courses,
                            semester.coursesInProgress,
                            semester.coursesInProgress,
                        ),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    // 78.9 is printed as 79: one decimal of a percentage is noise at this size.
                    text = stringResource(
                        R.string.home_semester_percent,
                        semester.percentComplete.roundToInt(),
                    ),
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Brand,
                )
            }
            Spacer(Modifier.height(12.dp))
            ProgressBar(semester)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Legend(Passed, R.plurals.home_credits_passed, semester.creditsPassed)
                Legend(InProgress, R.plurals.home_credits_in_progress, semester.creditsInProgress)
                Legend(Border, R.plurals.home_credits_remaining, semester.creditsRemaining)
            }
        }
    }
}

/**
 * Green for passed, lime for being taken, and the grey track showing through for the rest.
 *
 * The segments are weights rather than fractions of the width: inside a Row a second child asking
 * for a fraction would be measuring against what is left, not against the whole bar. A zero-credit
 * segment is left out, because a weight has to be greater than zero.
 */
@Composable
private fun ProgressBar(semester: SemesterState.Ready) {
    val passed = semester.passedFraction
    val inProgress = semester.inProgressFraction
    val rest = 1f - passed - inProgress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Border),
    ) {
        if (passed > 0f) {
            Box(Modifier.weight(passed).fillMaxHeight().background(Passed))
        }
        if (inProgress > 0f) {
            Box(Modifier.weight(inProgress).fillMaxHeight().background(InProgress))
        }
        if (rest > 0f) {
            Spacer(Modifier.weight(rest))
        }
    }
}

@Composable
private fun Legend(colour: Color, @PluralsRes label: Int, credits: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colour),
        )
        Text(
            text = pluralStringResource(label, credits, credits),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The semester card before semaphore-service has answered. */
@Composable
private fun SemesterSkeleton() {
    val loading = stringResource(R.string.home_loading)
    Panel(elevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { contentDescription = loading },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SkeletonBar(width = 96.dp, height = 14.dp, radius = 7.dp)
                SkeletonBar(width = 54.dp, height = 22.dp, radius = 7.dp)
            }
            SkeletonBar(width = null, height = 10.dp, color = BorderSoft, radius = 5.dp)
            SkeletonBar(width = 220.dp, height = 11.dp, color = BorderSoft, radius = 6.dp)
        }
    }
}

/** One of the later classes of the day. */
@Composable
private fun UpcomingRow(upcoming: UpcomingClass) {
    RailCard(rail = upcoming.color, railWidth = 4.dp, shape = RowShape, elevation = 2.dp) {
        Row(
            modifier = Modifier
                .height(58.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(42.dp)) {
                Text(
                    text = upcoming.startTime,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = upcoming.endTime,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = Placeholder,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = upcoming.courseName,
                    style = MaterialTheme.typography.labelLarge.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(
                        R.string.home_class_where,
                        upcoming.room,
                        upcoming.building,
                    ),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shapes the screen repeats.
// ---------------------------------------------------------------------------------------------

/** A white card with the shadow the mockup gives it. */
@Composable
private fun Panel(
    elevation: Dp,
    shape: RoundedCornerShape = CardShape,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, shape, ambientColor = Brand, spotColor = Brand)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

/**
 * A card with a coloured rail down its left edge.
 *
 * `IntrinsicSize.Min` rather than a fixed height: the rail has to run the full height of whatever
 * the text turns out to be, and at the default font scale that height is the one the mockup draws.
 */
@Composable
private fun RailCard(
    rail: Color,
    railWidth: Dp,
    shape: RoundedCornerShape,
    elevation: Dp,
    content: @Composable () -> Unit,
) {
    Panel(elevation = elevation, shape = shape) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .background(rail),
            )
            content()
        }
    }
}

/** The two empty states: a bubble, a line or two, and something to do about it. */
@Composable
private fun CenteredCard(height: Dp, content: @Composable () -> Unit) {
    Panel(elevation = 10.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

/**
 * A 48 dp disc of a colour at low opacity, with an icon in it.
 *
 * [iconColour] is separate because the free day draws a purple lamp on lime: white and lime are
 * 1.5:1, and so are lime on lime.
 */
@Composable
private fun IconBubble(
    @DrawableRes icon: Int,
    colour: Color,
    alpha: Float,
    iconColour: Color = colour,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colour.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = iconColour,
        )
    }
}

/** One grey bar of a skeleton. A null width fills the row. */
@Composable
private fun SkeletonBar(
    width: Dp?,
    height: Dp,
    color: Color = Border,
    radius: Dp = height / 2,
) {
    val sizing = if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)
    Box(
        sizing
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(color),
    )
}

// ---------------------------------------------------------------------------------------------
// The four states, at the size the mockups are drawn at.
// ---------------------------------------------------------------------------------------------

/** The shell is in the previews because the bar is part of what the mockup draws. */
@Composable
private fun HomePreview(state: HomeUiState) {
    KAppTheme {
        MainShell(current = KAppDestination.Home, onSelect = {}) {
            HomeContent(state = state, onOpen = {})
        }
    }
}

@Preview(name = "Inicio", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    HomePreview(SampleHomeUiState)
}

@Preview(name = "Inicio · cargando", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeLoadingPreview() {
    HomePreview(
        SampleHomeUiState.copy(day = DayState.Loading, semester = SemesterState.Loading),
    )
}

@Preview(name = "Inicio · hoy sin clases", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeFreeDayPreview() {
    HomePreview(SampleHomeUiState.copy(day = DayState.NoClassesToday))
}

@Preview(name = "Inicio · sin horario", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HomeNoSchedulePreview() {
    HomePreview(SampleHomeUiState.copy(day = DayState.NoSchedule))
}
