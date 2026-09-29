import { useState } from "react";
import { useElementWidth } from "../hooks";
import { linear, niceTicks } from "../lib/scale";

export interface ChartSeries {
  id: string;
  label: string;
  /** Name of a CSS variable that holds the series color, for example "--series-1". */
  color: string;
  values: (number | null)[];
  low?: (number | null)[];
  high?: (number | null)[];
}

interface Props {
  title: string;
  subtitle?: string;
  xLabels: string[];
  series: ChartSeries[];
  format: (value: number) => string;
  includeZero?: boolean;
  /** How many x positions have no visible data because the group was too small. */
  hiddenCount?: number;
  minCell?: number;
  loading?: boolean;
  intervalLabel?: string;
}

const HEIGHT = 250;
const MARGIN = { top: 12, bottom: 30, left: 52 };
const LABEL_MIN_GAP = 16;

/** Indices of consecutive positions where every listed array has a value. */
function runs(length: number, arrays: (number | null)[][]): number[][] {
  const result: number[][] = [];
  let current: number[] = [];
  for (let i = 0; i < length; i++) {
    if (arrays.every((a) => a[i] !== null && a[i] !== undefined)) {
      current.push(i);
    } else if (current.length) {
      result.push(current);
      current = [];
    }
  }
  if (current.length) {
    result.push(current);
  }
  return result;
}

