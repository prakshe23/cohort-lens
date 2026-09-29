/** Round, human friendly axis ticks that cover [min, max]. */
export function niceTicks(min: number, max: number, target = 5): number[] {
  let lo = min;
  let hi = max;
  if (lo === hi) {
    lo -= 1;
    hi += 1;
  }
  const rawStep = (hi - lo) / Math.max(1, target);
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const residual = rawStep / magnitude;
  const nice = residual <= 1 ? 1 : residual <= 2 ? 2 : residual <= 5 ? 5 : 10;
  const step = nice * magnitude;
  const start = Math.floor(lo / step) * step;
  const end = Math.ceil(hi / step) * step;
  const ticks: number[] = [];
  for (let i = 0; start + i * step <= end + step / 1e6; i++) {
    ticks.push(Number((start + i * step).toPrecision(12)));
  }
  return ticks;
}

/** Straight line mapping from a data range to a pixel range. */
export function linear(domain: [number, number], range: [number, number]): (v: number) => number {
  const [d0, d1] = domain;
  const [r0, r1] = range;
  const span = d1 - d0 || 1;
  return (v) => r0 + ((v - d0) / span) * (r1 - r0);
}
