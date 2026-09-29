import type { ClaimStatus } from "@/lib/api";
import { STATUS_LABEL, confidenceTone, statusTone, type Tone } from "@/lib/format";

export function Badge({ tone, children }: { tone: Tone; children: React.ReactNode }) {
  return <span className={`badge tone-${tone}`}>{children}</span>;
}

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <Badge tone={statusTone(status)}>{STATUS_LABEL[status]}</Badge>;
}

export function Confidence({ value }: { value: number }) {
  const tone = confidenceTone(value);
  const color = { good: "var(--good)", warn: "var(--warn)", bad: "var(--bad)", info: "var(--info)", neutral: "var(--ink-3)" }[tone];
  return (
    <span className="conf" title={`Confidence ${Math.round(value * 100)}%`}>
      <span className="bar"><span style={{ width: `${Math.max(value * 100, 3)}%`, background: color }} /></span>
      <span className="small" style={{ color, minWidth: 34 }}>{Math.round(value * 100)}%</span>
    </span>
  );
}
