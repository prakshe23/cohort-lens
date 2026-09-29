// These mirror the Java records returned by the backend, field for field.

export interface MeasureStat {
  suppressed: boolean;
  n: number | null;
  mean: number | null;
  ciLow: number | null;
  ciHigh: number | null;
}

export interface TrendPoint {
  termIndex: number;
  label: string;
  n: number | null;
  math: MeasureStat;
  reading: MeasureStat;
  attendance: MeasureStat;
  discipline: MeasureStat;
}

export interface GapStat {
  suppressed: boolean;
  difference: number | null;
  ciLow: number | null;
  ciHigh: number | null;
}

export type GapDimension = "ECONOMIC_STATUS" | "ENGLISH_LEARNER";

export interface GapPoint {
  termIndex: number;
  label: string;
  dimension: GapDimension;
  referenceGroup: string;
  comparisonGroup: string;
  math: GapStat;
  reading: GapStat;
  attendance: GapStat;
}

export interface RiskBySchool {
  school: string;
  suppressed: boolean;
  total: number | null;
  low: number | null;
  medium: number | null;
  high: number | null;
}

export interface RiskSummary {
  termIndex: number | null;
  termLabel: string | null;
  schools: RiskBySchool[];
}

export interface RiskFactor {
  code: string;
  description: string;
  points: number;
}

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";

export interface RiskAssessment {
  studentKey: string;
  school: string;
  grade: number;
  termIndex: number;
  termLabel: string;
  score: number;
  level: RiskLevel;
  factors: RiskFactor[];
}

export interface Overview {
  rows: number;
  students: number;
  schools: number;
  firstTerm: string | null;
  lastTerm: string | null;
}

export interface Options {
  schools: string[];
  grades: number[];
  terms: { termIndex: number; label: string }[];
}

export interface ImportSummary {
  id: number;
  filename: string;
  mode: "APPEND" | "REPLACE";
  status: "COMPLETED" | "FAILED";
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
  warnings: number;
  errors: number;
  issueCounts: Record<string, number>;
  message: string | null;
  createdAt: string;
  createdBy: string;
}

export interface ValidationIssue {
  rowNumber: number;
  field: string;
  severity: "ERROR" | "WARNING";
  code: string;
  message: string;
  rawValue: string;
}

export interface AuditEntry {
  id: number;
  at: string;
  actor: string;
  action: string;
  entityType: string;
  detail: string;
}

export interface Me {
  username: string;
  roles: string[];
}

/** The filter row above every chart. Empty string means "all". */
export interface Filters {
  school: string;
  grade: string;
  economicStatus: string;
  englishLearner: string;
}

export const NO_FILTERS: Filters = { school: "", grade: "", economicStatus: "", englishLearner: "" };
