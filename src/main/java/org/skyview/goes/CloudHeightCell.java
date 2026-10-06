package org.skyview.goes;

/** One cloud-top retrieval; unavailable heights stay null. */
public final class CloudHeightCell {
    private static final int GOOD_QUALITY = 0;
    private static final int MAX_QUALITY_CODE = 4;

    private final Double heightMeters;
    private final Integer qualityCode;

    CloudHeightCell(double height, double quality) {
        Integer decodedQuality = null;
        if (Double.isFinite(quality) && quality >= 0 && quality <= MAX_QUALITY_CODE && quality == Math.rint(quality)) {
            decodedQuality = (int) quality;
        }
        qualityCode = decodedQuality;

        Double decodedHeight = null;
        if (qualityCode != null && qualityCode == GOOD_QUALITY && Double.isFinite(height) && height >= 0.0) {
            decodedHeight = height;
        }
        heightMeters = decodedHeight;
    }

    /**
     * Returns cloud-top geopotential height above sea level, not height above the observer.
     * @return good-quality height in metres, or null when unavailable
     */
    public Double getHeightMeters() {
        return heightMeters;
    }

    /**
     * Returns the height product's own quality flag; its meanings differ from the mask's flags.
     * @return original flag from 0 to 4, or null when missing/unrecognized
     */
    public Integer getQualityCode() {
        return qualityCode;
    }
}
