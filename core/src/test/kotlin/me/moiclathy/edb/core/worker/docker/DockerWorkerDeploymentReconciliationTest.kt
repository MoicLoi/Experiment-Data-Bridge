package me.moiclathy.edb.core.worker.docker
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.worker.docker.registry.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
class DockerWorkerDeploymentReconciliationTest{
 private val base=DockerWorkerRuntimeConfig(image="edb/worker:old")
 private fun pending()=DockerWorkerDeployment("python","registry","edb/worker","new","registry/edb/worker:new","op","edb/worker:old",DockerWorkerDeploymentStatus.PENDING,null,Instant.now(),Instant.now())
 @Test fun `commits pending deployment when target container exists`()=runBlocking{val repo=mockk<DockerWorkerDeploymentRepository>();val fleet=mockk<DockerWorkerFleetLifecycle>(relaxed=true);val factory=mockk<DockerWorkerRuntimeFactory>();val session=mockk<DockerWorkerRebuildSession>(relaxed=true);val lifecycle=mockk<DockerWorkerLifecycle>(relaxed=true);every{repo.find("python")}returns pending();every{repo.findRecoverable()}returns listOf(pending());every{repo.save(any())}answers{firstArg()};every{factory.createRebuildSession()}returns session;every{factory.create(any())}returns lifecycle;every{session.findByName(base.containerName)}returns snapshot("registry/edb/worker:new");val service=service(repo,fleet,factory);val result=service.reconcile();assertEquals(DockerWorkerDeploymentStatus.COMMITTED,result.single().status);coVerify{fleet.replace(match{it.id=="python"})}}
 @Test fun `records previous image as committed when rollback already happened`()=runBlocking{val repo=mockk<DockerWorkerDeploymentRepository>();val fleet=mockk<DockerWorkerFleetLifecycle>(relaxed=true);val factory=mockk<DockerWorkerRuntimeFactory>();val session=mockk<DockerWorkerRebuildSession>(relaxed=true);every{repo.find("python")}returns pending();every{repo.findRecoverable()}returns listOf(pending());every{repo.save(any())}answers{firstArg()};every{factory.createRebuildSession()}returns session;every{factory.create(any())}returns mockk(relaxed=true);every{session.findByName(base.containerName)}returns snapshot("edb/worker:old");val result=service(repo,fleet,factory).reconcile().single();assertEquals("edb/worker:old",result.image);assertEquals(DockerWorkerDeploymentStatus.COMMITTED,result.status)}
 private fun service(repo:DockerWorkerDeploymentRepository,fleet:DockerWorkerFleetLifecycle,factory:DockerWorkerRuntimeFactory)=DockerWorkerRebuildApplicationService(mapOf("python" to base),mockk(relaxed=true),mockk(relaxed=true),repo,fleet,factory)
 private fun snapshot(image:String)=DockerWorkerContainerSnapshot("id",base.containerName,image,DockerJavaWorkerContainerGateway.managedLabels(base.workerKind),false)
}
