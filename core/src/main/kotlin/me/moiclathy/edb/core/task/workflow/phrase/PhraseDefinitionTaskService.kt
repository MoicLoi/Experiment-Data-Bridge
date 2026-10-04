package me.moiclathy.edb.core.task.workflow.phrase

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerRegistry
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.workflow.definition.*
import java.time.Duration

data class SubmitPhraseDefinitionCommand(val projectId:Int,val definitionId:String,val version:Int,val expectedSha256:String,val context:Map<String,String>,val requestedBy:Int,val idempotencyKey:String?=null,val priority:TaskPriority=TaskPriority.NORMAL,val retryPolicy:RetryPolicy=RetryPolicy.NONE,val timeout:Duration?=null)

class PhraseDefinitionTaskService(private val phrases:ProjectPhraseDefinitionService,private val workflowDefinitions:ProjectWorkflowDefinitionService,private val tasks:WorkflowDefinitionTaskApplicationService,private val handlers:AnalysisHandlerRegistry,private val json:Json=Json){
    suspend fun submit(command:SubmitPhraseDefinitionCommand):Task{
        val phrase=phrases.requireVersion(command.projectId,command.definitionId,command.version,command.requestedBy)
        require(phrase.sha256==command.expectedSha256){"Phrase definition SHA-256 does not match."}
        val workflow=compile(phrase)
        val definition=workflowDefinitions.create(command.projectId,json.encodeToString(JsonElement.serializer(),workflow),command.requestedBy,"phrase:${phrase.id}@${phrase.version}:${phrase.sha256}")
        val task=tasks.submit(SubmitWorkflowDefinitionTaskCommand(requireNotNull(definition.id),command.projectId,definition.version,definition.workflowSha256,command.context,command.requestedBy,command.idempotencyKey,command.priority,command.retryPolicy,command.timeout))
        phrases.recordSubmit(phrase,command.requestedBy,requireNotNull(task.id));return task
    }
    private fun compile(phrase:ProjectPhraseDefinition):JsonObject{
        val root=json.parseToJsonElement(phrase.documentJson).jsonObject;val actions=root["actions"]!!.jsonArray
        return buildJsonObject{put("schemaVersion",1);put("id",phrase.definitionId);put("version","${phrase.version}.0.0");put("description","Compiled from phrase definition ${phrase.definitionId}@${phrase.version}");put("inputs",JsonArray(emptyList()));put("outputs",JsonArray(emptyList()));put("steps",buildJsonArray{actions.forEachIndexed{index,element->val action=element.jsonObject;val kind=action.string("kind");val type=when(kind){"convert"->"datafile.convert";"analysis"->"datafile.analysis.run";"step"->action.string("stepType");else->error("Unsupported phrase action kind: $kind")};val rawParameters=action["parameters"] as? JsonObject?:JsonObject(emptyMap());val parameters=when(kind){"convert"->JsonObject(rawParameters+mapOf("targetFormat" to JsonPrimitive(action.string("target")),"sourceDataFileIdContext" to JsonPrimitive("sourceDataFileId")));"analysis"->buildJsonObject{put("handlerId",action.string("handlerId"));put("sourceDataFileIdsContext","sourceDataFileIds");rawParameters.forEach{(key,value)->put("parameter.$key",JsonPrimitive(if(value is JsonPrimitive&&value.isString)value.content else json.encodeToString(JsonElement.serializer(),value)))};val handler=handlers.find(action.string("handlerId"));if(action.string("mode")=="template"&&handler?.parameterContract?.id=="tabular-clean"){put("parameter.operation","tabular-clean");put("parameter.spec","{\"schemaVersion\":1,\"output\":{\"format\":\"XLSX\"},\"rules\":[{\"type\":\"trimStrings\"}]}")}};else->rawParameters};val id="${type.replace(Regex("[^A-Za-z0-9]+"),"-").trim('-').lowercase()}-${index+1}";add(buildJsonObject{put("id",id);put("type",type);put("version","1");put("order",index);put("dependsOn",if(index==0)JsonArray(emptyList()) else JsonArray(listOf(JsonPrimitive(previousId(actions,index-1)))));put("parameters",parameters);put("contextBindings",JsonArray(emptyList()));put("outputBindings",JsonArray(emptyList()));put("policy",buildJsonObject{put("timeoutMs",JsonNull);put("retry",buildJsonObject{put("maxAttempts",1);put("initialDelayMs",1000);put("backoffMultiplier",2.0);put("maxDelayMs",60000)});put("failureStrategy","STOP_WORKFLOW")})})}})}
    }
    private fun previousId(actions:JsonArray,index:Int):String{val action=actions[index].jsonObject;val type=when(action.string("kind")){"convert"->"datafile.convert";"analysis"->"datafile.analysis.run";else->action.string("stepType")};return "${type.replace(Regex("[^A-Za-z0-9]+"),"-").trim('-').lowercase()}-${index+1}"}
}
private fun JsonObject.string(name:String)=(get(name) as? JsonPrimitive)?.takeIf{it.isString}?.content?.takeIf{it.isNotBlank()}?:error("Phrase action requires '$name'.")
