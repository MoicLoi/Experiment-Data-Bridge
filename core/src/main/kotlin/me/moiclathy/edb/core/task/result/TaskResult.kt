package me.moiclathy.edb.core.task.result

data class TaskResult(
    val outputPath: String? = null,
    val contentType: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(outputPath == null || outputPath.length <= MAX_OUTPUT_PATH_LENGTH) {
            "Task result output path must not exceed $MAX_OUTPUT_PATH_LENGTH characters."
        }
        require(contentType == null || contentType.length <= MAX_CONTENT_TYPE_LENGTH) {
            "Task result content type must not exceed $MAX_CONTENT_TYPE_LENGTH characters."
        }
    }

    companion object {
        const val MAX_OUTPUT_PATH_LENGTH = 500
        const val MAX_CONTENT_TYPE_LENGTH = 100
    }
}
