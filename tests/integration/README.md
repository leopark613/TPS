# Local TPS integration verification

These are **test tools**, not application endpoints. They are not included in the server JAR. Python uses only its standard library; the observer uses Java 21 source-file mode and the Microsoft JDBC driver.

Run against a completed server package:

```powershell
$env:TPS_DB_TRUST_SERVER_CERTIFICATE = 'true' # Only the explicitly authorized local development/test exception.
python run_integration.py --jar <server.jar> --java <java.exe> --jdbc <mssql-jdbc.jar> --output <evidence-directory>
```

The script prompts for SQL username and a masked password if `TPS_DB_USER` / `TPS_DB_PASSWORD` are absent. Do not put a password on a command line or in a saved `.env` file. This runner hard-pins `10.121.13.159:1433`, database `TPS`, with TLS encryption. It has no remote fallback, printer API, DDL, identity/sequence call, or original RPS write path.

It generates a fresh `TPS_IT_<timestamp>_<random>` site and in-memory developer password, verifies the site is empty, then starts a packaged application on an unused loopback port (default 18761). Only its own `Popen` process is stopped. A fresh JVM is started to verify durable reopen. Existing processes are never killed by name.

The test report records status/case names/counts, SHA-256 fingerprints and owned test IDs. It does not record document bodies, business values, SQL errors or credentials. `integration-results.json` includes before/after RPS and other-site row hashes. `owned-cleanup-plan.txt` contains only the generated test site and UUIDs, and is retained for review/recovery if cleanup fails.

Cleanup happens after the owned server stops. It requires an exact match between tracked IDs and actual site templates, actor `tps-developer`, no active template, and no unrelated dependency rows. It deletes only this run's verified request receipts and revisions/templates in dependency order, in one transaction. An unexpected row causes rollback and explicit test failure. It never deletes baseline RPS data or another site. No broad site-prefix delete is used.

For read-only inspection without the HTTP runner:

```powershell
java -cp <mssql-jdbc.jar> LocalDbObserver.java snapshot TPS_IT_<run>
```

`cleanup <plan-file>` is deliberately restricted and should be used only for the exact test run whose generated plan has been reviewed. There is no cleanup-by-arbitrary-SQL command.

These checks do not prove physical printing, Excel import, render fidelity, production authentication or full TPS completion. Browser verification is a separate test run.
