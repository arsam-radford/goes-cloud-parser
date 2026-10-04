package org.skyview.goes;

public final class CloudCell {
    private static final int GOOD_QUALITY = 0;
    private static final int FILL_QUALITY = 255;

    private final CloudState state;
    private final Double cloudProbability;
    private final Integer qualityCode;

    CloudCell(double classification, double probability, double quality) {
        qualityCode = decodeQuality(quality);
        state = decodeState(classification);
        cloudProbability = decodeProbability(probability);
    }

    /**
     * Returns the cloud state
     * @return the cloud state, including UNKNOWN
     */
    public CloudState getState() {
        return state;
    }

    /**
     * Returns the probability of a cloud being present (not a cloud coverage percentage)
     * @return probability from 0 to 1, or null when unavailable/unusable
     */
    public Double getCloudProbability() {
        return cloudProbability;
    }

    /**
     * Returns the file's original data quality flag
     * @return the DQF code (including bad quality), or null when missing
     */
    public Integer getQualityCode() {
        return qualityCode;
    }

    /**
     * Converts an available byte flag without losing nonzero quality codes.
     * @param quality the quality value
     * @return the quality code, or null if invalid
     */
    private Integer decodeQuality(double quality) {
        if (!Double.isFinite(quality) || quality < 0 || quality >= FILL_QUALITY
                || quality != Math.rint(quality)) {
            return null;
        }

        return (int) quality;
    }

    /** Only a good quality, recognized ACM flag can describe clear/cloudy conditions */
    private CloudState decodeState(double classification) {
        if (qualityCode == null || qualityCode != GOOD_QUALITY) {
            return CloudState.UNKNOWN;
        }

        CloudState result = CloudState.UNKNOWN;

        for (CloudState candidate : CloudState.values()) {
            if (candidate != CloudState.UNKNOWN && classification == candidate.getClassificationCode()) {
                result = candidate;
            }
        }

        return result;
    }

    /** Missing, out of range, or bad quality probabilities are unavailable */
    private Double decodeProbability(double probability) {
        if (state == CloudState.UNKNOWN || !Double.isFinite(probability)
                || probability < 0 || probability > 1) {
            return null;
        }

        return probability;
    }
}
