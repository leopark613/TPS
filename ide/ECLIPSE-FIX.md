# TPS Eclipse import configuration fix — 2026-11-06

## Integrated result (2026-11-06)
Root integrated both POM fixes and an unmodified official local Maven XSD (ide/schema). Actual user Eclipse GUI showed correct source folders/APT/Maven Dependencies and Problems 0 errors, 6 warnings. AutoBuild ON and the user process were preserved. User deferred nonblocking warnings and optional Spring Tools external-info TLS diagnostics; no truststore or TLS changes were made. Application Run/Debug remains unverified. The isolated checks below are historical supporting evidence, not the latest GUI state. See ECLIPSE-SETUP.md for current execution steps.


Scope: portable project POM only. No user Eclipse installation, workspace, saved connection or global setting was changed. The active implementation/integration work remains separate.

## Observed evidence and confidence

- User Eclipse: 4.33; installed `org.eclipse.m2e.core_2.6.1.20240602-2342`, embedded Maven runtime 3.9.9, Maven archiver connector `2.0.501.20240411-1122`.
- Actual `server/.project` has both Java and Maven natures/builders. `server/.classpath` is absent and `.settings` contains the Maven settings but no generated JDT settings. Import configuration did not complete; this does not indicate that TPS Java source compilation has failed.
- Workspace `.metadata/.log` at 11:09 records `JarMojo` constructor injection failing because `MavenProject` is outside its execution scope. The stack runs through `AbstractMavenArchiverConfigurator.reflectManifestGeneration` and project import/update. Boot 4.1.2's locally resolved BOM selects maven-jar-plugin 3.5.1.
- **High confidence:** this matches the upstream m2e scope-initialization defect triggered by the 3.5.x JAR plugin. The official issue includes the same exception and a working 3.4.2 workaround. This is an Eclipse configurator compatibility problem, not a requirement to downgrade Spring Boot or the release build. [m2e issue 2084](https://github.com/eclipse-m2e/m2e-core/issues/2084), [follow-up 2091](https://github.com/eclipse-m2e/m2e-core/issues/2091).
- Independently, log lines 224/314/404 record `Cannot create linked resource '/frontend/dist'. The parent resource is not accessible.` The POM declared `../frontend/dist` as a Java project resource, while the frontend General project was imported later. **High confidence in the resource-configuration cause; GUI recovery remains to be verified.** The fix avoids creating that external Java resource link at all.

## Portable POM changes

1. Keep only `src/main/resources` in the Java project's `build/resources`.
2. Use a normal `maven-resources-plugin:copy-resources` execution at `process-resources` to copy `${project.basedir}/../frontend/dist` into `${project.build.outputDirectory}/static`, with filtering disabled. The same relative source and packaged destination are retained. [Apache Resources Plugin copy-resources](https://maven.apache.org/plugins/maven-resources-plugin/examples/copy-resources.html).
3. Add `eclipse-m2e-compatibility`, automatically activated only when property `m2e.version` is present. It sets `maven-jar-plugin.version=3.4.2` for Eclipse's model. Ordinary Maven CLI retains Boot's 3.5.1; Gradle is unchanged. m2e-only property activation is documented by Eclipse. [M2E-WTP FAQ](https://wiki.eclipse.org/M2E-WTP_FAQ).
4. The installed m2e 2.6.1 JAR was inspected read-only: its own `lifecycle-mapping-metadata.xml` maps resources plugin `[2.4,)`, including `copy-resources`, to execution with `runOnIncremental=true`. No new lifecycle-ignore rule or global plugin modification is needed.

This is a compatibility workaround for the current IDE. A later supported m2e version can remove the workaround after actual import/build verification. Do not change release plugin versions just to silence an IDE error.

## Verification commands and boundaries

Run from `server` with JDK 21 and the normal Maven wrapper:

```powershell
..\mvnw.cmd -B -q help:evaluate -Dexpression=maven-jar-plugin.version -DforceStdout
..\mvnw.cmd -B -q -Dm2e.version=2.6.1 help:evaluate -Dexpression=maven-jar-plugin.version -DforceStdout
```

Expected values are `3.5.1` and `3.4.2`, respectively. These check profile activation/model resolution; they do not simulate Eclipse's JDT/APT/manifest configurators or prove GUI import success.

After integrating the patch, rebuild frontend and clean-package via Maven, compare packaged static files and application/dependency entries with Gradle, then run the actual UI/API checks. Existing build scripts already build the frontend before Java packaging.

## User guide recovery steps

After the corrected POM is present: select `tps-server` in Eclipse, press F5, then Maven > Update Project (Alt+F5); enable project configuration update and clean project. Wait for jobs to finish. Check that Java 21, Maven Dependencies, Java source folders, `.classpath` and generated annotation-processor sources appear. Then use the supplied TPS local Run configuration with runtime credentials supplied through the launcher.

The frontend stays a General project for source navigation; it is not a Maven Java project. Run `npm run build` when using the bundled UI or the existing Vite development command for frontend development. Do not manually create a Java `.classpath`, copy machine-specific workspace metadata to Git, delete the user's workspace, or recreate projects just to hide the markers.

Verified in this isolated scratch: ordinary Maven model returned `3.5.1`, model with `-Dm2e.version=2.6.1` returned `3.4.2` (both exit 0). The first `process-resources` run exited 0 with no frontend/dist present in this source-only scratch, so it did not verify a file copy. Then the existing first-slice frontend build was copied into this scratch as a temporary fixture; a second `process-resources` run exited 0 and all 3 files matched the packaged static destination by SHA-256, with no extra files. Details are in `server/target/eclipse-fix-checks.json`. The model checks required cache write permission; only Maven's local cache and this scratch's fixture/build output were written. Original frontend build files were untouched.

Actual user GUI refresh, Run/Debug and marker clearance are NOT RUN by this sub-agent. A successful Maven model check is not proof that Eclipse has regenerated `.classpath`; the GUI recovery steps remain necessary.

The temporary `backend/frontend/dist` fixture was removed after the 3-file hash comparison was saved; the resolved scratch-only path and absence of reparse points were checked first. Evidence and generated build output are preserved for the parent integration work.
