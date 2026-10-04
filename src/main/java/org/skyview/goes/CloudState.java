package org.skyview.goes;

/** NOAA's four ACM classifications + unavailable or unusable data */
public enum CloudState {
    /** ACM 0: clear */
    CLEAR(0),

    /** ACM 1: probably clear */
    PROBABLY_CLEAR(1),

    /** ACM 2: probably cloudy */
    PROBABLY_CLOUDY(2),

    /** ACM 3: cloudy */
    CLOUDY(3),

    /** No usable classification, including locations outside the grid */
    UNKNOWN(-1);

    private final int classificationCode;

    CloudState(int classificationCode) {
        this.classificationCode = classificationCode;
    }

    /**
     * Returns the original classification code
     * @return the NOAA ACM code, or -1 for unknown
     */
    public int getClassificationCode() {
        return classificationCode;
    }
}
