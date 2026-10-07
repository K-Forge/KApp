package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuElectiveOffering;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The courses SINU offers each semester to fill a pensum's elective slots. Read from SINU every
 * time, never stored: the bank changes every semester.
 */
@Service
public class ElectiveBank {

    private final SinuRecordPort sinu;
    private final Clock clock;

    public ElectiveBank(SinuRecordPort sinu, Clock clock) {
        this.sinu = sinu;
        this.clock = clock;
    }

    public AcademicPeriod currentPeriod() {
        return sinu.periodOn(LocalDate.now(clock));
    }

    /** @param period null for the current one */
    public List<SinuElectiveOffering> offerings(String pensumCode, AcademicPeriod period) {
        return sinu.electiveBank(pensumCode, period == null ? currentPeriod() : period);
    }

    /** @return whether the course is offered this period for that slot of the pensum */
    public boolean offered(String pensumCode, String slotPensumItemCode, String sinuCode) {
        return offerings(pensumCode, null).stream()
                .anyMatch(o -> o.sinuCode().equals(sinuCode) && o.fills(slotPensumItemCode));
    }
}
