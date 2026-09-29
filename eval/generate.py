"""Generates the synthetic extraction eval set: eval/cases/NN.txt plus NN.expected.json.

Every person, policy and amount is fictional. The set is deterministic (fixed seed), so regenerating it
produces the same files and results stay comparable across runs.

Case kinds:
  form       labelled claim form, every field present
  email      free-text email, fields stated in prose
  missing    one required field is simply not in the document
  conflict   the document gives two different values for one required field

`mustFlag` lists required fields a correct extractor must NOT be confident about (absent or ambiguous).
"""

import json
import random
from datetime import date, timedelta
from pathlib import Path

random.seed(20260929)
OUT = Path(__file__).parent / "cases"
OUT.mkdir(exist_ok=True)

NAMES = ["Rudo Moyo", "Daniel Okafor", "Amelia Novak", "Kofi Mensah", "Sarah Lindqvist", "Tendai Chikore",
         "Priya Raman", "Lucas Ferreira", "Hana Sato", "Omar Haddad", "Grace Banda", "Jonas Weber"]
TYPES = {"TRV": ["BAGGAGE", "MEDICAL", "CANCELLATION"], "MTR": ["ACCIDENT", "THEFT"],
         "HOM": ["WATER_DAMAGE", "FIRE", "THEFT"]}
STORIES = {
    "BAGGAGE": "my suitcase never arrived at the destination airport",
    "MEDICAL": "I needed treatment at a clinic after a fall on holiday",
    "CANCELLATION": "the trip was cancelled because of a family emergency",
    "ACCIDENT": "another driver hit the back of my car at a junction",
    "THEFT": "the items were stolen while I was away",
    "WATER_DAMAGE": "a pipe burst and water went through the ground floor",
    "FIRE": "a kitchen fire damaged the cabinets and the ceiling",
}
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
          "October", "November", "December"]


def fields():
    prefix = random.choice(list(TYPES))
    kind = random.choice(TYPES[prefix])
    d = date(2026, 1, 1) + timedelta(days=random.randint(0, 250))
    amount = random.choice([180, 425.5, 760, 1240, 1875.25, 2300, 3150, 4800, 6420, 9900])
    return {
        "claimantName": random.choice(NAMES),
        "policyNumber": f"{prefix}-{random.randint(100000, 999999)}",
        "incidentDate": d.isoformat(),
        "incidentType": kind,
        "claimedAmount": f"{amount:.2f}",
    }


def long_date(iso):
    d = date.fromisoformat(iso)
    return f"{d.day} {MONTHS[d.month - 1]} {d.year}"


def money(value):
    return f"USD {float(value):,.2f}"


def form(f):
    return (f"CLAIM FORM\n\nClaimant name: {f['claimantName']}\nPolicy number: {f['policyNumber']}\n"
            f"Incident date: {f['incidentDate']}\nIncident type: {f['incidentType'].replace('_', ' ').title()}\n"
            f"Amount claimed: {money(f['claimedAmount'])}\n\nDescription:\n{STORIES[f['incidentType']].capitalize()}.\n")


def email(f):
    return (f"Subject: New claim\n\nHello,\n\nI would like to make a claim on policy {f['policyNumber']}. "
            f"On {long_date(f['incidentDate'])} {STORIES[f['incidentType']]}. "
            f"The total cost comes to {money(f['claimedAmount'])} and I have the receipts.\n\n"
            f"Kind regards,\n{f['claimantName']}\n")


def missing(f, field):
    text = email(f)
    if field == "policyNumber":
        text = text.replace(f" on policy {f['policyNumber']}", "")
    elif field == "incidentDate":
        text = text.replace(f"On {long_date(f['incidentDate'])} ", "Recently ")
    elif field == "claimedAmount":
        text = text.replace(f"The total cost comes to {money(f['claimedAmount'])} and I have the receipts.",
                            "I will send the quote once I have it.")
    return text


def conflict(f, field):
    text = form(f)
    if field == "claimedAmount":
        other = f"{float(f['claimedAmount']) * 2:.2f}"
        text += f"\nCorrection: the repairer now says the amount is {money(other)}, not the figure above.\n"
    elif field == "incidentDate":
        other = (date.fromisoformat(f["incidentDate"]) - timedelta(days=9)).isoformat()
        text += f"\nNote: I may have the date wrong, it could have been {other}.\n"
    elif field == "policyNumber":
        other = f"{f['policyNumber'][:4]}{random.randint(100000, 999999)}"
        text += f"\nMy renewal letter shows policy {other}, so I am not sure which one applies.\n"
    return text


cases = [("form", None)] * 12 + [("email", None)] * 8 \
    + [("missing", fld) for fld in ["policyNumber", "incidentDate", "claimedAmount", "claimedAmount", "incidentDate"]] \
    + [("conflict", fld) for fld in ["claimedAmount", "incidentDate", "policyNumber", "claimedAmount", "incidentDate"]]

for i, (kind, fld) in enumerate(cases, start=1):
    f = fields()
    text = {"form": lambda: form(f), "email": lambda: email(f),
            "missing": lambda: missing(f, fld), "conflict": lambda: conflict(f, fld)}[kind]()
    expected = {k: f[k] for k in ["policyNumber", "incidentDate", "claimedAmount", "incidentType"]}
    must_flag = []
    if fld:
        expected[fld] = None
        must_flag = [fld]
    name = f"{i:02d}"
    (OUT / f"{name}.txt").write_text(text, encoding="utf-8")
    (OUT / f"{name}.expected.json").write_text(
        json.dumps({"kind": kind, "expected": expected, "mustFlag": must_flag}, indent=2) + "\n", encoding="utf-8")

print(f"wrote {len(cases)} cases to {OUT}")
