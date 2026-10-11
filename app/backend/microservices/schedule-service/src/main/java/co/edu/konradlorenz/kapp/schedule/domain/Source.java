package co.edu.konradlorenz.kapp.schedule.domain;

/**
 * Where a timetable came from. {@code TEST} is the adapter that serves invented timetables while the
 * university has not opened SINU, and a client shows that the data is not real.
 */
public enum Source {
    SINU,
    TEST
}
