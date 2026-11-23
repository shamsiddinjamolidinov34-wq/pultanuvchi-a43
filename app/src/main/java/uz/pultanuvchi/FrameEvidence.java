package uz.pultanuvchi;

/** Bitta kadrdan olingan dalillar (har bir banknota turi uchun ball). */
public final class FrameEvidence {
    public final double[] numberScores;
    public final double[] wordScores;
    public final double headerScore;
    public final boolean hasText;

    public FrameEvidence(double[] numberScores, double[] wordScores, double headerScore, boolean hasText) {
        this.numberScores = numberScores;
        this.wordScores = wordScores;
        this.headerScore = headerScore;
        this.hasText = hasText;
    }

    /** Kadrda banknotaga oid biror belgi (raqam, so'z yoki "Markaziy bank" yozuvi) bormi. */
    public boolean anyEvidence() {
        if (headerScore > 0.0) {
            return true;
        }
        for (double v : numberScores) {
            if (v > 0.0) {
                return true;
            }
        }
        for (double v : wordScores) {
            if (v > 0.0) {
                return true;
            }
        }
        return false;
    }
}
