# Known limitations and release blockers

The repository is an implementation in progress, not a completed or production-deployed system. See WORK_STATE for ordered independent tasks and actual evidence.

External blockers: Figma SECOND-page screens/component/dialog exports; Owner-reviewed labeled dataset and approved release model; declared Owner tablet/Staff phone and normal test network; approved development Supabase access, managed MFA and Auth configuration; restricted independent backup cloud mounts, key custody, controller retention/erasure approval and a hosted isolated restore drill. No station accuracy, device latency, screen-loading, production RPO/RTO or Figma match is claimed.

Internal work: complete per-scenario UI/hosted test coverage, full selectors/state restoration and error-flow review, compensation tooling beyond the dry-run runbook, remaining reservation/concurrency adversarial variants, fully reviewed compensation execution tooling and source-specific traceability review. Backup retention/age and isolated privacy replay now have tested tooling; independent cloud custody/approvals remain external. Partial per-criterion mappings currently point to relevant implementation and passing backend subsets; they do not mark160 criteria passed. Lint warnings remain, especially programmatic labels/localization and absent final design assets.

The Android backup exclusion policy is configured; financial writes require a reachable backend and there is no offline write queue. Tokens remain in memory; process death requires sign-in. Request digest/idempotency keys survive saved-state recreation. Actual backend/managed Auth authority remains decisive.
