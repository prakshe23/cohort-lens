# CohortLens in plain language

**For:** a researcher, principal or program officer who has student data in spreadsheets and wants to learn from it without creating a privacy problem.

## The problem

School data usually arrives as spreadsheets that were typed by different people at different times. Scores are missing, a few are impossible (a math score of 105), the same student appears twice, and names or ids sit in shared folders. Before anyone can ask a research question, someone has to clean the file by hand, and nobody can later say exactly what was changed.

## What CohortLens does

1. **Reads a spreadsheet and reports on it.** You upload a CSV. You get a list, row by row, of what was accepted, what was fixed, and what was rejected, each with a reason in ordinary words.
2. **Protects students.** Student ids are scrambled with a secret key when the file arrives, and the original ids are never saved. The dashboard never shows a group of fewer than 10 students, so nobody can be picked out.
3. **Shows how things change over time.** Average scores, attendance and discipline by term. Gaps between groups of students and whether they are narrowing. A simple screening view that shows where students may need attention, with the reasons written out.
4. **Keeps a record.** Every import, replacement and export is logged with who did it and when.

## What you can learn from it (in the sample data)

In the invented sample data, the score gap between low income students and others was about 7 points at the start. It narrowed to about 2 points at one school and stayed at about 7 at another. Attendance slipped at one school over six years. The sample data was built to contain these patterns so the dashboard has something to show. Real data will differ.

## What it cannot tell you

* **Why** something happened. The numbers describe. They do not explain.
* **Whether a program worked.** Comparing schools does not separate the effect of a program from everything else that differs between them.
* **What will happen to an individual student.** The risk screening is a way to decide where to look first. It is not a prediction, and it should never be the only reason for a decision about a child.
* **Anything about small groups.** Groups under 10 students are hidden on purpose.

## What is needed to use it for real

* A secret key kept safely by whoever runs the system.
* HTTPS in front of the service.
* Approval under your organization's data privacy rules. Scrambled ids are still personal data.
* Someone who can read a validation report and go back to the source of the data to fix problems there.
