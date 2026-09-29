package com.cohortlens.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A transparent, rule based screening score. Each rule adds points and the reasons are returned
 * with the score, so a researcher can see exactly why a student was flagged.
 *
 * <p>This is a descriptive screening tool, not a validated predictor. It should be used to look at
 * patterns and to prioritize follow up, and it should never be the only basis for a decision about
 * a student.
 *
 * <pre>
 * Attendance below 90 percent: 2 points, below 80 percent: 3 points
 * Math score below 60: 2 points, below 50: 3 points
 * Reading score below 60: 2 points, below 50: 3 points
 * Two or more discipline incidents: 1 point, four or more: 2 points
 * Math score dropped 10 or more points since the previous term: 2 points
 * Score 0 to 2 is LOW, 3 to 5 is MEDIUM, 6 or more is HIGH.
 * </pre>
 */
public final class RiskScorer {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public record RiskFactor(String code, String description, int points) {
    }

    public record RiskAssessment(
            String studentKey,
            String school,
            int grade,
            int termIndex,
            String termLabel,
            int score,
            RiskLevel level,
            List<RiskFactor> factors) {
    }

    private RiskScorer() {
    }

    public static RiskAssessment assess(Observation latest, Observation previous) {
        List<RiskFactor> factors = new ArrayList<>();

        Double attendance = latest.attendanceRate();
        if (attendance != null) {
            if (attendance < 0.80) {
                factors.add(new RiskFactor("ATTENDANCE_VERY_LOW", "Attendance below 80 percent", 3));
            } else if (attendance < 0.90) {
                factors.add(new RiskFactor("ATTENDANCE_LOW", "Attendance below 90 percent", 2));
            }
        }
        addScoreFactor(factors, "MATH", "Math", latest.mathScore());
        addScoreFactor(factors, "READING", "Reading", latest.readingScore());

        if (latest.disciplineIncidents() >= 4) {
            factors.add(new RiskFactor("DISCIPLINE_HIGH", "Four or more discipline incidents", 2));
        } else if (latest.disciplineIncidents() >= 2) {
            factors.add(new RiskFactor("DISCIPLINE_ELEVATED", "Two or more discipline incidents", 1));
        }

        if (previous != null && latest.mathScore() != null && previous.mathScore() != null
                && previous.mathScore() - latest.mathScore() >= 10.0) {
            factors.add(new RiskFactor("MATH_DECLINE", "Math score dropped 10 or more points since the previous term", 2));
        }

        int score = factors.stream().mapToInt(RiskFactor::points).sum();
        RiskLevel level = score >= 6 ? RiskLevel.HIGH : score >= 3 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        return new RiskAssessment(latest.studentKey(), latest.school(), latest.grade(), latest.termIndex(),
                Term.label(latest.termIndex()), score, level, List.copyOf(factors));
    }

    private static void addScoreFactor(List<RiskFactor> factors, String code, String subject, Double score) {
        if (score == null) {
            return;
        }
        if (score < 50) {
            factors.add(new RiskFactor(code + "_VERY_LOW", subject + " score below 50", 3));
        } else if (score < 60) {
            factors.add(new RiskFactor(code + "_LOW", subject + " score below 60", 2));
        }
    }
}
