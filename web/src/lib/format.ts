import type { ClaimStatus, TaskType } from "./api.ts";

export const STATUS_LABEL: Record<ClaimStatus, string> = {
  SUBMITTED: "Submitted",
  START_FAILED: "Could not start",
  EXTRACTING: "Reading documents",
  NEEDS_VERIFICATION: "Checking details",
  ASSESSING: "Assessing",
  UNDER_REVIEW: "Under review",
  APPROVED: "Approved",
  PAYOUT_PENDING_RELEASE: "Payout awaiting release",
  PAYING: "Paying",
  PAID: "Paid",
  REJECTED: "Not covered",
  PAYOUT_REVERSED: "Payout reversed",
};

export const TASK_LABEL: Record<TaskType, string> = {
  VERIFY_FIELDS: "Verify extracted fields",
  ADJUSTER_REVIEW: "Review claim",
  PAYOUT_PREPARE: "Prepare payout",
  PAYOUT_RELEASE: "Release payout",
};

/** Where a claim in this status sits on the BPMN model, for highlighting. */
export const STATUS_ELEMENT: Record<ClaimStatus, string> = {
  SUBMITTED: "Task_ExtractFields",
  START_FAILED: "Start_ClaimSubmitted",
  EXTRACTING: "Task_ExtractFields",
  NEEDS_VERIFICATION: "Wait_Verify",
  ASSESSING: "Task_Coverage",
  UNDER_REVIEW: "Wait_Review",
  APPROVED: "Wait_Prepare",
  PAYOUT_PENDING_RELEASE: "Wait_Release",
  PAYING: "Task_ExecutePayout",
  PAID: "End_Paid",
  REJECTED: "End_Rejected",
  PAYOUT_REVERSED: "End_PayoutReversed",
};

export type Tone = "neutral" | "info" | "good" | "warn" | "bad";

export function statusTone(status: ClaimStatus): Tone {
  switch (status) {
    case "PAID":
      return "good";
    case "REJECTED":
    case "START_FAILED":
      return "bad";
    case "PAYOUT_REVERSED":
    case "NEEDS_VERIFICATION":
      return "warn";
    case "UNDER_REVIEW":
    case "APPROVED":
    case "PAYOUT_PENDING_RELEASE":
      return "info";
    default:
      return "neutral";
  }
}

export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return "n/a";
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

/** "in 3h", "2h overdue". SLA text for task lists. */
export function slaText(dueAt: string, now: Date = new Date()): string {
  const minutes = Math.round((new Date(dueAt).getTime() - now.getTime()) / 60000);
  const abs = Math.abs(minutes);
  const span = abs >= 60 * 24 ? `${Math.round(abs / 1440)}d` : abs >= 60 ? `${Math.round(abs / 60)}h` : `${abs}m`;
  return minutes >= 0 ? `due in ${span}` : `${span} overdue`;
}

export function confidenceTone(confidence: number, threshold = 0.85): Tone {
  if (confidence >= threshold) return "good";
  if (confidence > 0) return "warn";
  return "bad";
}
