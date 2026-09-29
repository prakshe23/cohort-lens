# Sample data

Both files are synthetic. No real students, schools or districts are represented.
They are produced by `scripts/generate_synthetic_data.py` with a fixed random seed.

* `synthetic_education_clean.csv` has no data entry problems.
* `synthetic_education_messy.csv` has about 4 percent of rows damaged on purpose, so the
  validation report has something to find: blank scores, out of range scores, attendance written
  as a percent, invalid terms and grades, missing schools, sloppy category spellings, and
  duplicate rows.

The generated data contains built in patterns so the dashboard has a story to show: an income
gap in test scores that narrows quickly at some schools and not at others, and one school whose
attendance slips over the six years. Real data will look different.
