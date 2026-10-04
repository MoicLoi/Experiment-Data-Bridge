package me.moiclathy.edb.agent.planning

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentExecutionContext
import me.moiclathy.edb.agent.model.AgentProposalValidation
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.policy.AgentActionRequest
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.agent.policy.AgentPolicyDecision
import me.moiclathy.edb.agent.policy.AgentPolicyEvaluator
import me.moiclathy.edb.agent.provider.AgentChatMessage
import me.moiclathy.edb.agent.provider.AgentChatModel
import me.moiclathy.edb.agent.provider.AgentChatRole
import me.moiclathy.edb.agent.provider.AgentModelRequest
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools
import me.moiclathy.edb.agent.tools.WorkflowPlanningValidationResult
import java.time.Clock
import java.time.Instant
import java.util.UUID
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.agent.policy.AgentSensitiveDataRedactor

data class WorkflowPlanningCommand(
    val context: AgentExecutionContext,
    val definition: AgentDefinition,
    val policy: AgentPolicy,
    val userIntent: String,
    val maxRepairAttempts: Int = 2,
    val modelProfileId: String = definition.modelProfileId,
    val conversationHistory: List<AgentChatMessage> = emptyList()
) {
    init {
        require(userIntent.isNotBlank()) { "Workflow planning intent must not be blank." }
        require(maxRepairAttempts >= 0) { "Workflow repair attempts must not be negative." }
        require(definition.policyId == policy.id) { "Agent definition and policy do not match." }
        require(modelProfileId.isNotBlank() && modelProfileId == modelProfileId.trim()) {
            "Workflow planning model profile id must not be blank or padded."
        }
        require(conversationHistory.all { it.role in setOf(AgentChatRole.USER, AgentChatRole.ASSISTANT) && it.toolCalls.isEmpty() }) {
            "Workflow planning conversation history may contain only plain user and assistant messages."
        }
    }
}

