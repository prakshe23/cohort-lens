import { useState } from "react";

interface Props {
  onSubmit: (username: string, password: string) => Promise<string | null>;
}

export default function Login({ onSubmit }: Props) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(await onSubmit(username, password));
    setBusy(false);
  };

  return (
    <div className="login card">
      <h1 style={{ fontSize: 22 }}>CohortLens</h1>
      <p className="sub">Sign in to view the dashboard.</p>
      <form onSubmit={submit}>
        <label className="field">
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </label>
        <label className="field">
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn primary" type="submit" disabled={busy}>
          {busy ? "Signing in" : "Sign in"}
        </button>
        {error && <p className="error" role="alert">{error}</p>}
      </form>
    </div>
  );
}
