package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.exception.InternalServerErrorException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerWorkerExceptionClassifierTest {
    @Test
    fun `classifies connection conflict and engine failures`() {
        val connection = DockerWorkerExceptionClassifier.classify("start", DockerClientException("offline"))
        val conflict = DockerWorkerExceptionClassifier.classify("create", ConflictException("name exists"))
        val engine = DockerWorkerExceptionClassifier.classify("logs", InternalServerErrorException("engine failed"))

        assertEquals(DockerWorkerFailureCategory.CONNECTION, connection.category)
        assertTrue(connection.retryable)
        assertEquals(DockerWorkerFailureCategory.CONFLICT, conflict.category)
        assertFalse(conflict.retryable)
        assertEquals(DockerWorkerFailureCategory.ENGINE, engine.category)
        assertTrue(engine.retryable)
    }
}
