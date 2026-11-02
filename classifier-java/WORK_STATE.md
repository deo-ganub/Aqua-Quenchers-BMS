# Java classifier work state

Objective: bounded trusted model loading, pure Java TF-IDF/softmax parity and safe expense-suggestion state. Scope: `classifier-java/` only. Root checkpoint maintained by coordinating agent. Source: `handoff/source_readable/algocom.md` and `handoff/CODEX_PROMPT.md`.

Status: In Progress. API/schema agreed with Python/Android agents. No trained release model supplied; app must default to UNAVAILABLE and retain manual entry. No real-device benchmark claim.

Current task: implement dependency-free strict JSON parser and provenance/dimension validation, inference and state controller.

Verification: none yet. Java runtime present but javac absent; coordinator preparing JDK.

NEXT_ACTION: finish Java code, tests and Python golden parity; execute host tests when compiler available; document model trust and device benchmark command.
