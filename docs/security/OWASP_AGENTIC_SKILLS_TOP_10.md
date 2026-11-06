# OWASP Agentic Skills Top 10 assessment

7 November 2026. Source: [official OWASP checklist](https://github.com/OWASP/www-project-agentic-skills-top-10/blob/d6f7d7d0de314f52a83a85d1828e06ab096e595c/checklist.md). This is a manual applicability assessment, not a completed scanner/certification. The BMS has no autonomous runtime agent. The taxonomy applies to the AI-assisted development workflow, not to claiming the expense classifier executes instructions.

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

Community scholarly360/owasp-top10-web-skills b066b36aa62cd576d02ddf91edabe7359059fcf8 was discovered as a separate Python-web reference. It is not the official OWASP Agentic Skills project and no completed execution of that pack is claimed. Product control implementation is mapped separately in the IAS checklist, with explicit source-branch limitations.
