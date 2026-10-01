package com.cohortlens.ml;

import java.util.SplittableRandom;

/**
 * Assigns each STUDENT, not each row, to train, validation or test.
 *
 * <p>The same student appears in many terms and their rows are strongly related. If rows were split
 * at random, the model would be tested on students it had already seen and the results would look
 * better than they should. Splitting by student keeps all of a student's rows together.
 */
public final class StudentSplit {

    public enum Part { TRAIN, VALIDATION, TEST }

    private final long seed;
    private final double trainShare;
    private final double validationShare;

    public StudentSplit(long seed, double trainShare, double validationShare) {
        if (trainShare <= 0 || validationShare <= 0 || trainShare + validationShare >= 1) {
            throw new IllegalArgumentException("shares must leave room for a test set");
        }
        this.seed = seed;
        this.trainShare = trainShare;
        this.validationShare = validationShare;
    }

    /** The same student key and seed always give the same part. */
    public Part assign(String studentKey) {
        double u = new SplittableRandom(seed ^ ((long) studentKey.hashCode() * 0x9E3779B97F4A7C15L)).nextDouble();
        if (u < trainShare) {
            return Part.TRAIN;
        }
        return u < trainShare + validationShare ? Part.VALIDATION : Part.TEST;
    }
}
