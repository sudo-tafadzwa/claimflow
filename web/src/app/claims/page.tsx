"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api, type ClaimSummary } from "@/lib/api";
import { money } from "@/lib/format";
import { StatusBadge } from "@/components/ui";

export default function MyClaims() {
  const [claims, setClaims] = useState<ClaimSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => api.myClaims().then(setClaims).catch((e: Error) => setError(e.message)), []);

  useEffect(() => {
    load();
    const timer = setInterval(load, 4000);
    return () => clearInterval(timer);
  }, [load]);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1>My claims</h1>
          <p className="muted">Submit a claim document and follow it here.</p>
        </div>
      </div>
      <div className="grid grid-main">
        <section className="card">
          <h2>Claims</h2>
          {error && <p className="error">{error}</p>}
          {claims && claims.length === 0 && <p className="muted">No claims yet.</p>}
          {claims && claims.length > 0 && (
            <table>
              <thead><tr><th>Reference</th><th>Status</th><th>Claimed</th><th>Payable</th></tr></thead>
              <tbody>
                {claims.map((c) => (
                  <tr key={c.id}>
                    <td><Link href={`/claims/${c.id}`}>{c.reference}</Link></td>
                    <td><StatusBadge status={c.status} /></td>
                    <td>{money(c.claimedAmount)}</td>
                    <td>{money(c.payableAmount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
        <NewClaim onSubmitted={load} />
      </div>
    </main>
  );
}

function NewClaim({ onSubmitted }: { onSubmitted: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = e.currentTarget;
    try {
      await api.submit(new FormData(form));
      form.reset();
      onSubmitted();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>New claim</h2>
      <form onSubmit={submit}>
        <label htmlFor="claimantName">Your name</label>
        <input id="claimantName" name="claimantName" required maxLength={120} />
        <label htmlFor="claimantEmail">Email</label>
        <input id="claimantEmail" name="claimantEmail" type="email" required />
        <label htmlFor="document">Claim document (PDF or text, up to 5 MB)</label>
        <input id="document" name="document" type="file" accept=".pdf,.txt,application/pdf,text/plain" required />
        <p className="small muted" style={{ marginTop: 8 }}>
          Sample documents are in <code>samples/</code> in the repository.
        </p>
        <button className="btn btn-primary" disabled={busy} style={{ marginTop: 12 }}>
          {busy ? "Submitting…" : "Submit claim"}
        </button>
        {error && <p className="error">{error}</p>}
      </form>
    </section>
  );
}
