#!/usr/bin/env bash
# Runs the in memory dev server (real analytics, no database, no login) and the dashboard.
# Needs only a JDK (17 or newer) and Node. No Maven, no Docker, no Postgres.
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=backend/target/dev-classes
mkdir -p "$OUT"
javac --release 17 -d "$OUT" backend/src/main/java/com/cohortlens/core/*.java backend/src/main/java/com/cohortlens/dev/*.java

java -cp "$OUT" com.cohortlens.dev.DevServer --port 8080 --load data/synthetic_education_messy.csv &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT

cd frontend
[ -d node_modules ] || npm install
npm run dev
