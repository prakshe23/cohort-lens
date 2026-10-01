# CohortLens

A small tool that cleans messy school data, protects student privacy, and shows trends in a dashboard. All data in this repository is synthetic. No real students, schools or districts appear anywhere.

![Overview](docs/screenshots/overview.png)

## The problem

Researchers who study education often get their data as spreadsheets exported from different school systems. Before any analysis can start, they hit three problems:

1. The files are messy. Scores are blank, attendance is written as 0.93 in one file and 93 in another, and the same student shows up twice.
2. Nobody can say what was changed during cleaning, so results are hard to trust or repeat.
3. Student ids sit in shared folders, which is a privacy risk.

## How I found out

I read the description of Luddy's Faculty Assistance in Data Science (FADS) program and the kinds of skills it asks for: databases, SQL, data visualization, statistics and Java. Faculty in that kind of program typically need help getting research data into a clean, trustworthy form before analysis. I built this project to show that skill set on a realistic version of the problem.

## The solution

CohortLens does three things:

- **Cleans on upload.** Every uploaded file gets a report that says which rows were accepted, fixed or rejected, and why. Nothing is changed silently.
- **Protects students.** Student ids are replaced with a keyed hash as soon as the file arrives, and the originals are never stored. Any group with fewer than 10 students is hidden.
- **Shows what the data says.** A dashboard with trends over time, gaps between student groups, and a simple risk screening score that lists its reasons.

## Implementation details

| Part | What it is |
|---|---|
| Core logic | Plain Java with no framework: CSV parser, 21 validation checks, pseudonymization (HMAC SHA 256), analytics, risk score. Package `com.cohortlens.core`. |
| Backend | Spring Boot 3, Java 17, JPA, Flyway and PostgreSQL. Roles are Viewer, Researcher and Admin. Imports and exports go in an audit log. |
| Dashboard | React, TypeScript and Vite. Charts are drawn by hand in SVG, with keyboard support, a table view and dark mode. |
| Data | `scripts/generate_synthetic_data.py` makes seeded fake data, and `--messy` adds about 4 percent errors on purpose. |

Rules in short:

- A row is **rejected** for a missing student or school, a grade outside 0 to 12, a bad term, a score outside 0 to 100, or a repeat of the same student and term.
- A row is **kept with a warning** for a blank score, a blank incident count (counted as 0), or attendance written as a percent (divided by 100).
- Trends use a rough 95 percent interval. Gaps are the reference group average minus the comparison group average. The risk score is a transparent point system and is not a validated prediction.

## Result

On the messy sample file (19,514 rows):

- 19,217 rows accepted, 297 rejected, 518 warnings, each one explained in the report.
- The dashboard found the patterns planted in the synthetic data: one school's income gap narrows from about 7 points to about 2, and another school's attendance slips.
- 53 Java tests pass for the core logic. The database schema was applied to a real PostgreSQL 16 and its constraints rejected every bad row tried. The dashboard was checked in a browser, and its 6 frontend tests pass.

**Not yet verified:** the Spring Boot layer, the Docker files and the H2 dev profile were written but never compiled or run, because Maven Central was blocked where this was built. Run `cd backend && mvn test` before relying on them.

## Run it

Quickest way, needing only a JDK 17 or newer and Node. It runs the real import and analytics code with no database and no login:

```bash
./scripts/run_dev.sh
```

Open http://localhost:5173. The messy sample file is loaded at startup.

With Spring Boot and an in memory database:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
```

Sign in as `admin` / `admin-dev`, `researcher` / `researcher-dev` or `viewer` / `viewer-dev`. These credentials exist only in the `dev` profile.

With Docker and PostgreSQL (untested): copy `.env.example` to `.env`, fill in every value, then run `docker compose up --build`.

## Data format

One row per student per term. Columns: `student_id`, `school`, `grade`, `academic_year`, `term` (FALL or SPRING), `economic_status` (LOW_INCOME or NOT_LOW_INCOME), `english_learner` (Y or N), `math_score`, `reading_score`, `attendance_rate`, `discipline_incidents`.
