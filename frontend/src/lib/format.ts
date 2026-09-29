export function formatNumber(value: number, digits = 1): string {
  return value.toLocaleString("en-US", { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

export function formatInteger(value: number): string {
  return Math.round(value).toLocaleString("en-US");
}

/** 0.942 becomes "94.2%". */
export function formatPercent(fraction: number, digits = 1): string {
  return `${formatNumber(fraction * 100, digits)}%`;
}

export function formatSigned(value: number, digits = 1): string {
  const text = formatNumber(Math.abs(value), digits);
  if (value > 0) {
    return `+${text}`;
  }
  return value < 0 ? `−${text}` : text;
}

/** "2022-05-01T10:20:30Z" becomes a short local date and time. */
export function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });
}

/** Turns a code like MISSING_MATH_SCORE into "Missing math score". */
export function humanizeCode(code: string): string {
  const text = code.toLowerCase().replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}