class WorkflowPlanningAgent(
    private val model: AgentChatModel,
    private val tools: WorkflowPlanningTools,
    private val policyEvaluator: AgentPolicyEvaluator = AgentPolicyEvaluator(),
    private val observer: WorkflowPlanningObserver = WorkflowPlanningObserver.None,
    private val redactor: AgentSensitiveDataRedactor = AgentSensitiveDataRedactor(),
    private val clock: Clock = Clock.systemUTC(),
    private val proposalIdSupplier: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun plan(command: WorkflowPlanningCommand): AgentWorkflowProposal {
        val availableTools = tools.definitions().filter { it.name in command.definition.enabledTools }
        require(availableTools.isNotEmpty()) { "Workflow Planning Agent has no enabled tools." }
        val messages = mutableListOf(
            AgentChatMessage(AgentChatRole.SYSTEM, command.definition.systemPrompt)
        ).apply {
            addAll(command.conversationHistory)
            add(
            AgentChatMessage(
                AgentChatRole.USER,
                "Create a Workflow IR v1 proposal for this request. Discover step schemas when needed and return only the structured proposal: ${command.userIntent}"
            )
            )
        }
        messages.forEach { observer.messageAdded(command.context, it, Instant.now(clock)) }
        var modelCalls = 0
        var toolCalls = 0
        var repairAttempts = 0
        var consumedTokens = 0L
        val maxModelCalls = command.policy.maxToolCallsPerRun + command.maxRepairAttempts + 2

        while (modelCalls < maxModelCalls) {
            enforcePromptLimit(command.policy, messages)
            val modelRequest = AgentModelRequest(
                    requestId = "${command.context.runId}:${modelCalls + 1}",
                    messages = messages.toList(),
                    tools = availableTools,
                    responseSchema = WORKFLOW_PROPOSAL_SCHEMA,
                    modelProfileId = command.modelProfileId,
                    actorUserId = command.context.userId,
                    projectId = command.context.projectId
                )
            observer.modelRequested(command.context, modelCalls + 1, modelRequest, Instant.now(clock))
            var deltaSequence = 0L
            val response = try {
                model.generateStreaming(modelRequest) { text ->
                    if (text.isNotEmpty()) {
                        deltaSequence++
                        observer.modelTextDelta(
                            command.context,
                            modelCalls + 1,
                            deltaSequence,
                            text,
                            Instant.now(clock)
                        )
                    }
                }.let(redactor::redact)
            } catch (failure: Throwable) {
                observer.modelFailed(command.context, modelCalls + 1, failure, Instant.now(clock))
                throw WorkflowPlanningException(
                    (failure as? EDBException)?.code ?: "AGENT_MODEL_FAILED",
                    failure.message ?: "Agent model invocation failed.",
                    failure
                )
            }
            modelCalls++
            observer.modelResponded(command.context, modelCalls, response, Instant.now(clock))
            consumedTokens = addTokens(consumedTokens, response.tokenUsage?.totalTokens ?: 0, command.policy)

            if (response.toolCalls.isNotEmpty()) {
                messages += AgentChatMessage(
                    AgentChatRole.ASSISTANT,
                    response.text ?: "Requested Workflow planning tools: ${response.toolCalls.joinToString { it.name }}",
                    toolCalls = response.toolCalls
                )
                response.toolCalls.forEach { call ->
                    val capability = tools.capability(call.name)
                    authorize(command.policy, capability, call.name, toolCalls, 0, messages, consumedTokens)
                    toolCalls++
                    observer.toolStarted(command.context, call, capability, Instant.now(clock))
                    val result = redactor.redact(tools.execute(call))
                    observer.toolCompleted(command.context, call, result, Instant.now(clock))
                    val toolMessage = AgentChatMessage(
                        AgentChatRole.TOOL,
                        result.toString(),
                        toolCallId = call.id
                    )
                    messages += toolMessage
                    observer.messageAdded(command.context, toolMessage, Instant.now(clock))
                }
                continue
            }

            val candidate = try {
                response.structuredOutput?.let { parseCandidate(it) }
            } catch (failure: WorkflowPlanningException) {
                if (repairAttempts >= command.maxRepairAttempts) throw failure
                messages += AgentChatMessage(AgentChatRole.ASSISTANT, response.structuredOutput.toString())
                val repairMessage = AgentChatMessage(
                    AgentChatRole.USER,
                    "The structured proposal envelope is invalid: ${failure.message} Return the complete corrected proposal."
                )
                messages += repairMessage
                observer.messageAdded(command.context, repairMessage, Instant.now(clock))
                repairAttempts++
                observer.repairRequested(command.context, repairAttempts, failure.message, Instant.now(clock))
                continue
            }
            if (candidate == null) {
                if (repairAttempts >= command.maxRepairAttempts) {
                    throw WorkflowPlanningException(
                        "AGENT_WORKFLOW_OUTPUT_INVALID",
                        "Model did not return a structured Workflow proposal after ${repairAttempts + 1} attempts."
                    )
                }
                response.text?.let { messages += AgentChatMessage(AgentChatRole.ASSISTANT, it) }
                val repairMessage = AgentChatMessage(
                    AgentChatRole.USER,
                    "The response was not the required structured proposal. Return workflow, explanation, assumptions, and warnings using the response schema."
                )
                messages += repairMessage
                observer.messageAdded(command.context, repairMessage, Instant.now(clock))
                repairAttempts++
                observer.repairRequested(command.context, repairAttempts, "Model response was not structured.", Instant.now(clock))
                continue
            }

            val stepCount = (candidate.workflow["steps"] as? JsonArray)?.size ?: 0
            authorize(
                command.policy,
                AgentCapability.VALIDATE_WORKFLOW,
                WorkflowPlanningTools.VALIDATE_WORKFLOW,
                toolCalls,
                stepCount,
                messages,
                consumedTokens
            )
            toolCalls++
            val validation = tools.validate(candidate.workflow)
            if (!validation.valid) {
                observer.validationFailed(command.context, validation, Instant.now(clock))
                if (repairAttempts >= command.maxRepairAttempts) {
                    throw WorkflowPlanningException(
                        "AGENT_WORKFLOW_REPAIR_EXHAUSTED",
                        "Workflow proposal remained invalid after ${repairAttempts + 1} validation attempts: " +
                            validation.errors.joinToString("; ") { "${it.path}: ${it.message}" }
                    )
                }
                messages += AgentChatMessage(AgentChatRole.ASSISTANT, candidate.raw.toString())
                val repairMessage = AgentChatMessage(AgentChatRole.USER, validation.repairMessage())
                messages += repairMessage
                observer.messageAdded(command.context, repairMessage, Instant.now(clock))
                repairAttempts++
                observer.repairRequested(command.context, repairAttempts, "Workflow IR validation failed.", Instant.now(clock))
                continue
            }

            return AgentWorkflowProposal(
                id = proposalIdSupplier().requireGeneratedId(),
                runId = command.context.runId,
                workflowIrJson = checkNotNull(validation.canonicalWorkflowIrJson),
                explanation = candidate.explanation,
                assumptions = candidate.assumptions,
                warnings = candidate.warnings,
                validation = AgentProposalValidation(
                    valid = true,
                    executionOrder = validation.executionOrder
                ),
                approvalRequired = true,
                createdAt = Instant.now(clock)
            ).also { observer.proposalCreated(command.context, it, Instant.now(clock)) }
        }
        throw WorkflowPlanningException("AGENT_PLANNING_LIMIT", "Workflow planning exceeded the bounded model-call loop.")
    }

    private fun authorize(
        policy: AgentPolicy,
        capability: AgentCapability,
        toolName: String,
        toolCalls: Int,
        proposedSteps: Int,
        messages: List<AgentChatMessage>,
        consumedTokens: Long
    ) {
        when (val decision = policyEvaluator.evaluate(
            policy,
            AgentActionRequest(
                capability = capability,
                toolName = toolName,
                toolCallsSoFar = toolCalls,
                proposedWorkflowSteps = proposedSteps,
                promptCharacters = messages.sumOf { it.content.length },
                consumedTokens = consumedTokens
            )
        )) {
            AgentPolicyDecision.Allowed -> Unit
            is AgentPolicyDecision.ApprovalRequired -> throw WorkflowPlanningException(
                "AGENT_PLANNING_APPROVAL_FORBIDDEN",
                "A read-only planning tool unexpectedly requires approval: ${decision.reason}"
            )
            is AgentPolicyDecision.Denied -> throw WorkflowPlanningException(decision.code, decision.reason)
        }
    }

    private fun enforcePromptLimit(policy: AgentPolicy, messages: List<AgentChatMessage>) {
        if (messages.sumOf { it.content.length } > policy.maxPromptCharacters) {
            throw WorkflowPlanningException("AGENT_PROMPT_LIMIT", "Agent prompt exceeds the character limit.")
        }
    }

    private fun addTokens(current: Long, added: Long, policy: AgentPolicy): Long {
        val total = try {
            Math.addExact(current, added)
        } catch (_: ArithmeticException) {
            throw WorkflowPlanningException("AGENT_TOKEN_LIMIT", "Agent token usage overflowed its budget.")
        }
        if (total > policy.maxTokensPerRun) {
            throw WorkflowPlanningException("AGENT_TOKEN_LIMIT", "Agent token limit exceeded.")
        }
        return total
    }

    companion object {
        val WORKFLOW_PROPOSAL_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("required", JsonArray(listOf("workflow", "explanation").map(::JsonPrimitive)))
            put("properties", buildJsonObject {
                put("workflow", buildJsonObject { put("type", "object") })
                put("explanation", stringSchema())
                put("assumptions", stringArraySchema())
                put("warnings", stringArraySchema())
            })
        }

        private fun stringSchema() = buildJsonObject { put("type", "string"); put("minLength", 1) }
        private fun stringArraySchema() = buildJsonObject {
            put("type", "array"); put("items", stringSchema())
        }
    }
}

