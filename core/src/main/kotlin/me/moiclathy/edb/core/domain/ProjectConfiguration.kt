package me.moiclathy.edb.core.domain

data class ProjectConfiguration(

    val yaml: String

){
    // 后续可以在这里扩展方法，例如：
    // fun toMap(): Map<String, Any> { ... }
}