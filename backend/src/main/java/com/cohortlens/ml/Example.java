package com.cohortlens.ml;

/**
 * One prediction example: what is known about a student at the end of one term, and what happened in
 * the next term.
 *
 * @param studentKey  pseudonymous student key, used to keep all of a student's rows in one split
 * @param termIndex   the term the features come from
 * @param features    raw feature values in the order of {@link FeatureBuilder#NAMES}; NaN means missing
 * @param label       true if the student's math score in the NEXT term is below the proficiency cut
 * @param ruleScore   points from the transparent rule based screening score for the same term
 * @param lowIncome   kept for the subgroup check, never used by the model without the demographic option
 * @param englishLearner same, for the subgroup check
 */
public record Example(
        String studentKey,
        int termIndex,
        double[] features,
        boolean label,
        int ruleScore,
        boolean lowIncome,
        boolean englishLearner) {
}
