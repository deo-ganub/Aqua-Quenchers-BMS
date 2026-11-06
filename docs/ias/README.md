> Branch scope: this academic package is added to `algo-ias` based on `owner-module` at `bf2b43e`. Implementation links to `424c701` describe the separate `work` baseline; they do **not** prove those controls exist in `owner-module`. See [branch review](../security/OWNER_MODULE_REVIEW.md).

# IAS presentation package

Prepared 6 November 2026 (Asia/Manila) for the professor's supplied checklist. These are repository deliverables and source/test evidence, not an adopted legal policy, penetration-test certification or production release approval.

| Requested item | Deliverable | Readiness |
|---|---|---|
| DRAFT Privacy Policy | [Draft policy](DRAFT_PRIVACY_POLICY.md) | Written; operator/contact, lawful-basis, retention, provider-region and adoption decisions pending |
| DRAFT Terms and Conditions | [Draft terms](DRAFT_TERMS_AND_CONDITIONS.md) | Written; operator/contact/effective-date and legal/operator review pending |
| Recommended security controls | [Checklist and implementation links](SECURITY_CONTROLS.md) | Source and retained local evidence distinguished from hosted/physical verification |
| Cloudflare security-audit report across the codebase | [Security review index](../security/README.md) | See report scope, source revision, machine-readable coverage and unresolved validation |
| OWASP Agent Skills Top 10 report | [OWASP assessment](../security/OWASP_AGENTIC_SKILLS_TOP_10.md) | Actual agentic-development applicability and gaps; separate from product web/mobile controls |
| Implementation/demo links | [Academic presentation index](../academic/README.md) | Working code paths linked; missing data/device/PBL material marked explicitly |

The professor's authentication-module screenshot is used as a control comparison. This project implements native Java/XML Android, Supabase managed Auth and checked PostgreSQL RPCs. It has no Prisma runtime client or Expo SecureStore. Token persistence is not implemented: Android keeps access/refresh tokens in memory and clears local session state on logout; any future persistence requires reviewed Android Keystore protection. Protected transactional audit rows are not advertised as a cryptographic hash chain against a database administrator.

## Source alignment and provenance

The user supplied both Google Docs. Read-only text exports were retrieved successfully on 6 November 2026; the digests identify those review snapshots, not future edits to the live documents. Private academic originals and personal/student details are excluded from public source publication.

| Source | Review-snapshot SHA-256 | Use |
|---|---|---|
| [IAS live paper](https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit) | `fc9102539e518cf3dad726fe2a120d2f502ff4a647fe7ec43c265eb1d571a088` | Privacy, least privilege, accounts/MFA, device session, finance/audit, encrypted recovery and SEC-TASK-101–107 |
| [ALGOCOM live paper](https://docs.google.com/document/d/1ad4xmnPsoNZSpVCHALvLoQ5zGRYaHF0hEQ5UhTwF4_o/edit) | `4d976caa8b54eb1a1b2bec6ea5a89e9eec7f4328321615c50591a9f188c8c14f` | TF-IDF/logistic regression, weighted NB comparison, frozen grouped evaluation and real-device benchmarks |
| Local original handoff | `handoff/CODEX_PROMPT.md` (actual persistent path) | Approved architecture, business rules and deferred production boundary; private originals remain local |
| Professor screenshots | User-provided images in the conversation | Required draft policies, controls, whole-codebase skill reports, algorithm demo and milestone/benchmark material |

The papers contain proposals, archived laboratory/reference results and older prototype observations. Those counts, cryptography exercises, consent-endpoint examples and implementation claims do not become passed tests or new product scope. Current source and actual evidence take precedence when describing what this repository does. The original paper's named review/acceptance roles still require their own approval; a generated report does not sign off on their behalf.

## Before adoption or demonstration with real data

Confirm the legal operator and privacy/security channel; approve lawful bases and commercial retention; verify the effective hosted Auth/grants/session settings and device controls; establish independent encrypted-copy/key/erasure-ledger custody and complete an isolated restore drill. Only the Owner's reviewed anonymized corpus and declared physical device can close classifier quality and device-performance gates. Keep production deployment deferred.

The separately discovered `owner-module` branch has unrelated history. Its [supplemental review](../security/OWNER_MODULE_REVIEW.md) is explicitly revision-specific; no controls from the reviewed `Design-overhaul-v.10` source are silently claimed for that branch.
