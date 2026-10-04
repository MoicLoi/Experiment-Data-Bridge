import { NavLink, Outlet } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthProvider";
import { LanguageSelect } from "./LanguageSelect";
import { ThemeSelect } from "./ThemeSelect";
import { API_BASE_URL } from "../lib/api";
import { TaskNotifications, TaskNotificationPermissionButton } from "./TaskNotifications";

const navigation = [
  ["/", "nav.overview", "01"],
  ["/tasks", "nav.tasks", "02"],
  ["/datafiles", "nav.datafiles", "03"],
  ["/workflows", "nav.workflows", "04"],
  ["/agent", "nav.agent", "05"],
  ["/admin", "nav.admin", "06"],
  ["/algorithms", "operations.algorithms", "07"],
  ["/docker", "operations.docker", "08"],
  ["/docker/rebuild", "operations.imageCatalog", "09"],
] as const;

export function AppShell() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <header>
          <div className="brand-mark small">EDB</div>
          <div>
            <strong>{t("common.appName")}</strong>
            <span>{t("common.console")}</span>
          </div>
        </header>
        <nav>
          {navigation.map(([to, key, index]) => (
            <NavLink key={to} to={to} end={to === "/"}>
              <span>{index}</span>
              {t(key)}
            </NavLink>
          ))}
        </nav>
        <footer>
          <span>{user?.username}</span>
          <small>{user?.role}</small>
          <button onClick={logout}>{t("common.signOut")}</button>
        </footer>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            <span className="pulse" />
            {t("shell.apiConnected")}
            <small>{API_BASE_URL || window.location.origin}</small>
          </div>
          <div>
            <TaskNotificationPermissionButton />
            <LanguageSelect />
            <ThemeSelect />
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
      <TaskNotifications />
    </div>
  );
}
