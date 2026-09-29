"use client";

import { config } from "./config";
import { currentUser, signIn } from "./auth";

export type ClaimStatus =
  | "SUBMITTED" | "START_FAILED" | "EXTRACTING" | "NEEDS_VERIFICATION" | "ASSESSING" | "UNDER_REVIEW"
  | "APPROVED" | "PAYOUT_PENDING_RELEASE" | "PAYING" | "PAID" | "REJECTED" | "PAYOUT_REVERSED";

export type TaskType = "VERIFY_FIELDS" | "ADJUSTER_REVIEW" | "PAYOUT_PREPARE" | "PAYOUT_RELEASE";

export interface ClaimSummary {
  id: string; reference: string; claimantName: string; status: ClaimStatus; policyNumber: string | null;
  claimedAmount: number | null; payableAmount: number | null; createdAt: string; updatedAt: string;
}
export interface Field { name: string; value: string | null; confidence: number; verifiedBy: string | null }
export interface Task {
  id: string; claimId: string; claimReference: string; type: TaskType; candidateRole: string; status: string;
  slaDueAt: string; escalated: boolean; createdAt: string; completedBy: string | null; outcome: string | null;
}
export interface AuditEntry { at: string; actor: string; type: string; detail: string | null }
export interface ClaimDetail extends ClaimSummary {
  claimantEmail: string; policyType: string | null; incidentDate: string | null; incidentType: string | null;
  decisionReason: string | null; paymentReference: string | null;
  staff: null | {
    fraudScore: number | null; processInstanceKey: number | null; payoutAccount: string | null;
    payoutPreparedBy: string | null; payoutReleasedBy: string | null; fields: Field[]; tasks: Task[];
    audit: AuditEntry[]; documents: { id: string; filename: string; contentType: string; sizeBytes: number }[];
  };
}
export interface Me { username: string; roles: string[] }

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = await currentUser();
  if (!user) {
    await signIn();
    throw new ApiError(401, "Signing in");
  }
  const res = await fetch(`${config.apiUrl}${path}`, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${user.access_token}` },
  });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      detail = (await res.json()).detail ?? detail;
    } catch {
      // not a problem-details body
    }
    throw new ApiError(res.status, detail);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export const api = {
  me: () => request<Me>("/api/me"),
  myClaims: () => request<ClaimSummary[]>("/api/claims/mine"),
  allClaims: () => request<ClaimSummary[]>("/api/claims"),
  claim: (id: string) => request<ClaimDetail>(`/api/claims/${id}`),
  tasks: () => request<Task[]>("/api/tasks"),
  submit: (form: FormData) => request<ClaimSummary>("/api/claims", { method: "POST", body: form }),
  completeTask: (id: string, body: Record<string, string>) =>
    request<Task>(`/api/tasks/${id}/complete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
  processModel: () => fetch(`${config.apiUrl}/process/claim.bpmn`).then((r) => r.text()),
};
