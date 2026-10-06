package org.skyview.goes;

import java.time.Instant;
import ucar.ma2.Array;

/** A cloud-top height grid with its own resolution, quality flags, and observation times. */
public final class CloudTopHeight {
    private final CloudGrid grid;
    private final Array heights;
    private final Array qualities;

    CloudTopHeight(CloudGrid grid, Array heights, Array qualities) {
        this.grid = grid;
        this.heights = heights;
        this.qualities = qualities;
    }

    /** @return the first observation time, in UTC */
    public Instant getScanStart() {
        return grid.getScanStart();
    }

    /** @return the last observation time, in UTC */
    public Instant getScanEnd() {
        return grid.getScanEnd();
    }

    /** @return the number of north/south rows */
    public int getRowCount() {
        return grid.getRowCount();
    }

    /** @return the number of east/west columns */
    public int getColumnCount() {
        return grid.getColumnCount();
    }

    /**
     * Looks up the nearest pixel at a location on the ellipsoid; no cloud parallax correction is applied.
     * @param latitudeDegrees latitude from -90 to 90
     * @param longitudeDegrees longitude from -180 to 180, east positive
     * @return a quality-checked height, or null fields outside coverage
     * @throws IllegalArgumentException if coordinates are nonfinite or out of range
     */
    public CloudHeightCell getCellAt(double latitudeDegrees, double longitudeDegrees) {
        int index = grid.getIndexAt(latitudeDegrees, longitudeDegrees);
        if (index < 0) {
            return new CloudHeightCell(Double.NaN, Double.NaN);
        }

        return readCell(index);
    }

    /**
     * Returns a pixel by its file row and column.
     * @param row zero based row
     * @param column zero based column
     * @return its quality-checked height
     * @throws IndexOutOfBoundsException if either index is outside the grid
     */
    public CloudHeightCell getCell(int row, int column) {
        return readCell(grid.getIndex(row, column));
    }

    private CloudHeightCell readCell(int index) {
        return new CloudHeightCell(heights.getDouble(index), qualities.getDouble(index));
    }
}
