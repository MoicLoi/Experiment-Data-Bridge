package me.moiclathy.edb.core.worker.docker

import me.moiclathy.edb.core.worker.docker.registry.*
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

data class DockerWorkerRebuildPreview(val workerId:String,val currentImage:String,val targetImage:String,val registryProfileId:String,val credentialConfigured:Boolean,val localImage:Boolean,val confirmationToken:String)
data class DockerWorkerRebuildProgress(val workerId:String,val stage:DockerWorkerRebuildStage,val targetImage:String,val startedAt:Instant,val updatedAt:Instant,val result:DockerWorkerRebuildResult?=null)

class DockerWorkerRebuildApplicationService(
 private val baseConfigs:Map<String,DockerWorkerRuntimeConfig>,private val profiles:DockerRegistryProfileService,private val credentials:DockerRegistryCredentialResolver,
 private val deployments:DockerWorkerDeploymentRepository,private val fleet:DockerWorkerFleetLifecycle,private val factory:DockerWorkerRuntimeFactory
){
 private val current=ConcurrentHashMap(baseConfigs.mapValues{(id,c)->deployments.find(id)?.let{c.copy(image=it.image)}?:c});private val progress=ConcurrentHashMap<String,DockerWorkerRebuildProgress>()
 fun preview(workerId:String,profileId:String,repository:String,tag:String,useLocalImage:Boolean=false):DockerWorkerRebuildPreview{val config=requireConfig(workerId);val profile=if(useLocalImage)null else profiles.find(profileId);val image=profile?.let{image(it,repository,tag)}?:localImage(repository,tag);return DockerWorkerRebuildPreview(workerId,config.image,image,if(useLocalImage) LOCAL_PROFILE else profileId,profile?.credentialRef?.let(credentials::resolve)!=null,useLocalImage,token(workerId,config.image,image,if(useLocalImage) LOCAL_PROFILE else profileId,useLocalImage.toString()))}
 fun progress(workerId:String)=progress[workerId]
 suspend fun rebuild(workerId:String,profileId:String,repository:String,tag:String,confirmationToken:String,useLocalImage:Boolean=false):DockerWorkerRebuildResult{
  val preview=preview(workerId,profileId,repository,tag,useLocalImage);require(preview.confirmationToken==confirmationToken){"Rebuild confirmation token is stale or invalid."};val source=requireConfig(workerId);val target=source.copy(image=preview.targetImage);val profile=if(useLocalImage)null else profiles.find(profileId)
  val credential=profile?.credentialRef?.let(credentials::resolve)?.let{DockerRegistryCredential(URI(profile.registryUrl).authority,it.username,it.password)}
  val deploymentProfile=if(useLocalImage) LOCAL_PROFILE else profileId;val started=Instant.now();val previous=deployments.find(workerId);val pending=DockerWorkerDeployment(workerId,deploymentProfile,repository,tag,target.image,UUID.randomUUID().toString(),source.image,DockerWorkerDeploymentStatus.PENDING,null,started,started);deployments.save(pending);progress[workerId]=DockerWorkerRebuildProgress(workerId,DockerWorkerRebuildStage.PRECHECK,target.image,started,started)
  try{factory.createRebuildSession().use{session->val result=session.service.rebuild(workerId,source,target,credential,pullImage=!useLocalImage){stage->progress[workerId]=DockerWorkerRebuildProgress(workerId,stage,target.image,started,Instant.now())};deployments.save(pending.copy(status=DockerWorkerDeploymentStatus.COMMITTED,updatedAt=Instant.now()));current[workerId]=target;fleet.replace(DockerWorkerFleetMember(workerId,factory.create(target)));progress[workerId]=DockerWorkerRebuildProgress(workerId,result.stage,target.image,started,Instant.now(),result);return result}}
  catch(failure:DockerWorkerRebuildException){if(failure.result.rolledBack){if(previous==null)deployments.delete(workerId)else deployments.save(previous);current[workerId]=source}else deployments.save(pending.copy(status=DockerWorkerDeploymentStatus.ROLLBACK_REQUIRED,failureMessage=failure.message,updatedAt=Instant.now()));throw failure}
 }
 suspend fun reconcile():List<DockerWorkerDeployment>{val repaired=mutableListOf<DockerWorkerDeployment>();factory.createRebuildSession().use{session->deployments.findRecoverable().forEach{record->val base=baseConfigs[record.workerId]?:return@forEach;val actual=session.findByName(base.containerName);val resolved=when{actual?.image==record.image->record.copy(status=DockerWorkerDeploymentStatus.COMMITTED,failureMessage=null,updatedAt=Instant.now());record.previousImage!=null&&(actual==null||actual.image==record.previousImage)->record.copy(image=record.previousImage,status=DockerWorkerDeploymentStatus.COMMITTED,failureMessage=null,updatedAt=Instant.now());else->record.copy(status=DockerWorkerDeploymentStatus.ROLLBACK_REQUIRED,failureMessage="Container image '${actual?.image}' matches neither target nor previous image.",updatedAt=Instant.now())};deployments.save(resolved);if(resolved.status==DockerWorkerDeploymentStatus.COMMITTED){val runtime=base.copy(image=resolved.image);current[record.workerId]=runtime;fleet.replace(DockerWorkerFleetMember(record.workerId,factory.create(runtime)))};repaired+=resolved}};return repaired}
 fun configured()=current.toMap()
 private fun requireConfig(id:String)=current[id]?:throw DockerWorkerFleetMemberNotFoundException(id)
 private fun image(p:DockerRegistryProfile,r:String,t:String):String{require(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,499}").matches(r));require(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,159}").matches(t));val host=URI(p.registryUrl).authority;return listOfNotNull(host,p.namespace,r).joinToString("/")+":$t"}
 private fun localImage(r:String,t:String):String{require(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,499}").matches(r));require(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,159}").matches(t));return "$r:$t"}
 private fun token(vararg values:String)=MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u0000").toByteArray()).joinToString(""){"%02x".format(it)}
 companion object{const val LOCAL_PROFILE="local-engine"}
}
