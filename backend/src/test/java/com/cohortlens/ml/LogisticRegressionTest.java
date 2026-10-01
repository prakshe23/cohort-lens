package com.cohortlens.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LogisticRegressionTest {

    /** Data drawn from a known model: P(y) = sigmoid(1.5 x - 0.5). */
    private static double[][] x;
    private static boolean[] y;

    private static void generate(int n, long seed) {
        Random random = new Random(seed);
        x = new double[n][1];
        y = new boolean[n];
        for (int i = 0; i < n; i++) {
            x[i][0] = random.nextGaussian();
            double p = LogisticRegression.sigmoid(1.5 * x[i][0] - 0.5);
            y[i] = random.nextDouble() < p;
        }
    }

    @Test
    void recoversTheWeightsThatGeneratedTheData() {
        generate(8000, 1);
        double[] w = LogisticRegression.fit(x, y, 0.001).weights();
        assertEquals(-0.5, w[0], 0.15);
        assertEquals(1.5, w[1], 0.15);
    }

    @Test
    void largerPenaltyShrinksTheWeights() {
        generate(2000, 2);
        double small = Math.abs(LogisticRegression.fit(x, y, 0.01).weights()[1]);
        double large = Math.abs(LogisticRegression.fit(x, y, 1000).weights()[1]);
        assertTrue(large < small);
    }

    @Test
    void perfectlySeparableDataGivesFiniteWeightsBecauseOfThePenalty() {
        double[][] xs = {{-2}, {-1}, {1}, {2}};
        boolean[] ys = {false, false, true, true};
        double[] w = LogisticRegression.fit(xs, ys, 1.0).weights();
        assertTrue(Double.isFinite(w[0]) && Double.isFinite(w[1]));
        assertTrue(w[1] > 0);
    }

    @Test
    void predictionsAreProbabilities() {
        generate(500, 3);
        LogisticRegression m = LogisticRegression.fit(x, y, 1.0);
        for (double[] row : x) {
            double p = m.predict(row);
            assertTrue(p >= 0.0 && p <= 1.0);
        }
    }

    @Test
    void solvesALinearSystem() {
        double[] s = LogisticRegression.solve(new double[][] {{2, 1}, {1, 3}}, new double[] {3, 5});
        assertEquals(0.8, s[0], 1e-12);
        assertEquals(1.4, s[1], 1e-12);
    }

    @Test
    void sigmoidIsStableForLargeInputs() {
        assertEquals(1.0, LogisticRegression.sigmoid(1000), 1e-12);
        assertEquals(0.0, LogisticRegression.sigmoid(-1000), 1e-12);
        assertEquals(0.5, LogisticRegression.sigmoid(0), 1e-12);
    }
}
