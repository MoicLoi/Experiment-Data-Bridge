package me.moiclathy.edb.core.git

class GitHistoryService(

    private val repositoryService: GitRepositoryService

){
    fun history(

        projectId:Int,
        limit: Int = 50

    ):List<GitCommitInfo>{

        return repositoryService
            .open(projectId)
            .use { git ->

                val commits =

                    git.log()
                        .setMaxCount(limit) // 限制最大返回条数
                        .call()

                commits.map {
                    GitCommitInfo(
                        commitId = it.id.name(),
                        shortCommitId = it.id.name().take(7),
                        message = it.fullMessage,
                        author = it.authorIdent.name,
                        email = it.authorIdent.emailAddress, // 补上缺失的 email 参数
                        time = it.authorIdent.`when`.toInstant()
                    )
                }
            }
    }
}