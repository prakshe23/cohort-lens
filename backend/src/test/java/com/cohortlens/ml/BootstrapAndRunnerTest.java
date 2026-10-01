package com.cohortlens.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BootstrapAndRunnerTest {

    @Test
    void aPerfectModelHasAnIntervalAtOneAndIdenticalModelsDifferByZero() {
        int students = 40;
        String[] keys = new String[students * 2];
        boolean[] labels = new boolean[keys.length];
        double[] perfect = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "s" + (i / 2);
            labels[i] = (i / 2) % 2 == 0;
            perfect[i] = labels[i] ? 1.0 : 0.0;
        }
        Bootstrap.Result r = Bootstrap.run(keys, labels, new double[][] {perfect, perfect}, new int[][] {{0, 1}}, 200, 5);
        assertEquals(1.0, r.auc()[0].low(), 1e-12);
        assertEquals(1.0, r.auc()[0].high(), 1e-12);
        assertEquals(0.0, r.difference()[0].low(), 1e-12);
        assertEquals(0.0, r.difference()[0].high(), 1e-12);
    }

    @Test
    void theIntervalBracketsTheEstimateForANoisyModel() {
        Random random = new Random(3);
        int n = 400;
        String[] keys = new String[n];
        boolean[] labels = new boolean[n];
        double[] score = new double[n];
        for (int i = 0; i < n; i++) {
            keys[i] = "s" + (i / 4);
            labels[i] = random.nextBoolean();
            score[i] = (labels[i] ? 0.5 : 0.0) + random.nextGaussian();
        }
        Bootstrap.Result r = Bootstrap.run(keys, labels, new double[][] {score}, new int[0][], 300, 9);
        Bootstrap.Interval a = r.auc()[0];
        assertTrue(a.low() <= a.estimate() && a.estimate() <= a.high());
        assertTrue(a.high() - a.low() > 0.0);
    }

    /** A small synthetic school file: 300 students, six terms, the same format the import validator expects. */
    private static String syntheticCsv() {
        Random random = new Random(42);
        StringBuilder sb = new StringBuilder(
                "student_id,school,grade,academic_year,term,economic_status,english_learner,math_score,reading_score,attendance_rate,discipline_incidents\n");
        for (int s = 0; s < 300; s++) {
            double ability = random.nextGaussian();
            boolean low = random.nextDouble() < 0.4;
            boolean el = random.nextDouble() < 0.15;
            int grade = 4;
            for (int year = 2020; year <= 2022; year++) {
                for (String term : new String[] {"FALL", "SPRING"}) {
                    double math = Math.max(0, Math.min(100, 60 + 10 * ability + random.nextGaussian() * 6));
                    double reading = Math.max(0, Math.min(100, 62 + 9 * ability + random.nextGaussian() * 6));
                    double attendance = Math.max(0.5, Math.min(1.0, 0.95 + 0.01 * ability + random.nextGaussian() * 0.02));
                    sb.append("S").append(s).append(",Maple Grove,").append(grade).append(',').append(year).append(',')
                            .append(term).append(',').append(low ? "LOW_INCOME" : "NOT_LOW_INCOME").append(',')
                            .append(el ? "Y" : "N").append(',').append(String.format("%.1f", math)).append(',')
                            .append(String.format("%.1f", reading)).append(',').append(String.format("%.3f", attendance))
                            .append(',').append(random.nextInt(3)).append('\n');
                }
                grade++;
            }
        }
        return sb.toString();
    }

    @Test
    void theWholePipelineRunsAndIsDeterministic() {
        String first = EvaluationRunner.run(syntheticCsv(), "test.csv");
        String second = EvaluationRunner.run(syntheticCsv(), "test.csv");
        assertEquals(first, second);
        assertTrue(first.contains("# Machine learning evaluation"));
        assertTrue(first.contains("Logistic regression, history only"));
        assertTrue(first.contains("## Sanity check: shuffled labels"));
        assertTrue(first.contains("The data is synthetic"));
    }
}
