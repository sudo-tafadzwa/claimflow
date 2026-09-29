"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type ClaimSummary } from "@/lib/api";
import { money } from "@/lib/format";
import { StatusBadge } from "@/components/ui";

export default function AllClaims() {
  const [claims, setClaims] = useState<ClaimSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = () => api.allClaims().then(setClaims).catch((e: Error) => setError(e.message));
    load();
    const timer = setInterval(load, 5000);
    return () => clearInterval(timer);
  }, []);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1>All claims</h1>
          <p className="muted">Read model: updated by the job workers, never by querying the engine.</p>
        </div>
      </div>
      <section className="card">
        {error && <p className="error">{error}</p>}
        {claims && (
          <table>
            <thead>
              <tr><th>Reference</th><th>Claimant</th><th>Status</th><th>Policy</th><th>Claimed</th><th>Payable</th><th>Updated</th></tr>
            </thead>
            <tbody>
              {claims.map((c) => (
                <tr key={c.id}>
                  <td><Link href={`/claims/${c.id}`}>{c.reference}</Link></td>
                  <td>{c.claimantName}</td>
                  <td><StatusBadge status={c.status} /></td>
                  <td>{c.policyNumber ?? <span className="muted">unknown</span>}</td>
                  <td>{money(c.claimedAmount)}</td>
                  <td>{money(c.payableAmount)}</td>
                  <td className="small muted">{new Date(c.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