class WorkflowPlanningException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

private data class WorkflowProposalCandidate(
    val workflow: JsonObject,
    val explanation: String,
    val assumptions: List<String>,
    val warnings: List<String>,
    val raw: JsonObject
)

private fun parseCandidate(element: JsonElement): WorkflowProposalCandidate {
    val root = element as? JsonObject ?: throw WorkflowPlanningException(
        "AGENT_WORKFLOW_OUTPUT_INVALID",
        "Structured Workflow proposal must be a JSON object."
    )
    val allowed = setOf("workflow", "explanation", "assumptions", "warnings")
    (root.keys - allowed).firstOrNull()?.let {
        throw WorkflowPlanningException("AGENT_WORKFLOW_OUTPUT_INVALID", "Unknown Workflow proposal property: $it")
    }
    val workflow = root["workflow"] as? JsonObject ?: throw WorkflowPlanningException(
        "AGENT_WORKFLOW_OUTPUT_INVALID",
        "Structured Workflow proposal requires an object property named workflow."
    )
    return WorkflowProposalCandidate(
        workflow = workflow,
        explanation = root.requiredString("explanation"),
        assumptions = root.stringList("assumptions"),
        warnings = root.stringList("warnings"),
        raw = root
    )
}

private fun JsonObject.requiredString(name: String): String =
    (get(name) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.takeIf(String::isNotBlank)
        ?: throw WorkflowPlanningException("AGENT_WORKFLOW_OUTPUT_INVALID", "Workflow proposal $name must be a non-blank string.")

private fun JsonObject.stringList(name: String): List<String> = get(name)?.let { value ->
    val array = value as? JsonArray ?: throw WorkflowPlanningException(
        "AGENT_WORKFLOW_OUTPUT_INVALID",
        "Workflow proposal $name must be an array of strings."
    )
    array.mapIndexed { index, item ->
        (item as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.takeIf(String::isNotBlank)
            ?: throw WorkflowPlanningException(
                "AGENT_WORKFLOW_OUTPUT_INVALID",
                "Workflow proposal $name[$index] must be a non-blank string."
            )
    }
} ?: emptyList()

private fun WorkflowPlanningValidationResult.repairMessage(): String = buildJsonObject {
    put("instruction", "Correct the Workflow IR and return the complete structured proposal again.")
    put("validationErrors", buildJsonArray { errors.forEach { error -> add(buildJsonObject {
        put("path", error.path); put("message", error.message)
    }) } })
}.toString()

private fun String.requireGeneratedId(): String = also {
    require(it.isNotBlank() && it == it.trim()) { "Generated Agent proposal id must not be blank or padded." }
}
