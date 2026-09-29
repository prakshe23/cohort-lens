import { useState } from "react";
import { formatInteger } from "../lib/format";

export interface BarSegment {
  id: string;
  label: string;
  value: number;
  /** CSS variable name for the fill, for example "--risk-high". */
  color: string;
}

export interface BarRow {
  label: string;
  suppressed: boolean;
  total: number | null;
  segments: BarSegment[];
  /** Short text to the right of the bar. */
  side?: string;
}

interface Props {
  title: string;
  subtitle?: string;
  rows: BarRow[];
  legend: { id: string; label: string; color: string }[];
  minCell: number;
  loading?: boolean;
}

interface TipState {
  text: string;
  x: number;
  y: number;
}

/** 100 percent stacked horizontal bars: one bar per group, segments in a fixed order. */
export default function StackedBars({ title, subtitle, rows, legend, minCell, loading = false }: Props) {
  const [tip, setTip] = useState<TipState | null>(null);
  const [asTable, setAsTable] = useState(false);

  const header = (
    <div className="card-head">
      <div>
        <h2>{title}</h2>
        {subtitle && <p className="sub">{subtitle}</p>}
      </div>
      {rows.length > 0 && (
        <button className="link-btn" type="button" onClick={() => setAsTable((t) => !t)}>
          {asTable ? "Show chart" : "Show table"}
        </button>
      )}
    </div>
  );

  if (rows.length === 0) {
    return (
      <div className={"card" + (loading ? " loading" : "")}>
        {header}
        <div className="empty">Nothing to show for this selection.</div>
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
                <th scope="col">Group</th>
                <th scope="col" className="num">Students</th>
                {legend.map((l) => (
                  <th scope="col" className="num" key={l.id}>{l.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.label}>
                  <th scope="row">{r.label}</th>
                  {r.suppressed ? (
                    <td colSpan={legend.length + 1}>Hidden: fewer than {minCell} students</td>
                  ) : (
                    <>
                      <td className="num">{formatInteger(r.total ?? 0)}</td>
                      {legend.map((l) => (
                        <td className="num" key={l.id}>
                          {formatInteger(r.segments.find((s) => s.id === l.id)?.value ?? 0)}
                        </td>
                      ))}
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  const show = (e: React.PointerEvent<HTMLElement>, text: string) => {
    const host = e.currentTarget.closest(".bars-host") as HTMLElement | null;
    if (!host) {
      return;
    }
    const rect = host.getBoundingClientRect();
    setTip({ text, x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  const showFocus = (e: React.FocusEvent<HTMLElement>, text: string) => {
    const host = e.currentTarget.closest(".bars-host") as HTMLElement | null;
    if (!host) {
      return;
    }
    const rect = host.getBoundingClientRect();
    const seg = e.currentTarget.getBoundingClientRect();
    setTip({ text, x: seg.left - rect.left + seg.width / 2, y: seg.top - rect.top });
  };

  return (
    <div className={"card" + (loading ? " loading" : "")}>
      {header}
      <div className="legend">
        {legend.map((l) => (
          <span className="item" key={l.id}>
            <span className="key-box" style={{ background: `var(${l.color})` }} />
            {l.label}
          </span>
        ))}
      </div>
      <div className="bars-host" style={{ position: "relative" }}>
        {rows.map((r) => (
          <div className="bar-row" key={r.label}>
            <div>{r.label}</div>
            {r.suppressed ? (
              <div className="bar-hidden">Hidden: fewer than {minCell} students</div>
            ) : (
              <div className="bar" role="group" aria-label={`${r.label}: ${r.segments.map((s) => `${s.label} ${s.value}`).join(", ")}`}>
                {r.segments
                  .filter((s) => s.value > 0)
                  .map((s) => {
                    const pct = r.total ? Math.round((s.value / r.total) * 100) : 0;
                    const text = `${r.label}: ${s.label}, ${formatInteger(s.value)} students (${pct}%)`;
                    return (
                      <div
                        key={s.id}
                        className="seg"
                        tabIndex={0}
                        style={{ flexGrow: s.value, flexBasis: 0, background: `var(${s.color})` }}
                        aria-label={text}
                        onPointerMove={(e) => show(e, text)}
                        onPointerLeave={() => setTip(null)}
                        onFocus={(e) => showFocus(e, text)}
                        onBlur={() => setTip(null)}
                      />
                    );
                  })}
              </div>
            )}
            <div className="bar-side">{r.suppressed ? "" : r.side}</div>
          </div>
        ))}
        {tip && (
          <div
            className="tooltip"
            style={{ left: tip.x, top: tip.y - 44, transform: "translateX(-50%)", minWidth: 0, whiteSpace: "nowrap" }}
          >
            {tip.text}
          </div>
        )}
      </div>
    </div>
  );
}
