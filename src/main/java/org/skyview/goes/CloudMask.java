package org.skyview.goes;

import java.time.Instant;
import ucar.ma2.Array;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.projection.sat.Geostationary;

public final class CloudMask {
    private static final double MAX_LATITUDE_DEGREES = 90.0;
    private static final double MAX_LONGITUDE_DEGREES = 180.0;

    private final Instant scanStart;
    private final Instant scanEnd;
    private final double[] xRadians;
    private final double[] yRadians;
    private final Array classifications;
    private final Array probabilities;
    private final Array qualities;
    private final Geostationary projection;

    CloudMask(Instant scanStart, Instant scanEnd, double[] xRadians, double[] yRadians,
            Array classifications, Array probabilities, Array qualities, Geostationary projection) {
        this.scanStart = scanStart;
        this.scanEnd = scanEnd;

        this.xRadians = xRadians;
        this.yRadians = yRadians;

        this.classifications = classifications;
        this.probabilities = probabilities;
        this.qualities = qualities;
        this.projection = projection;
    }

    /**
     * Returns when the scan began
     * @return the first observation time, in UTC
     */
    public Instant getScanStart() {
        return scanStart;
    }

    /**
     * Returns when the scan ended
     * @return the last observation time, in UTC
     */
    public Instant getScanEnd() {
        return scanEnd;
    }

    /**
     * Returns the grid height
     * @return the number of north/south rows
     */
    public int getRowCount() {
        return yRadians.length;
    }

    /**
     * Reports the grid width.
     * @return the number of east/west columns
     */
    public int getColumnCount() {
        return xRadians.length;
    }

    /**
     * Looks up the nearest pixel containing a location on the ellipsoid.
     * @param latitudeDegrees latitude from -90 to 90
     * @param longitudeDegrees longitude from -180 to 180, east positive
     * @return a cell, or UNKNOWN with null fields outside the satellite/grid coverage
     * @throws IllegalArgumentException if coordinates are nonfinite or outside those ranges
     */
    public CloudCell getCellAt(double latitudeDegrees, double longitudeDegrees) {
        validateLocation(latitudeDegrees, longitudeDegrees);

        var point = projection.latLonToProj(LatLonPoint.create(latitudeDegrees, longitudeDegrees));
        int column = nearestIndex(xRadians, point.getX());
        int row = nearestIndex(yRadians, point.getY());

        if (row < 0 || column < 0) {
            return new CloudCell(Double.NaN, Double.NaN, Double.NaN);
        }

        return getCell(row, column);
    }

    /**
     * Returns a pixel by its file row and column.
     * @param row zero based row
     * @param column zero based column
     * @return the quality checked cell
     * @throws IndexOutOfBoundsException if either index is outside the grid
     */
    public CloudCell getCell(int row, int column) {
        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
            throw new IndexOutOfBoundsException("Cloud-mask pixel: row " + row + ", column " + column);
        }

        int index = row * getColumnCount() + column;
        return new CloudCell(classifications.getDouble(index), probabilities.getDouble(index), qualities.getDouble(index));
    }

    /** Validates the latitude and longitude coordinates. */
    private void validateLocation(double latitudeDegrees, double longitudeDegrees) {
        if (!Double.isFinite(latitudeDegrees) || Math.abs(latitudeDegrees) > MAX_LATITUDE_DEGREES
                || !Double.isFinite(longitudeDegrees) || Math.abs(longitudeDegrees) > MAX_LONGITUDE_DEGREES) {
            throw new IllegalArgumentException("Latitude/longitude must be finite and within their geographic ranges.");
        }
    }

    /**negative y coordinates work the same as positive x */
    private int nearestIndex(double[] axis, double coordinate) {
        double firstEdge = axis[0] - (axis[1] - axis[0]) / 2;
        int last = axis.length - 1;
        double lastEdge = axis[last] + (axis[last] - axis[last - 1]) / 2;

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
