#!/usr/bin/env bash
# Trains and evaluates the next term math classifier on the synthetic data and writes docs/ML_EVALUATION.md.
# Needs only a JDK (17 or newer). No Maven, no database, no network.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=backend/target/ml-classes
mkdir -p "$OUT"
javac --release 17 -d "$OUT" backend/src/main/java/com/cohortlens/core/*.java backend/src/main/java/com/cohortlens/ml/*.java
java -cp "$OUT" com.cohortlens.ml.EvaluationRunner "${1:-data/synthetic_education_messy.csv}" "${2:-docs/ML_EVALUATION.md}"
