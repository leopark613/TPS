# TPS implementation instructions

Read README.md, docs/ARCHITECTURE.md, docs/VERIFICATION.md, docs/PORTABLE-HANDOFF.md and the current spec before changing code. This is one TPS application; do not extract a core product or introduce separate RPS/LPS servers by assumption.

- Java21/Spring Boot, React, Lombok DTOs, module-local MapStruct, MyBatis SQL. Maven is primary; Gradle must build the same source and match runtime dependencies/application resources.
- Preserve original RPS/LPS data and references. No automatic DDL or printer output. Integration tools are explicitly limited to the authorized local TPS database and their own tracked synthetic rows.
- Never commit credentials, private keys, real business payloads or uncontrolled logs. Local TLS exceptions are explicit and fixed in scope; the product default validates certificates.
- Saving creates immutable draft revisions; it never activates business output. Retry receipts precede stale-head checks. Historical revisions and unsupported document features remain protected.
- Test meaningful data integrity, concurrency and browser behavior. Run an independent review and record actual results; distinguish mocks, real DB tests and physical output.
- Keep local Eclipse paths/configuration out of portable runtime assumptions. Secrets are process inputs, not launch-file contents.
- The final portable knowledge/reference package and design/manual PDFs are due after the agreed full first implementation and tests, not this first vertical slice. Keep editable records current meanwhile.
- Clean only verified task-owned temporary files/processes after evidence is retained. Preserve user processes, originals, deliverables, installed tools and intentional backups. Delayed user answers never authorize cancellation or rollback.

- D056: Preserve actual legacy RPS/LPS business names and record mappings; invented illustrative names are not implementation requirements. Current next-slice contract is docs/spec/TPS-IMPL-002.md. Fixed local report-list query and saved-revision preview do not imply StoredQuery execution, printing or new DDL permission.

- D057: Provide the complete local Eclipse/browser run guide after each implemented and tested unit. Preserve AutoBuild ON; the user deferred nonblocking warnings and optional Spring Tools TLS diagnostics. Use build-frontend.cmd for verified UI build/copy; distinguish real GUI import/APT checks from application Run/Debug. Continue independent authorized work without unnecessary questions.
