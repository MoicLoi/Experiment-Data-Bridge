package me.moiclathy.edb.web.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import me.moiclathy.edb.agent.application.AgentPlanningApplicationService
import me.moiclathy.edb.agent.application.AgentAsyncPlanningService
import me.moiclathy.edb.agent.application.AgentProposalApprovalService
import me.moiclathy.edb.agent.application.AgentProposalSubmissionService
import me.moiclathy.edb.agent.model.AgentDefinitionRegistry
import me.moiclathy.edb.agent.model.AgentPolicyRegistry
import me.moiclathy.edb.web.dto.AgentAuditEventResponse
import me.moiclathy.edb.web.dto.AgentAuditTimelineResponse
import me.moiclathy.edb.web.dto.AgentPlanningResponse
import me.moiclathy.edb.web.dto.AgentProposalDecisionResponse
import me.moiclathy.edb.web.dto.AgentProposalSubmissionResponse
import me.moiclathy.edb.web.dto.AgentProposalListResponse
import me.moiclathy.edb.web.dto.AgentRunResponse
import me.moiclathy.edb.web.dto.AgentRunAcceptedResponse
import me.moiclathy.edb.web.dto.AgentSessionResponse
import me.moiclathy.edb.web.dto.AgentSessionMessageListResponse
import me.moiclathy.edb.web.dto.AgentSessionMessageResponse
import me.moiclathy.edb.web.dto.AgentTokenQuotaResponse
import me.moiclathy.edb.web.dto.AgentWorkflowProposalResponse
import me.moiclathy.edb.web.dto.CreateAgentSessionRequest
import me.moiclathy.edb.web.dto.PlanAgentWorkflowRequest
import me.moiclathy.edb.web.dto.RejectAgentProposalRequest
import me.moiclathy.edb.web.dto.SubmitAgentProposalRequest
import me.moiclathy.edb.web.security.requireUserId

fun Route.agentRoutes(
    service: AgentPlanningApplicationService,
    definitions: AgentDefinitionRegistry,
    policies: AgentPolicyRegistry,
    approvals: AgentProposalApprovalService,
    submissions: AgentProposalSubmissionService,
    asyncPlanning: AgentAsyncPlanningService
) {
    authenticate("auth-jwt") {
        get("/api/v1/projects/{projectId}/agent-token-quota") {
            val userId = call.requireUserId() ?: return@get
            val projectId = call.parameters["projectId"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Project ID must be a valid integer.")
            call.respond(AgentTokenQuotaResponse.from(service.getTokenQuota(projectId, userId)))
        }
        post("/api/v1/projects/{projectId}/agent-sessions") {
            val userId = call.requireUserId() ?: return@post
            val projectId = call.parameters["projectId"]?.toIntOrNull()
                ?: throw IllegalArgumentException("Project ID must be a valid integer.")
            val request = call.receive<CreateAgentSessionRequest>()
            val definition = definitions.require(request.agentId, request.agentVersion)
            call.respond(HttpStatusCode.Created, AgentSessionResponse.from(service.createSession(definition, userId, projectId)))
        }

        route("/api/v1/agent-sessions/{sessionId}") {
            get {
                val userId = call.requireUserId() ?: return@get
                call.respond(AgentSessionResponse.from(service.getSession(call.sessionId(), userId)))
            }
            post("/close") {
                val userId = call.requireUserId() ?: return@post
                call.respond(AgentSessionResponse.from(service.closeSession(call.sessionId(), userId)))
            }
            post("/messages") {
                val userId = call.requireUserId() ?: return@post
                val session = service.getSession(call.sessionId(), userId)
                val request = call.receive<PlanAgentWorkflowRequest>()
                val baseDefinition = definitions.require(session.agentId, session.agentVersion)
                val definition = request.modelProfileId?.takeIf(String::isNotBlank)?.let { profileId ->
                    baseDefinition.copy(modelProfileId = profileId)
                } ?: baseDefinition
                val policy = policies.require(definition.policyId)
                val run = asyncPlanning.start(
                    session.id, userId, definition, policy, request.message, request.maxRepairAttempts
                )
                call.respond(HttpStatusCode.Accepted, AgentRunAcceptedResponse.from(run))
            }
            get("/messages") {
                val userId = call.requireUserId() ?: return@get
                call.respond(AgentSessionMessageListResponse(
                    service.getSessionMessages(call.sessionId(), userId).map(AgentSessionMessageResponse::from)
                ))
            }
        }

        route("/api/v1/agent-runs/{runId}") {
            get {
                val userId = call.requireUserId() ?: return@get
                call.respond(AgentRunResponse.from(service.getRun(call.runId(), userId)))
            }
            get("/proposals") {
                val userId = call.requireUserId() ?: return@get
                call.respond(AgentProposalListResponse(service.getRunProposals(call.runId(), userId).map(AgentWorkflowProposalResponse::from)))
            }
            get("/audit") {
                val userId = call.requireUserId() ?: return@get
                val runId = call.runId()
                call.respond(AgentAuditTimelineResponse(runId, service.getRunAudit(runId, userId).map(AgentAuditEventResponse::from)))
            }
            post("/cancel") {
                val userId = call.requireUserId() ?: return@post
                call.respond(AgentRunResponse.from(asyncPlanning.cancel(call.runId(), userId)))
            }
        }

        route("/api/v1/agent-proposals/{proposalId}") {
            get {
                val userId = call.requireUserId() ?: return@get
                call.respond(AgentWorkflowProposalResponse.from(service.getProposal(call.proposalId(), userId)))
            }
            get("/decision") {
                val userId = call.requireUserId() ?: return@get
                val decision = approvals.getDecision(call.proposalId(), userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(AgentProposalDecisionResponse.from(decision))
            }
            post("/approve") {
                val userId = call.requireUserId() ?: return@post
                call.respond(AgentProposalDecisionResponse.from(approvals.approve(call.proposalId(), userId)))
            }
            post("/reject") {
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<RejectAgentProposalRequest>()
                call.respond(AgentProposalDecisionResponse.from(approvals.reject(call.proposalId(), userId, request.reason)))
            }
            get("/submission") {
                val userId = call.requireUserId() ?: return@get
                val submission = submissions.getSubmission(call.proposalId(), userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(AgentProposalSubmissionResponse.from(submission))
            }
            post("/submit") {
                val userId = call.requireUserId() ?: return@post
                val request = call.receive<SubmitAgentProposalRequest>()
                call.respond(HttpStatusCode.Accepted,
                    AgentProposalSubmissionResponse.from(submissions.submit(call.proposalId(), userId, request.context)))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.sessionId() =
    parameters["sessionId"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Agent session ID is required.")
private fun io.ktor.server.application.ApplicationCall.runId() =
    parameters["runId"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Agent run ID is required.")
private fun io.ktor.server.application.ApplicationCall.proposalId() =
    parameters["proposalId"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Agent proposal ID is required.")
