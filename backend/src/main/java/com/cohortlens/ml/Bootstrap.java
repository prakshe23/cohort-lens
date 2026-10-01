package com.cohortlens.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cluster bootstrap for the test set. Whole STUDENTS are resampled with replacement, not single rows,
 * because one student contributes several related rows. Resampling rows would make the intervals too
 * narrow.
 */
public final class Bootstrap {

    public record Interval(double estimate, double low, double high) {
    }

    private Bootstrap() {
    }

    /**
     * AUC for each scoring vector, plus the difference in AUC between pairs, all computed on the SAME
     * resamples so that the differences are paired.
     *
     * @param studentKeys one key per test row
     * @param scores      one score vector per model, each aligned with the rows
     * @param pairs       each entry {a, b} asks for AUC(a) minus AUC(b)
     */
    public static Result run(String[] studentKeys, boolean[] labels, double[][] scores, int[][] pairs,
                             int replicates, long seed) {
        Map<String, List<Integer>> rowsByStudent = new LinkedHashMap<>();
        for (int i = 0; i < studentKeys.length; i++) {
            rowsByStudent.computeIfAbsent(studentKeys[i], k -> new ArrayList<>()).add(i);
        }
        List<List<Integer>> students = new ArrayList<>(rowsByStudent.values());
        Random random = new Random(seed);

        double[][] aucs = new double[scores.length][replicates];
        double[][] diffs = new double[pairs.length][replicates];
        for (int r = 0; r < replicates; r++) {
            List<Integer> picked = new ArrayList<>();
            for (int s = 0; s < students.size(); s++) {
                picked.addAll(students.get(random.nextInt(students.size())));
            }
            boolean[] y = new boolean[picked.size()];
            for (int i = 0; i < y.length; i++) {
                y[i] = labels[picked.get(i)];
            }
            double[] replicateAuc = new double[scores.length];
            for (int m = 0; m < scores.length; m++) {
                double[] s = new double[picked.size()];
                for (int i = 0; i < s.length; i++) {
                    s[i] = scores[m][picked.get(i)];
                }
                replicateAuc[m] = Metrics.auc(s, y);
                aucs[m][r] = replicateAuc[m];
            }
            for (int p = 0; p < pairs.length; p++) {
                diffs[p][r] = replicateAuc[pairs[p][0]] - replicateAuc[pairs[p][1]];
            }
        }

        Interval[] aucIntervals = new Interval[scores.length];
        for (int m = 0; m < scores.length; m++) {
            aucIntervals[m] = percentile(Metrics.auc(scores[m], labels), aucs[m]);
        }
        Interval[] diffIntervals = new Interval[pairs.length];
        for (int p = 0; p < pairs.length; p++) {
            double est = Metrics.auc(scores[pairs[p][0]], labels) - Metrics.auc(scores[pairs[p][1]], labels);
            diffIntervals[p] = percentile(est, diffs[p]);
        }
        return new Result(aucIntervals, diffIntervals);
    }

    public record Result(Interval[] auc, Interval[] difference) {
    }

    /** The middle 95 percent of the resampled values. */
    static Interval percentile(double estimate, double[] values) {
        double[] sorted = Arrays.stream(values).filter(v -> !Double.isNaN(v)).sorted().toArray();
        if (sorted.length == 0) {
            return new Interval(estimate, Double.NaN, Double.NaN);
        }
        int lo = (int) Math.floor(0.025 * (sorted.length - 1));
        int hi = (int) Math.ceil(0.975 * (sorted.length - 1));
        return new Interval(estimate, sorted[lo], sorted[hi]);
    }
}
