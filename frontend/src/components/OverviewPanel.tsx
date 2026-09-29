import { filterQuery } from "../api";
import { MIN_CELL } from "../constants";
import { useApi } from "../hooks";
import { formatInteger, formatNumber, formatPercent } from "../lib/format";
import type { Filters, MeasureStat, Overview, TrendPoint } from "../types";
import LineChart, { type ChartSeries } from "./LineChart";

interface Props {
  filters: Filters;
}

function toSeries(points: TrendPoint[], pick: (p: TrendPoint) => MeasureStat, id: string, label: string, color: string): ChartSeries {
  return {
    id,
    label,
    color,
    values: points.map((p) => pick(p).mean),
    low: points.map((p) => pick(p).ciLow),
    high: points.map((p) => pick(p).ciHigh),
  };
}

export default function OverviewPanel({ filters }: Props) {
  const query = filterQuery(filters);
  const overview = useApi<Overview>(`/api/analytics/overview${query}`);
  const trend = useApi<TrendPoint[]>(`/api/analytics/trend${query}`);
  const points = trend.data ?? [];
  const labels = points.map((p) => p.label);
  const o = overview.data;

  if (o && o.rows === 0) {
    return (
      <div className="card">
        <div className="empty">No data matches this selection. If the database is empty, an administrator can load a CSV from the Data tab.</div>
      </div>
    );
  }

  const hiddenScores = points.filter((p) => p.math.suppressed).length;

  return (
    <div>
      <div className="tiles">
        <div className="tile">
          <div className="label">Students</div>
          <div className="value">{o ? formatInteger(o.students) : "–"}</div>
          <div className="hint">distinct, by pseudonymous key</div>
        </div>
        <div className="tile">
          <div className="label">Student term records</div>
          <div className="value">{o ? formatInteger(o.rows) : "–"}</div>
          <div className="hint">one per student per term</div>
        </div>
        <div className="tile">
          <div className="label">Schools</div>
          <div className="value">{o ? formatInteger(o.schools) : "–"}</div>
          <div className="hint">in this selection</div>
        </div>
        <div className="tile">
          <div className="label">Period covered</div>
          <div className="value" style={{ fontSize: 16, paddingTop: 8 }}>{o?.firstTerm ? `${o.firstTerm} to ${o.lastTerm}` : "–"}</div>
          <div className="hint">first to last term</div>
        </div>
      </div>

      {trend.error && <p className="error" role="alert">{trend.error}</p>}

      <div className="grid">
        <LineChart
          title="Math and reading scores"
          subtitle="Average score by term. The shaded band is a rough 95% interval around each average."
          xLabels={labels}
          series={[
            toSeries(points, (p) => p.math, "math", "Math", "--series-1"),
            toSeries(points, (p) => p.reading, "reading", "Reading", "--series-2"),
          ]}
          format={(v) => formatNumber(v, 1)}
          hiddenCount={hiddenScores}
          minCell={MIN_CELL}
          loading={trend.loading}
        />
        <div className="grid two">
          <LineChart
            title="Attendance rate"
            subtitle="Share of school days attended, average by term."
            xLabels={labels}
            series={[toSeries(points, (p) => p.attendance, "attendance", "Attendance", "--series-1")]}
            format={(v) => formatPercent(v, 1)}
            minCell={MIN_CELL}
            loading={trend.loading}
          />
          <LineChart
            title="Discipline incidents"
            subtitle="Average incidents per student, by term."
            xLabels={labels}
            series={[toSeries(points, (p) => p.discipline, "discipline", "Incidents per student", "--series-1")]}
            format={(v) => formatNumber(v, 2)}
            includeZero
            minCell={MIN_CELL}
            loading={trend.loading}
          />
        </div>
      </div>
    </div>
  );
}
