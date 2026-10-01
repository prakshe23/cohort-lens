package com.cohortlens.ml;

import java.util.Arrays;

/**
 * L2 regularized logistic regression, written from scratch and fitted by iteratively reweighted
 * least squares (Newton's method). It has no dependencies and gives the same answer every run.
 *
 * <p>The intercept is not penalized. A larger lambda shrinks the other weights toward zero.
 */
public final class LogisticRegression {

    private final double[] weights; // weights[0] is the intercept

    private LogisticRegression(double[] weights) {
        this.weights = weights;
    }

    public static LogisticRegression fit(double[][] x, boolean[] y, double lambda) {
        int n = x.length;
        if (n == 0 || n != y.length) {
            throw new IllegalArgumentException("need at least one row and one label per row");
        }
        int d = x[0].length + 1;
        double[] beta = new double[d];
        for (int iter = 0; iter < 100; iter++) {
            double[] gradient = new double[d];
            double[][] hessian = new double[d][d];
            for (int i = 0; i < n; i++) {
                double z = beta[0];
                for (int j = 1; j < d; j++) {
                    z += beta[j] * x[i][j - 1];
                }
                double p = sigmoid(z);
                double w = Math.max(p * (1 - p), 1e-9);
                double r = (y[i] ? 1.0 : 0.0) - p;
                gradient[0] += r;
                hessian[0][0] += w;
                for (int j = 1; j < d; j++) {
                    double xj = x[i][j - 1];
                    gradient[j] += r * xj;
                    hessian[0][j] += w * xj;
                    hessian[j][0] += w * xj;
                    for (int k = 1; k <= j; k++) {
                        double v = w * xj * x[i][k - 1];
                        hessian[j][k] += v;
                        if (k != j) {
                            hessian[k][j] += v;
                        }
                    }
                }
            }
            for (int j = 1; j < d; j++) {
                gradient[j] -= lambda * beta[j];
                hessian[j][j] += lambda;
            }
            hessian[0][0] += 1e-8;
            double[] step = solve(hessian, gradient);
            double largest = 0;
            for (int j = 0; j < d; j++) {
                beta[j] += step[j];
                largest = Math.max(largest, Math.abs(step[j]));
            }
            if (largest < 1e-8) {
                break;
            }
        }
        return new LogisticRegression(beta);
    }

    /** Probability that the label is true. */
    public double predict(double[] features) {
        double z = weights[0];
        for (int j = 1; j < weights.length; j++) {
            z += weights[j] * features[j - 1];
        }
        return sigmoid(z);
    }

    /** Intercept first, then one weight per feature. */
    public double[] weights() {
        return Arrays.copyOf(weights, weights.length);
    }

    static double sigmoid(double z) {
        if (z >= 0) {
            double e = Math.exp(-z);
            return 1.0 / (1.0 + e);
        }
        double e = Math.exp(z);
        return e / (1.0 + e);
    }

    /** Solves a x = b by Gaussian elimination with partial pivoting. Inputs are not modified. */
    static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = m[col];
            m[col] = m[pivot];
            m[pivot] = tmp;
            if (Math.abs(m[col][col]) < 1e-12) {
                throw new IllegalStateException("matrix is singular; increase lambda");
            }
            for (int r = col + 1; r < n; r++) {
                double f = m[r][col] / m[col][col];
                for (int c = col; c <= n; c++) {
                    m[r][c] -= f * m[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = m[i][n];
            for (int c = i + 1; c < n; c++) {
                s -= m[i][c] * x[c];
            }
            x[i] = s / m[i][i];
        }
        return x;
    }
}
