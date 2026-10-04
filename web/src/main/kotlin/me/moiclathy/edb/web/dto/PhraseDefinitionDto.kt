package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.workflow.phrase.ProjectPhraseDefinition
import me.moiclathy.edb.core.task.workflow.phrase.SubmitPhraseDefinitionCommand
import java.time.Duration

@Serializable data class SavePhraseDefinitionRequest(val definitionId:String,val document:JsonObject)
@Serializable data class PhraseDefinitionResponse(val id:Long,val projectId:Int,val definitionId:String,val version:Int,val document:JsonObject,val sha256:String,val createdBy:Int,val createdAt:String){companion object{fun from(value:ProjectPhraseDefinition)=PhraseDefinitionResponse(requireNotNull(value.id),value.projectId,value.definitionId,value.version,Json.parseToJsonElement(value.documentJson).jsonObject,value.sha256,value.createdBy,value.createdAt.toString())}}
@Serializable data class PhraseDefinitionPageResponse(val items:List<PhraseDefinitionResponse>,val page:Int,val size:Int,val total:Long)
@Serializable data class RenamePhraseDefinitionRequest(val newDefinitionId:String)
@Serializable data class SubmitPhraseDefinitionRequest(val version:Int,val expectedSha256:String,val context:Map<String,String> = emptyMap(),val idempotencyKey:String?=null,val priority:Int=TaskPriority.NORMAL.value,val retry:TaskRetryPolicyRequest=TaskRetryPolicyRequest(),val timeoutMs:Long?=null){fun toCommand(projectId:Int,definitionId:String,userId:Int)=SubmitPhraseDefinitionCommand(projectId,definitionId,version,expectedSha256,context,userId,idempotencyKey,TaskPriority(priority),retry.toDomain(),timeoutMs?.let(Duration::ofMillis))}
