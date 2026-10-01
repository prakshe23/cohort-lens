package com.cohortlens.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cohortlens.core.EconomicStatus;
import com.cohortlens.core.Observation;
import com.cohortlens.core.Term;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureBuilderTest {

    private static Observation obs(String key, int year, Term term, Double math) {
        return new Observation(key, "Maple Grove", 5, year, term, EconomicStatus.LOW_INCOME, true,
                math, 70.0, 0.95, 1);
    }

    @Test
    void pairsOnlyConsecutiveTermsAndTakesTheLabelFromTheNextTerm() {
        List<Observation> data = new ArrayList<>();
        data.add(obs("A", 2022, Term.FALL, 70.0));
        data.add(obs("A", 2022, Term.SPRING, 65.0));
        data.add(obs("A", 2023, Term.FALL, 40.0));

        List<Example> examples = FeatureBuilder.build(data);
        assertEquals(2, examples.size()); // the last term has no next term

        Example first = examples.get(0);
        assertFalse(first.label()); // next math 65 is not below 60
        assertTrue(Double.isNaN(first.features()[5])); // no previous term, so prior math is missing

        Example second = examples.get(1);
        assertTrue(second.label()); // next math 40 is below 60
        assertEquals(70.0, second.features()[5], 1e-12); // prior math
        assertEquals(65.0 - 70.0, second.features()[6], 1e-12); // change in math
    }

    @Test
    void aGapBetweenTermsIsNotBridged() {
        List<Observation> data = List.of(obs("B", 2022, Term.FALL, 70.0), obs("B", 2023, Term.FALL, 40.0));
        assertTrue(FeatureBuilder.build(data).isEmpty());
    }

    @Test
    void aMissingNextMathScoreGivesNoExample() {
        List<Observation> data = List.of(obs("C", 2022, Term.FALL, 80.0), obs("C", 2022, Term.SPRING, null));
        assertTrue(FeatureBuilder.build(data).isEmpty());
    }

    @Test
    void changingTheNextTermChangesTheLabelButNeverTheFeatures() {
        List<Example> low = FeatureBuilder.build(List.of(obs("D", 2022, Term.FALL, 70.0), obs("D", 2022, Term.SPRING, 20.0)));
        List<Example> high = FeatureBuilder.build(List.of(obs("D", 2022, Term.FALL, 70.0), obs("D", 2022, Term.SPRING, 95.0)));
        assertTrue(low.get(0).label());
        assertFalse(high.get(0).label());
        assertEquals(java.util.Arrays.toString(low.get(0).features()), java.util.Arrays.toString(high.get(0).features()));
    }

    @Test
    void demographicsAreCarriedForTheSubgroupCheckButOutsideTheBaseColumns() {
        List<Example> examples = FeatureBuilder.build(List.of(obs("E", 2022, Term.FALL, 70.0), obs("E", 2022, Term.SPRING, 50.0)));
        Example e = examples.get(0);
        assertTrue(e.lowIncome());
        assertTrue(e.englishLearner());
        for (int column : FeatureBuilder.BASE_COLUMNS) {
            assertTrue(!FeatureBuilder.NAMES[column].equals("low_income") && !FeatureBuilder.NAMES[column].equals("english_learner"));
        }
    }
}
