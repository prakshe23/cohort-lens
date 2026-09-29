import { useApi } from "../hooks";
import { formatTimestamp } from "../lib/format";
import type { AuditEntry } from "../types";

export default function AuditPanel({ refreshKey }: { refreshKey: number }) {
  const audit = useApi<AuditEntry[]>("/api/audit", refreshKey);
  const rows = audit.data ?? [];

  return (
    <div className={"card" + (audit.loading ? " loading" : "")}>
      <div className="card-head">
        <div>
          <h2>Change history</h2>
          <p className="sub">Who imported, replaced, or exported data, and when. The latest 200 entries.</p>
        </div>
      </div>
      {audit.error && <p className="error" role="alert">{audit.error}</p>}
      {rows.length === 0 && !audit.loading && <div className="empty">Nothing recorded yet.</div>}
      {rows.length > 0 && (
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Who</th>
                <th scope="col">Action</th>
                <th scope="col">What</th>
                <th scope="col">Detail</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{formatTimestamp(r.at)}</td>
                  <td>{r.actor}</td>
                  <td>{r.action}</td>
                  <td>{r.entityType}</td>
                  <td>{r.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
