import { test } from "node:test";
import assert from "node:assert/strict";
import { STATUS_ELEMENT, STATUS_LABEL, confidenceTone, money, slaText } from "./format.ts";

test("every status has a label and a place on the process model", () => {
  for (const status of Object.keys(STATUS_LABEL)) {
    assert.ok(STATUS_ELEMENT[status as keyof typeof STATUS_ELEMENT], `no BPMN element for ${status}`);
  }
});

test("SLA text counts down and then shows how overdue", () => {
  const now = new Date("2026-09-29T10:00:00Z");
  assert.equal(slaText("2026-09-29T13:00:00Z", now), "due in 3h");
  assert.equal(slaText("2026-09-29T09:30:00Z", now), "30m overdue");
  assert.equal(slaText("2026-10-01T10:00:00Z", now), "due in 2d");
});

test("confidence tone follows the routing threshold", () => {
  assert.equal(confidenceTone(0.97), "good");
  assert.equal(confidenceTone(0.6), "warn");
  assert.equal(confidenceTone(0), "bad");
});

test("money is formatted, and missing amounts say so", () => {
  assert.equal(money(1300), "$1,300.00");
  assert.equal(money(null), "n/a");
});
