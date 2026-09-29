import { useState } from "react";
import { downloadExport, uploadCsv } from "../api";
import { useApi } from "../hooks";
import { formatInteger, formatTimestamp, humanizeCode } from "../lib/format";
import { ISSUE_HELP } from "../lib/issueCodes";
import type { ImportSummary, ValidationIssue } from "../types";

interface Props {
  canImport: boolean;
  onDataChanged: () => void;
}

export default function DataPanel({ canImport, onDataChanged }: Props) {
  const [refresh, setRefresh] = useState(0);
  const history = useApi<ImportSummary[]>("/api/imports", refresh);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [severity, setSeverity] = useState("");

  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<"APPEND" | "REPLACE">("APPEND");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const jobs = history.data ?? [];
  const activeId = selectedId ?? jobs[0]?.id ?? null;
  const selected = jobs.find((j) => j.id === activeId) ?? null;
  const issues = useApi<ValidationIssue[]>(
    activeId === null ? null : `/api/imports/${activeId}/issues?limit=200${severity ? `&severity=${severity}` : ""}`
  );

  const upload = async () => {
    if (!file) {
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const summary = await uploadCsv(file, mode);
      setSelectedId(summary.id);
      setRefresh((r) => r + 1);
      onDataChanged();
      setMessage(
        summary.status === "FAILED"
          ? `Import failed: ${summary.message ?? "unknown problem"}`
          : `Loaded ${formatInteger(summary.acceptedRows)} rows. ${formatInteger(summary.rejectedRows)} rows were rejected.`
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid">
      {canImport && (
        <div className="card">
          <h2>Load data</h2>
          <p className="sub">
            Upload a CSV with these columns: student_id, school, grade, academic_year, term, economic_status, english_learner, math_score, reading_score, attendance_rate, discipline_incidents. Student ids are replaced with a keyed hash on the server and are never stored.
          </p>
          <div className="upload-row" style={{ marginTop: 12 }}>
            <label className="field">
              CSV file
              <input type="file" accept=".csv,text/csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </label>
            <label className="field">
              When rows already exist
              <select value={mode} onChange={(e) => setMode(e.target.value as "APPEND" | "REPLACE")}>
                <option value="APPEND">Add new rows, reject repeats</option>
                <option value="REPLACE">Replace everything</option>
              </select>
            </label>
            <button className="btn primary" type="button" onClick={upload} disabled={!file || busy}>
              {busy ? "Uploading" : "Upload and validate"}
            </button>
          </div>
          {mode === "REPLACE" && (
            <p className="warn-box">Replace removes all stored data first, then loads this file. The removal is written to the audit log.</p>
          )}
          {message && <p className="note" role="status">{message}</p>}
          {error && <p className="error" role="alert">{error}</p>}
        </div>
      )}

      <div className={"card" + (history.loading ? " loading" : "")}>
        <div className="card-head">
          <div>
            <h2>Import history</h2>
            <p className="sub">Choose an import to review what was accepted, fixed, or rejected.</p>
          </div>
          <button className="btn" type="button" onClick={() => downloadExport().catch((e) => setError(String(e.message ?? e)))}>
            Download cleaned data (CSV)
          </button>
        </div>
        {history.error && <p className="error" role="alert">{history.error}</p>}
        {jobs.length === 0 && !history.loading && <div className="empty">No imports yet.</div>}
        {jobs.length > 0 && (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th scope="col">File</th>
                  <th scope="col">When</th>
                  <th scope="col">By</th>
                  <th scope="col">Mode</th>
                  <th scope="col" className="num">Accepted</th>
                  <th scope="col" className="num">Rejected</th>
                  <th scope="col" className="num">Warnings</th>
                  <th scope="col"><span className="visually-hidden">Review</span></th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((j) => (
                  <tr key={j.id}>
                    <td>{j.filename}{j.status === "FAILED" && " (failed)"}</td>
                    <td>{formatTimestamp(j.createdAt)}</td>
                    <td>{j.createdBy}</td>
                    <td>{j.mode === "APPEND" ? "Add" : "Replace"}</td>
                    <td className="num">{formatInteger(j.acceptedRows)}</td>
                    <td className="num">{formatInteger(j.rejectedRows)}</td>
                    <td className="num">{formatInteger(j.warnings)}</td>
                    <td>
                      <button className="link-btn" type="button" aria-pressed={j.id === activeId} onClick={() => setSelectedId(j.id)}>
                        {j.id === activeId ? "Reviewing" : "Review"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && (
        <div className={"card" + (issues.loading ? " loading" : "")}>
          <div className="card-head">
            <div>
              <h2>Validation report: {selected.filename}</h2>
              <p className="sub">
                {formatInteger(selected.totalRows)} rows read, {formatInteger(selected.acceptedRows)} accepted, {formatInteger(selected.rejectedRows)} rejected, {formatInteger(selected.warnings)} warnings.
              </p>
            </div>
          </div>
          {selected.message && <p className="error" role="alert">{selected.message}</p>}

          {Object.keys(selected.issueCounts).length > 0 && (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th scope="col">Problem</th>
                    <th scope="col">What it means</th>
                    <th scope="col">Result</th>
                    <th scope="col" className="num">Rows</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(selected.issueCounts)
                    .sort((a, b) => b[1] - a[1])
                    .map(([code, count]) => (
                      <tr key={code}>
                        <td>{humanizeCode(code)}</td>
                        <td>{ISSUE_HELP[code]?.meaning ?? ""}</td>
                        <td>{ISSUE_HELP[code]?.outcome ?? ""}</td>
                        <td className="num">{formatInteger(count)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="filters" style={{ paddingBottom: 0 }}>
            <label className="field">
              Show
              <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
                <option value="">Rejected and fixed rows</option>
                <option value="ERROR">Rejected rows only</option>
                <option value="WARNING">Fixed or incomplete rows only</option>
              </select>
            </label>
          </div>
          <p className="note" style={{ marginTop: 0 }}>Showing up to 200 issues. Row numbers match the spreadsheet, where row 1 is the header.</p>
          <div className="table-wrap scroll-y">
            <table className="data">
              <thead>
                <tr>
                  <th scope="col" className="num">Row</th>
                  <th scope="col">Field</th>
                  <th scope="col">Result</th>
                  <th scope="col">Problem</th>
                  <th scope="col">Value found</th>
                </tr>
              </thead>
              <tbody>
                {(issues.data ?? []).map((i, k) => (
                  <tr key={`${i.rowNumber}-${i.code}-${k}`}>
                    <td className="num">{i.rowNumber}</td>
                    <td>{i.field}</td>
                    <td>{i.severity === "ERROR" ? "Rejected" : "Kept"}</td>
                    <td>{i.message}</td>
                    <td style={{ fontFamily: "ui-monospace, monospace" }}>{i.rawValue}</td>
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
