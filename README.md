# CohortLens

A research data management app and longitudinal analytics dashboard for school data. A lab or a school data team can load messy spreadsheets, get a plain report of what was accepted, fixed or rejected, and then explore trends, subgroup gaps and risk screening without seeing individual students.

The included data is synthetic. No real students, schools or districts appear anywhere in this repository.

![Overview](docs/screenshots/overview.png)

## Why it exists

Researchers outside data science often have a spreadsheet problem before they have an analysis problem: inconsistent files, no record of what was changed, and student identifiers sitting in shared folders. CohortLens is built around three ideas.

1. **Cleaning should be visible.** Every upload produces a row by row report. Nothing is silently dropped or silently fixed.
2. **Privacy by design.** Student ids are replaced with a keyed hash on arrival and the originals are never stored. Groups under 10 students are never displayed.
3. **Descriptive, honest analytics.** Trends and gaps come with rough intervals and plain statements about what the numbers cannot say.

## Scope, milestones and deliverables

Written as a project of roughly 150 hours of effort, the size of a research assistantship.

| Milestone | Deliverable | Status |
|---|---|---|
| 1. Data model and validation | Schema migration, CSV parser, 21 validation checks, pseudonymization, 53 unit tests | Done |
| 2. Analytics | Trends with intervals, subgroup gaps, small cell suppression, explainable risk score | Done |
| 3. Service | Spring Boot REST API, JPA, Flyway, roles, audit log, CSV export | Written, see verification below |
| 4. Dashboard | React and TypeScript, hand built accessible charts, import and validation report | Done |
| 5. Delivery | Docker Compose with PostgreSQL (written, not built in the environment where this was made), one page summary for non technical readers | Summary done, Docker unverified |

## Run it

### Option A: no installs beyond a JDK and Node

Runs the real import, validation and analytics code behind a small in memory server. There is no database and no login. Best for looking around and for working on the dashboard.

```bash
./scripts/run_dev.sh
```

Open http://localhost:5173. The messy sample file is loaded at startup, so the Data tab already shows a validation report.

### Option B: Spring Boot with an in memory database

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# in another terminal
cd frontend && npm install && npm run dev
```

Sign in as `admin` / `admin-dev`, `researcher` / `researcher-dev` or `viewer` / `viewer-dev`. These throwaway credentials exist only in the `dev` profile.

### Option C: Docker Compose with PostgreSQL

```bash
cp .env.example .env      # then edit every value
docker compose up --build
```

Open http://localhost:8080. The app refuses to start if any secret is missing. Use HTTPS in front of it for anything real, because the API uses HTTP Basic authentication.

## What each role can do

| Role | Can do |
|---|---|
| Viewer | Aggregate charts only. Small groups hidden. |
| Researcher | Also the pseudonymized risk list, import reports, and CSV export. |
| Admin | Also uploads data and reads the audit log. |

## Data format

One row per student per term, with these columns in any order: `student_id`, `school`, `grade`, `academic_year`, `term` (FALL or SPRING), `economic_status` (LOW_INCOME or NOT_LOW_INCOME), `english_learner` (Y or N), `math_score` and `reading_score` (0 to 100), `attendance_rate` (0 to 1, or a percent), `discipline_incidents`.

Rules that reject a row: missing student or school, grade outside 0 to 12, invalid year or term, unknown category, a score outside 0 to 100, attendance outside range, a negative incident count, a wrong column count, and a repeat of the same student and term.

Rules that keep a row but flag it: a blank score or attendance (left empty), a blank incident count (counted as 0), and an attendance written as a percent (divided by 100).

Uploads either **add** new rows (repeats are rejected) or **replace** everything (recorded in the audit log). The whole import runs in one transaction, so a failure leaves the previous data untouched.

## How the analytics work, and their limits

* **Trends.** The mean per term with a 95 percent interval from a normal approximation. The same student appears in many terms, so rows are not independent and the intervals are somewhat too narrow. Read them as a rough guide to noise, not as tests.
* **Gaps.** The average for the reference group minus the average for the comparison group, with a Welch style interval. These are descriptions, not explanations.
* **Risk screening.** A transparent point score with the reasons listed for every student. It is a way to decide where to look first. It is not a validated prediction and must not be the only basis for any decision about a student.
* **Suppression.** Any group with fewer than 10 students returns no numbers at all. The threshold is configurable (`cohortlens.min-cell-size`).
* **Pseudonymization.** HMAC SHA 256 with a secret you provide. The same id always maps to the same key, so students can be followed across terms. Changing the secret changes every key, so keep it stable and out of source control. Pseudonymized data is still personal data under most privacy rules, so treat it accordingly.

## API

All routes are under `/api`. Filters (`school`, `grade`, `economicStatus`, `englishLearner`) are optional query parameters on every analytics route.

| Route | Role | Returns |
|---|---|---|
| `GET /analytics/options` | Viewer | Schools, grades and terms present |
| `GET /analytics/overview` | Viewer | Counts and period covered |
| `GET /analytics/trend` | Viewer | Per term means with intervals |
| `GET /analytics/gaps?dimension=` | Viewer | `ECONOMIC_STATUS` or `ENGLISH_LEARNER` gap per term |
| `GET /analytics/risk/summary` | Viewer | Risk level counts per school |
| `GET /analytics/risk/students?limit=` | Researcher | Pseudonymized list with reasons |
| `POST /imports?filename=&mode=` | Admin | Body is the raw CSV (`text/csv`). Returns the import summary |
| `GET /imports`, `GET /imports/{id}/issues` | Researcher | History and row level report |
| `GET /export.csv` | Researcher | Cleaned, pseudonymized data. Audited |
| `GET /audit` | Admin | Latest 200 audit entries |
| `GET /me` | Any | Current user and roles |

Example upload:

```bash
curl -u admin:admin-dev -H "Content-Type: text/csv" --data-binary @data/synthetic_education_messy.csv \
  "http://localhost:8080/api/imports?filename=messy.csv&mode=REPLACE"
