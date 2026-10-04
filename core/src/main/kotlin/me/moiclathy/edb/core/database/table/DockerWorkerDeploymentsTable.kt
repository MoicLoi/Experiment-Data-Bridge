package me.moiclathy.edb.core.database.table
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
object DockerWorkerDeploymentsTable:Table("docker_worker_deployments"){val workerId=varchar("worker_id",100);val registryProfileId=varchar("registry_profile_id",100);val repository=varchar("repository_name",500);val tag=varchar("image_tag",160);val image=varchar("committed_image",800);val operationId=varchar("operation_id",36).nullable();val previousImage=varchar("previous_image",800).nullable();val status=varchar("deployment_status",32);val failureMessage=text("failure_message").nullable();val startedAt=timestamp("started_at").nullable();val updatedAt=timestamp("updated_at");override val primaryKey=PrimaryKey(workerId)}
