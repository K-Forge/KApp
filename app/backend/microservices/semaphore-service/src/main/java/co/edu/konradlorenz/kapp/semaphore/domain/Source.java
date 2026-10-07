package co.edu.konradlorenz.kapp.semaphore.domain;

/**
 * Where a semáforo came from. {@code TEST} is the adapter that serves invented data while the
 * university has not opened SINU, and a client shows that the data is not real.
 */
public enum Source {
    SINU,
    TEST
}
