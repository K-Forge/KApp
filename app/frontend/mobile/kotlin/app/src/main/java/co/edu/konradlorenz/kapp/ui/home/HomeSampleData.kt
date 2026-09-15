package co.edu.konradlorenz.kapp.ui.home

import co.edu.konradlorenz.kapp.ui.theme.Brand
import co.edu.konradlorenz.kapp.ui.theme.Person
import co.edu.konradlorenz.kapp.ui.theme.Subject

/**
 * The state the mockups are drawn with.
 *
 * Pepe is the student every artboard in docs/design/mobile/ uses, and the numbers are the ones on
 * HomeAndroid.dc.html: 112 credits of 142, a semester 8 that is 78.9% done. This is what the
 * screen shows until there is a repository behind it, and what the previews render.
 */
val SampleHomeUiState = HomeUiState(
    student = student("Pepe Pérez"),
    day = DayState.Classes(
        next = NextClass(
            courseName = "Bases de Datos",
            startTime = "8:00",
            endTime = "10:00",
            room = "Salón 401",
            building = "Bloque B",
            color = Subject,
            startsInMinutes = 25,
        ),
        later = listOf(
            UpcomingClass(
                courseName = "Arquitectura de SW",
                startTime = "11:00",
                endTime = "13:00",
                room = "Salón 302",
                building = "Bloque A",
                color = Brand,
            ),
            UpcomingClass(
                courseName = "Redes II",
                startTime = "14:00",
                endTime = "16:00",
                room = "Lab 105",
                building = "Bloque C",
                color = Person,
            ),
        ),
    ),
    semester = SemesterState.Ready(
        level = 8,
        coursesInProgress = 5,
        creditsPassed = 112,
        creditsInProgress = 15,
        creditsRemaining = 15,
        totalCredits = 142,
        percentComplete = 78.9,
    ),
)
