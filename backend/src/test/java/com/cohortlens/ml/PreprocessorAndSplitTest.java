package com.cohortlens.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cohortlens.ml.StudentSplit.Part;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreprocessorAndSplitTest {

    private static final double NAN = Double.NaN;

    @Test
    void missingValuesAreFilledWithTheTrainingMeanAndFlagged() {
        List<double[]> train = List.of(new double[] {10}, new double[] {20}, new double[] {NAN}, new double[] {30});
        Preprocessor p = Preprocessor.fit(train, new int[] {0});
        double[] missing = p.transform(new double[] {NAN});
        assertEquals(2, missing.length);
        assertEquals(0.0, missing[0], 1e-12); // the mean, standardized
        assertEquals(1.0, missing[1], 1e-12); // the "was missing" flag
        double[] present = p.transform(new double[] {20});
        assertEquals(0.0, present[0], 1e-12);
        assertEquals(0.0, present[1], 1e-12);
    }

    @Test
    void noFlagColumnIsAddedWhenTheTrainingDataHasNoMissingValues() {
        List<double[]> train = List.of(new double[] {1}, new double[] {2}, new double[] {3});
        Preprocessor p = Preprocessor.fit(train, new int[] {0});
        assertEquals(1, p.transform(new double[] {2}).length);
        assertEquals(List.of("math"), p.outputNames(new String[] {"math"}));
    }

    @Test
    void standardizationUsesTrainingStatisticsOnly() {
        List<double[]> train = List.of(new double[] {0}, new double[] {10});
        Preprocessor p = Preprocessor.fit(train, new int[] {0});
        // mean 5, sample standard deviation sqrt(50). A value never seen in training is scaled with those numbers.
        assertEquals((100 - 5) / Math.sqrt(50), p.transform(new double[] {100})[0], 1e-9);
    }

    @Test
    void aConstantColumnDoesNotDivideByZero() {
        Preprocessor p = Preprocessor.fit(List.of(new double[] {4}, new double[] {4}), new int[] {0});
        assertEquals(0.0, p.transform(new double[] {4})[0], 1e-12);
    }

    @Test
    void theSameStudentAlwaysLandsInTheSamePart() {
        StudentSplit a = new StudentSplit(7, 0.6, 0.2);
        StudentSplit b = new StudentSplit(7, 0.6, 0.2);
        for (int i = 0; i < 200; i++) {
            assertEquals(a.assign("student-" + i), b.assign("student-" + i));
        }
    }

    @Test
    void partsRoughlyMatchTheRequestedShares() {
        StudentSplit split = new StudentSplit(11, 0.6, 0.2);
        int train = 0;
        int validation = 0;
        int test = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            Part part = split.assign("k" + i);
            if (part == Part.TRAIN) {
                train++;
            } else if (part == Part.VALIDATION) {
                validation++;
            } else {
                test++;
            }
        }
        assertTrue(Math.abs(train / (double) n - 0.6) < 0.02);
        assertTrue(Math.abs(validation / (double) n - 0.2) < 0.02);
        assertTrue(Math.abs(test / (double) n - 0.2) < 0.02);
    }

    @Test
    void differentSeedsGiveDifferentSplits() {
        StudentSplit a = new StudentSplit(1, 0.6, 0.2);
        StudentSplit b = new StudentSplit(2, 0.6, 0.2);
        List<String> differing = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            if (a.assign("s" + i) != b.assign("s" + i)) {
                differing.add("s" + i);
            }
        }
        assertTrue(!differing.isEmpty());
    }

    @Test
    void sharesThatLeaveNoTestSetAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StudentSplit(1, 0.8, 0.2));
    }
}
