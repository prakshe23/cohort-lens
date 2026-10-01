package com.cohortlens.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Evaluation metrics for a binary classifier that outputs a score. Higher score means more likely true. */
public final class Metrics {

    private Metrics() {
    }

    /** Counts at a score threshold: a row is flagged when its score is at or above the threshold. */
    public record Confusion(int tp, int fp, int tn, int fn) {
        public double precision() {
            return tp + fp == 0 ? Double.NaN : (double) tp / (tp + fp);
        }

        public double recall() {
            return tp + fn == 0 ? Double.NaN : (double) tp / (tp + fn);
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return Double.isNaN(p) || Double.isNaN(r) || p + r == 0 ? 0.0 : 2 * p * r / (p + r);
        }

        public double flaggedRate() {
            int n = tp + fp + tn + fn;
            return n == 0 ? Double.NaN : (double) (tp + fp) / n;
        }
    }

    public record CalibrationBin(double low, double high, int count, double meanPredicted, double observedRate) {
    }

    /**
     * Area under the ROC curve: the chance that a randomly chosen true case gets a higher score than a
     * randomly chosen false case, with ties counting one half. Returns NaN if only one class is present.
     */
    public static double auc(double[] score, boolean[] label) {
        int n = score.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> score[i]));
        double positiveRankSum = 0;
        long positives = 0;
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && score[order[j + 1]] == score[order[i]]) {
                j++;
            }
            double averageRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                if (label[order[k]]) {
                    positiveRankSum += averageRank;
                    positives++;
                }
            }
            i = j + 1;
        }
        long negatives = n - positives;
        if (positives == 0 || negatives == 0) {
            return Double.NaN;
        }
        return (positiveRankSum - positives * (positives + 1) / 2.0) / ((double) positives * negatives);
    }

    /** Average precision: area under the precision recall curve, using each distinct score as a cut. */
    public static double averagePrecision(double[] score, boolean[] label) {
        int n = score.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> score[i]).reversed());
        long totalPositive = 0;
        for (boolean b : label) {
            if (b) {
                totalPositive++;
            }
        }
        if (totalPositive == 0) {
            return Double.NaN;
        }
        double ap = 0;
        double previousRecall = 0;
        long tp = 0;
        long seen = 0;
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && score[order[j + 1]] == score[order[i]]) {
                j++;
            }
            for (int k = i; k <= j; k++) {
                seen++;
                if (label[order[k]]) {
                    tp++;
                }
            }
            double recall = (double) tp / totalPositive;
            double precision = (double) tp / seen;
            ap += (recall - previousRecall) * precision;
            previousRecall = recall;
            i = j + 1;
        }
        return ap;
    }

    public static double brier(double[] probability, boolean[] label) {
        double sum = 0;
        for (int i = 0; i < probability.length; i++) {
            double d = probability[i] - (label[i] ? 1.0 : 0.0);
            sum += d * d;
        }
        return sum / probability.length;
    }

    public static double logLoss(double[] probability, boolean[] label) {
        double sum = 0;
        for (int i = 0; i < probability.length; i++) {
            double p = Math.min(Math.max(probability[i], 1e-12), 1 - 1e-12);
            sum -= label[i] ? Math.log(p) : Math.log(1 - p);
        }
        return sum / probability.length;
    }

    public static Confusion confusionAt(double[] score, boolean[] label, double threshold) {
        int tp = 0;
        int fp = 0;
        int tn = 0;
        int fn = 0;
        for (int i = 0; i < score.length; i++) {
            boolean flagged = score[i] >= threshold;
            if (flagged && label[i]) {
                tp++;
            } else if (flagged) {
                fp++;
            } else if (label[i]) {
                fn++;
            } else {
                tn++;
            }
        }
        return new Confusion(tp, fp, tn, fn);
    }

    /** The threshold that gives the best F1 on the given data. Ties go to the higher threshold. */
    public static double bestF1Threshold(double[] score, boolean[] label) {
        double[] candidates = Arrays.stream(score).distinct().sorted().toArray();
        double best = candidates.length == 0 ? 0.5 : candidates[candidates.length - 1];
        double bestF1 = -1;
        for (double t : candidates) {
            double f1 = confusionAt(score, label, t).f1();
            if (f1 >= bestF1) {
                bestF1 = f1;
                best = t;
            }
        }
        return best;
    }

    /** Ten equal width bins of predicted probability, with the observed rate in each. */
    public static List<CalibrationBin> calibration(double[] probability, boolean[] label, int bins) {
        int[] count = new int[bins];
        double[] sumPredicted = new double[bins];
        int[] positives = new int[bins];
        for (int i = 0; i < probability.length; i++) {
            int b = Math.min(bins - 1, (int) Math.floor(probability[i] * bins));
            count[b]++;
            sumPredicted[b] += probability[i];
            if (label[i]) {
                positives[b]++;
            }
        }
        List<CalibrationBin> result = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            double mean = count[b] == 0 ? Double.NaN : sumPredicted[b] / count[b];
            double observed = count[b] == 0 ? Double.NaN : (double) positives[b] / count[b];
            result.add(new CalibrationBin((double) b / bins, (double) (b + 1) / bins, count[b], mean, observed));
        }
        return result;
    }

    /** Expected calibration error: the weighted average gap between predicted and observed rates. */
    public static double expectedCalibrationError(List<CalibrationBin> bins) {
        int total = bins.stream().mapToInt(CalibrationBin::count).sum();
        double ece = 0;
        for (CalibrationBin b : bins) {
            if (b.count() > 0) {
                ece += (double) b.count() / total * Math.abs(b.meanPredicted() - b.observedRate());
            }
        }
        return ece;
    }
}
