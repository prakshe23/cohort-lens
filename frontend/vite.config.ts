import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the dashboard proxies /api to the backend (Spring Boot or the in memory dev server).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://127.0.0.1:8080" },
  },
  test: { environment: "node" },
});
