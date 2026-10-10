package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuElectiveOffering;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * Wire shape of {@code ElectiveOffering}: a course SINU offers in one period to fill elective slots.
 *
 * @param slots the pensumItemCodes of the slots it fills; empty means any elective slot of the pensum
 */
public record ElectiveOfferingDto(
        String sinuCode,
        String name,
        int credits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double weeklyHours,
        List<String> slots
) {

    public static ElectiveOfferingDto from(SinuElectiveOffering offering) {
        return new ElectiveOfferingDto(offering.sinuCode(), offering.name(), offering.credits(),
                offering.weeklyHours(), offering.slots());
    }
}
