package com.cohortlens.ml;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills in missing values and standardizes features. It is fitted on the TRAINING data only, then
 * applied unchanged to validation and test data, so nothing about the test set leaks into training.
 *
 * <p>A missing value is replaced by the training mean, and a 0 or 1 "was missing" column is added for
 * every feature that had at least one missing value in the training data. This lets the model learn
 * whether missingness itself carries information.
 */
public final class Preprocessor {

    private final int[] columns;
    private final double[] mean;
    private final double[] sd;
    private final boolean[] indicator;

    private Preprocessor(int[] columns, double[] mean, double[] sd, boolean[] indicator) {
        this.columns = columns;
        this.mean = mean;
        this.sd = sd;
        this.indicator = indicator;
    }

    public static Preprocessor fit(List<double[]> trainRaw, int[] columns) {
        int k = columns.length;
        double[] mean = new double[k];
        double[] sd = new double[k];
        boolean[] indicator = new boolean[k];
        for (int j = 0; j < k; j++) {
            double sum = 0;
            int count = 0;
            for (double[] row : trainRaw) {
                double v = row[columns[j]];
                if (Double.isNaN(v)) {
                    indicator[j] = true;
                } else {
                    sum += v;
                    count++;
                }
            }
            mean[j] = count == 0 ? 0 : sum / count;
            double ss = 0;
            for (double[] row : trainRaw) {
                double v = row[columns[j]];
                if (!Double.isNaN(v)) {
                    ss += (v - mean[j]) * (v - mean[j]);
                }
            }
            double s = count > 1 ? Math.sqrt(ss / (count - 1)) : 1.0;
            sd[j] = s < 1e-9 ? 1.0 : s;
        }
        return new Preprocessor(columns.clone(), mean, sd, indicator);
    }

    public double[] transform(double[] raw) {
        int extra = 0;
        for (boolean b : indicator) {
            if (b) {
                extra++;
            }
        }
        double[] out = new double[columns.length + extra];
        int next = columns.length;
        for (int j = 0; j < columns.length; j++) {
            double v = raw[columns[j]];
            boolean missing = Double.isNaN(v);
            out[j] = ((missing ? mean[j] : v) - mean[j]) / sd[j];
            if (indicator[j]) {
                out[next++] = missing ? 1.0 : 0.0;
            }
        }
        return out;
    }

    public double[][] transformAll(List<double[]> rows) {
        double[][] out = new double[rows.size()][];
        for (int i = 0; i < out.length; i++) {
            out[i] = transform(rows.get(i));
        }
        return out;
    }

    /** Names of the output columns, in order, for reporting coefficients. */
    public List<String> outputNames(String[] allNames) {
        List<String> names = new ArrayList<>();
        for (int c : columns) {
            names.add(allNames[c]);
        }
        for (int j = 0; j < columns.length; j++) {
            if (indicator[j]) {
                names.add(allNames[columns[j]] + "_missing");
            }
        }
        return names;
    }
}
