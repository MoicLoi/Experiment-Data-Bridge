package me.moiclathy.edb.core.config.merge

class MergeEngine {

    fun merge(
        request: MergeRequest
    ): MergeResult {

        val conflicts = mutableListOf<MergeConflict>()

        val merged = mergeMaps(
            target = request.target,
            source = request.source,
            strategy = request.strategy,
            path = "",
            conflicts = conflicts
        )

        return MergeResult(
            merged = merged,
            conflicts = conflicts
        )
    }

    private fun mergeMaps(
        target: Map<String, Any?>,
        source: Map<String, Any?>,
        strategy: MergeStrategy,
        path: String,
        conflicts: MutableList<MergeConflict>
    ): Map<String, Any?> {

        val result = LinkedHashMap<String, Any?>()

        target.forEach { (key, value) ->
            result[key] = value
        }

        source.forEach { (key, sourceValue) ->

            val currentPath =
                if (path.isEmpty()) {
                    key
                } else {
                    "$path.$key"
                }

            if (!result.containsKey(key)) {

                result[key] = sourceValue
                return@forEach
            }

            val targetValue = result[key]

            val mergedValue =
                mergeValue(
                    targetValue = targetValue,
                    sourceValue = sourceValue,
                    strategy = strategy,
                    path = currentPath,
                    conflicts = conflicts
                )

            if (mergedValue.shouldWrite) {
                result[key] = mergedValue.value
            }
        }

        return result
    }

    private fun mergeValue(
        targetValue: Any?,
        sourceValue: Any?,
        strategy: MergeStrategy,
        path: String,
        conflicts: MutableList<MergeConflict>
    ): MergeValueResult {

        /*
         * 两边都是 Map：
         *
         * 递归进入下一层。
         */
        if (
            targetValue is Map<*, *> &&
            sourceValue is Map<*, *>
        ) {

            val targetMap =
                targetValue.entries.associate {
                    it.key.toString() to it.value
                }

            val sourceMap =
                sourceValue.entries.associate {
                    it.key.toString() to it.value
                }

            return MergeValueResult(
                value = mergeMaps(
                    target = targetMap,
                    source = sourceMap,
                    strategy = strategy,
                    path = path,
                    conflicts = conflicts
                ),
                shouldWrite = true
            )
        }


        /*
        * 两边都是 List。
        *
        * 当前 0.23.9 不进行自动拼接，
        * 避免数组重复以及顺序语义被破坏。
        */
        if (
            targetValue is List<*> &&
            sourceValue is List<*>
        ) {

            conflicts += MergeConflict(
                path = path,
                type = MergeConflictType.LIST_CONFLICT,
                targetValue = targetValue,
                sourceValue = sourceValue,
                message = "Both target and source contain a list."
            )

            return resolveConflict(
                targetValue = targetValue,
                sourceValue = sourceValue,
                strategy = strategy
            )
        }

        /*
         * 类型不同。
         */
        if (
            targetValue != null &&
            sourceValue != null &&
            targetValue::class != sourceValue::class
        ) {

            conflicts += MergeConflict(
                path = path,
                type = MergeConflictType.TYPE_MISMATCH,
                targetValue = targetValue,
                sourceValue = sourceValue,
                message = "Target and source values have different types."
            )

            return resolveConflict(
                targetValue = targetValue,
                sourceValue = sourceValue,
                strategy = strategy
            )
        }



        /*
         * 值完全相同，不构成冲突。
         */
        if (targetValue == sourceValue) {

            return MergeValueResult(
                value = targetValue,
                shouldWrite = true
            )
        }

        /*
         * 普通标量冲突。
         */
        conflicts += MergeConflict(
            path = path,
            type = MergeConflictType.VALUE_CONFLICT,
            targetValue = targetValue,
            sourceValue = sourceValue,
            message = "Target and source contain different values."
        )

        return resolveConflict(
            targetValue = targetValue,
            sourceValue = sourceValue,
            strategy = strategy
        )
    }

    private fun resolveConflict(
        targetValue: Any?,
        sourceValue: Any?,
        strategy: MergeStrategy
    ): MergeValueResult {

        return when (strategy) {

            MergeStrategy.SOURCE_WINS ->
                MergeValueResult(
                    value = sourceValue,
                    shouldWrite = true
                )

            MergeStrategy.TARGET_WINS ->
                MergeValueResult(
                    value = targetValue,
                    shouldWrite = true
                )

            MergeStrategy.RECURSIVE ->
                MergeValueResult(
                    value = targetValue,
                    shouldWrite = true
                )

            MergeStrategy.FAIL_ON_CONFLICT ->
                MergeValueResult(
                    value = targetValue,
                    shouldWrite = true
                )
        }
    }

    private data class MergeValueResult(

        val value: Any?,

        val shouldWrite: Boolean
    )
}