package co.edu.konradlorenz.kapp.schedule.color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CourseColorsTest {

    private static final List<String> SIX = List.of("13013", "46033", "31404", "31614", "46012", "75081");

    @Test
    @DisplayName("the palette is the six colours of K-COLORS.md that are not its pink")
    void palette() {
        assertThat(CourseColors.PALETTE).hasSize(6).doesNotContain("#D51A65").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("up to six courses never share a colour")
    void distinct() {
        assertThat(CourseColors.assign(SIX).values()).doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    @DisplayName("the same courses are painted the same way, whatever order they come in")
    void deterministic() {
        Map<String, String> once = CourseColors.assign(SIX);
        assertThat(CourseColors.assign(SIX.reversed())).isEqualTo(once);
    }

    @Test
    @DisplayName("a course alone, or with others, starts from its own colour")
    void ownColour() {
        String alone = CourseColors.assign(List.of("13013")).get("13013");
        assertThat(CourseColors.assign(List.of("13013", "99999")).get("13013")).isEqualTo(alone);
        assertThat(CourseColors.assign(SIX).get("13013")).isEqualTo(alone);
    }

    @Test
    @DisplayName("a seventh course reuses a colour rather than having none")
    void moreThanSix() {
        List<String> seven = new java.util.ArrayList<>(SIX);
        seven.add("11015");
        Map<String, String> colors = CourseColors.assign(seven);
        assertThat(colors).hasSize(7);
        assertThat(colors.values()).allMatch(CourseColors.PALETTE::contains);
    }
}
