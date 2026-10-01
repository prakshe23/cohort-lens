package com.cohortlens.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cohortlens.ml.Metrics.CalibrationBin;
import com.cohortlens.ml.Metrics.Confusion;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsTest {

    @Test
    void aucIsOneForPerfectRankingAndZeroForReversedRanking() {
        boolean[] y = {false, false, true, true};
        assertEquals(1.0, Metrics.auc(new double[] {0.1, 0.2, 0.8, 0.9}, y), 1e-12);
        assertEquals(0.0, Metrics.auc(new double[] {0.9, 0.8, 0.2, 0.1}, y), 1e-12);
    }

    @Test
    void aucMatchesAKnownHandComputedValue() {
        // Positive scores 0.35 and 0.8, negative scores 0.1 and 0.4: three of the four pairs are ordered correctly.
        assertEquals(0.75, Metrics.auc(new double[] {0.1, 0.4, 0.35, 0.8}, new boolean[] {false, false, true, true}), 1e-12);
    }

    @Test
    void tiedScoresCountAsHalf() {
        assertEquals(0.5, Metrics.auc(new double[] {1, 1, 1, 1}, new boolean[] {true, false, true, false}), 1e-12);
    }

    @Test
    void aucIsNaNWhenOnlyOneClassIsPresent() {
        assertTrue(Double.isNaN(Metrics.auc(new double[] {0.1, 0.9}, new boolean[] {true, true})));
    }

    @Test
    void averagePrecisionMatchesAKnownValue() {
        double ap = Metrics.averagePrecision(new double[] {0.1, 0.4, 0.35, 0.8}, new boolean[] {false, false, true, true});
        assertEquals(5.0 / 6.0, ap, 1e-12);
    }

    @Test
    void confusionCountsAndF1() {
        Confusion c = Metrics.confusionAt(new double[] {0.9, 0.8, 0.3, 0.2}, new boolean[] {true, false, true, false}, 0.5);
        assertEquals(1, c.tp());
        assertEquals(1, c.fp());
        assertEquals(1, c.fn());
        assertEquals(1, c.tn());
        assertEquals(0.5, c.precision(), 1e-12);
        assertEquals(0.5, c.recall(), 1e-12);
        assertEquals(0.5, c.f1(), 1e-12);
        assertEquals(0.5, c.flaggedRate(), 1e-12);
    }

    @Test
    void bestThresholdSeparatesPerfectlySeparableScores() {
        double t = Metrics.bestF1Threshold(new double[] {0.9, 0.8, 0.2, 0.1}, new boolean[] {true, true, false, false});
        assertEquals(0.8, t, 1e-12);
    }

    @Test
    void brierAndLogLossForPerfectAndUninformativeProbabilities() {
        boolean[] y = {true, false};
        assertEquals(0.0, Metrics.brier(new double[] {1.0, 0.0}, y), 1e-12);
        assertEquals(0.0, Metrics.logLoss(new double[] {1.0, 0.0}, y), 1e-9);
        assertEquals(0.25, Metrics.brier(new double[] {0.5, 0.5}, y), 1e-12);
        assertEquals(Math.log(2), Metrics.logLoss(new double[] {0.5, 0.5}, y), 1e-12);
    }

    @Test
    void calibrationBinsAndExpectedCalibrationError() {
        List<CalibrationBin> bins = Metrics.calibration(new double[] {0.05, 0.05, 0.95, 0.95},
                new boolean[] {false, true, true, true}, 10);
        assertEquals(2, bins.get(0).count());
        assertEquals(0.5, bins.get(0).observedRate(), 1e-12);
        assertEquals(2, bins.get(9).count());
        assertEquals(1.0, bins.get(9).observedRate(), 1e-12);
        assertEquals(0.25, Metrics.expectedCalibrationError(bins), 1e-12);
    }

    @Test
    void aProbabilityOfExactlyOneLandsInTheLastBin() {
        List<CalibrationBin> bins = Metrics.calibration(new double[] {1.0}, new boolean[] {true}, 10);
        assertEquals(1, bins.get(9).count());
    }
}
