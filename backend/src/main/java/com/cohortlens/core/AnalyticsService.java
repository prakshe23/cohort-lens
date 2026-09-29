package com.cohortlens.core;

import com.cohortlens.core.RiskScorer.RiskAssessment;
import com.cohortlens.core.RiskScorer.RiskLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Descriptive analytics over observations: trends over time, subgroup gaps, and risk screening.
 *
 * <p>Privacy rule: any group with fewer than {@code minCell} students is suppressed. Its numbers are
 * returned as null with {@code suppressed = true}, so small subgroups can never be picked out.
 *
 * <p>Everything here is descriptive. Confidence intervals use a normal approximation and treat rows
 * as independent, which they are not (the same student appears in many terms), so intervals are
 * somewhat too narrow. Read them as a rough guide to noise, not as formal tests.
 */
public final class AnalyticsService {

    public static final int DEFAULT_MIN_CELL = 10;
    private static final double Z_95 = 1.96;

    private final int minCell;

    public AnalyticsService() {
        this(DEFAULT_MIN_CELL);
    }

    public AnalyticsService(int minCell) {
        if (minCell < 1) {
            throw new IllegalArgumentException("minCell must be at least 1");
        }
        this.minCell = minCell;
    }

    // ---------------------------------------------------------------- result types

    public record Filter(String school, Integer grade, EconomicStatus economicStatus, Boolean englishLearner) {
        public static Filter none() {
            return new Filter(null, null, null, null);
        }

        boolean matches(Observation o) {
            return (school == null || school.equals(o.school()))
                    && (grade == null || grade == o.grade())
                    && (economicStatus == null || economicStatus == o.economicStatus())
                    && (englishLearner == null || englishLearner == o.englishLearner());
        }
    }

    /** Mean with a 95 percent interval. All values are null and suppressed is true for small groups. */
    public record MeasureStat(boolean suppressed, Integer n, Double mean, Double ciLow, Double ciHigh) {
    }

    public record TrendPoint(int termIndex, String label, Integer n, MeasureStat math, MeasureStat reading,
                             MeasureStat attendance, MeasureStat discipline) {
    }

    public enum GapDimension { ECONOMIC_STATUS, ENGLISH_LEARNER }

    /** Reference group mean minus comparison group mean, with a 95 percent interval. */
    public record GapStat(boolean suppressed, Double difference, Double ciLow, Double ciHigh) {
    }

    public record GapPoint(int termIndex, String label, GapDimension dimension, String referenceGroup,
                           String comparisonGroup, GapStat math, GapStat reading, GapStat attendance) {
    }

    public record RiskBySchool(String school, boolean suppressed, Integer total, Integer low, Integer medium,
                               Integer high) {
    }

    public record RiskSummary(Integer termIndex, String termLabel, List<RiskBySchool> schools) {
    }

    public record Overview(int rows, int students, int schools, String firstTerm, String lastTerm) {
    }

    public record TermOption(int termIndex, String label) {
    }

    public record Options(List<String> schools, List<Integer> grades, List<TermOption> terms) {
    }

    // ---------------------------------------------------------------- queries

    public Options options(List<Observation> all) {
        TreeSet<String> schools = new TreeSet<>();
        TreeSet<Integer> grades = new TreeSet<>();
        TreeSet<Integer> terms = new TreeSet<>();
        for (Observation o : all) {
            schools.add(o.school());
            grades.add(o.grade());
            terms.add(o.termIndex());
        }
        List<TermOption> termOptions = terms.stream().map(t -> new TermOption(t, Term.label(t))).toList();
        return new Options(List.copyOf(schools), List.copyOf(grades), termOptions);
    }

    public Overview overview(List<Observation> all, Filter filter) {
        List<Observation> rows = apply(all, filter);
        if (rows.isEmpty()) {
            return new Overview(0, 0, 0, null, null);
        }
        long students = rows.stream().map(Observation::studentKey).distinct().count();
        long schools = rows.stream().map(Observation::school).distinct().count();
        int first = rows.stream().mapToInt(Observation::termIndex).min().orElseThrow();
        int last = rows.stream().mapToInt(Observation::termIndex).max().orElseThrow();
        return new Overview(rows.size(), (int) students, (int) schools, Term.label(first), Term.label(last));
    }

    public List<TrendPoint> trend(List<Observation> all, Filter filter) {
        Map<Integer, List<Observation>> byTerm = apply(all, filter).stream()
                .collect(Collectors.groupingBy(Observation::termIndex, TreeMap::new, Collectors.toList()));
        List<TrendPoint> points = new ArrayList<>();
        for (Map.Entry<Integer, List<Observation>> e : byTerm.entrySet()) {
            List<Observation> rows = e.getValue();
            Integer n = rows.size() < minCell ? null : rows.size();
            points.add(new TrendPoint(e.getKey(), Term.label(e.getKey()), n,
                    measure(rows, Observation::mathScore),
                    measure(rows, Observation::readingScore),
                    measure(rows, Observation::attendanceRate),
                    measure(rows, o -> (double) o.disciplineIncidents())));
        }
        return points;
    }

