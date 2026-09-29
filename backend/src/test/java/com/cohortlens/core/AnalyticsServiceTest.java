package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cohortlens.core.AnalyticsService.Filter;
import com.cohortlens.core.AnalyticsService.GapDimension;
import com.cohortlens.core.AnalyticsService.GapPoint;
import com.cohortlens.core.AnalyticsService.RiskSummary;
import com.cohortlens.core.AnalyticsService.TrendPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {
    private final AnalyticsService service = new AnalyticsService(10);

    private static Observation obs(String key, String school, EconomicStatus status, boolean el, int year,
                                   Term term, double math) {
        return new Observation(key, school, 5, year, term, status, el, math, math, 0.95, 0);
    }

    /** Ten students with math scores 60 through 69 in one term, mean 64.5. */
    private static List<Observation> tenStudents(String school, EconomicStatus status, int year, Term term) {
        List<Observation> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(obs(school + status + i, school, status, false, year, term, 60 + i));
        }
        return list;
    }

    @Test
    void smallGroupsAreSuppressed() {
        List<Observation> five = tenStudents("A", EconomicStatus.LOW_INCOME, 2022, Term.FALL).subList(0, 5);
        TrendPoint p = service.trend(five, Filter.none()).get(0);
        assertNull(p.n());
        assertTrue(p.math().suppressed());
        assertNull(p.math().mean());
    }

    @Test
    void meanAndIntervalAreComputed() {
        List<Observation> ten = tenStudents("A", EconomicStatus.LOW_INCOME, 2022, Term.FALL);
        TrendPoint p = service.trend(ten, Filter.none()).get(0);
        assertFalse(p.math().suppressed());
        assertEquals(10, p.n().intValue());
        assertEquals(64.5, p.math().mean().doubleValue(), 1e-9);
        assertTrue(p.math().ciLow() < 64.5 && p.math().ciHigh() > 64.5);
    }

    @Test
    void trendIsOrderedByTerm() {
        List<Observation> all = new ArrayList<>();
        all.addAll(tenStudents("A", EconomicStatus.LOW_INCOME, 2022, Term.SPRING));
        all.addAll(tenStudents("A", EconomicStatus.LOW_INCOME, 2022, Term.FALL));
        List<TrendPoint> trend = service.trend(all, Filter.none());
        assertEquals("2022 Fall", trend.get(0).label());
        assertEquals("2022 Spring", trend.get(1).label());
    }

    @Test
    void filterBySchoolRestrictsRows() {
        List<Observation> all = new ArrayList<>();
        all.addAll(tenStudents("A", EconomicStatus.LOW_INCOME, 2022, Term.FALL));
        all.addAll(tenStudents("B", EconomicStatus.LOW_INCOME, 2022, Term.FALL));
        TrendPoint p = service.trend(all, new Filter("A", null, null, null)).get(0);
        assertEquals(10, p.n().intValue());
    }

    @Test
    void economicGapIsReferenceMinusComparison() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("hi" + i, "A", EconomicStatus.NOT_LOW_INCOME, false, 2022, Term.FALL, 70 + (i % 3)));
            all.add(obs("lo" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.FALL, 60 + (i % 3)));
        }
        GapPoint g = service.gaps(all, Filter.none(), GapDimension.ECONOMIC_STATUS).get(0);
        assertFalse(g.math().suppressed());
        assertEquals(10.0, g.math().difference().doubleValue(), 1e-9);
        assertTrue(g.math().ciLow() <= 10.0 && g.math().ciHigh() >= 10.0);
    }

    @Test
    void gapIsSuppressedWhenEitherGroupIsSmall() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("hi" + i, "A", EconomicStatus.NOT_LOW_INCOME, false, 2022, Term.FALL, 70));
        }
        for (int i = 0; i < 4; i++) {
            all.add(obs("lo" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.FALL, 60));
        }
        GapPoint g = service.gaps(all, Filter.none(), GapDimension.ECONOMIC_STATUS).get(0);
        assertTrue(g.math().suppressed());
        assertNull(g.math().difference());
    }

    @Test
    void gapIgnoresAFilterOnTheDimensionBeingCompared() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("hi" + i, "A", EconomicStatus.NOT_LOW_INCOME, false, 2022, Term.FALL, 70));
            all.add(obs("lo" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.FALL, 60));
        }
        Filter onlyLow = new Filter(null, null, EconomicStatus.LOW_INCOME, null);
        GapPoint g = service.gaps(all, onlyLow, GapDimension.ECONOMIC_STATUS).get(0);
        assertFalse(g.math().suppressed());
    }

    @Test
    void englishLearnerGapUsesTheRightGroups() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("n" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.FALL, 75));
            all.add(obs("e" + i, "A", EconomicStatus.LOW_INCOME, true, 2022, Term.FALL, 65));
        }
        GapPoint g = service.gaps(all, Filter.none(), GapDimension.ENGLISH_LEARNER).get(0);
        assertEquals(10.0, g.math().difference().doubleValue(), 1e-9);
    }

    @Test
    void riskUsesTheLatestTermAndPreviousTermHistory() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("s" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.FALL, 80));
            // student 0 drops 30 points in spring, everyone else stays put
            all.add(obs("s" + i, "A", EconomicStatus.LOW_INCOME, false, 2022, Term.SPRING, i == 0 ? 50 : 80));
        }
        var students = service.riskStudents(all, Filter.none());
        assertEquals(12, students.size());
        var top = students.get(0);
        assertEquals("s0", top.studentKey());
        // math 50 is below 60 (2), reading 50 is below 60 (2), and math dropped 30 points (2)
        assertEquals(6, top.score());
    }

    @Test
    void riskSummarySuppressesSchoolsWithFewStudents() {
        List<Observation> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            all.add(obs("a" + i, "Big", EconomicStatus.LOW_INCOME, false, 2022, Term.SPRING, 80));
        }
        for (int i = 0; i < 3; i++) {
            all.add(obs("b" + i, "Tiny", EconomicStatus.LOW_INCOME, false, 2022, Term.SPRING, 80));
        }
        RiskSummary summary = service.riskSummary(all, Filter.none());
        assertEquals("2022 Spring", summary.termLabel());
        var big = summary.schools().stream().filter(s -> s.school().equals("Big")).findFirst().orElseThrow();
        var tiny = summary.schools().stream().filter(s -> s.school().equals("Tiny")).findFirst().orElseThrow();
        assertFalse(big.suppressed());
        assertEquals(12, big.total().intValue());
        assertTrue(tiny.suppressed());
        assertNull(tiny.total());
    }

    @Test
    void optionsListSchoolsGradesAndTerms() {
        List<Observation> all = new ArrayList<>();
        all.addAll(tenStudents("B", EconomicStatus.LOW_INCOME, 2022, Term.FALL));
        all.addAll(tenStudents("A", EconomicStatus.LOW_INCOME, 2023, Term.SPRING));
        var options = service.options(all);
        assertEquals(List.of("A", "B"), options.schools());
        assertEquals(2, options.terms().size());
        assertEquals("2022 Fall", options.terms().get(0).label());
    }
}
