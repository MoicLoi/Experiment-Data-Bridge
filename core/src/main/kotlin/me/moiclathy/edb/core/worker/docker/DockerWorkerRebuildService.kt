package me.moiclathy.edb.core.worker.docker

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import kotlin.math.ceil

enum class DockerWorkerRebuildStage { PRECHECK, PULL_IMAGE, STOP_OLD, CREATE_CANDIDATE, START_CANDIDATE, HEALTH_CHECK, COMMIT, ROLLBACK, COMPLETED, FAILED }
data class DockerWorkerRebuildResult(val workerId:String,val previousImage:String?,val targetImage:String,val stage:DockerWorkerRebuildStage,val rolledBack:Boolean,val containerId:String?,val completedAt:Instant,val failure:String?=null)
class DockerWorkerRebuildException(val result:DockerWorkerRebuildResult,cause:Throwable):IllegalStateException("Docker worker rebuild failed at ${result.stage}: ${result.failure}",cause)

class DockerWorkerRebuildService(private val containers:DockerWorkerContainerGateway,private val health:DockerWorkerHealthClient=HttpDockerWorkerHealthClient(),private val clock:Clock=Clock.systemUTC()){
 private val mutex=Mutex()
 suspend fun rebuild(workerId:String,current:DockerWorkerRuntimeConfig,target:DockerWorkerRuntimeConfig,credential:DockerRegistryCredential?=null,pullImage:Boolean=true,onStage:(DockerWorkerRebuildStage)->Unit={}):DockerWorkerRebuildResult=mutex.withLock{
  require(workerId.isNotBlank());require(current.containerName==target.containerName){"A rebuild cannot change the canonical container name."};require(current.workerKind==target.workerKind){"A rebuild cannot change worker kind."}
  val old=containers.findByName(current.containerName);old?.let{require(it.labels[DockerJavaWorkerContainerGateway.MANAGED_LABEL]=="true"){"Existing container is not managed by EDB."}}
  var candidateId:String?=null;var oldStopped=false;var stage=DockerWorkerRebuildStage.PRECHECK;onStage(stage)
  try{
   if(pullImage){stage=DockerWorkerRebuildStage.PULL_IMAGE;onStage(stage);containers.pullImage(target.image,credential)}
   if(old?.running==true){stage=DockerWorkerRebuildStage.STOP_OLD;onStage(stage);containers.stop(old.id,timeout(current));oldStopped=true}
   val candidate=target.copy(containerName="${target.containerName}-candidate-${clock.instant().epochSecond}")
   stage=DockerWorkerRebuildStage.CREATE_CANDIDATE;onStage(stage);candidateId=containers.create(candidate)
   stage=DockerWorkerRebuildStage.START_CANDIDATE;onStage(stage);containers.start(candidateId)
   stage=DockerWorkerRebuildStage.HEALTH_CHECK;onStage(stage);awaitHealthy(target)
   stage=DockerWorkerRebuildStage.COMMIT;onStage(stage);old?.let{containers.remove(it.id)};containers.rename(candidateId,target.containerName);onStage(DockerWorkerRebuildStage.COMPLETED)
   DockerWorkerRebuildResult(workerId,old?.image,target.image,DockerWorkerRebuildStage.COMPLETED,false,candidateId,clock.instant())
  }catch(failure:Throwable){
   val failedAt=stage;onStage(DockerWorkerRebuildStage.ROLLBACK);var rolledBack=false;val rollbackFailures=mutableListOf<Throwable>()
   runCatching{candidateId?.let{containers.inspect(it)?.takeIf(DockerWorkerContainerSnapshot::running)?.let{running->containers.stop(running.id,timeout(target))};containers.remove(it,true)}}.exceptionOrNull()?.let(rollbackFailures::add)
   if(old!=null&&oldStopped)runCatching{containers.start(old.id);rolledBack=true}.exceptionOrNull()?.let(rollbackFailures::add)
   rollbackFailures.forEach(failure::addSuppressed)
   onStage(DockerWorkerRebuildStage.FAILED);throw DockerWorkerRebuildException(DockerWorkerRebuildResult(workerId,old?.image,target.image,failedAt,rolledBack,old?.id,clock.instant(),failure.message),failure)
  }
 }
 private suspend fun awaitHealthy(config:DockerWorkerRuntimeConfig){val deadline=clock.instant().plus(config.readinessTimeout);while(true){val result=health.check(config.healthEndpoint,config.healthRequestTimeout);if(result.healthy)return;if(!clock.instant().isBefore(deadline))throw DockerWorkerReadinessException(result.detail?:"Candidate worker readiness timeout.",true);delay(config.readinessPollInterval.toMillis())}}
 private fun timeout(config:DockerWorkerRuntimeConfig)=ceil(config.stopTimeout.toMillis()/1000.0).toInt().coerceAtLeast(1)
}
