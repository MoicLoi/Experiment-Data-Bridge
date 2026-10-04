# EDB Desktop

Tauri 2 shell for the existing EDB Web console. It starts the packaged Kotlin server silently, shows startup health, then navigates the same window to `http://127.0.0.1:25560`.

## Development

Requirements: Rust stable with Cargo, Node.js, Java 21+ and WebView2.

```powershell
cd E:\edb\desktop
npm install
npm run dev
```

By default development uses `E:\edb\release\edb-demo-v1`. Override it with `EDB_SERVER_HOME`. A production binary currently also requires `EDB_SERVER_HOME` or a sibling `resources\edb-server` directory. If port 25560 already hosts a healthy EDB instance, Desktop connects to it and will not stop that externally managed process.

Logs opened by the launcher are under `<server-home>\logs`. Desktop-managed stdout and stderr use `desktop-server.out.log` and `desktop-server.err.log`.

The current phase intentionally does not enable installers, updates, tray behavior or Docker installation. Do not bundle the working `release` directory directly: it may contain local database credentials, logs and uploaded data. A later packaging stage must build a clean server resource directory from templates and binaries.
