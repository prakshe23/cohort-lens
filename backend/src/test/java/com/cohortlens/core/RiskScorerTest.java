package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cohortlens.core.RiskScorer.RiskAssessment;
import com.cohortlens.core.RiskScorer.RiskLevel;
import org.junit.jupiter.api.Test;

class RiskScorerTest {

    private static Observation obs(int year, Term term, Double math, Double reading, Double attendance, int discipline) {
        return new Observation("k1", "Maple Grove", 5, year, term, EconomicStatus.LOW_INCOME, false,
                math, reading, attendance, discipline);
    }

    @Test
    void healthyStudentScoresZero() {
        RiskAssessment a = RiskScorer.assess(obs(2022, Term.SPRING, 80.0, 78.0, 0.97, 0), null);
        assertEquals(0, a.score());
        assertEquals(RiskLevel.LOW, a.level());
        assertTrue(a.factors().isEmpty());
    }

    @Test
    void lowAttendanceAndVeryLowMathIsHighRiskAndExplained() {
        RiskAssessment a = RiskScorer.assess(obs(2022, Term.SPRING, 45.0, 75.0, 0.75, 0), null);
        assertEquals(6, a.score());
        assertEquals(RiskLevel.HIGH, a.level());
        assertEquals(2, a.factors().size());
    }

    @Test
    void mediumBoundaryIsThreePoints() {
        RiskAssessment a = RiskScorer.assess(obs(2022, Term.SPRING, 55.0, 75.0, 0.97, 2), null);
        assertEquals(3, a.score()); // math below 60 (2) plus two incidents (1)
        assertEquals(RiskLevel.MEDIUM, a.level());
    }

    @Test
    void mathDeclineAddsPointsOnlyWhenDropIsTenOrMore() {
        Observation before = obs(2022, Term.FALL, 80.0, 78.0, 0.97, 0);
        RiskAssessment big = RiskScorer.assess(obs(2022, Term.SPRING, 68.0, 78.0, 0.97, 0), before);
        RiskAssessment small = RiskScorer.assess(obs(2022, Term.SPRING, 72.0, 78.0, 0.97, 0), before);
        assertEquals(2, big.score());
        assertEquals(0, small.score());
    }

    @Test
    void missingValuesAddNoPoints() {
        RiskAssessment a = RiskScorer.assess(obs(2022, Term.SPRING, null, null, null, 0), null);
        assertEquals(0, a.score());
    }

    @Test
    void termLabelsRoundTrip() {
        assertEquals("2022 Spring", Term.label(Term.index(2022, Term.SPRING)));
        assertEquals("2019 Fall", Term.label(Term.index(2019, Term.FALL)));
    }
}
