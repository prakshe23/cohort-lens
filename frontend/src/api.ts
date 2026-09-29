import type { Filters, ImportSummary } from "./types";

let authHeader: string | null = null;
let onUnauthorized: (() => void) | null = null;

/** Credentials live in memory only. Reloading the page signs you out, which is the point. */
export function setCredentials(username: string, password: string): void {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  let binary = "";
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  authHeader = `Basic ${btoa(binary)}`;
}

export function clearCredentials(): void {
  authHeader = null;
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  // Tells Spring Security to answer 401 without a WWW-Authenticate header, so the browser
  // does not pop up its own login box on top of ours.
  headers.set("X-Requested-With", "XMLHttpRequest");
  if (authHeader) {
    headers.set("Authorization", authHeader);
  }
  const res = await fetch(path, { ...init, headers });
  if (!res.ok) {
    if (res.status === 401 && onUnauthorized) {
      onUnauthorized();
    }
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && typeof body.error === "string") {
        message = body.error;
      }
    } catch {
      // keep the generic message
    }
    throw new ApiError(res.status, message);
  }
  return res;
}

export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await request(path, { signal });
  return (await res.json()) as T;
}

/** Builds "?school=...&grade=..." from the filter row, skipping empty values. */
export function filterQuery(filters: Filters, extra: Record<string, string> = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries({ ...filters, ...extra })) {
    if (value !== "") {
      params.set(key, value);
    }
  }
  const text = params.toString();
  return text ? `?${text}` : "";
}

export async function uploadCsv(file: File, mode: "APPEND" | "REPLACE"): Promise<ImportSummary> {
  const path = `/api/imports?filename=${encodeURIComponent(file.name)}&mode=${mode}`;
  const res = await request(path, {
    method: "POST",
    headers: { "Content-Type": "text/csv" },
    body: await file.text(),
  });
  return (await res.json()) as ImportSummary;
}

export async function downloadExport(): Promise<void> {
  const res = await request("/api/export.csv");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "cohortlens_export.csv";
  link.click();
  URL.revokeObjectURL(url);
}
