package org.skyview.goes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDatasets;
import ucar.nc2.dataset.VariableDS;
import ucar.unidata.geoloc.projection.sat.Geostationary;

/** Decodes downloaded GOES-19 CONUS cloud masks and cloud-top height files. */
public final class GoesCloudMaskParser {
    private static final String MASK_PRODUCT = "ABI-L2-ACMC";
    private static final String HEIGHT_PRODUCT = "ABI-L2-ACHAC";
    private static final int MIN_AXIS_LENGTH = 2;
    private static final int MAX_GRID_CELLS = 4_000_000;
    private static final double MAX_LONGITUDE_DEGREES = 180.0;

    public GoesCloudMaskParser() {
    }

    /**
     * Reads the mask into memory and closes the file before returning.
     * @param netcdfFile local GOES-19 CONUS clear-sky-mask file
     * @return its decoded grid and scan timestamps
     * @throws IllegalArgumentException if the path is null
     * @throws IOException if reading fails or required data/metadata are malformed
     */
    public CloudMask parse(Path netcdfFile) throws IOException {
        if (netcdfFile == null) {
            throw new IllegalArgumentException("A local NetCDF file path is required.");
        }

        try (var file = NetcdfDatasets.openDataset(netcdfFile.toAbsolutePath().toString())) {
            validateProduct(file, MASK_PRODUCT);
            return readMask(file);
        } catch (IOException | RuntimeException exception) {
            throw new IOException("GOES file " + netcdfFile + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Reads cloud-top geopotential heights into memory and closes the file before returning.
     * @param netcdfFile local GOES-19 CONUS cloud-top height file
     * @return its own height grid and scan timestamps
     * @throws IllegalArgumentException if the path is null
     * @throws IOException if reading fails or required data/metadata are malformed
     */
    public CloudTopHeight parseCloudTopHeight(Path netcdfFile) throws IOException {
        if (netcdfFile == null) {
            throw new IllegalArgumentException("A local NetCDF file path is required.");
        }

        try (var file = NetcdfDatasets.openDataset(netcdfFile.toAbsolutePath().toString())) {
            validateProduct(file, HEIGHT_PRODUCT);
            return readCloudTopHeight(file);
        } catch (IOException | RuntimeException exception) {
            throw new IOException("GOES height file " + netcdfFile + ": " + exception.getMessage(), exception);
        }
    }

    /** Reads only the fields needed for cloud lookup, avoiding extra ABI variables */
    private CloudMask readMask(NetcdfFile file) throws IOException {
        CloudGrid grid = readGrid(file);
        VariableDS acm = readGridVariable(file, "ACM", grid.getRowCount(), grid.getColumnCount());
        VariableDS probability = readGridVariable(file, "Cloud_Probabilities", grid.getRowCount(), grid.getColumnCount());
        VariableDS quality = readGridVariable(file, "DQF", grid.getRowCount(), grid.getColumnCount());

        return new CloudMask(grid, acm.read(), probability.read(), quality.read());
    }

    /** Uses NetCDF enhancement for height scaling, fill values, and valid-range checks. */
    private CloudTopHeight readCloudTopHeight(NetcdfFile file) throws IOException {
        CloudGrid grid = readGrid(file);
        VariableDS height = readGridVariable(file, "HT", grid.getRowCount(), grid.getColumnCount());
        VariableDS quality = readGridVariable(file, "DQF", grid.getRowCount(), grid.getColumnCount());

        if (!height.findAttributeString("units", "").equals("m")
                || !height.findAttributeString("standard_name", "").equals("geopotential_height_at_cloud_top")) {
            throw new IOException("HT must be cloud-top geopotential height in metres.");
        }

        height.setInvalidDataIsMissing(true);

        return new CloudTopHeight(grid, height.read(), quality.read());
    }

    /** Each product supplies its own axes, navigation, and observation times. */
    private CloudGrid readGrid(NetcdfFile file) throws IOException {
        double[] x = readAxis(file, "x");
        double[] y = readAxis(file, "y");

        if ((long) x.length * y.length > MAX_GRID_CELLS) {
            throw new IOException("CONUS grid exceeds " + MAX_GRID_CELLS + " pixels.");
        }

        Instant start = readTime(file, "time_coverage_start");
        Instant end = readTime(file, "time_coverage_end");

        if (end.isBefore(start)) {
            throw new IOException("time_coverage_end precedes time_coverage_start.");
        }

        return new CloudGrid(start, end, x, y, readProjection(file));
    }

    /** Prevents reading the wrong satellite, region, or product as a cloud dataset. */
    private void validateProduct(NetcdfFile file, String product) throws IOException {
        if (!readText(file, "platform_ID").equals("G19") || !readText(file, "scene_id").equals("CONUS")
                || !readText(file, "dataset_name").startsWith("OR_" + product + "-")) {
            throw new IOException("Expected a GOES-19 " + product + " CONUS product.");
        }
    }

    /** Requires real (y,x) arrays with a consistent projection. */
    @SuppressWarnings("deprecation")
    private VariableDS readGridVariable(NetcdfFile file, String name, int rows, int columns) throws IOException {
        Variable variable = requireVariable(file, name);

        if (!variable.getDataType().isNumeric() || !Arrays.equals(variable.getShape(), new int[] {rows, columns})
                || !variable.getDimension(0).getShortName().equals("y")
                || !variable.getDimension(1).getShortName().equals("x")
                || !variable.findAttributeString("grid_mapping", "").equals("goes_imager_projection")) {
            throw new IOException(name + " must be numeric with dimensions (y,x) and the GOES projection.");
        }

        VariableDS enhanced = (VariableDS) variable;
        enhanced.setInvalidDataIsMissing(false);

        return enhanced;
    }

    /** Decodes an axis including scale/offset, then checks finite monotonic coordinates. */
    private double[] readAxis(NetcdfFile file, String name) throws IOException {
        Variable variable = requireVariable(file, name);

        if (!variable.getDataType().isNumeric() || variable.getRank() != 1
                || variable.getSize() < MIN_AXIS_LENGTH || variable.getSize() > MAX_GRID_CELLS
                || !variable.getDimension(0).getShortName().equals(name)
                || !variable.findAttributeString("units", "").equals("rad")) {
            throw new IOException(name + " must be a one-dimensional scan-angle axis in radians.");
        }

        var values = variable.read();
        double[] axis = new double[(int) values.getSize()];

        for (int index = 0; index < axis.length; index++) {
            axis[index] = values.getDouble(index);
        }

        validateAxis(name, axis);

        return axis;
    }

    /** Rejects duplicate or reversed axis sections that would make lookup ambiguous. */
    private void validateAxis(String name, double[] axis) throws IOException {
        double direction = Math.signum(axis[1] - axis[0]);

        for (int index = 0; index < axis.length; index++) {
            if (!Double.isFinite(axis[index]) || direction == 0
                    || (index > 0 && Math.signum(axis[index] - axis[index - 1]) != direction)) {
                throw new IOException(name + " coordinate " + index + " is nonfinite or not strictly monotonic.");
            }
        }
    }

    /** Uses ellipsoid and scan geometry from the file rather than a fixed satellite longitude. */
    private Geostationary readProjection(NetcdfFile file) throws IOException {
        Variable mapping = requireVariable(file, "goes_imager_projection");

        if (!mapping.findAttributeString("grid_mapping_name", "").equals("geostationary")
                || !mapping.findAttributeString("sweep_angle_axis", "").equals("x")
                || readNumber(mapping, "latitude_of_projection_origin") != 0) {
            throw new IOException("Expected an equatorial geostationary projection with sweep_angle_axis=x.");
        }

        double longitude = readNumber(mapping, "longitude_of_projection_origin");
        double height = readNumber(mapping, "perspective_point_height");
        double major = readNumber(mapping, "semi_major_axis");
        double minor = readNumber(mapping, "semi_minor_axis");
        double flattening = readNumber(mapping, "inverse_flattening");

        if (Math.abs(longitude) > MAX_LONGITUDE_DEGREES || height <= 0 || minor <= 0
                || major <= minor || flattening <= 1) {
            throw new IOException("Invalid GOES projection longitude, height, or ellipsoid.");
        }

        return new Geostationary(longitude, height, minor, major, flattening, true);
    }

    /** Reads a finite numeric projection attribute. */
    private double readNumber(Variable variable, String name) throws IOException {
        var attribute = variable.findAttribute(name);

        if (attribute == null || attribute.getNumericValue() == null
                || !Double.isFinite(attribute.getNumericValue().doubleValue())) {
            throw new IOException(variable.getShortName() + ": missing/nonfinite attribute " + name);
        }

        return attribute.getNumericValue().doubleValue();
    }

    /** Requires a named variable rather than silently synthesizing missing required data. */
    private Variable requireVariable(NetcdfFile file, String name) throws IOException {
        Variable variable = file.findVariable(name);

        if (variable == null) {
            throw new IOException("Missing required variable " + name);
        }

        return variable;
    }

    /** Reads a required global string attribute. */
    private String readText(NetcdfFile file, String name) throws IOException {
        var attribute = file.findGlobalAttribute(name);

        if (attribute == null || attribute.getStringValue() == null || attribute.getStringValue().isBlank()) {
            throw new IOException("Missing required attribute " + name);
        }

        return attribute.getStringValue();
    }

    /** Parses NOAA's ISO UTC observation timestamps; parse adds file context on failure. */
    private Instant readTime(NetcdfFile file, String name) throws IOException {
        try {
            return Instant.parse(readText(file, name));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IOException("Invalid timestamp " + name, exception);
        }
    }
}
