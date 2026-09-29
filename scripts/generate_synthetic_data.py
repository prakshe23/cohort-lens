#!/usr/bin/env python3
"""Generate a SYNTHETIC longitudinal school dataset for CohortLens.

Everything here is invented. No real students, schools, or districts are
represented. The data has realistic structure (student level ability, an
achievement gap that varies by school, a school with slipping attendance) so
the dashboard has something meaningful to show.

Usage:
    python3 scripts/generate_synthetic_data.py --out data/synthetic_education_messy.csv --messy
    python3 scripts/generate_synthetic_data.py --out data/synthetic_education_clean.csv
"""
import argparse
import csv
import random

SCHOOLS = {
    # name: (share low income, gap narrowing per year in points, attendance drift per year)
    "Maple Grove": (0.30, 0.6, 0.000),
    "Riverside": (0.55, 0.2, -0.004),
    "Oakwood": (0.42, 0.9, 0.000),
    "Cedar Hill": (0.65, 0.1, -0.002),
    "Summit Ridge": (0.25, 0.5, 0.001),
    "Harbor View": (0.50, 0.4, -0.006),  # attendance slips over time
}
FIRST_YEAR, LAST_YEAR = 2019, 2024
COHORT_SIZE = 45  # per school per entering cohort
TERMS = ["FALL", "SPRING"]


def clip(x, lo, hi):
    return max(lo, min(hi, x))


def poisson(rng, lam):
    # Knuth's algorithm; fine for small lambda
    import math

    l, k, p = math.exp(-lam), 0, 1.0
    while True:
        k += 1
        p *= rng.random()
        if p <= l:
            return k - 1


def build_rows(rng):
    rows = []
    sid = 100000
    for school, (low_share, narrow, att_drift) in SCHOOLS.items():
        for entry_year in range(FIRST_YEAR - 5, LAST_YEAR + 1):
            for _ in range(COHORT_SIZE):
                sid += 1
                low = rng.random() < low_share
                el = rng.random() < (0.22 if low else 0.06)
                theta = rng.gauss(0, 1)
                for year in range(FIRST_YEAR, LAST_YEAR + 1):
                    grade = 3 + (year - entry_year)
                    if grade < 3 or grade > 8:
                        continue
                    for term in TERMS:
                        t = (year - FIRST_YEAR) + (0.5 if term == "SPRING" else 0.0)
                        gap = max(0.0, 7.0 - narrow * t) if low else 0.0
                        el_pen_math = 3.0 if el else 0.0
                        el_pen_read = 6.0 if el else 0.0
                        growth = 1.6 * (grade - 3) + (1.0 if term == "SPRING" else 0.0)
                        base = 58 + growth + 9 * theta
                        math = clip(base - gap - el_pen_math + rng.gauss(0, 6), 0, 100)
                        read = clip(base + 2 - gap - el_pen_read + rng.gauss(0, 6), 0, 100)
                        att = clip(
                            0.955 + 0.015 * theta - (0.025 if low else 0) + att_drift * t + rng.gauss(0, 0.02),
                            0.4,
                            1.0,
                        )
                        disc = poisson(rng, max(0.05, 0.30 + (0.45 if low else 0) - 0.15 * theta))
                        rows.append(
                            {
                                "student_id": f"S{sid}",
                                "school": school,
                                "grade": grade,
                                "academic_year": year,
                                "term": term,
                                "economic_status": "LOW_INCOME" if low else "NOT_LOW_INCOME",
                                "english_learner": "Y" if el else "N",
                                "math_score": round(math, 1),
                                "reading_score": round(read, 1),
                                "attendance_rate": round(att, 3),
                                "discipline_incidents": disc,
                            }
                        )
    return rows


def make_messy(rng, rows):
    """Inject realistic data entry problems into about 4 percent of rows."""
    out = []
    for r in rows:
        r = dict(r)
        roll = rng.random()
        if roll < 0.012:
            r["math_score"] = ""  # missing score (warning)
        elif roll < 0.020:
            r["reading_score"] = ""  # missing score (warning)
        elif roll < 0.026:
            r["attendance_rate"] = round(r["attendance_rate"] * 100, 1)  # percent format (warning, normalized)
        elif roll < 0.030:
            r["math_score"] = rng.choice([105, 120, -3])  # out of range (error)
        elif roll < 0.033:
            r["term"] = "Winter"  # invalid term (error)
        elif roll < 0.035:
            r["grade"] = 14  # invalid grade (error)
        elif roll < 0.038:
            r["economic_status"] = " low_income "  # sloppy formatting (fixed silently)
        elif roll < 0.041:
            r["english_learner"] = rng.choice(["yes", "no", "TRUE", "0"])  # alternate booleans
        elif roll < 0.043:
            r["school"] = ""  # missing school (error)
        out.append(r)
        if rng.random() < 0.004:
            out.append(dict(r))  # exact duplicate row (error on second copy)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--messy", action="store_true", help="inject data entry problems")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    rows = build_rows(rng)
    if args.messy:
        rows = make_messy(rng, rows)

    fields = [
        "student_id", "school", "grade", "academic_year", "term",
        "economic_status", "english_learner", "math_score", "reading_score",
        "attendance_rate", "discipline_incidents",
    ]
    with open(args.out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)
    print(f"wrote {len(rows)} rows to {args.out}")


if __name__ == "__main__":
    main()
