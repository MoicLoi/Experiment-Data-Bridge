package me.moiclathy.edb.core.worker.docker.registry
import java.time.Instant
enum class DockerWorkerDeploymentStatus{PENDING,COMMITTED,ROLLBACK_REQUIRED}
data class DockerWorkerDeployment(val workerId:String,val registryProfileId:String,val repository:String,val tag:String,val image:String,val operationId:String?=null,val previousImage:String?=null,val status:DockerWorkerDeploymentStatus=DockerWorkerDeploymentStatus.COMMITTED,val failureMessage:String?=null,val startedAt:Instant?=null,val updatedAt:Instant=Instant.now())
interface DockerWorkerDeploymentRepository{fun find(workerId:String):DockerWorkerDeployment?;fun findRecoverable():List<DockerWorkerDeployment>;fun save(value:DockerWorkerDeployment):DockerWorkerDeployment;fun delete(workerId:String):Boolean}
