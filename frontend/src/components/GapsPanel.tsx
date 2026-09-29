import { useEffect, useState } from "react";
import { filterQuery, getJson } from "../api";
import { MIN_CELL } from "../constants";
import { useApi } from "../hooks";
import { formatNumber, formatSigned } from "../lib/format";
import type { Filters, GapDimension, GapPoint, GapStat } from "../types";
import LineChart, { type ChartSeries } from "./LineChart";

interface Props {
  filters: Filters;
  schools: string[];
}

const DIMENSIONS: Record<GapDimension, { label: string; explain: string }> = {
  ECONOMIC_STATUS: {
    label: "Family income",
    explain: "Average for students who are not low income, minus the average for low income students.",
  },
  ENGLISH_LEARNER: {
    label: "English learners",
    explain: "Average for students who are not English learners, minus the average for English learners.",
  },
};

function toSeries(points: GapPoint[], pick: (p: GapPoint) => GapStat, id: string, label: string, color: string): ChartSeries {
  return {
    id,
    label,
    color,
    values: points.map((p) => pick(p).difference),
    low: points.map((p) => pick(p).ciLow),
    high: points.map((p) => pick(p).ciHigh),
  };
}

interface SchoolChange {
  school: string;
  first: number | null;
  last: number | null;
  firstLabel: string | null;
  lastLabel: string | null;
}

function firstAndLast(points: GapPoint[]): Omit<SchoolChange, "school"> {
  const visible = points.filter((p) => p.math.difference !== null);
  if (visible.length < 2) {
    return { first: null, last: null, firstLabel: null, lastLabel: null };
  }
  const a = visible[0];
  const b = visible[visible.length - 1];
  return { first: a.math.difference, last: b.math.difference, firstLabel: a.label, lastLabel: b.label };
}

export default function GapsPanel({ filters, schools }: Props) {
  const [dimension, setDimension] = useState<GapDimension>("ECONOMIC_STATUS");
  const query = filterQuery(filters, { dimension });
  const gaps = useApi<GapPoint[]>(`/api/analytics/gaps${query}`);
  const points = gaps.data ?? [];

  // The same gap, school by school. The school filter is ignored here because this table compares schools.
  const [changes, setChanges] = useState<SchoolChange[]>([]);
  const [changesLoading, setChangesLoading] = useState(false);
  const schoolKey = schools.join("|");
  useEffect(() => {
    if (schools.length === 0) {
      setChanges([]);
      return;
    }
    const controller = new AbortController();
    setChangesLoading(true);
    Promise.all(
      schools.map(async (school): Promise<SchoolChange> => {
        const q = filterQuery({ ...filters, school }, { dimension });
        const data = await getJson<GapPoint[]>(`/api/analytics/gaps${q}`, controller.signal);
        return { school, ...firstAndLast(data) };
      })
    )
      .then((rows) => {
        setChanges(rows);
        setChangesLoading(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setChangesLoading(false);
        }
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [schoolKey, filters.grade, filters.economicStatus, filters.englishLearner, dimension]);

  const sorted = [...changes].sort((a, b) => {
    const ca = a.first !== null && a.last !== null ? a.last - a.first : Infinity;
    const cb = b.first !== null && b.last !== null ? b.last - b.first : Infinity;
    return ca - cb;
  });

  const info = DIMENSIONS[dimension];
  const hidden = points.filter((p) => p.math.suppressed).length;

  return (
    <div className="grid">
      <div className="card">
        <div className="card-head">
          <div>
            <h2>Achievement gaps</h2>
            <p className="sub">{info.explain} Zero means no difference. Positive means the first group scores higher.</p>
          </div>
          <div className="segmented" role="group" aria-label="Compare by">
            {(Object.keys(DIMENSIONS) as GapDimension[]).map((d) => (
              <button key={d} type="button" aria-pressed={dimension === d} onClick={() => setDimension(d)}>
                {DIMENSIONS[d].label}
              </button>
            ))}
          </div>
        </div>
        <p className="note" style={{ marginTop: 0 }}>
          These are averages, not explanations. A gap can come from many things that this data does not measure.
        </p>
      </div>

      {gaps.error && <p className="error" role="alert">{gaps.error}</p>}

      <LineChart
        title="Gap in average score, in points"
        subtitle={filters.school ? `Within ${filters.school}` : "Across the selected schools"}
        xLabels={points.map((p) => p.label)}
        series={[
          toSeries(points, (p) => p.math, "math", "Math", "--series-1"),
          toSeries(points, (p) => p.reading, "reading", "Reading", "--series-2"),
        ]}
        format={(v) => formatNumber(v, 1)}
        includeZero
        hiddenCount={hidden}
        minCell={MIN_CELL}
        loading={gaps.loading}
      />

      <div className={"card" + (changesLoading ? " loading" : "")}>
        <div className="card-head">
          <div>
            <h2>Math gap by school</h2>
            <p className="sub">First and latest term with enough students to report. Sorted from most narrowed to most widened.</p>
          </div>
        </div>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th scope="col">School</th>
                <th scope="col" className="num">First term</th>
                <th scope="col" className="num">Latest term</th>
                <th scope="col">Change</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((row) => {
                if (row.first === null || row.last === null) {
                  return (
                    <tr key={row.school}>
                      <td>{row.school}</td>
                      <td colSpan={3} style={{ color: "var(--text-muted)" }}>Not enough students to report</td>
                    </tr>
                  );
                }
                const change = row.last - row.first;
                const narrowed = change <= -0.5;
                const widened = change >= 0.5;
                return (
                  <tr key={row.school}>
                    <td>{row.school}</td>
                    <td className="num">{formatNumber(row.first, 1)}</td>
                    <td className="num">{formatNumber(row.last, 1)}</td>
                    <td>
                      <span className="pill" style={{ color: narrowed ? "var(--good-text)" : widened ? "var(--bad-text)" : undefined }}>
                        <span aria-hidden="true">{narrowed ? "▼" : widened ? "▲" : "▬"}</span>
                        {formatSigned(change, 1)} points, {narrowed ? "narrowed" : widened ? "widened" : "about the same"}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
