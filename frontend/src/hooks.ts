import { useEffect, useState } from "react";
import { getJson } from "./api";

export interface ApiState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
}

/**
 * Fetches JSON whenever the path changes. While a new request is running the previous data stays
 * on screen (marked as loading), so charts fade instead of jumping or flashing empty.
 */
export function useApi<T>(path: string | null, refreshKey = 0): ApiState<T> {
  const [state, setState] = useState<ApiState<T>>({ data: null, loading: path !== null, error: null });

  useEffect(() => {
    if (path === null) {
      setState({ data: null, loading: false, error: null });
      return;
    }
    const controller = new AbortController();
    setState((prev) => ({ data: prev.data, loading: true, error: null }));
    getJson<T>(path, controller.signal)
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState((prev) => ({
          data: prev.data,
          loading: false,
          error: err instanceof Error ? err.message : "Something went wrong",
        }));
      });
    return () => controller.abort();
  }, [path, refreshKey]);

  return state;
}

/**
 * Width of an element, kept current with ResizeObserver, for responsive SVG charts.
 * Returns a callback ref, so it also works when the element appears later (for example once data has loaded).
 */
export function useElementWidth<T extends HTMLElement>(fallback = 640): [(node: T | null) => void, number] {
  const [node, setNode] = useState<T | null>(null);
  const [width, setWidth] = useState(fallback);

  useEffect(() => {
    if (!node) {
      return;
    }
    const update = () => setWidth(Math.max(280, Math.floor(node.getBoundingClientRect().width)));
    update();
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [node]);

  return [setNode, width];
}
