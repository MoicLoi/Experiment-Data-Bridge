package me.moiclathy.edb.agent.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.model.AgentProposalValidationError
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.provider.AgentToolDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowValidationException
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowStepParameterValidationException

data class WorkflowPlanningValidationResult(
    val valid: Boolean,
    val canonicalWorkflowIrJson: String? = null,
    val executionOrder: List<String> = emptyList(),
    val errors: List<AgentProposalValidationError> = emptyList()
)

/** Read-only bridge from an Agent to the existing Workflow application boundaries. */
class WorkflowPlanningTools(
    private val catalog: WorkflowStepCatalogService,
    private val workflowService: WorkflowIrApplicationService,
    private val codec: WorkflowIrCodec
) {
    fun definitions(): List<AgentToolDefinition> = TOOL_DEFINITIONS

    fun capability(toolName: String): AgentCapability = when (toolName) {
        LIST_STEPS, GET_STEP_SCHEMA -> AgentCapability.DISCOVER_STEPS
        VALIDATE_WORKFLOW -> AgentCapability.VALIDATE_WORKFLOW
        else -> throw WorkflowPlanningToolException("AGENT_TOOL_NOT_FOUND", "Unknown Workflow planning tool: $toolName")
    }

    fun execute(call: AgentRequestedToolCall): JsonObject = try {
        when (call.name) {
            LIST_STEPS -> listSteps(call.arguments)
            GET_STEP_SCHEMA -> getStepSchema(call.arguments)
            VALIDATE_WORKFLOW -> validateTool(call.arguments)
            else -> throw WorkflowPlanningToolException("AGENT_TOOL_NOT_FOUND", "Unknown Workflow planning tool: ${call.name}")
        }
    } catch (failure: WorkflowPlanningToolException) {
        failure.toJson()
    } catch (failure: Exception) {
        buildJsonObject {
            put("ok", false)
            put("error", failure.message ?: "Workflow planning tool failed.")
        }
    }

    fun validate(workflow: JsonElement): WorkflowPlanningValidationResult {
        val document = workflow.toString()
        return try {
            val compilation = workflowService.validate(document)
            val canonical = codec.serialize(codec.deserialize(document))
            WorkflowPlanningValidationResult(
                valid = true,
                canonicalWorkflowIrJson = canonical,
                executionOrder = compilation.executionOrder
            )
        } catch (failure: Exception) {
            WorkflowPlanningValidationResult(valid = false, errors = failure.validationErrors())
        }
    }

    private fun listSteps(arguments: JsonObject): JsonObject {
        arguments.requireOnly(emptySet(), LIST_STEPS)
        return buildJsonObject {
            put("ok", true)
            put("steps", buildJsonArray {
                catalog.list().forEach { entry ->
                    add(buildJsonObject {
                        put("type", entry.type)
                        put("version", entry.version)
                        put("source", entry.source.name)
                        put("title", entry.title)
                        entry.description?.let { put("description", it) }
                        put("schemaAvailable", entry.schemaAvailable)
                    })
                }
            })
        }
    }

    private fun getStepSchema(arguments: JsonObject): JsonObject {
        arguments.requireOnly(setOf("type", "version"), GET_STEP_SCHEMA)
        val type = arguments.requiredString("type", GET_STEP_SCHEMA)
        val version = arguments.optionalString("version", GET_STEP_SCHEMA) ?: WorkflowStepCatalogService.DEFAULT_VERSION
        return buildJsonObject {
            put("ok", true)
            put("type", type)
            put("version", version)
            put("schema", catalog.requireSchema(type, version))
        }
    }

    private fun validateTool(arguments: JsonObject): JsonObject {
        arguments.requireOnly(setOf("workflow"), VALIDATE_WORKFLOW)
        val workflow = arguments["workflow"] ?: throw WorkflowPlanningToolException(
            "AGENT_TOOL_ARGUMENT_INVALID",
            "$VALIDATE_WORKFLOW requires a workflow property."
        )
        return validate(workflow).toJson()
    }

    companion object {
        const val LIST_STEPS = "listWorkflowSteps"
        const val GET_STEP_SCHEMA = "getWorkflowStepSchema"
        const val VALIDATE_WORKFLOW = "validateWorkflowIr"

        private val TOOL_DEFINITIONS = listOf(
            AgentToolDefinition(
                LIST_STEPS,
                "List Workflow step types and versions available to the current EDB runtime.",
                objectSchema(emptyMap(), emptyList())
            ),
            AgentToolDefinition(
                GET_STEP_SCHEMA,
                "Get the strict JSON Schema for one Workflow step type and version.",
                objectSchema(
                    mapOf(
                        "type" to stringSchema(),
                        "version" to stringSchema()
                    ),
                    listOf("type")
                )
            ),
            AgentToolDefinition(
                VALIDATE_WORKFLOW,
                "Strictly validate and compile-preview a complete Workflow IR without registering or executing it.",
                objectSchema(
                    mapOf("workflow" to buildJsonObject { put("type", "object") }),
                    listOf("workflow")
                )
            )
        )

        private fun stringSchema() = buildJsonObject { put("type", "string"); put("minLength", 1) }

        private fun objectSchema(properties: Map<String, JsonObject>, required: List<String>) = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("additionalProperties", false)
            if (required.isNotEmpty()) put("required", JsonArray(required.map(::JsonPrimitive)))
        }
    }
}

class WorkflowPlanningToolException(val code: String, override val message: String) : IllegalArgumentException(message) {
    fun toJson() = buildJsonObject { put("ok", false); put("code", code); put("error", message) }
}

private fun WorkflowPlanningValidationResult.toJson() = buildJsonObject {
    put("ok", valid)
    put("valid", valid)
    canonicalWorkflowIrJson?.let { put("canonicalWorkflowIr", it) }
    put("executionOrder", buildJsonArray { executionOrder.forEach { add(JsonPrimitive(it)) } })
    put("errors", buildJsonArray { errors.forEach { error -> add(buildJsonObject {
        put("path", error.path); put("message", error.message)
    }) } })
}

private fun JsonObject.requireOnly(allowed: Set<String>, tool: String) {
    (keys - allowed).sorted().firstOrNull()?.let {
        throw WorkflowPlanningToolException("AGENT_TOOL_ARGUMENT_INVALID", "$tool received unknown argument: $it")
    }
}

private fun JsonObject.requiredString(name: String, tool: String): String =
    optionalString(name, tool) ?: throw WorkflowPlanningToolException(
        "AGENT_TOOL_ARGUMENT_INVALID",
        "$tool requires a non-blank string argument: $name"
    )

private fun JsonObject.optionalString(name: String, tool: String): String? = get(name)?.let {
    (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.takeIf(String::isNotBlank)
        ?: throw WorkflowPlanningToolException("AGENT_TOOL_ARGUMENT_INVALID", "$tool argument $name must be a non-blank string.")
}

private fun Throwable.validationErrors(): List<AgentProposalValidationError> {
    val chain = generateSequence(this) { it.cause }.toList()
    chain.filterIsInstance<WorkflowValidationException>().firstOrNull()?.let { validation ->
        return validation.errors.map { AgentProposalValidationError(it.path, it.message) }
    }
    chain.filterIsInstance<WorkflowStepParameterValidationException>().firstOrNull()?.let { validation ->
        return validation.violations.map {
            AgentProposalValidationError("$.steps[${validation.stepId}].parameters${it.instancePath}", it.message)
        }
    }
    return listOf(AgentProposalValidationError("$", message ?: "Workflow IR validation failed."))
}