```

## Project layout

```
backend/src/main/java/com/cohortlens/
  core/    Framework free logic: CSV parser, validation, pseudonymization, analytics, risk score
  dev/     In memory server for local work (development only)
  app/     Spring Boot: entities, repositories, services, security, controllers
backend/src/main/resources/db/migration/   Flyway schema (PostgreSQL)
frontend/src/                              React and TypeScript dashboard, hand built SVG charts
scripts/generate_synthetic_data.py         Seeded synthetic data with injected errors
```

The core package has no framework dependencies on purpose. It is where the logic that matters lives, it is easy to test, and both the Spring app and the dev server use the same code.

## Testing and verification status

Stated plainly, because a portfolio project should not overclaim.

| Part | How it was checked | Result |
|---|---|---|
| Core Java (parser, validation, pseudonymization, analytics, risk, export, JSON) | 53 JUnit 5 tests, compiled with `--release 17` | Pass |
| Database schema | Migration applied to a real PostgreSQL 16; bad rows rejected by constraints; 19,440 rows loaded | Pass |
| Dev server and dashboard | Ran end to end in a headless browser: charts, filters, hover, keyboard, upload, replace and append, suppression, dark mode | Pass |
| Dashboard code | TypeScript strict mode, production build, 6 unit tests | Pass |
| **Spring Boot layer** | **Written but not compiled or run in the environment where this was built** (Maven Central was unreachable there) | **Run `mvn test` first** |
| H2 dev profile | The migration uses portable SQL, but it was only tested on PostgreSQL | Unverified |
| Dockerfile and Compose file | Written, not built or started | Unverified |

Please run `cd backend && mvn test` before relying on the Spring layer. It includes `ApiSecurityAndImportTest`, which exercises the role rules and the import flow against the H2 database. If something fails, the most likely places are annotation details in `app/` or a column type mismatch reported by Hibernate's schema validation.

Frontend checks:

```bash
cd frontend && npm test && npm run build
```

## Accessibility

Charts are hand built SVG with a keyboard mode (arrow keys move between terms), a table view for every chart, a legend for any chart with two or more series, and color pairs checked for color blindness and contrast in both light and dark themes. Risk level is always written out as text, never carried by color alone.

## Ideas for next steps

* Store the pseudonymization secret in a secrets manager and support key rotation.
* Add a correction endpoint with before and after values in the audit log.
* Replace HTTP Basic with the university single sign on.
* Add a mixed effects model that respects repeated measures, to give honest intervals.
