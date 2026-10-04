import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type KeyboardEvent,
  type PointerEvent,
} from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";
import { formatDateTime } from "../../i18n/formatters";
import type { SupportedLocale } from "../../i18n/resources";
import { API_BASE_URL, ApiError } from "../../lib/api";
import { streamSse } from "../../lib/sse";
import { createDemoProject, listProjects } from "../tasks/taskApi";
import type { Project } from "../tasks/types";
import {
  approveProposal,
  cancelRun,
  closeSession,
  createSession,
  getAudit,
  getMessages,
  getProposalDecision,
  getProposalSubmission,
  getProposals,
  getRun,
  getSession,
  modelProfiles,
  plan,
  rejectProposal,
  submitProposal,
} from "./agentApi";
import type {
  AgentAuditEvent,
  AgentLifecycleEvent,
  AgentMessage,
  AgentModelProfile,
  AgentProposal,
  AgentRun,
  AgentSession,
  ProposalDecision,
  ProposalSubmission,
} from "./types";

const RUNNING = new Set([
  "RECEIVED",
  "PLANNING",
  "VALIDATING",
  "REPAIRING",
  "EXECUTING",
]);
const sessionKey = (projectId: number) => `edb.agent.session.${projectId}`;

export function AgentWorkbenchPage() {
  const { t, i18n } = useTranslation();
  const { api, token } = useAuth();
  const locale = i18n.resolvedLanguage as SupportedLocale;
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectId, setProjectId] = useState<number | null>(null);
  const [session, setSession] = useState<AgentSession | null>(null);
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [run, setRun] = useState<AgentRun | null>(null);
  const [audit, setAudit] = useState<AgentAuditEvent[]>([]);
  const [proposals, setProposals] = useState<AgentProposal[]>([]);
  const [selectedProposal, setSelectedProposal] =
    useState<AgentProposal | null>(null);
  const [decision, setDecision] = useState<ProposalDecision | null>(null);
  const [submission, setSubmission] = useState<ProposalSubmission | null>(null);
  const [message, setMessage] = useState("");
  const [liveText, setLiveText] = useState("");
  const [rejectReason, setRejectReason] = useState("");
  const [connected, setConnected] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [profiles, setProfiles] = useState<AgentModelProfile[]>([]);
  const [profileId, setProfileId] = useState("");
  const fail = useCallback(
    (failure: unknown) =>
      setError(
        failure instanceof ApiError
          ? t(
              `errors.${failure.code === "FORBIDDEN" ? "forbidden" : failure.code === "NOT_FOUND" ? "notFound" : failure.code === "NETWORK_ERROR" ? "network" : "server"}`,
            )
          : failure instanceof Error
            ? failure.message
            : t("errors.unknown"),
      ),
    [t],
  );

  useEffect(() => {
    listProjects(api)
      .then((items) => {
        setProjects(items);
        setProjectId(items[0]?.id ?? null);
      })
      .catch(fail);
  }, [api, fail]);
  useEffect(() => {
    modelProfiles(api)
      .then((value) => {
        setProfiles(value.items);
        setProfileId(value.defaultProfileId);
      })
      .catch(fail);
  }, [api, fail]);
  const quickProject = async () => {
    setBusy("project");
    setError(null);
    try {
      const created = await createDemoProject(api);
      setProjects([created]);
      setProjectId(created.id);
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  useEffect(() => {
    setSession(null);
    setMessages([]);
    setRun(null);
    setAudit([]);
    setProposals([]);
    setSelectedProposal(null);
    setDecision(null);
    setSubmission(null);
    setLiveText("");
    if (!projectId) return;
    const saved = localStorage.getItem(sessionKey(projectId));
    if (!saved) return;
    setLoading(true);
    getSession(api, saved)
      .then((next) => {
        setSession(next);
        return getMessages(api, next.id);
      })
      .then(setMessages)
      .catch(() => localStorage.removeItem(sessionKey(projectId)))
      .finally(() => setLoading(false));
  }, [api, projectId]);

  const refreshRun = useCallback(
    async (runId: string) => {
      const [nextRun, nextAudit, nextProposals] = await Promise.all([
        getRun(api, runId),
        getAudit(api, runId),
        getProposals(api, runId),
      ]);
      setRun(nextRun);
      setAudit(nextAudit);
      setProposals(nextProposals);
      const proposal = nextProposals.at(-1) ?? null;
      setSelectedProposal(proposal);
      if (proposal) {
        const [nextDecision, nextSubmission] = await Promise.all([
          getProposalDecision(api, proposal.id),
          getProposalSubmission(api, proposal.id),
        ]);
        setDecision(nextDecision);
        setSubmission(nextSubmission);
      } else {
        setDecision(null);
        setSubmission(null);
      }
      if (session) setMessages(await getMessages(api, session.id));
    },
    [api, session?.id],
  );

  useEffect(() => {
    if (!session || !token) return;
    const controller = new AbortController();
    let stopped = false;
    const connect = async () => {
      while (!stopped) {
        try {
          setConnected(true);
          await streamSse(`/api/v1/agent-sessions/${session.id}/events`, {
            baseUrl: API_BASE_URL,
            token,
            signal: controller.signal,
            onMessage: (frame) => {
              const event = JSON.parse(frame.data) as AgentLifecycleEvent;
              if (event.runId && (!run || event.runId === run.id)) {
                setRun((current) =>
                  current
                    ? {
                        ...current,
                        status: event.runStatus ?? current.status,
                        updatedAt: event.occurredAt,
                      }
                    : current,
                );
                if (event.type === "MESSAGE_DELTA") {
                  const detail = safeJson(event.detailsJson);
                  setLiveText((value) => value + String(detail.text ?? ""));
                }
                if (event.type !== "MESSAGE_DELTA")
                  setAudit((current) => [
                    ...current,
                    {
                      id: -Date.now(),
                      sessionId: event.sessionId,
                      runId: event.runId,
                      actorUserId: 0,
                      projectId: event.projectId,
                      type: event.type,
                      runStatus: event.runStatus,
                      detailsJson: event.detailsJson,
                      createdAt: event.occurredAt,
                    },
                  ]);
                if (
                  [
                    "RUN_SNAPSHOT",
                    "PROPOSAL_CREATED",
                    "RUN_COMPLETED",
                    "RUN_FAILED",
                    "RUN_CANCELLED",
                  ].includes(event.type)
                )
                  void refreshRun(event.runId);
              }
            },
          });
        } catch (failure) {
          if (controller.signal.aborted) return;
          setConnected(false);
          if (failure instanceof ApiError && failure.code === "UNAUTHORIZED")
            return;
          await new Promise((resolve) => window.setTimeout(resolve, 2000));
        }
      }
    };
    void connect();
    return () => {
      stopped = true;
      controller.abort();
    };
  }, [session?.id, token, run?.id, refreshRun]);

  const newSession = async () => {
    if (!projectId) return;
    setBusy("session");
    setError(null);
    try {
      const next = await createSession(api, projectId);
      localStorage.setItem(sessionKey(projectId), next.id);
      setSession(next);
      setMessages([]);
      setRun(null);
      setAudit([]);
      setProposals([]);
      setSelectedProposal(null);
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  const endSession = async () => {
    if (!session) return;
    setBusy("close");
    try {
      const next = await closeSession(api, session.id);
      setSession(next);
      if (projectId) localStorage.removeItem(sessionKey(projectId));
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  const send = async () => {
    if (!session || !message.trim() || !profileId) return;
    setBusy("send");
    setError(null);
    setLiveText("");
    setAudit([]);
    setProposals([]);
    setSelectedProposal(null);
    setDecision(null);
    setSubmission(null);
    const content = message.trim();
    setMessage("");
    setMessages((current) => [
      ...current,
      {
        id: -Date.now(),
        runId: "pending",
        role: "USER",
        content,
        createdAt: new Date().toISOString(),
      },
    ]);
    try {
      const next = await plan(api, session.id, content, profileId);
      setRun(next);
      await refreshRun(next.id);
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  const decide = async (kind: "approve" | "reject") => {
    if (!selectedProposal) return;
    setBusy(kind);
    setError(null);
    try {
      setDecision(
        kind === "approve"
          ? await approveProposal(api, selectedProposal.id)
          : await rejectProposal(
              api,
              selectedProposal.id,
              rejectReason || t("agent.rejectDefault"),
            ),
      );
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  const submit = async () => {
    if (!selectedProposal) return;
    setBusy("submit");
    setError(null);
    try {
      setSubmission(await submitProposal(api, selectedProposal.id));
    } catch (failure) {
      fail(failure);
    } finally {
      setBusy(null);
    }
  };
  const chooseProposal = (proposal: AgentProposal) => {
    setSelectedProposal(proposal);
    setDecision(null);
    setSubmission(null);
    Promise.all([
      getProposalDecision(api, proposal.id),
      getProposalSubmission(api, proposal.id),
    ])
      .then(([nextDecision, nextSubmission]) => {
        setDecision(nextDecision);
        setSubmission(nextSubmission);
      })
      .catch(fail);
  };
  const pointerChoose = (
    event: PointerEvent<HTMLElement>,
    proposal: AgentProposal,
  ) => {
    if (event.isPrimary) chooseProposal(proposal);
  };
  const keyboardChoose = (
    event: KeyboardEvent<HTMLElement>,
    proposal: AgentProposal,
  ) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      chooseProposal(proposal);
    }
  };
  const running = !!run && RUNNING.has(run.status);
  const chatItems = useMemo(
    () => messages.filter((item) => item.content.trim()),
    [messages],
  );

  return (
    <div className="agent-workbench">
      <header className="page-heading">
        <div>
          <span>CONTROLLED / AGENT</span>
          <h1>{t("agent.title")}</h1>
          <p>{t("agent.subtitle")}</p>
        </div>
        <div className={`live-state ${connected ? "connected" : ""}`}>
          <i />
          {connected ? t("agent.live") : t("agent.offline")}
        </div>
      </header>
      <section className="agent-toolbar">
        <label>
          {t("agent.project")}
          <select
            value={projectId ?? ""}
            onChange={(event) =>
              setProjectId(Number(event.target.value) || null)
            }
          >
            <option value="">—</option>
            {projects.map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("agent.profile")}
          <select
            value={profileId}
            onChange={(event) => setProfileId(event.target.value)}
            disabled={running}
          >
            <option value="">—</option>
            {profiles.map((profile) => (
              <option key={profile.id} value={profile.id}>
                {profile.modelName} · {profile.providerId}
                {profile.default ? " · default" : ""}
              </option>
            ))}
          </select>
        </label>
        <div className="session-identity">
          <small>{t("agent.session")}</small>
          <strong>
            {session ? shortId(session.id) : t("agent.noSession")}
          </strong>
          <span>{session?.status ?? "—"}</span>
        </div>
        {projects.length === 0 ? (
          <button disabled={busy !== null} onClick={() => void quickProject()}>
            Create test project
          </button>
        ) : (
          <button
            disabled={!projectId || busy !== null}
            onClick={() => void newSession()}
          >
            {t("agent.newSession")}
          </button>
        )}
        {session && ["OPEN", "ACTIVE"].includes(session.status) && (
          <button
            className="secondary-button"
            disabled={busy !== null}
            onClick={() => void endSession()}
          >
            {t("agent.closeSession")}
          </button>
        )}
      </section>
      {profiles.length === 1 && profiles[0]?.builtIn && (
        <div className="inline-notice">
          Only the built-in Fake Profile is available. Bind model API keys in
          the server environment and application.yml, then restart EDB.
        </div>
      )}
      {error && (
        <div className="inline-error" role="alert">
          {error}
        </div>
      )}
      <div className="agent-layout">
        <section className="agent-chat-panel">
          <header>
            <div>
              <strong>{t("agent.chat")}</strong>
              <small>
                {session?.agentId ?? "workflow-planner"} · v
                {session?.agentVersion ?? "1"}
              </small>
            </div>
            {run && (
              <span
                className={`agent-run-status status-${run.status.toLowerCase()}`}
              >
                {run.status}
              </span>
            )}
          </header>
          <div className="agent-messages" aria-live="polite">
            {loading ? (
              <div className="task-empty">{t("common.loading")}</div>
            ) : !session ? (
              <div className="task-empty">{t("agent.startHint")}</div>
            ) : chatItems.length === 0 && !liveText ? (
              <div className="task-empty">{t("agent.messageHint")}</div>
            ) : (
              <>
                {chatItems.map((item) => (
                  <article
                    key={item.id}
                    className={`agent-message ${item.role.toLowerCase()}`}
                  >
                    <span>{item.role}</span>
                    <p>{item.content}</p>
                    <time>{formatDateTime(item.createdAt, locale)}</time>
                  </article>
                ))}
                {liveText && (
                  <article className="agent-message assistant streaming">
                    <span>AGENT</span>
                    <p>
                      {liveText}
                      <i />
                    </p>
                  </article>
                )}
              </>
            )}
          </div>
          <footer className="agent-composer">
            <textarea
              rows={3}
              value={message}
              disabled={!session || !["OPEN", "ACTIVE"].includes(session.status) || running}
              onChange={(event) => setMessage(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
                  event.preventDefault();
                  void send();
                }
              }}
              placeholder={t("agent.messagePlaceholder")}
            />
            <div>
              <small>{t("agent.sendHint")}</small>
              {running && run ? (
                <button
                  className="danger-button"
                  onClick={() =>
                    void cancelRun(api, run.id).then(setRun).catch(fail)
                  }
                >
                  {t("agent.cancelRun")}
                </button>
              ) : (
                <button
                  disabled={!session || !message.trim() || busy !== null}
                  onClick={() => void send()}
                >
                  {busy === "send" ? t("agent.sending") : t("agent.send")}
                </button>
              )}
            </div>
          </footer>
        </section>
        <aside className="agent-inspector">
          <section className="run-card">
            <header>
              <h2>{t("agent.run")}</h2>
              {run && <span>#{shortId(run.id)}</span>}
            </header>
            {!run ? (
              <p>{t("agent.noRun")}</p>
            ) : (
              <>
                <dl>
                  <Fact label={t("agent.status")} value={run.status} />
                  <Fact label={t("agent.profile")} value={run.modelProfileId} />
                  <Fact
                    label={t("agent.tokens")}
                    value={`${run.inputTokens ?? 0} / ${run.outputTokens ?? 0}`}
                  />
                  <Fact
                    label={t("agent.updated")}
                    value={formatDateTime(run.updatedAt, locale)}
                  />
                </dl>
                {run.failureMessage && (
                  <div className="run-failure">
                    <strong>{run.failureCode}</strong>
                    <p>{run.failureMessage}</p>
                  </div>
                )}
              </>
            )}
          </section>
          <section className="agent-timeline">
            <h2>{t("agent.timeline")}</h2>
            {audit.length === 0 ? (
              <p>{t("agent.noEvents")}</p>
            ) : (
              audit.map((event, index) => (
                <article key={`${event.id}-${index}`}>
                  <i />
                  <div>
                    <strong>{event.type}</strong>
                    <time>{formatDateTime(event.createdAt, locale)}</time>
                    <EventDetails json={event.detailsJson} />
                  </div>
                </article>
              ))
            )}
          </section>
        </aside>
      </div>
      <section className="proposal-workspace">
        <header>
          <div>
            <span>{t("agent.proposals")}</span>
            <h2>{t("agent.proposalTitle")}</h2>
          </div>
          <strong>{proposals.length}</strong>
        </header>
        <div className="proposal-layout">
          <nav>
            {proposals.length === 0 ? (
              <p>{t("agent.noProposals")}</p>
            ) : (
              proposals.map((proposal) => (
                <article
                  key={proposal.id}
                  role="button"
                  tabIndex={0}
                  className={
                    selectedProposal?.id === proposal.id ? "selected" : ""
                  }
                  onPointerUp={(event) => pointerChoose(event, proposal)}
                  onKeyDown={(event) => keyboardChoose(event, proposal)}
                >
                  <strong>{shortId(proposal.id)}</strong>
                  <span>
                    {proposal.valid ? t("agent.valid") : t("agent.invalid")}
                  </span>
                  <small>{formatDateTime(proposal.createdAt, locale)}</small>
                </article>
              ))
            )}
          </nav>
          <div className="proposal-detail">
            {!selectedProposal ? (
              <div className="task-empty">{t("agent.selectProposal")}</div>
            ) : (
              <>
                <header>
                  <div>
                    <span>#{selectedProposal.id}</span>
                    <h3>{selectedProposal.explanation}</h3>
                  </div>
                  <span
                    className={
                      selectedProposal.valid
                        ? "proposal-valid"
                        : "proposal-invalid"
                    }
                  >
                    {selectedProposal.valid
                      ? t("agent.valid")
                      : t("agent.invalid")}
                  </span>
                </header>
                {selectedProposal.assumptions.length > 0 && (
                  <ProposalList
                    title={t("agent.assumptions")}
                    items={selectedProposal.assumptions}
                  />
                )}{" "}
                {selectedProposal.warnings.length > 0 && (
                  <ProposalList
                    title={t("agent.warnings")}
                    items={selectedProposal.warnings}
                  />
                )}{" "}
                {selectedProposal.validationErrors.length > 0 && (
                  <ProposalList
                    title={t("agent.validation")}
                    items={selectedProposal.validationErrors.map(
                      (item) => `${item.path}: ${item.message}`,
                    )}
                  />
                )}
                <section>
                  <h4>{t("agent.executionOrder")}</h4>
                  <p>{selectedProposal.executionOrder.join(" → ") || "—"}</p>
                </section>
                <details>
                  <summary>{t("agent.workflowIr")}</summary>
                  <pre>{prettyJson(selectedProposal.workflowIrJson)}</pre>
                </details>
                <div className="proposal-actions">
                  <button
                    disabled={!selectedProposal.valid || busy !== null}
                    onClick={() => void decide("approve")}
                  >
                    {t("agent.approve")}
                  </button>
                  <label>
                    {t("agent.rejectReason")}
                    <input
                      value={rejectReason}
                      onChange={(event) => setRejectReason(event.target.value)}
                    />
                  </label>
                  <button
                    className="danger-button"
                    disabled={busy !== null}
                    onClick={() => void decide("reject")}
                  >
                    {t("agent.reject")}
                  </button>
                  <button
                    className="submit-proposal"
                    disabled={
                      decision?.status !== "APPROVED" ||
                      busy !== null ||
                      !!submission
                    }
                    onClick={() => void submit()}
                  >
                    {t("agent.submitTask")}
                  </button>
                </div>
                {decision && (
                  <div className="decision-banner">
                    {t("agent.decision")}: {decision.status}
                  </div>
                )}
                {submission && (
                  <div className="inline-notice">
                    {t("agent.taskSubmitted", { id: submission.task.id })}
                    <Link to="/tasks">{t("nav.tasks")} →</Link>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
function EventDetails({ json }: { json: string }) {
  const value = safeJson(json);
  const text = String(
    value.toolName ?? value.reason ?? value.message ?? value.proposalId ?? "",
  );
  return text ? <p>{text}</p> : null;
}
function ProposalList({ title, items }: { title: string; items: string[] }) {
  return (
    <section>
      <h4>{title}</h4>
      <ul>
        {items.map((item, index) => (
          <li key={index}>{item}</li>
        ))}
      </ul>
    </section>
  );
}
function safeJson(source: string): Record<string, unknown> {
  try {
    return JSON.parse(source) as Record<string, unknown>;
  } catch {
    return {};
  }
}
function prettyJson(source: string) {
  try {
    return JSON.stringify(JSON.parse(source), null, 2);
  } catch {
    return source;
  }
}
function shortId(value: string) {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value;
}
