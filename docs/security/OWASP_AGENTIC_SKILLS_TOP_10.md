# OWASP Agentic Skills Security Assessment

**Document identifier:** AQ-SEC-03

**Edition:** 1.0 — Manual Applicability Assessment

**Issue date:** 7 November 2026

## 1. Assessment Basis

This assessment applies the official OWASP Agentic Skills Top 10 checklist at revision `d6f7d7d0de314f52a83a85d1828e06ab096e595c` to the AI-assisted development workflow. The business application contains no autonomous runtime agent. Expense classification is advisory numerical inference and does not execute natural-language instructions or invoke privileged tools.

The assessment is a manual applicability and gap analysis. It does not constitute certification, a completed automated scan or proof of runtime isolation. The framework's category risk labels shall not be interpreted as severity assignments for unconfirmed application findings.

## 2. Assessment Matrix

| Risk | Observed safeguard / remaining gap |
|---|---|
| AST01 Malicious Skills | Exact publisher sources reviewed; signed-publisher and dynamic-canary verification absent |
| AST02 Supply Chain Compromise | Skill commits pinned; complete signed dependency/SBOM verification absent |
| AST03 Over-Privileged Skills | Source-only hunters restricted by instructions; full per-agent OS permission isolation not established |
| AST04 Insecure Metadata | Metadata treated as untrusted instructions; full metadata schema/behavior scanner not run |
| AST05 Untrusted External Instructions | User documents separated from user authorization; paper snapshots hashed; transitive external graph not fully audited |
| AST06 Weak Isolation | No new target code executed during audit because complete required resource/isolation controls were not established |
| AST07 Update Drift | Review revisions pinned; future changes require re-review; automatic advisory/update gate absent |
| AST08 Poor Scanning | Multi-subsystem source review performed; independent audit phases interrupted, no claim of comprehensive dynamic/SAST/CVE scanning |
| AST09 No Governance | Persistent scope/checkpoint and source refs retained; formal organizational inventory/revocation/review cadence pending |
| AST10 Cross-Platform Reuse | Android/Supabase and development-agent boundaries distinguished; no cross-platform certification |


## 3. Interpretation and Required Follow-up

Pinned source revisions and documented scope support repeatable review. Complete signed dependency verification, metadata/behavior analysis, enforceable per-agent isolation, independent dynamic assessment and organizational governance remain incomplete. Application web/mobile controls are assessed separately through the IAS control matrix; an agentic-workflow checklist does not replace them.

The community `scholarly360/owasp-top10-web-skills` reference at revision `b066b36aa62cd576d02ddf91edabe7359059fcf8` is separate from the official OWASP Agentic Skills project. No completed execution of that community pack is claimed.

## 4. Reference

OWASP. (n.d.). [Skill Security Assessment Checklist](https://github.com/OWASP/www-project-agentic-skills-top-10/blob/d6f7d7d0de314f52a83a85d1828e06ab096e595c/checklist.md). OWASP Agentic Skills Top 10. Reviewed source revision as identified above.