    public List<GapPoint> gaps(List<Observation> all, Filter filter, GapDimension dimension) {
        // The filter must not restrict the very dimension being compared.
        Filter f = switch (dimension) {
            case ECONOMIC_STATUS -> new Filter(filter.school(), filter.grade(), null, filter.englishLearner());
            case ENGLISH_LEARNER -> new Filter(filter.school(), filter.grade(), filter.economicStatus(), null);
        };
        Map<Integer, List<Observation>> byTerm = apply(all, f).stream()
                .collect(Collectors.groupingBy(Observation::termIndex, TreeMap::new, Collectors.toList()));

        String reference = dimension == GapDimension.ECONOMIC_STATUS ? "NOT_LOW_INCOME" : "NOT_ENGLISH_LEARNER";
        String comparison = dimension == GapDimension.ECONOMIC_STATUS ? "LOW_INCOME" : "ENGLISH_LEARNER";

        List<GapPoint> points = new ArrayList<>();
        for (Map.Entry<Integer, List<Observation>> e : byTerm.entrySet()) {
            List<Observation> refGroup = new ArrayList<>();
            List<Observation> cmpGroup = new ArrayList<>();
            for (Observation o : e.getValue()) {
                boolean isComparison = dimension == GapDimension.ECONOMIC_STATUS
                        ? o.economicStatus() == EconomicStatus.LOW_INCOME
                        : o.englishLearner();
                (isComparison ? cmpGroup : refGroup).add(o);
            }
            points.add(new GapPoint(e.getKey(), Term.label(e.getKey()), dimension, reference, comparison,
                    gap(refGroup, cmpGroup, Observation::mathScore),
                    gap(refGroup, cmpGroup, Observation::readingScore),
                    gap(refGroup, cmpGroup, Observation::attendanceRate)));
        }
        return points;
    }

    /** Risk assessments for every student present in the most recent term of the filtered data. */
    public List<RiskAssessment> riskStudents(List<Observation> all, Filter filter) {
        // Score against the full history of each student, then apply the filter to the latest row.
        Map<String, List<Observation>> byStudent = all.stream()
                .collect(Collectors.groupingBy(Observation::studentKey));
        List<Observation> filtered = apply(all, filter);
        if (filtered.isEmpty()) {
            return List.of();
        }
        int lastTerm = filtered.stream().mapToInt(Observation::termIndex).max().orElseThrow();

        List<RiskAssessment> result = new ArrayList<>();
        for (Observation latest : filtered) {
            if (latest.termIndex() != lastTerm) {
                continue;
            }
            Observation previous = byStudent.get(latest.studentKey()).stream()
                    .filter(o -> o.termIndex() < lastTerm)
                    .max(Comparator.comparingInt(Observation::termIndex))
                    .orElse(null);
            result.add(RiskScorer.assess(latest, previous));
        }
        result.sort(Comparator.comparingInt(RiskAssessment::score).reversed()
                .thenComparing(RiskAssessment::studentKey));
        return result;
    }

    public RiskSummary riskSummary(List<Observation> all, Filter filter) {
        List<RiskAssessment> assessments = riskStudents(all, filter);
        if (assessments.isEmpty()) {
            return new RiskSummary(null, null, List.of());
        }
        int termIndex = assessments.get(0).termIndex();
        Map<String, List<RiskAssessment>> bySchool = assessments.stream()
                .collect(Collectors.groupingBy(RiskAssessment::school, TreeMap::new, Collectors.toList()));
        List<RiskBySchool> schools = new ArrayList<>();
        for (Map.Entry<String, List<RiskAssessment>> e : bySchool.entrySet()) {
            List<RiskAssessment> group = e.getValue();
            if (group.size() < minCell) {
                schools.add(new RiskBySchool(e.getKey(), true, null, null, null, null));
                continue;
            }
            schools.add(new RiskBySchool(e.getKey(), false, group.size(),
                    count(group, RiskLevel.LOW), count(group, RiskLevel.MEDIUM), count(group, RiskLevel.HIGH)));
        }
        return new RiskSummary(termIndex, Term.label(termIndex), schools);
    }

    // ---------------------------------------------------------------- helpers

    private static int count(List<RiskAssessment> group, RiskLevel level) {
        return (int) group.stream().filter(a -> a.level() == level).count();
    }

    private static List<Observation> apply(List<Observation> all, Filter filter) {
        Filter f = Objects.requireNonNullElse(filter, Filter.none());
        return all.stream().filter(f::matches).toList();
    }

    private MeasureStat measure(List<Observation> rows, Function<Observation, Double> getter) {
        double[] values = rows.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        if (values.length < minCell) {
            return new MeasureStat(true, null, null, null, null);
        }
        double mean = Stats.mean(values);
        double half = Z_95 * Math.sqrt(Stats.variance(values) / values.length);
        return new MeasureStat(false, values.length, round(mean), round(mean - half), round(mean + half));
    }

    private GapStat gap(List<Observation> reference, List<Observation> comparison,
                        Function<Observation, Double> getter) {
        double[] a = reference.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        double[] b = comparison.stream().map(getter).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        if (a.length < minCell || b.length < minCell) {
            return new GapStat(true, null, null, null);
        }
        double diff = Stats.mean(a) - Stats.mean(b);
        double se = Math.sqrt(Stats.variance(a) / a.length + Stats.variance(b) / b.length);
        return new GapStat(false, round(diff), round(diff - Z_95 * se), round(diff + Z_95 * se));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Small numeric helpers. */
    static final class Stats {
        private Stats() {
        }

        static double mean(double[] v) {
            double sum = 0;
            for (double x : v) {
                sum += x;
            }
            return sum / v.length;
        }

        /** Sample variance (n minus 1 in the denominator). */
        static double variance(double[] v) {
            if (v.length < 2) {
                return 0;
            }
            double m = mean(v);
            double sum = 0;
            for (double x : v) {
                sum += (x - m) * (x - m);
            }
            return sum / (v.length - 1);
        }
    }
}
