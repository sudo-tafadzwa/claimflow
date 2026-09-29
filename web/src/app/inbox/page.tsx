"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, type Task } from "@/lib/api";
import { TASK_LABEL, slaText } from "@/lib/format";
import { Badge } from "@/components/ui";

export default function Inbox() {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = () => api.tasks().then(setTasks).catch((e: Error) => setError(e.message));
    load();
    const timer = setInterval(load, 4000);
    return () => clearInterval(timer);
  }, []);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1>Inbox</h1>
          <p className="muted">Open work for your roles, most urgent first.</p>
        </div>
      </div>
      <section className="card">
        {error && <p className="error">{error}</p>}
        {tasks && tasks.length === 0 && <p className="muted">Nothing waiting for you.</p>}
        {tasks && tasks.length > 0 && (
          <table>
            <thead><tr><th>Task</th><th>Claim</th><th>Role</th><th>SLA</th></tr></thead>
            <tbody>
              {tasks.map((t) => {
                const overdue = new Date(t.slaDueAt) < new Date();
                return (
                  <tr key={t.id}>
                    <td><Link href={`/claims/${t.claimId}`}>{TASK_LABEL[t.type]}</Link></td>
                    <td>{t.claimReference}</td>
                    <td className="small">{t.candidateRole}</td>
                    <td>
                      <Badge tone={overdue ? "bad" : t.escalated ? "warn" : "neutral"}>
                        {t.escalated ? "escalated · " : ""}{slaText(t.slaDueAt)}
                      </Badge>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
