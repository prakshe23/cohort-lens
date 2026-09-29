import { describe, expect, it } from "vitest";
import { formatPercent, formatSigned, humanizeCode } from "./format";
import { linear, niceTicks } from "./scale";

describe("niceTicks", () => {
  it("gives round ticks that cover the range", () => {
    expect(niceTicks(0, 97, 5)).toEqual([0, 20, 40, 60, 80, 100]);
    const ticks = niceTicks(55.3, 61.9, 4);
    expect(ticks[0]).toBeLessThanOrEqual(55.3);
    expect(ticks[ticks.length - 1]).toBeGreaterThanOrEqual(61.9);
  });

  it("includes zero when the range crosses it", () => {
    expect(niceTicks(-3, 8, 5)).toContain(0);
  });

  it("handles a flat range", () => {
    const ticks = niceTicks(5, 5, 4);
    expect(ticks.length).toBeGreaterThan(1);
    expect(ticks[0]).toBeLessThan(5);
  });

  it("avoids floating point noise", () => {
    expect(niceTicks(0, 0.3, 3)).toEqual([0, 0.1, 0.2, 0.3]);
  });
});

describe("linear", () => {
  it("maps the domain onto the range and can invert direction", () => {
    const y = linear([0, 100], [200, 0]);
    expect(y(0)).toBe(200);
    expect(y(100)).toBe(0);
    expect(y(50)).toBe(100);
  });
});

describe("format", () => {
  it("formats percent, signed values and codes", () => {
    expect(formatPercent(0.942)).toBe("94.2%");
    expect(formatSigned(-2.25)).toBe("−2.3");
    expect(formatSigned(3)).toBe("+3.0");
    expect(humanizeCode("MISSING_MATH_SCORE")).toBe("Missing math score");
  });
});
