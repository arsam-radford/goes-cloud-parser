package org.skyview.goes;

import java.time.Instant;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.projection.sat.Geostationary;

/** Observation times and navigation for one product's own geographic grid. */
final class CloudGrid {
    private static final double MAX_LATITUDE_DEGREES = 90.0;
    private static final double MAX_LONGITUDE_DEGREES = 180.0;
    private static final double HALF_CELL = 0.5;

    private final Instant scanStart;
    private final Instant scanEnd;
    private final double[] xRadians;
    private final double[] yRadians;
    private final Geostationary projection;

    CloudGrid(Instant scanStart, Instant scanEnd, double[] xRadians, double[] yRadians, Geostationary projection) {
        this.scanStart = scanStart;
        this.scanEnd = scanEnd;
        this.xRadians = xRadians;
        this.yRadians = yRadians;
        this.projection = projection;
    }

    Instant getScanStart() {
        return scanStart;
    }

    Instant getScanEnd() {
        return scanEnd;
    }

    int getRowCount() {
        return yRadians.length;
    }

    int getColumnCount() {
        return xRadians.length;
    }

    /** Returns a linear array index, or -1 outside Earth/grid coverage. */
    int getIndexAt(double latitudeDegrees, double longitudeDegrees) {
        validateLocation(latitudeDegrees, longitudeDegrees);

        var point = projection.latLonToProj(LatLonPoint.create(latitudeDegrees, longitudeDegrees));
        int column = nearestIndex(xRadians, point.getX());
        int row = nearestIndex(yRadians, point.getY());

        if (row < 0 || column < 0) {
            return -1;
        }

        return getIndex(row, column);
    }

    int getIndex(int row, int column) {
        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
            throw new IndexOutOfBoundsException("Cloud pixel: row " + row + ", column " + column);
        }

        return row * getColumnCount() + column;
    }

    private void validateLocation(double latitudeDegrees, double longitudeDegrees) {
        if (!Double.isFinite(latitudeDegrees) || Math.abs(latitudeDegrees) > MAX_LATITUDE_DEGREES
                || !Double.isFinite(longitudeDegrees) || Math.abs(longitudeDegrees) > MAX_LONGITUDE_DEGREES) {
            throw new IllegalArgumentException("Latitude/longitude must be finite and within their geographic ranges.");
        }
    }

    /** Descending y coordinates work the same as ascending x coordinates. */
    private int nearestIndex(double[] axis, double coordinate) {
        double firstEdge = axis[0] - (axis[1] - axis[0]) * HALF_CELL;
        int last = axis.length - 1;
        double lastEdge = axis[last] + (axis[last] - axis[last - 1]) * HALF_CELL;

        if (!Double.isFinite(coordinate) || coordinate < Math.min(firstEdge, lastEdge)
                || coordinate > Math.max(firstEdge, lastEdge)) {
            return -1;
        }

        int nearest = 0;
        double distance = Math.abs(coordinate - axis[0]);

        for (int index = 1; index < axis.length; index++) {
            double candidateDistance = Math.abs(coordinate - axis[index]);

            if (candidateDistance < distance) {
                nearest = index;
                distance = candidateDistance;
            }
        }

        return nearest;
    }
}
