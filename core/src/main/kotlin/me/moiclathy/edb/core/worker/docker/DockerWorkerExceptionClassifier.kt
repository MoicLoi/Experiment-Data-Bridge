package me.moiclathy.edb.core.worker.docker

import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.exception.DockerClientException
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.exception.UnauthorizedException

object DockerWorkerExceptionClassifier {
    fun classify(operation: String, failure: Throwable): DockerWorkerException {
        if (failure is DockerWorkerException) return failure
        val category = when (failure) {
            is UnauthorizedException -> DockerWorkerFailureCategory.AUTHORIZATION
            is NotFoundException -> DockerWorkerFailureCategory.NOT_FOUND
            is ConflictException -> DockerWorkerFailureCategory.CONFLICT
            is DockerClientException -> DockerWorkerFailureCategory.CONNECTION
            is DockerException -> DockerWorkerFailureCategory.ENGINE
            else -> DockerWorkerFailureCategory.UNKNOWN
        }
        val retryable = when (failure) {
            is UnauthorizedException, is NotFoundException, is ConflictException -> false
            is DockerException -> failure.httpStatus == 429 || failure.httpStatus >= 500
            else -> true
        }
        return DockerWorkerOperationException(category, retryable, operation, failure)
    }
}
