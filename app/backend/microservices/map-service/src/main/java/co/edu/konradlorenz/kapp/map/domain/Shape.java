package co.edu.konradlorenz.kapp.map.domain;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Geometry of a polygon given as its corners, in order: the outline of a space or of a floor.
 *
 * <p>Rooms are not rectangles. The evacuation plans draw rooms in L, rooms cut by a stair and a
 * house with a diagonal wall, so a space is a polygon; a rectangle is simply one with four corners.
 */
public final class Shape {

    /** Below this many square units two shapes only touch: they share a wall, not a room. */
    private static final double OVERLAP_TOLERANCE = 1.0;

    private Shape() {
    }

    public static double area(List<Point> points) {
        double twice = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            twice += (double) a.x() * b.y() - (double) b.x() * a.y();
        }
        return Math.abs(twice) / 2;
    }

    /** True when no two non-adjacent edges cross or touch: the outline does not fold over itself. */
    public static boolean isSimple(List<Point> points) {
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Point a = points.get(i), b = points.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) {
                    continue;
                }
                Point c = points.get(j), d = points.get((j + 1) % n);
                if (segmentsTouch(a, b, c, d)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean within(List<Point> points, int width, int height) {
        return points.stream().allMatch(p -> p.x() >= 0 && p.y() >= 0 && p.x() <= width && p.y() <= height);
    }

    /** True when the two shapes share some floor, not just an edge or a corner. */
    public static boolean overlap(List<Point> a, List<Point> b) {
        Rectangle2D ba = bounds(a), bb = bounds(b);
        if (!ba.intersects(bb)) {
            return false;
        }
        Area shared = new Area(path(a));
        shared.intersect(new Area(path(b)));
        if (shared.isEmpty()) {
            return false;
        }
        Rectangle2D r = shared.getBounds2D();
        return r.getWidth() * r.getHeight() > OVERLAP_TOLERANCE && area(shared) > OVERLAP_TOLERANCE;
    }

    /**
     * Whether a door from {@code a} to {@code b} lies along one edge of the outline, to within a
     * unit - the rounding of a traced plan.
     */
    public static boolean onOutline(List<Point> points, Point a, Point b) {
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            Point q = points.get((i + 1) % points.size());
            if (Line2D.ptSegDist(p.x(), p.y(), q.x(), q.y(), a.x(), a.y()) <= 1.0
                    && Line2D.ptSegDist(p.x(), p.y(), q.x(), q.y(), b.x(), b.y()) <= 1.0) {
                return true;
            }
        }
        return false;
    }

    public static Rectangle2D bounds(List<Point> points) {
        return path(points).getBounds2D();
    }

    private static Path2D path(List<Point> points) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.moveTo(points.getFirst().x(), points.getFirst().y());
        for (Point p : points.subList(1, points.size())) {
            path.lineTo(p.x(), p.y());
        }
        path.closePath();
        return path;
    }

    private static double area(Area area) {
        double total = 0;
        double[] c = new double[6];
        double startX = 0, startY = 0, lastX = 0, lastY = 0;
        for (var it = area.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case java.awt.geom.PathIterator.SEG_MOVETO -> { startX = lastX = c[0]; startY = lastY = c[1]; }
                case java.awt.geom.PathIterator.SEG_LINETO -> {
                    total += lastX * c[1] - c[0] * lastY;
                    lastX = c[0];
                    lastY = c[1];
                }
                case java.awt.geom.PathIterator.SEG_CLOSE -> {
                    total += lastX * startY - startX * lastY;
                    lastX = startX;
                    lastY = startY;
                }
                default -> { }
            }
        }
        return Math.abs(total) / 2;
    }

    private static boolean segmentsTouch(Point a, Point b, Point c, Point d) {
        long d1 = cross(c, d, a), d2 = cross(c, d, b), d3 = cross(a, b, c), d4 = cross(a, b, d);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return (d1 == 0 && onSegment(c, d, a)) || (d2 == 0 && onSegment(c, d, b))
                || (d3 == 0 && onSegment(a, b, c)) || (d4 == 0 && onSegment(a, b, d));
    }

    private static long cross(Point o, Point a, Point b) {
        return (long) (a.x() - o.x()) * (b.y() - o.y()) - (long) (a.y() - o.y()) * (b.x() - o.x());
    }

    private static boolean onSegment(Point a, Point b, Point p) {
        return Math.min(a.x(), b.x()) <= p.x() && p.x() <= Math.max(a.x(), b.x())
                && Math.min(a.y(), b.y()) <= p.y() && p.y() <= Math.max(a.y(), b.y());
    }
}
