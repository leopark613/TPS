# TPS React designer

Native template save/reopen slice. The backend API must be running before real use. No simulated server is included in the product bundle.

```powershell
npm ci
npm test
npm run build
npm run dev
```

Vite serves on `127.0.0.1:5173` and proxies `/api` to `127.0.0.1:18080`. Deployment copies `dist` into the Spring Boot static resources; the browser uses same-origin `/api/tps` URLs. No credential belongs in Vite environment files or the built bundle. The developer connection password is supplied by the person starting the server and held only in page memory.

Implemented UI: RPS/LPS area choice, explicit new draft, template catalog with pagination, name/text editing, text/rectangle placement and mm properties, drag/keyboard move, explicit save, history read-only, latest reopen, copy to new template, preserved edits during pending save/conflicts and inline dirty-navigation check. No business activation, print preview, printing, QR/data binding, Excel importer or production authentication is claimed here.

`tests/visual_smoke.py` is a **mock API** browser test for UI behavior and screenshot checks. It never touches a database and does not replace the parent project's real Spring Boot/SQL Server browser integration test. The browser runner accepts a deliberately selected isolated Chrome executable and writes evidence only to its requested output folder.

Unit tests verify save-generation safety, same-snapshot retries, stale response rejection, conflicts, document preservation and read-only feature limits. `npm run build` also type-checks every source file.
