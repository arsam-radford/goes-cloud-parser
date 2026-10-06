package org.skyview.goes;

import java.time.Instant;
import ucar.ma2.Array;

/** A quality-checked cloud mask with its own observation times and geographic grid. */
public final class CloudMask {
    private final CloudGrid grid;
    private final Array classifications;
    private final Array probabilities;
    private final Array qualities;

    CloudMask(CloudGrid grid, Array classifications, Array probabilities, Array qualities) {
        this.grid = grid;
        this.classifications = classifications;
        this.probabilities = probabilities;
        this.qualities = qualities;
    }

    /**
     * Returns when the scan began
     * @return the first observation time, in UTC
     */
    public Instant getScanStart() {
        return grid.getScanStart();
    }

    /**
     * Returns when the scan ended
     * @return the last observation time, in UTC
     */
    public Instant getScanEnd() {
        return grid.getScanEnd();
    }

    /**
     * Returns the grid height
     * @return the number of north/south rows
     */
    public int getRowCount() {
        return grid.getRowCount();
    }

    /**
     * Reports the grid width.
     * @return the number of east/west columns
     */
    public int getColumnCount() {
        return grid.getColumnCount();
    }

    /**
     * Looks up the nearest pixel containing a location on the ellipsoid.
     * @param latitudeDegrees latitude from -90 to 90
     * @param longitudeDegrees longitude from -180 to 180, east positive
     * @return a cell, or UNKNOWN with null fields outside the satellite/grid coverage
     * @throws IllegalArgumentException if coordinates are nonfinite or outside those ranges
     */
    public CloudCell getCellAt(double latitudeDegrees, double longitudeDegrees) {
        int index = grid.getIndexAt(latitudeDegrees, longitudeDegrees);
        if (index < 0) {
            return new CloudCell(Double.NaN, Double.NaN, Double.NaN);
        }

        return readCell(index);
    }

    /**
     * Returns a pixel by its file row and column.
     * @param row zero based row
     * @param column zero based column
     * @return the quality checked cell
     * @throws IndexOutOfBoundsException if either index is outside the grid
     */
    public CloudCell getCell(int row, int column) {
        return readCell(grid.getIndex(row, column));
    }

    private CloudCell readCell(int index) {
        return new CloudCell(classifications.getDouble(index), probabilities.getDouble(index), qualities.getDouble(index));
    }
}
