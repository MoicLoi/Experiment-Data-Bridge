package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogEntry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Serializable
data class WorkflowStepCatalogResponse(
    val items: List<WorkflowStepCatalogItemResponse>
)

@Serializable
data class WorkflowStepCatalogItemResponse(
    val type: String,
    val version: String,
    val source: String,
    val title: String,
    val description: String?,
    val schemaAvailable: Boolean,
    val schemaUrl: String?,
    val capabilities: List<WorkflowStepCapabilityResponse>,
    val phrasePatterns: List<WorkflowPhrasePatternResponse>
) {
    companion object {
        fun from(entry: WorkflowStepCatalogEntry) = WorkflowStepCatalogItemResponse(
            type = entry.type,
            version = entry.version,
            source = entry.source.name,
            title = entry.title,
            description = entry.description,
            schemaAvailable = entry.schemaAvailable,
            schemaUrl = if (entry.schemaAvailable) {
                "/api/v1/workflow-steps/${URLEncoder.encode(entry.type, StandardCharsets.UTF_8)}/schema?version=" +
                    URLEncoder.encode(entry.version, StandardCharsets.UTF_8)
            } else null,
            capabilities = entry.capabilities.map(WorkflowStepCapabilityResponse::from),
            phrasePatterns = entry.phrasePatterns.map(WorkflowPhrasePatternResponse::from)
        )
    }
}

@Serializable
data class WorkflowPhrasePatternResponse(val id:String,val stepType:String,val stepVersion:String,val tokens:List<WorkflowPhraseTokenResponse>){
    companion object { fun from(value:me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePattern)=WorkflowPhrasePatternResponse(value.id,value.stepType,value.stepVersion,value.tokens.map(WorkflowPhraseTokenResponse::from)) }
}

@Serializable
data class WorkflowPhraseTokenResponse(val type:String,val name:String?,val i18nKey:String?,val source:String?,val optional:Boolean){
    companion object { fun from(value:me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhraseToken)=WorkflowPhraseTokenResponse(value.type.name,value.name,value.i18nKey,value.source,value.optional) }
}

@Serializable
data class WorkflowStepCapabilityResponse(
    val id: String,
    val titleKey: String,
    val descriptionKey: String?,
    val cliPath: List<String>,
    val sourceType: String,
    val sourceId: String,
    val sourceVersion: String,
    val phrasePatterns: List<WorkflowPhrasePatternResponse>
) {
    companion object {
        fun from(value: me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCapabilityEntry) =
            WorkflowStepCapabilityResponse(value.id, value.titleKey, value.descriptionKey, value.cliPath, value.sourceType, value.sourceId, value.sourceVersion, value.phrasePatterns.map(WorkflowPhrasePatternResponse::from))
    }
}
