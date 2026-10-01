# CohortLens

CohortLens is a research data management tool and longitudinal analytics dashboard for school level education data. It ingests heterogeneous spreadsheet exports, validates and pseudonymizes them, and supports descriptive analysis of student outcomes over time. All data in this repository is synthetic. No real students, schools or districts are represented.

![Overview](docs/screenshots/overview.png)

## Problem

Education researchers commonly receive administrative data as spreadsheet exports from several student information systems. Three issues recur before any analysis can begin.

1. **Data quality.** Exports contain missing scores, inconsistent units (attendance recorded as 0.93 in one file and 93 in another), out of range values and duplicate student term records.
2. **Provenance.** Cleaning is often done by hand, so the transformations applied to the data are undocumented. This undermines reproducibility and makes it difficult to defend results.
3. **Privacy.** Direct student identifiers are frequently stored in shared folders, and small subgroups can be re identified when results are reported at fine granularity.

## Solution

CohortLens addresses each issue directly and adds a small machine learning study on top of the cleaned data.

- **Transparent validation.** Every upload produces a report that lists each row as accepted, corrected with a warning, or rejected, together with the reason and the row number. No change is applied silently.
- **Privacy by design.** Student identifiers are replaced on arrival with a keyed hash (HMAC SHA 256) and the originals are never stored. Any subgroup with fewer than 10 students is suppressed in all outputs. Access is role based, and imports and exports are written to an audit log.
- **Descriptive analytics.** The dashboard reports term level trends with approximate 95 percent intervals, subgroup gaps (economic status and English learner status), and a transparent point based risk screening score that lists the reasons behind each score.
- **A classifier with a proper evaluation.** A logistic regression predicts which students will score below 60 in math next term, using only what is known at the end of the current term. It is compared against the rule based risk score and simple baselines, with the evaluation designed to avoid leakage (see `docs/ML_EVALUATION.md`).

## Implementation details

| Component | Description |
|---|---|
| Core logic | Framework independent Java 17 in `com.cohortlens.core`: CSV parser, 21 validation checks, pseudonymizer, analytics service, risk scorer and CSV exporter. Both the Spring application and the development server reuse this code. |
| Backend | Spring Boot 3.3 with JPA, Flyway migrations, PostgreSQL and Spring Security (HTTP Basic, stateless). Roles are Viewer, Researcher and Admin. |
| Dashboard | React 18 and TypeScript in strict mode, built with Vite. Charts are hand built SVG with keyboard navigation, a table view for each chart and a light and dark theme. The color palette was validated for color vision deficiency and contrast. |
| Machine learning | Framework independent Java 17 in `com.cohortlens.ml`: logistic regression written from scratch (Newton's method with an L2 penalty), a split by student, preprocessing fitted on training data only, metrics (AUC, average precision, Brier score, log loss, calibration), a student level bootstrap for intervals, and a report writer. |
| Data | `scripts/generate_synthetic_data.py` produces seeded synthetic records. The `--messy` flag injects roughly 4 percent errors to exercise the validator. |

**Validation rules.** A row is rejected for a missing student or school, a grade outside 0 to 12, an invalid year or term, an unknown category, a score outside 0 to 100, attendance out of range, a negative incident count, a wrong column count, or a repeated student and term pair. A row is retained with a warning when a score or attendance value is blank (left empty), when an incident count is blank (treated as 0), or when attendance appears to be a percentage (divided by 100). An append import rejects keys that already exist, and a replace import clears existing data first. Each import runs in a single transaction, so a failure leaves the previous data unchanged.

**Analytical limitations.** The same student appears in many terms, so observations are not independent and the reported intervals are somewhat too narrow. They should be read as a rough indication of noise, not as formal tests. Gaps are descriptive differences between group means and are not causal estimates. The risk score is a prioritization heuristic, not a validated predictive model. Pseudonymized data remains personal data under most privacy frameworks and should be handled accordingly.

## Results

Running the validator on the messy synthetic file (19,514 rows) gave 19,217 accepted rows, 297 rejected rows and 518 warnings, each documented in the import report. The dashboard recovered the patterns that were planted in the generator: the income gap at one school narrows from about 7 points to about 2, and attendance at another school declines over time.

**Machine learning result.** The task is to predict, from one term of data (and the term before it), whether a student's math score next term will be below 60. On the test students (587 students, 3,125 examples, never used for any choice), the logistic regression reached an AUC of 0.896 (95% interval 0.879 to 0.911). The rule based risk score reached 0.850 and the current math score alone 0.857, so the model is better by about 0.046 (interval 0.038 to 0.054 from a paired bootstrap of students). Its predicted probabilities were well calibrated (expected calibration error 0.015). Adding economic status and English learner status made no difference (AUC change 0.000), so the final model does not need them. Within each subgroup the mean predicted rate was within about one point of the observed rate. A pipeline trained on shuffled labels averaged an AUC of 0.49, as it should. The full tables, coefficients and limitations are in `docs/ML_EVALUATION.md`.

These numbers come from synthetic data in which next term math is largely a continuation of this term, so they show that the method and the evaluation work, not how well it would predict for real students.

Verification performed so far:

- 85 JUnit tests pass: 53 for the core logic and 32 for the machine learning package (metrics checked against hand computed values, the classifier recovering known weights, the split, the preprocessing, leakage checks on the features, and a deterministic end to end run).
- I also checked the classifier against scikit-learn on the same splits, once and outside this repository: the test AUC was 0.8955 against 0.896 here, and the coefficients agreed to three decimals.
- The database schema was applied to PostgreSQL 16, and its constraints rejected every invalid row I tested. 19,440 clean rows loaded successfully.
- The dashboard was exercised in a headless browser (filters, hover, keyboard navigation, upload in both modes, suppression, dark mode and a mobile width). Its production build succeeds and its 6 unit tests pass.

**Limitation of this evaluation.** The Spring Boot layer, the Docker configuration and the H2 development profile were written but not compiled or run, because Maven Central was inaccessible in the environment where I built the project. Run `cd backend && mvn test` before relying on them.

**Future work.** A mixed effects model would account for repeated measures and give more defensible intervals. Other improvements include key rotation for the pseudonymization secret, a correction endpoint with before and after values in the audit log, and university single sign on.

## Running the project

The quickest option needs only a JDK (17 or newer) and Node. It runs the real import and analytics code behind a small in memory server, with no database and no authentication.

```bash
./scripts/run_dev.sh
```

Open http://localhost:5173. The messy sample file is loaded at startup.

To train and evaluate the classifier (needs only a JDK, takes a few seconds, and rewrites `docs/ML_EVALUATION.md`):

```bash
./scripts/run_ml_evaluation.sh
```

To run the Spring Boot application with an in memory database:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
```

The development credentials are `admin` / `admin-dev`, `researcher` / `researcher-dev` and `viewer` / `viewer-dev`. They exist only in the `dev` profile. For Docker with PostgreSQL (untested), copy `.env.example` to `.env`, set every value, and run `docker compose up --build`.

## Input format

One row per student per term, with the columns `student_id`, `school`, `grade`, `academic_year`, `term` (FALL or SPRING), `economic_status` (LOW_INCOME or NOT_LOW_INCOME), `english_learner` (Y or N), `math_score`, `reading_score`, `attendance_rate` and `discipline_incidents`.
