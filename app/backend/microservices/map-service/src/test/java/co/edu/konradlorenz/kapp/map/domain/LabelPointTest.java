package co.edu.konradlorenz.kapp.map.domain;

import co.edu.konradlorenz.kapp.map.migration.SurveySnapshot;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The label point: inside the room, as far from its walls as the room allows.
 */
class LabelPointTest {

    @Test
    @DisplayName("a rectangle's label point is its centre")
    void rectangle() {
        assertThat(LabelPoint.of(rect(80, 80, 240, 160))).isEqualTo(new Point(160, 120));
    }

    @Test
    @DisplayName("an L-shaped room's label point is inside the L, where the centre of its box is not")
    void lShape() {
        // An L: a tall arm on the left and a long foot along the bottom. The box's centre, (100, 100),
        // falls in the notch.
        List<Point> l = List.of(new Point(0, 0), new Point(60, 0), new Point(60, 140), new Point(200, 140),
                new Point(200, 200), new Point(0, 200));

        Point label = LabelPoint.of(l);

        assertThat(label).isEqualTo(new Point(35, 165));
        assertThat(LabelPoint.signedDistance(100, 100, l)).isNegative();
        assertThat(LabelPoint.signedDistance(label.x(), label.y(), l)).isEqualTo(35.0);
    }

    @Test
    @DisplayName("a trapezoid's label point sits toward its wide end")
    void trapezoid() {
        List<Point> wedge = List.of(new Point(0, 0), new Point(400, 80), new Point(400, 200), new Point(0, 100));

        Point label = LabelPoint.of(wedge);

        assertThat(label.x()).isGreaterThan(200);
        assertThat(LabelPoint.signedDistance(label.x(), label.y(), wedge)).isPositive();
    }

    @Test
    @DisplayName("a shape too thin for a whole point still gets one, on its walls")
    void sliver() {
        Point label = LabelPoint.of(List.of(new Point(0, 0), new Point(100, 0), new Point(100, 0), new Point(0, 0)));

        assertThat(label).isEqualTo(new Point(0, 0));
    }

    @Test
    @DisplayName("every drawn space in the seeds has its label point inside its shape")
    void everySeededSpace() {
        List<String> outside = new ArrayList<>();
        int checked = 0;
        for (SurveySnapshot.Building building : SurveySnapshot.load()) {
            for (SurveySnapshot.SnapshotFloor floor : building.floors()) {
                for (var space : floor.spaces()) {
                    if (space.shape() == null) {
                        continue;
                    }
                    List<Point> shape = MapMapper.toPoints(space.shape());
                    Point label = LabelPoint.of(shape);
                    checked++;
                    if (LabelPoint.signedDistance(label.x(), label.y(), shape) <= 0) {
                        outside.add(building.code() + " " + floor.code() + " " + space.code() + " at " + label);
                    }
                }
            }
        }

        assertThat(checked).isGreaterThan(100);
        assertThat(outside).isEmpty();
    }

    private static List<Point> rect(int x1, int y1, int x2, int y2) {
        return List.of(new Point(x1, y1), new Point(x2, y1), new Point(x2, y2), new Point(x1, y2));
    }
}