export default function LineChart({
  title,
  subtitle,
  xLabels,
  series,
  format,
  includeZero = false,
  hiddenCount = 0,
  minCell = 10,
  loading = false,
  intervalLabel = "95% interval",
}: Props) {
  const [wrapRef, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const [asTable, setAsTable] = useState(false);
  const n = xLabels.length;

  const allValues: number[] = [];
  for (const s of series) {
    for (const arr of [s.values, s.low ?? [], s.high ?? []]) {
      for (const v of arr) {
        if (v !== null && v !== undefined && Number.isFinite(v)) {
          allValues.push(v);
        }
      }
    }
  }
  const hasData = allValues.length > 0;

  const header = (
    <div className="card-head">
      <div>
        <h2>{title}</h2>
        {subtitle && <p className="sub">{subtitle}</p>}
      </div>
      {hasData && (
        <button className="link-btn" type="button" onClick={() => setAsTable((t) => !t)}>
          {asTable ? "Show chart" : "Show table"}
        </button>
      )}
    </div>
  );

  if (!hasData) {
    return (
      <div className={"card" + (loading ? " loading" : "")}>
        {header}
        <div className="empty">
          Nothing to show for this selection. Groups smaller than {minCell} students are hidden to protect privacy.
        </div>
      </div>
    );
  }

  if (asTable) {
    return (
      <div className={"card" + (loading ? " loading" : "")}>
        {header}
        <div className="table-wrap">
          <table className="data">
            <caption className="visually-hidden">{title}</caption>
            <thead>
              <tr>
                <th scope="col">Term</th>
                {series.map((s) => (
                  <th scope="col" className="num" key={s.id}>
                    {s.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {xLabels.map((label, i) => (
                <tr key={label}>
                  <th scope="row">{label}</th>
                  {series.map((s) => {
                    const v = s.values[i];
                    const lo = s.low?.[i];
                    const hi = s.high?.[i];
                    return (
                      <td className="num" key={s.id}>
                        {v === null || v === undefined ? "hidden" : format(v)}
                        {v !== null && v !== undefined && lo != null && hi != null && (
                          <span style={{ color: "var(--text-muted)" }}>
                            {" "}
                            ({format(lo)} to {format(hi)})
                          </span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // ---- scales
  const dataMin = Math.min(...allValues, includeZero ? 0 : Infinity);
  const dataMax = Math.max(...allValues, includeZero ? 0 : -Infinity);
  const pad = (dataMax - dataMin || 1) * 0.06;
  // A measure that cannot be negative (a count, a rate) should not get an axis that dips below zero.
  const lower = includeZero && dataMin >= 0 ? 0 : dataMin - pad;
  const ticks = niceTicks(lower, dataMax + pad, 5);
  const yDomain: [number, number] = [ticks[0], ticks[ticks.length - 1]];
  const y = linear(yDomain, [HEIGHT - MARGIN.bottom, MARGIN.top]);

  // Direct labels sit at the line ends. They are used only when they do not collide.
  const lastIndex = series.map((s) => {
    for (let i = s.values.length - 1; i >= 0; i--) {
      if (s.values[i] !== null && s.values[i] !== undefined) {
        return i;
      }
    }
    return -1;
  });
  const endYs = series
    .map((s, k) => (lastIndex[k] >= 0 ? y(s.values[lastIndex[k]] as number) : null))
    .filter((v): v is number => v !== null)
    .sort((a, b) => a - b);
  let directLabels = series.length >= 2 && series.length <= 4;
  for (let i = 1; i < endYs.length; i++) {
    if (endYs[i] - endYs[i - 1] < LABEL_MIN_GAP) {
      directLabels = false;
    }
  }

  const right = directLabels ? 84 : 40;
  const plotW = Math.max(60, width - MARGIN.left - right);
  const x = (i: number) => (n <= 1 ? MARGIN.left + plotW / 2 : MARGIN.left + (i / (n - 1)) * plotW);
  const stride = n <= 4 ? 1 : width < 520 ? 4 : 2;

  const pathFor = (indices: number[], values: (number | null)[]) =>
    indices.map((i, k) => `${k === 0 ? "M" : "L"}${x(i).toFixed(1)} ${y(values[i] as number).toFixed(1)}`).join(" ");

  const bandFor = (indices: number[], low: (number | null)[], high: (number | null)[]) => {
    const forward = indices.map((i) => `${x(i).toFixed(1)} ${y(high[i] as number).toFixed(1)}`);
    const backward = [...indices].reverse().map((i) => `${x(i).toFixed(1)} ${y(low[i] as number).toFixed(1)}`);
    return `M${forward.join(" L")} L${backward.join(" L")} Z`;
  };

  const onPointerMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const idx = n <= 1 ? 0 : Math.round(((px - MARGIN.left) / plotW) * (n - 1));
    setActive(Math.max(0, Math.min(n - 1, idx)));
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      setActive((a) => (a === null ? 0 : Math.min(n - 1, a + 1)));
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      setActive((a) => (a === null ? n - 1 : Math.max(0, a - 1)));
    } else if (e.key === "Home") {
      setActive(0);
    } else if (e.key === "End") {
      setActive(n - 1);
    } else if (e.key === "Escape") {
      setActive(null);
    }
  };

  const activeRows =
    active === null
      ? []
      : series
          .map((s) => ({ s, v: s.values[active], lo: s.low?.[active], hi: s.high?.[active] }))
          .filter((r) => r.v !== null && r.v !== undefined);

  const spoken =
    active === null
      ? ""
      : `${xLabels[active]}: ` +
        (activeRows.length
          ? activeRows.map((r) => `${r.s.label} ${format(r.v as number)}`).join(", ")
          : "hidden, group too small");

  return (
    <div className={"card" + (loading ? " loading" : "")}>
      {header}
      {series.length >= 2 && (
        <div className="legend">
          {series.map((s) => (
            <span className="item" key={s.id}>
              <span className="key-line" style={{ background: `var(${s.color})` }} />
              {s.label}
            </span>
          ))}
        </div>
      )}
      <div
        className="chart-wrap"
        ref={wrapRef}
        tabIndex={0}
        role="group"
        aria-label={`${title}. Use the left and right arrow keys to read each term.`}
        onKeyDown={onKeyDown}
        onBlur={() => setActive(null)}
      >
        <svg
          viewBox={`0 0 ${width} ${HEIGHT}`}
          height={HEIGHT}
          role="img"
          aria-label={`${title}, ${series.map((s) => s.label).join(" and ")} by term`}
          onPointerMove={onPointerMove}
          onPointerLeave={() => setActive(null)}
        >
          {ticks.map((t) => (
            <g key={t}>
              <line className={t === 0 && includeZero ? "axis-line" : "grid-line"} x1={MARGIN.left} x2={MARGIN.left + plotW} y1={y(t)} y2={y(t)} />
              <text className="tick-label" x={MARGIN.left - 8} y={y(t)} dy="0.32em" textAnchor="end">
                {format(t)}
              </text>
            </g>
          ))}
          {xLabels.map((label, i) =>
            i % stride === 0 ? (
              <text className="tick-label" key={label} x={x(i)} y={HEIGHT - 8} textAnchor="middle">
                {label}
              </text>
            ) : null
          )}

          {series.map((s) =>
            s.low && s.high
              ? runs(n, [s.values, s.low, s.high]).map((r) => (
                  <path
                    key={`${s.id}-band-${r[0]}`}
                    d={bandFor(r, s.low as (number | null)[], s.high as (number | null)[])}
                    fill={`var(${s.color})`}
                    opacity={0.1}
                  />
                ))
              : null
          )}

          {series.map((s) =>
            runs(n, [s.values]).map((r) => (
              <path
                key={`${s.id}-line-${r[0]}`}
                d={pathFor(r, s.values)}
                fill="none"
                stroke={`var(${s.color})`}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            ))
          )}

          {active !== null && <line className="crosshair" x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={HEIGHT - MARGIN.bottom} />}

          {series.map((s, k) => {
            const idx = active !== null && s.values[active] !== null && s.values[active] !== undefined ? active : lastIndex[k];
            if (idx < 0 || (active !== null && idx !== active)) {
              return null;
            }
            return (
              <circle
                key={`${s.id}-dot`}
                cx={x(idx)}
                cy={y(s.values[idx] as number)}
                r={4}
                fill={`var(${s.color})`}
                stroke="var(--surface)"
                strokeWidth={2}
              />
            );
          })}

          {directLabels &&
            active === null &&
            series.map((s, k) =>
              lastIndex[k] >= 0 ? (
                <text
                  key={`${s.id}-label`}
                  className="direct-label"
                  x={x(lastIndex[k]) + 10}
                  y={y(s.values[lastIndex[k]] as number)}
                  dy="0.32em"
                >
                  {s.label}
                </text>
              ) : null
            )}
        </svg>

        {active !== null && (
          <div
            className="tooltip"
            style={{
              left: x(active),
              transform: x(active) > width * 0.6 ? "translateX(calc(-100% - 12px))" : "translateX(12px)",
            }}
          >
            <div className="t-head">{xLabels[active]}</div>
            {activeRows.length === 0 && <div className="t-name">Hidden: fewer than {minCell} students</div>}
            {activeRows.map((r) => (
              <div key={r.s.id}>
                <div className="t-row">
                  <span className="key-line" style={{ background: `var(${r.s.color})` }} />
                  <strong>{format(r.v as number)}</strong>
                  <span className="t-name">{r.s.label}</span>
                </div>
                {r.lo != null && r.hi != null && (
                  <div className="t-range">
                    {intervalLabel} {format(r.lo)} to {format(r.hi)}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        <div className="visually-hidden" aria-live="polite">
          {spoken}
        </div>
      </div>
      {hiddenCount > 0 && (
        <p className="note">
          {hiddenCount} of {n} terms are hidden because the selected group has fewer than {minCell} students.
        </p>
      )}
    </div>
  );
}
