package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Point;

import java.util.List;

/**
 * How the grid the map used to be drawn on turns into the floor's units.
 *
 * <p>Until map model v3 a floor was a grid and a space a rectangle of cells. Each cell becomes a
 * square of {@link #CELL} units: a room keeps exactly the place and size it had, and a corridor
 * that ran through the middle of cells still does.
 */
final class GridCells {

    /** Units per grid cell. Forty keeps a converted floor about as wide as one traced from a photo. */
    static final int CELL = 40;

    private GridCells() {
    }

    static List<Point> rectangle(int row, int col, int rowSpan, int colSpan) {
        int x0 = col * CELL, y0 = row * CELL, x1 = (col + colSpan) * CELL, y1 = (row + rowSpan) * CELL;
        return List.of(new Point(x0, y0), new Point(x1, y0), new Point(x1, y1), new Point(x0, y1));
    }

    static Point centre(int row, int col) {
        return new Point(col * CELL + CELL / 2, row * CELL + CELL / 2);
    }
}
