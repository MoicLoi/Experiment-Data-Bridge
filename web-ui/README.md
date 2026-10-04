# EDB Web UI

React/TypeScript console for EDB 0.25. The UI uses `zh-CN` by default on Chinese systems and falls back to `en-US`.

```powershell
npm.cmd install
npm.cmd run dev
```

Development requests under `/api` are proxied to `http://127.0.0.1:25560`. Set `VITE_EDB_API_BASE_URL` only when the API is hosted on another origin with CORS enabled.

Authentication tokens are stored in `sessionStorage` by default. They are stored in `localStorage` only when the user explicitly chooses to remain signed in.
