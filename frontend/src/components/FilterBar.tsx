import type { Filters, Options } from "../types";
import { NO_FILTERS } from "../types";

interface Props {
  options: Options | null;
  filters: Filters;
  onChange: (next: Filters) => void;
}

export default function FilterBar({ options, filters, onChange }: Props) {
  const set = (key: keyof Filters) => (e: React.ChangeEvent<HTMLSelectElement>) =>
    onChange({ ...filters, [key]: e.target.value });
  const active = Object.values(filters).some((v) => v !== "");

  return (
    <div className="filters" role="search" aria-label="Filters. These apply to every chart on the page.">
      <label className="field">
        School
        <select value={filters.school} onChange={set("school")}>
          <option value="">All schools</option>
          {options?.schools.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </label>
      <label className="field">
        Grade
        <select value={filters.grade} onChange={set("grade")}>
          <option value="">All grades</option>
          {options?.grades.map((g) => (
            <option key={g} value={String(g)}>Grade {g}</option>
          ))}
        </select>
      </label>
      <label className="field">
        Family income
        <select value={filters.economicStatus} onChange={set("economicStatus")}>
          <option value="">All students</option>
          <option value="LOW_INCOME">Low income</option>
          <option value="NOT_LOW_INCOME">Not low income</option>
        </select>
      </label>
      <label className="field">
        English learner
        <select value={filters.englishLearner} onChange={set("englishLearner")}>
          <option value="">All students</option>
          <option value="true">English learners</option>
          <option value="false">Not English learners</option>
        </select>
      </label>
      {active && (
        <button className="btn" type="button" onClick={() => onChange(NO_FILTERS)}>
          Reset filters
        </button>
      )}
    </div>
  );
}
