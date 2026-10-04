use serde::Serialize;
use std::{
    env,
    fs::{self, OpenOptions},
    io::{Read, Write},
    net::{SocketAddr, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
    time::Duration,
};
use tauri::{Manager, State, WindowEvent};

const SERVER_URL: &str = "http://127.0.0.1:25560";

#[derive(Clone, Copy, Serialize)]
#[serde(rename_all = "UPPERCASE")]
enum Phase { Starting, Ready, Failed, Stopped }

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ServerStatus {
    phase: Phase,
    managed: bool,
    pid: Option<u32>,
    server_url: &'static str,
    log_directory: String,
    message: Option<String>,
}

struct Runtime {
    phase: Phase,
    managed: bool,
    child: Option<Child>,
    server_home: PathBuf,
    message: Option<String>,
}

struct AppState { runtime: Mutex<Runtime> }

fn resolve_server_home() -> PathBuf {
    if let Some(path) = env::var_os("EDB_SERVER_HOME") { return PathBuf::from(path); }
    if cfg!(debug_assertions) {
        return Path::new(env!("CARGO_MANIFEST_DIR")).join(r"..\..\release\edb-demo-v1");
    }
    if let Ok(exe) = env::current_exe() {
        if let Some(parent) = exe.parent() {
            let bundled = parent.join("resources").join("edb-server");
            if bundled.exists() { return bundled; }
        }
    }
    Path::new(env!("CARGO_MANIFEST_DIR")).join(r"..\..\release\edb-demo-v1")
}

fn java_command() -> PathBuf {
    env::var_os("EDB_JAVA_HOME").or_else(|| env::var_os("JAVA_HOME"))
        .map(PathBuf::from)
        .map(|home| home.join("bin").join(if cfg!(windows) { "java.exe" } else { "java" }))
        .unwrap_or_else(|| PathBuf::from("java"))
}

fn healthy() -> bool {
    let address: SocketAddr = match "127.0.0.1:25560".parse() { Ok(value) => value, Err(_) => return false };
    let mut stream = match TcpStream::connect_timeout(&address, Duration::from_millis(300)) { Ok(value) => value, Err(_) => return false };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(700)));
    if stream.write_all(b"GET /api/v1/health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n").is_err() { return false; }
    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok() && response.contains("200 OK") && response.contains("\"status\":\"UP\"")
}

fn launch(runtime: &mut Runtime) -> Result<(), String> {
    if healthy() {
        runtime.phase = Phase::Ready;
        runtime.managed = false;
        runtime.message = Some("Connected to an existing EDB server.".into());
        return Ok(());
    }
    if runtime.child.as_mut().is_some_and(|child| child.try_wait().ok().flatten().is_none()) { return Ok(()); }
    for relative in ["config/workflows", "config/workflow-templates", "config/reports", "plugins", "data", "logs"] {
        fs::create_dir_all(runtime.server_home.join(relative)).map_err(|error| format!("Cannot prepare server directory '{relative}': {error}"))?;
    }
    let jar = runtime.server_home.join("server").join("EXPERIMENT_DATA_BRIDGE.jar");
    if !jar.is_file() { return Err(format!("Server JAR was not found: {}", jar.display())); }
    let logs = runtime.server_home.join("logs");
    fs::create_dir_all(&logs).map_err(|error| format!("Cannot create log directory: {error}"))?;
    let stdout = OpenOptions::new().create(true).append(true).open(logs.join("desktop-server.out.log")).map_err(|error| error.to_string())?;
    let stderr = OpenOptions::new().create(true).append(true).open(logs.join("desktop-server.err.log")).map_err(|error| error.to_string())?;
    let child = Command::new(java_command()).arg("-jar").arg(&jar).current_dir(&runtime.server_home)
        .stdin(Stdio::null()).stdout(Stdio::from(stdout)).stderr(Stdio::from(stderr))
        .spawn().map_err(|error| format!("Unable to start Java server: {error}"))?;
    runtime.phase = Phase::Starting;
    runtime.managed = true;
    runtime.message = Some("Loading configuration, database, modules and workers.".into());
    runtime.child = Some(child);
    Ok(())
}

fn stop_managed(state: &AppState) {
    if let Ok(mut runtime) = state.runtime.lock() {
        if runtime.managed {
            if let Some(mut child) = runtime.child.take() { let _ = child.kill(); let _ = child.wait(); }
            runtime.phase = Phase::Stopped;
            runtime.message = Some("Desktop-managed server stopped.".into());
        }
    }
}

#[tauri::command]
fn server_status(state: State<'_, AppState>) -> ServerStatus {
    let mut runtime = state.runtime.lock().expect("desktop runtime lock poisoned");
    if healthy() { runtime.phase = Phase::Ready; runtime.message = None; }
    else if let Some(child) = runtime.child.as_mut() {
        if let Ok(Some(exit)) = child.try_wait() { runtime.phase = Phase::Failed; runtime.message = Some(format!("EDB Server exited with {exit}. Open logs for details.")); }
    } else if !runtime.managed {
        runtime.phase = Phase::Stopped;
        runtime.message = Some("The external server disappeared; switching to Desktop-managed mode.".into());
        if let Err(message) = launch(&mut runtime) { runtime.phase = Phase::Failed; runtime.message = Some(message); }
    }
    ServerStatus {
        phase: runtime.phase, managed: runtime.managed, pid: runtime.child.as_ref().map(Child::id), server_url: SERVER_URL,
        log_directory: runtime.server_home.join("logs").display().to_string(), message: runtime.message.clone()
    }
}

#[tauri::command]
fn start_server(state: State<'_, AppState>) -> Result<(), String> {
    let mut runtime = state.runtime.lock().map_err(|_| "Desktop runtime lock poisoned.".to_string())?;
    launch(&mut runtime).inspect_err(|message| { runtime.phase = Phase::Failed; runtime.message = Some(message.clone()); })
}

#[tauri::command]
fn stop_server(state: State<'_, AppState>) { stop_managed(&state); }

#[tauri::command]
fn open_log_directory(state: State<'_, AppState>) -> Result<(), String> {
    let logs = state.runtime.lock().map_err(|_| "Desktop runtime lock poisoned.".to_string())?.server_home.join("logs");
    fs::create_dir_all(&logs).map_err(|error| error.to_string())?;
    #[cfg(target_os = "windows")]
    let result = Command::new("explorer.exe").arg(&logs).spawn();
    #[cfg(target_os = "macos")]
    let result = Command::new("open").arg(&logs).spawn();
    #[cfg(all(unix, not(target_os = "macos")))]
    let result = Command::new("xdg-open").arg(&logs).spawn();
    result.map(|_| ()).map_err(|error| format!("Unable to open log directory: {error}"))
}

pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            let mut runtime = Runtime { phase: Phase::Stopped, managed: false, child: None, server_home: resolve_server_home(), message: None };
            if let Err(message) = launch(&mut runtime) { runtime.phase = Phase::Failed; runtime.message = Some(message); }
            app.manage(AppState { runtime: Mutex::new(runtime) });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![server_status, start_server, stop_server, open_log_directory])
        .on_window_event(|window, event| { if matches!(event, WindowEvent::Destroyed) { stop_managed(&window.state::<AppState>()); } })
        .run(tauri::generate_context!())
        .expect("error while running EDB Desktop");
}
