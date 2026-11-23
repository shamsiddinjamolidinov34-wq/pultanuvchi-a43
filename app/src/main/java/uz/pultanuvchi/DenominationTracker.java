package uz.pultanuvchi;

/**
 * Bir necha ketma-ket kadrdagi dalillarni jamlab, qaror chiqaradi.
 * Eski kadrlar ta'siri har kadrda pasayadi (DECAY), shuning uchun bitta xato o'qish
 * natijani o'zgartira olmaydi, lekin pul almashtirilsa, natija tez yangilanadi.
 */
public final class DenominationTracker {

    private static final double DECAY = 0.6;
    private static final double RATIO = 1.3;
    private static final double MIN_GAP = 2.0;
    private static final double HIGH_SUPPORT = 7.5;
    private static final double WORD_PRESENT = 2.5;
    private static final double NUMBER_HIGH = 6.0;
    private static final double NUMBER_HIGH_SUPPORT = 9.0;
    private static final double NUMBER_RATIO = 1.5;
    private static final double LOW_NUMBER = 5.4;
    private static final double LOW_NUMBER_WITH_HEADER = 7.0;
    private static final double AMBIGUOUS_MIN = 5.0;
    private static final double HEADER_CAP = 3.0;
    private static final double HAS_EVIDENCE = 0.2;

    private final int n = Banknotes.ALL.size();
    private final double[] numAcc = new double[n];
    private final double[] wordAcc = new double[n];
    private double headerAcc = 0.0;
    private boolean ambiguous = false;

    public void reset() {
        for (int i = 0; i < n; i++) {
            numAcc[i] = 0.0;
            wordAcc[i] = 0.0;
        }
        headerAcc = 0.0;
        ambiguous = false;
    }

    /** Kadrda bir nechta turli banknota belgilari teng kuchda ko'rinyaptimi. */
    public boolean isAmbiguous() {
        return ambiguous;
    }

    public Decision update(FrameEvidence ev) {
        for (int i = 0; i < n; i++) {
            numAcc[i] = numAcc[i] * DECAY + ev.numberScores[i];
            wordAcc[i] = wordAcc[i] * DECAY + ev.wordScores[i];
        }
        headerAcc = headerAcc * DECAY + ev.headerScore;
        return decide();
    }

    private Decision decide() {
        double header = Math.min(headerAcc, HEADER_CAP);
        int best = -1;
        double bestS = 0.0;
        double second = 0.0;
        for (int i = 0; i < n; i++) {
            double base = numAcc[i] + wordAcc[i];
            double s = base > HAS_EVIDENCE ? base + header : 0.0;
            if (s > bestS) {
                second = bestS;
                bestS = s;
                best = i;
            } else if (s > second) {
                second = s;
            }
        }
        ambiguous = best >= 0 && second >= AMBIGUOUS_MIN && bestS < second * RATIO;
        if (best < 0) {
            return null;
        }
        if (bestS < second * RATIO || bestS - second < MIN_GAP) {
            return null;
        }
        double w = wordAcc[best];
        double nu = numAcc[best];
        if (w >= WORD_PRESENT) {
            // so'z yozuvi ko'rindi: yetarli dalil to'planguncha kutamiz
            return bestS >= HIGH_SUPPORT ? new Decision(best, true) : null;
        }
        // faqat raqam: xato o'qish (masalan, nol tushib qolishi) ehtimoli ko'proq, shuning uchun qattiqroq talab
        if (headerAcc >= 1.0 && nu >= NUMBER_HIGH && bestS >= NUMBER_HIGH_SUPPORT && bestS >= second * NUMBER_RATIO) {
            return new Decision(best, true);
        }
        if (nu >= LOW_NUMBER && (headerAcc < 1.0 || nu >= LOW_NUMBER_WITH_HEADER)) {
            return new Decision(best, false);
        }
        return null;
    }
}
