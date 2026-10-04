package me.moiclathy.edb.core.worker.docker.registry
import me.moiclathy.edb.core.database.table.DockerWorkerDeploymentsTable as T
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
class MySqlDockerWorkerDeploymentRepository:DockerWorkerDeploymentRepository{
 override fun find(workerId:String)=transaction{T.selectAll().where{T.workerId eq workerId}.limit(1).singleOrNull()?.let(::map)}
 override fun findRecoverable()=transaction{T.selectAll().where{T.status neq DockerWorkerDeploymentStatus.COMMITTED.name}.map(::map)}
 override fun delete(workerId:String)=transaction{T.deleteWhere{T.workerId eq workerId}>0}
 override fun save(v:DockerWorkerDeployment)=transaction{val exists=T.selectAll().where{T.workerId eq v.workerId}.limit(1).any();if(exists)T.update({T.workerId eq v.workerId}){write(it,v)}else T.insert{write(it,v)};T.selectAll().where{T.workerId eq v.workerId}.single().let(::map)}
 private fun write(r:org.jetbrains.exposed.sql.statements.UpdateBuilder<*>,v:DockerWorkerDeployment){r[T.workerId]=v.workerId;r[T.registryProfileId]=v.registryProfileId;r[T.repository]=v.repository;r[T.tag]=v.tag;r[T.image]=v.image;r[T.operationId]=v.operationId;r[T.previousImage]=v.previousImage;r[T.status]=v.status.name;r[T.failureMessage]=v.failureMessage;r[T.startedAt]=v.startedAt;r[T.updatedAt]=v.updatedAt}
 private fun map(r:ResultRow)=DockerWorkerDeployment(r[T.workerId],r[T.registryProfileId],r[T.repository],r[T.tag],r[T.image],r[T.operationId],r[T.previousImage],DockerWorkerDeploymentStatus.valueOf(r[T.status]),r[T.failureMessage],r[T.startedAt],r[T.updatedAt])
}
