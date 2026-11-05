package co.edu.konradlorenz.kapp.map.domain;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Where a space's label and icon go: the point inside its shape farthest from its walls.
 *
 * <p>The centre of the box around a room in L falls in the notch, outside the room. This is the
 * pole of inaccessibility instead, found as the polylabel algorithm finds it: cover the shape with
 * square cells, keep splitting the cells that could still hold a point farther from the walls than
 * the best one found, and stop once no cell can beat it by more than {@link #PRECISION}. The result
 * is then the best of the four whole points around it, since the API speaks in whole units.
 *
 * <p>The contract's floor example was computed with this same procedure, step by step; a test
 * keeps the two in agreement.
 */
public final class LabelPoint {

    /** How close to the true pole is close enough, in the floor's units. */
    static final double PRECISION = 1.0;

    private static final double SQRT2 = Math.sqrt(2);

    private LabelPoint() {
    }

    /** @return the label point of a shape of three or more corners, in order */
    public static Point of(List<Point> shape) {
        double[] pole = pole(shape);
        int x = (int) Math.floor(pole[0]);
        int y = (int) Math.floor(pole[1]);
        Point best = null;
        double bestDistance = Double.NEGATIVE_INFINITY;
        for (Point candidate : List.of(new Point(x, y), new Point(x + 1, y), new Point(x, y + 1), new Point(x + 1, y + 1))) {
            double distance = signedDistance(candidate.x(), candidate.y(), shape);
            if (distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * How far a point is from the shape's walls: positive inside, negative outside.
     */
    public static double signedDistance(double x, double y, List<Point> shape) {
        boolean inside = false;
        double minSquared = Double.POSITIVE_INFINITY;
        int n = shape.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = shape.get(i);
            Point b = shape.get(j);
            if ((a.y() > y) != (b.y() > y)
                    && x < (double) (b.x() - a.x()) * (y - a.y()) / (b.y() - a.y()) + a.x()) {
                inside = !inside;
            }
            minSquared = Math.min(minSquared, segmentDistanceSquared(x, y, a, b));
        }
        double distance = Math.sqrt(minSquared);
        return inside ? distance : -distance;
    }

    private static double[] pole(List<Point> shape) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : shape) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        double width = maxX - minX;
        double height = maxY - minY;
        double cellSize = Math.min(width, height);
        if (cellSize == 0) {
            return new double[] {minX, minY};
        }

        // Highest potential first; among equals, the cell made first.
        PriorityQueue<Cell> queue = new PriorityQueue<>(
                Comparator.comparingDouble((Cell c) -> -c.max).thenComparingLong(c -> c.order));
        long order = 0;
        double h = cellSize / 2;
        for (double x = minX; x < maxX; x += cellSize) {
            for (double y = minY; y < maxY; y += cellSize) {
                queue.add(new Cell(x + h, y + h, h, shape, order++));
            }
        }

        Cell best = centroid(shape);
        Cell box = new Cell(minX + width / 2, minY + height / 2, 0, shape, -1);
        if (box.distance > best.distance) {
            best = box;
        }

        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (cell.distance > best.distance) {
                best = cell;
            }
            if (cell.max - best.distance <= PRECISION) {
                continue;
            }
            double half = cell.half / 2;
            queue.add(new Cell(cell.x - half, cell.y - half, half, shape, order++));
            queue.add(new Cell(cell.x + half, cell.y - half, half, shape, order++));
            queue.add(new Cell(cell.x - half, cell.y + half, half, shape, order++));
            queue.add(new Cell(cell.x + half, cell.y + half, half, shape, order++));
        }
        return new double[] {best.x, best.y};
    }

    /** The cell at the shape's centre of mass: often already near the pole, so a good first best. */
    private static Cell centroid(List<Point> shape) {
        double area = 0, x = 0, y = 0;
        int n = shape.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point a = shape.get(i);
            Point b = shape.get(j);
            double f = (double) a.x() * b.y() - (double) b.x() * a.y();
            x += (a.x() + b.x()) * f;
            y += (a.y() + b.y()) * f;
            area += f * 3;
        }
        if (area == 0) {
            return new Cell(shape.getFirst().x(), shape.getFirst().y(), 0, shape, -1);
        }
        return new Cell(x / area, y / area, 0, shape, -1);
    }

    private static double segmentDistanceSquared(double px, double py, Point a, Point b) {
        double x = a.x(), y = a.y();
        double dx = b.x() - x, dy = b.y() - y;
        if (dx != 0 || dy != 0) {
            double t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy);
            if (t > 1) {
                x = b.x();
                y = b.y();
            } else if (t > 0) {
                x += dx * t;
                y += dy * t;
            }
        }
        dx = px - x;
        dy = py - y;
        return dx * dx + dy * dy;
    }

    /** A square cell: its centre, half its side, its centre's distance to the walls, and the most any point in it could reach. */
    private static final class Cell {
        final double x, y, half, distance, max;
        final long order;

        Cell(double x, double y, double half, List<Point> shape, long order) {
            this.x = x;
            this.y = y;
            this.half = half;
            this.distance = signedDistance(x, y, shape);
            this.max = distance + half * SQRT2;
            this.order = order;
        }
    }
}
