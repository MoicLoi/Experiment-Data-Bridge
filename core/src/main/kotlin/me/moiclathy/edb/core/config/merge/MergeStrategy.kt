package me.moiclathy.edb.core.config.merge

enum class MergeStrategy {

    /**
     * 当两个 Map 都存在同一个 key 时继续递归合并。
     */
    RECURSIVE,

    /**
     * 发生冲突时使用 source 值。
     */
    SOURCE_WINS,

    /**
     * 发生冲突时使用 target 值。
     */
    TARGET_WINS,

    /**
     * 发现冲突后停止并返回冲突结果。
     */
    FAIL_ON_CONFLICT
}