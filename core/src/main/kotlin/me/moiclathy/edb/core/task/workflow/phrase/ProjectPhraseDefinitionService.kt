package me.moiclathy.edb.core.task.workflow.phrase

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.service.AuditService
import java.security.MessageDigest
import java.time.Clock

data class PhraseDefinitionPage(val items:List<ProjectPhraseDefinition>,val page:Int,val size:Int,val total:Long)
class ProjectPhraseDefinitionService(private val repository: ProjectPhraseDefinitionRepository,private val projects: ProjectRepository,private val permissions: PermissionChecker,private val audit:AuditService,private val clock: Clock=Clock.systemUTC(),private val json: Json=Json) {
    fun create(projectId:Int,definitionId:String,document:JsonObject,userId:Int):ProjectPhraseDefinition {
        require(DEFINITION_ID.matches(definitionId)) { "Invalid phrase definition id." }; requireAccess(projectId,userId,true)
        require((document["schemaVersion"] as? JsonPrimitive)?.intOrNull==1 && document["actions"] is JsonArray && (document["actions"] as JsonArray).isNotEmpty()) { "Phrase document must be schemaVersion 1 with actions." }
        val canonical=json.encodeToString(JsonElement.serializer(),document);val hash=canonical.sha256();var lastFailure:Exception?=null
        repeat(3){val latest=repository.findLatest(projectId,definitionId);if(latest?.sha256==hash)return latest;try{val saved=repository.save(ProjectPhraseDefinition(projectId=projectId,definitionId=definitionId,version=(latest?.version?:0)+1,documentJson=canonical,sha256=hash,createdBy=userId,createdAt=clock.instant()));audit.record(userId,"PHRASE_DEFINITION_CREATE","PHRASE_DEFINITION",saved.id?.toInt(),"projectId=$projectId,definitionId=$definitionId,version=${saved.version},sha256=$hash");return saved}catch(failure:Exception){lastFailure=failure;repository.findLatest(projectId,definitionId)?.takeIf{it.sha256==hash}?.let{return it}}}
        throw checkNotNull(lastFailure)
    }
    fun list(projectId:Int,userId:Int,page:Int,size:Int):PhraseDefinitionPage{require(page>=0&&size in 1..100);requireAccess(projectId,userId,false);return PhraseDefinitionPage(repository.list(projectId,page.toLong()*size,size),page,size,repository.count(projectId))}
    fun latest(projectId:Int,definitionId:String,userId:Int):ProjectPhraseDefinition?{requireAccess(projectId,userId,false);return repository.findLatest(projectId,definitionId)}
    fun requireVersion(projectId:Int,definitionId:String,version:Int,userId:Int):ProjectPhraseDefinition{requireAccess(projectId,userId,false);return repository.find(projectId,definitionId,version)?:throw IllegalArgumentException("Phrase definition version was not found: $definitionId@$version")}
    fun rename(projectId:Int,definitionId:String,newDefinitionId:String,userId:Int):Int{require(DEFINITION_ID.matches(newDefinitionId));requireAccess(projectId,userId,true);require(repository.findLatest(projectId,newDefinitionId)==null){"Target phrase definition id already exists."};val changed=repository.rename(projectId,definitionId,newDefinitionId);require(changed>0){"Phrase definition was not found."};audit.record(userId,"PHRASE_DEFINITION_RENAME","PHRASE_DEFINITION",projectId,"from=$definitionId,to=$newDefinitionId,versions=$changed");return changed}
    fun delete(projectId:Int,definitionId:String,version:Int,userId:Int){requireAccess(projectId,userId,true);check(repository.delete(projectId,definitionId,version)){"Phrase definition version was not found."};audit.record(userId,"PHRASE_DEFINITION_DELETE","PHRASE_DEFINITION",projectId,"definitionId=$definitionId,version=$version")}
    fun recordSubmit(value:ProjectPhraseDefinition,userId:Int,taskId:Int){audit.record(userId,"PHRASE_DEFINITION_SUBMIT","PHRASE_DEFINITION",value.id?.toInt(),"projectId=${value.projectId},definitionId=${value.definitionId},version=${value.version},sha256=${value.sha256},taskId=$taskId")}
    private fun requireAccess(projectId:Int,userId:Int,write:Boolean){val project=projects.findById(projectId)?:throw ProjectNotFoundException(projectId);val allowed=if(write)permissions.requireWritable(userId,project) else permissions.requireReadable(userId,project);if(!allowed)throw PermissionDeniedException()}
    private companion object{val DEFINITION_ID=Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,149}$")}
}
private fun String.sha256()=MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString(""){"%02x".format(it)}
