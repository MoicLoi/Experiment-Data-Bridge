package me.moiclathy.edb.core.worker.docker

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.*

class DockerWorkerRebuildServiceTest {
    private val current= DockerWorkerRuntimeConfig(image="edb/worker:old",readinessTimeout=Duration.ofMillis(10),readinessPollInterval=Duration.ofMillis(1))
    private val target=current.copy(image="edb/worker:new")
    private val old= DockerWorkerContainerSnapshot("old-id",current.containerName,current.image,DockerJavaWorkerContainerGateway.managedLabels(current.workerKind),true)
    private val candidate= DockerWorkerContainerSnapshot("candidate-id","candidate",target.image,DockerJavaWorkerContainerGateway.managedLabels(target.workerKind),true)

    @Test fun `commits candidate only after health succeeds`()=runBlocking{
        val gateway=mockk<DockerWorkerContainerGateway>(relaxed=true)
        every{gateway.findByName(current.containerName)}returns old
        every{gateway.create(match{it.image==target.image&&it.containerName.contains("candidate")})}returns candidate.id
        val service= DockerWorkerRebuildService(gateway, DockerWorkerHealthClient{_,_->DockerWorkerHealth(true)})
        val result=service.rebuild("python",current,target)
        assertEquals(DockerWorkerRebuildStage.COMPLETED,result.stage);assertFalse(result.rolledBack)
        verifyOrder{gateway.pullImage(target.image,null);gateway.stop(old.id,any());gateway.create(any());gateway.start(candidate.id);gateway.remove(old.id,false);gateway.rename(candidate.id,current.containerName)}
    }

    @Test fun `removes failed candidate and restarts old container`()=runBlocking{
        val gateway=mockk<DockerWorkerContainerGateway>(relaxed=true)
        every{gateway.findByName(current.containerName)}returns old
        every{gateway.create(any())}returns candidate.id
        every{gateway.inspect(candidate.id)}returns candidate
        val service= DockerWorkerRebuildService(gateway, DockerWorkerHealthClient{_,_->throw IllegalStateException("bad health")})
        val failure=assertFailsWith<DockerWorkerRebuildException>{service.rebuild("python",current,target)}
        assertEquals(DockerWorkerRebuildStage.HEALTH_CHECK,failure.result.stage);assertTrue(failure.result.rolledBack)
        verify{gateway.stop(candidate.id,any());gateway.remove(candidate.id,true);gateway.start(old.id)}
        verify(exactly=0){gateway.remove(old.id,any())}
    }

    @Test fun `local image rebuild skips registry pull`()=runBlocking{
        val gateway=mockk<DockerWorkerContainerGateway>(relaxed=true)
        every{gateway.findByName(current.containerName)}returns old
        every{gateway.create(any())}returns candidate.id
        val service= DockerWorkerRebuildService(gateway, DockerWorkerHealthClient{_,_->DockerWorkerHealth(true)})
        val stages=mutableListOf<DockerWorkerRebuildStage>()
        val result=service.rebuild("python",current,target,pullImage=false,onStage=stages::add)
        assertEquals(DockerWorkerRebuildStage.COMPLETED,result.stage)
        assertFalse(DockerWorkerRebuildStage.PULL_IMAGE in stages)
        verify(exactly=0){gateway.pullImage(any(),any())}
    }
}
