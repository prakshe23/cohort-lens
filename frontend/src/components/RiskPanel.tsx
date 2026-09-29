import { filterQuery } from "../api";
import { MIN_CELL } from "../constants";
import { useApi } from "../hooks";
import { formatInteger } from "../lib/format";
import type { Filters, RiskAssessment, RiskLevel, RiskSummary } from "../types";
import StackedBars, { type BarRow } from "./StackedBars";

interface Props {
  filters: Filters;
  canSeeStudents: boolean;
}

const LEVELS: { id: RiskLevel; label: string; color: string }[] = [
  { id: "LOW", label: "Low", color: "--risk-low" },
  { id: "MEDIUM", label: "Medium", color: "--risk-medium" },
  { id: "HIGH", label: "High", color: "--risk-high" },
];

export default function RiskPanel({ filters, canSeeStudents }: Props) {
  const query = filterQuery(filters);
  const summary = useApi<RiskSummary>(`/api/analytics/risk/summary${query}`);
  const students = useApi<RiskAssessment[]>(
    canSeeStudents ? `/api/analytics/risk/students${filterQuery(filters, { limit: "25" })}` : null
  );

  const rows: BarRow[] = (summary.data?.schools ?? []).map((s) => {
    const total = s.total ?? 0;
    const highShare = total > 0 && s.high !== null ? Math.round((s.high / total) * 100) : 0;
    return {
      label: s.school,
      suppressed: s.suppressed,
      total: s.total,
      side: s.suppressed ? "" : `${formatInteger(total)} students, ${highShare}% high`,
      segments: [
        { id: "LOW", label: "Low", value: s.low ?? 0, color: "--risk-low" },
        { id: "MEDIUM", label: "Medium", value: s.medium ?? 0, color: "--risk-medium" },
        { id: "HIGH", label: "High", value: s.high ?? 0, color: "--risk-high" },
      ],
    };
  });

  const levelColor = (level: RiskLevel) => LEVELS.find((l) => l.id === level)?.color ?? "--risk-low";

  return (
    <div className="grid">
      {summary.error && <p className="error" role="alert">{summary.error}</p>}
      <StackedBars
        title="Risk screening by school"
        subtitle={summary.data?.termLabel ? `Students enrolled in ${summary.data.termLabel}, share by risk level` : "Share of students by risk level"}
        rows={rows}
        legend={LEVELS}
        minCell={MIN_CELL}
        loading={summary.loading}
      />

      <div className="card">
        <h2>How the score works</h2>
        <p className="sub">
          A simple, transparent screening score. It describes patterns and helps decide where to look first. It is not a validated prediction and must never be the only basis for a decision about a student.
        </p>
        <details className="method">
          <summary>Show the rules</summary>
          <ul>
            <li>Attendance below 90%: 2 points. Below 80%: 3 points.</li>
            <li>Math score below 60: 2 points. Below 50: 3 points.</li>
            <li>Reading score below 60: 2 points. Below 50: 3 points.</li>
            <li>Two or more discipline incidents: 1 point. Four or more: 2 points.</li>
            <li>Math score dropped 10 or more points since the previous term: 2 points.</li>
            <li>Total of 0 to 2 is low, 3 to 5 is medium, 6 or more is high. Missing values add no points.</li>
          </ul>
        </details>
      </div>

      {canSeeStudents && (
        <div className={"card" + (students.loading ? " loading" : "")}>
          <div className="card-head">
            <div>
              <h2>Highest scores</h2>
              <p className="sub">Up to 25 students in the latest term. Students appear only as pseudonymous keys.</p>
            </div>
          </div>
          {students.error && <p className="error" role="alert">{students.error}</p>}
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th scope="col">Student key</th>
                  <th scope="col">School</th>
                  <th scope="col" className="num">Grade</th>
                  <th scope="col" className="num">Score</th>
                  <th scope="col">Level</th>
                  <th scope="col">Why</th>
                </tr>
              </thead>
              <tbody>
                {(students.data ?? []).map((s) => (
                  <tr key={s.studentKey}>
                    <td style={{ fontFamily: "ui-monospace, monospace" }}>{s.studentKey}</td>
                    <td>{s.school}</td>
                    <td className="num">{s.grade}</td>
                    <td className="num">{s.score}</td>
                    <td>
                      <span className="pill">
                        <span className="key-box" style={{ background: `var(${levelColor(s.level)})` }} />
                        {s.level.charAt(0) + s.level.slice(1).toLowerCase()}
                      </span>
                    </td>
                    <td>{s.factors.map((f) => f.description).join("; ")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
