import { useCallback, useEffect, useState } from "react";
import { clearCredentials, getJson, setCredentials, setUnauthorizedHandler } from "./api";
import AuditPanel from "./components/AuditPanel";
import DataPanel from "./components/DataPanel";
import FilterBar from "./components/FilterBar";
import GapsPanel from "./components/GapsPanel";
import Login from "./components/Login";
import OverviewPanel from "./components/OverviewPanel";
import RiskPanel from "./components/RiskPanel";
import { useApi } from "./hooks";
import type { Filters, Me, Options } from "./types";
import { NO_FILTERS } from "./types";

type TabId = "overview" | "gaps" | "risk" | "data" | "audit";

const TABS: { id: TabId; label: string; role: string }[] = [
  { id: "overview", label: "Overview", role: "VIEWER" },
  { id: "gaps", label: "Gaps", role: "VIEWER" },
  { id: "risk", label: "Risk screening", role: "VIEWER" },
  { id: "data", label: "Data", role: "RESEARCHER" },
  { id: "audit", label: "History", role: "ADMIN" },
];

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [checked, setChecked] = useState(false);
  const [tab, setTab] = useState<TabId>("overview");
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [dataVersion, setDataVersion] = useState(0);
  const [theme, setTheme] = useState<"light" | "dark" | null>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearCredentials();
      setMe(null);
    });
    // In development the in memory server needs no login, so try without credentials first.
    getJson<Me>("/api/me")
      .then(setMe)
      .catch(() => undefined)
      .finally(() => setChecked(true));
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    if (theme) {
      document.documentElement.dataset.theme = theme;
    } else {
      delete document.documentElement.dataset.theme;
    }
  }, [theme]);

  const signIn = useCallback(async (username: string, password: string): Promise<string | null> => {
    setCredentials(username, password);
    try {
      setMe(await getJson<Me>("/api/me"));
      return null;
    } catch {
      clearCredentials();
      return "Sign in failed. Check the username and password.";
    }
  }, []);

  const signOut = () => {
    clearCredentials();
    setMe(null);
    setFilters(NO_FILTERS);
    setTab("overview");
  };

  const options = useApi<Options>(me ? "/api/analytics/options" : null, dataVersion);

  if (!checked) {
    return null;
  }
  if (!me) {
    return (
      <div className="app">
        <Login onSubmit={signIn} />
      </div>
    );
  }

  const has = (role: string) => me.roles.includes(role);
  const visibleTabs = TABS.filter((t) => has(t.role));
  const activeTab = visibleTabs.some((t) => t.id === tab) ? tab : "overview";
  const dark = theme === "dark" || (theme === null && window.matchMedia("(prefers-color-scheme: dark)").matches);

  return (
    <div className="app">
      <header className="header">
        <div className="brand">
          <h1>CohortLens</h1>
          <p>Longitudinal school data for research</p>
        </div>
        <div className="header-actions">
          <span>{me.username}</span>
          <button className="btn" type="button" onClick={() => setTheme(dark ? "light" : "dark")} aria-label="Switch between light and dark">
            {dark ? "Light" : "Dark"}
          </button>
          <button className="btn" type="button" onClick={signOut}>Sign out</button>
        </div>
      </header>

      <div className="tabs" role="tablist" aria-label="Sections">
        {visibleTabs.map((t) => (
          <button key={t.id} className="tab" role="tab" aria-selected={activeTab === t.id} onClick={() => setTab(t.id)}>
            {t.label}
          </button>
        ))}
      </div>

      {(activeTab === "overview" || activeTab === "gaps" || activeTab === "risk") && (
        <FilterBar options={options.data} filters={filters} onChange={setFilters} />
      )}

      <main style={{ paddingTop: activeTab === "data" || activeTab === "audit" ? 16 : 0 }}>
        {activeTab === "overview" && <OverviewPanel key={`o${dataVersion}`} filters={filters} />}
        {activeTab === "gaps" && <GapsPanel key={`g${dataVersion}`} filters={filters} schools={options.data?.schools ?? []} />}
        {activeTab === "risk" && <RiskPanel key={`r${dataVersion}`} filters={filters} canSeeStudents={has("RESEARCHER")} />}
        {activeTab === "data" && <DataPanel canImport={has("ADMIN")} onDataChanged={() => setDataVersion((v) => v + 1)} />}
        {activeTab === "audit" && <AuditPanel refreshKey={dataVersion} />}
      </main>

      <footer className="footer">
        Descriptive statistics only. Groups smaller than 10 students are hidden. Nothing here should be the sole basis for a decision about an individual student.
      </footer>
    </div>
  );
}
