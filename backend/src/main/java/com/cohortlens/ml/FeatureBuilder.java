package com.cohortlens.ml;

import com.cohortlens.core.Observation;
import com.cohortlens.core.RiskScorer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns term level observations into prediction examples.
 *
 * <p><b>Task.</b> Using what is known about a student at the end of one term (and the term before it),
 * predict whether the student's math score in the very next term will be below {@link #PROFICIENCY_CUT}.
 *
 * <p><b>No leakage by construction.</b> Features come only from the current term and the one before it.
 * The label comes only from the next term. A pair of terms is used only when the next term is exactly one
 * term later and has a math score, so there are no gaps and no invented labels.
 */
public final class FeatureBuilder {

    public static final double PROFICIENCY_CUT = 60.0;

    /** Order of the raw feature columns. */
    public static final String[] NAMES = {
            "math", "reading", "attendance", "discipline", "grade", "prior_math", "math_change",
            "low_income", "english_learner"
    };

    /** Columns that describe academic and behavior history only. */
    public static final int[] BASE_COLUMNS = {0, 1, 2, 3, 4, 5, 6};

    /** Base columns plus economic status and English learner status. */
    public static final int[] WITH_DEMOGRAPHICS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private FeatureBuilder() {
    }

    public static List<Example> build(List<Observation> observations) {
        Map<String, List<Observation>> byStudent = new HashMap<>();
        for (Observation o : observations) {
            byStudent.computeIfAbsent(o.studentKey(), k -> new ArrayList<>()).add(o);
        }
        List<Example> examples = new ArrayList<>();
        for (Map.Entry<String, List<Observation>> entry : byStudent.entrySet()) {
            TreeMap<Integer, Observation> byTerm = new TreeMap<>();
            for (Observation o : entry.getValue()) {
                byTerm.put(o.termIndex(), o);
            }
            for (Observation current : byTerm.values()) {
                Observation next = byTerm.get(current.termIndex() + 1);
                if (next == null || next.mathScore() == null) {
                    continue;
                }
                Observation previous = byTerm.get(current.termIndex() - 1);
                examples.add(toExample(current, previous, next));
            }
        }
        examples.sort(Comparator.comparing(Example::studentKey).thenComparingInt(Example::termIndex));
        return examples;
    }

    private static Example toExample(Observation current, Observation previous, Observation next) {
        double math = value(current.mathScore());
        double priorMath = previous == null ? Double.NaN : value(previous.mathScore());
        double change = Double.isNaN(math) || Double.isNaN(priorMath) ? Double.NaN : math - priorMath;
        double[] features = {
                math,
                value(current.readingScore()),
                value(current.attendanceRate()),
                current.disciplineIncidents(),
                current.grade(),
                priorMath,
                change,
                current.economicStatus() == com.cohortlens.core.EconomicStatus.LOW_INCOME ? 1.0 : 0.0,
                current.englishLearner() ? 1.0 : 0.0
        };
        boolean label = next.mathScore() < PROFICIENCY_CUT;
        int rule = RiskScorer.assess(current, previous).score();
        return new Example(current.studentKey(), current.termIndex(), features, label, rule,
                features[7] == 1.0, features[8] == 1.0);
    }

    private static double value(Double d) {
        return d == null ? Double.NaN : d;
    }
}
