"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api, type ClaimDetail, type Task } from "@/lib/api";
import { STATUS_ELEMENT, TASK_LABEL, money, slaText } from "@/lib/format";
import { useSession } from "@/components/session";
import { Badge, Confidence, StatusBadge } from "@/components/ui";
import { ProcessView } from "@/components/process-view";

const FIELD_LABEL: Record<string, string> = {
  policyNumber: "Policy number",
  incidentDate: "Incident date",
  incidentType: "Incident type",
  claimedAmount: "Amount claimed",
  claimantName: "Claimant name",
};

export default function ClaimPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { me } = useSession();
  const [claim, setClaim] = useState<ClaimDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => api.claim(id).then(setClaim).catch((e: Error) => setError(e.message)), [id]);

  useEffect(() => {
    load();
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [load]);

  if (error) return <main className="page"><p className="error">{error}</p></main>;
  if (!claim) return <main className="page muted">Loading…</main>;

  const staff = claim.staff;
  const openTask = staff?.tasks.find((t) => t.status === "OPEN");
  const canWork = !!openTask && !!me && (me.roles.includes(openTask.candidateRole)
    || (openTask.candidateRole === "adjuster" && me.roles.includes("senior-adjuster")));

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <p className="small muted">{claim.claimantName} · submitted {new Date(claim.createdAt).toLocaleString()}</p>
          <h1>{claim.reference} <StatusBadge status={claim.status} /></h1>
          {claim.decisionReason && <p className="muted">{claim.decisionReason}</p>}
        </div>
      </div>

      <section className="card" style={{ marginBottom: 16 }}>
        <h2>Where this claim is</h2>
        <ProcessView elementId={STATUS_ELEMENT[claim.status]} />
      </section>

      <div className="grid grid-main">
        <div className="grid">
          {openTask && (canWork
            ? <TaskPanel task={openTask} claim={claim} onDone={load} />
            : <section className="card"><h2>Waiting on</h2><p>{TASK_LABEL[openTask.type]}, by someone with the <b>{openTask.candidateRole}</b> role ({slaText(openTask.slaDueAt)}).</p></section>)}

          {staff && (
            <section className="card">
              <h2>Extracted fields</h2>
              <table>
                <thead><tr><th>Field</th><th>Value</th><th style={{ width: 180 }}>Confidence</th></tr></thead>
                <tbody>
                  {staff.fields.map((f) => (
                    <tr key={f.name}>
                      <td>{FIELD_LABEL[f.name] ?? f.name}</td>
                      <td>{f.value ?? <span className="muted">not found</span>}
                        {f.verifiedBy && <span className="small muted"> · verified by {f.verifiedBy}</span>}</td>
                      <td><Confidence value={f.confidence} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          {staff && (
            <section className="card">
              <h2>Audit trail</h2>
              <ul className="timeline">
                {staff.audit.map((a, i) => (
                  <li key={i}>
                    <b>{a.type.replaceAll("_", " ").toLowerCase()}</b> <span className="muted">by {a.actor}</span>
                    {a.detail && <div className="small muted">{a.detail}</div>}
                    <div className="small muted">{new Date(a.at).toLocaleString()}</div>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>

        <div className="grid" style={{ alignContent: "start" }}>
          <section className="card">
            <h2>Claim</h2>
            <dl className="kv">
              <dt>Policy</dt><dd>{claim.policyNumber ?? "unknown"} {claim.policyType && <span className="muted">({claim.policyType})</span>}</dd>
              <dt>Incident</dt><dd>{claim.incidentType ?? "n/a"} on {claim.incidentDate ?? "n/a"}</dd>
              <dt>Claimed</dt><dd>{money(claim.claimedAmount)}</dd>
              <dt>Payable</dt><dd>{money(claim.payableAmount)}</dd>
              {claim.paymentReference && <><dt>Payment</dt><dd>{claim.paymentReference}</dd></>}
            </dl>
          </section>
          {staff && (
            <section className="card">
              <h2>Controls</h2>
              <dl className="kv">
                <dt>Fraud score</dt>
                <dd>{staff.fraudScore === null ? "not scored" : (
                  <Badge tone={staff.fraudScore >= 0.3 ? "warn" : "good"}>{staff.fraudScore.toFixed(2)}</Badge>)}</dd>
                <dt>Prepared by</dt><dd>{staff.payoutPreparedBy ?? "n/a"}</dd>
                <dt>Released by</dt><dd>{staff.payoutReleasedBy ?? "n/a"}</dd>
                <dt>Process key</dt><dd className="small">{staff.processInstanceKey ?? "n/a"}</dd>
              </dl>
            </section>
          )}
        </div>
      </div>
    </main>
  );
}

function TaskPanel({ task, claim, onDone }: { task: Task; claim: ClaimDetail; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const field = (name: string) => claim.staff?.fields.find((f) => f.name === name)?.value ?? "";

  async function submit(body: Record<string, string>) {
    setBusy(true);
    setError(null);
    try {
      await api.completeTask(task.id, body);
      onDone();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function onForm(e: React.FormEvent<HTMLFormElement>, extra: Record<string, string> = {}) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<string, string>;
    submit({ ...data, ...extra });
  }

  return (
    <section className="card" style={{ borderColor: "var(--brand)" }}>
      <h2>Your task: {TASK_LABEL[task.type]}</h2>
      <p className="small muted">{slaText(task.slaDueAt)}{task.escalated && " · escalated"}</p>

      {task.type === "VERIFY_FIELDS" && (
        <form onSubmit={(e) => onForm(e)}>
          <p className="small">The extractor was not confident about these. Check the document and correct them.</p>
          <div className="grid grid-2">
            <div><label>Policy number</label><input name="policyNumber" defaultValue={field("policyNumber")} required /></div>
            <div><label>Incident date (YYYY-MM-DD)</label><input name="incidentDate" defaultValue={field("incidentDate")} required /></div>
          </div>
          <label>Amount claimed</label><input name="claimedAmount" defaultValue={field("claimedAmount")} required />
          <DocumentLinks claim={claim} />
          <button className="btn btn-primary" disabled={busy} style={{ marginTop: 12 }}>Confirm fields</button>
        </form>
      )}

      {task.type === "ADJUSTER_REVIEW" && (
        <form onSubmit={(e) => e.preventDefault()}>
          <label>Note (required to reject; the claimant sees it)</label>
          <textarea name="note" id="note" />
          <DocumentLinks claim={claim} />
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button className="btn btn-primary" disabled={busy} type="button"
              onClick={() => submit({ decision: "APPROVE", note: (document.getElementById("note") as HTMLTextAreaElement).value })}>
              Approve</button>
            <button className="btn btn-danger" disabled={busy} type="button"
              onClick={() => submit({ decision: "REJECT", note: (document.getElementById("note") as HTMLTextAreaElement).value })}>
              Reject</button>
          </div>
        </form>
      )}

      {task.type === "PAYOUT_PREPARE" && (
        <form onSubmit={(e) => onForm(e)}>
          <p className="small">Pay <b>{money(claim.payableAmount)}</b>. Someone else must release it.</p>
          <label>Payout account</label><input name="payoutAccount" placeholder="ZW01 2345 6789" required />
          <button className="btn btn-primary" disabled={busy} style={{ marginTop: 12 }}>Prepare payout</button>
        </form>
      )}

      {task.type === "PAYOUT_RELEASE" && (
        <div>
          <p className="small">
            Release <b>{money(claim.payableAmount)}</b> to <b>{claim.staff?.payoutAccount}</b>, prepared by{" "}
            <b>{claim.staff?.payoutPreparedBy}</b>.
          </p>
          <button className="btn btn-primary" disabled={busy} onClick={() => submit({})}>Release payout</button>
        </div>
      )}

      {error && <p className="error">{error}</p>}
    </section>
  );
}

function DocumentLinks({ claim }: { claim: ClaimDetail }) {
  const docs = claim.staff?.documents ?? [];
  if (docs.length === 0) return null;
  return (
    <p className="small" style={{ marginTop: 10 }}>
      Documents: {docs.map((d) => <span key={d.id}>{d.filename} </span>)}
    </p>
  );
}
