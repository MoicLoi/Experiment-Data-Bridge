package me.moiclathy.edb.core.git

class GitDiffService(

    private val loader: GitFileLoader

){
    fun diff(

        projectId: Int,

        oldCommit: String,

        newCommit: String

    ): GitDiffResult {

        val oldContent =
            loader.loadConfig(
                projectId,
                oldCommit
            )


        val newContent =
            loader.loadConfig(
                projectId,
                newCommit
            )



        return GitDiffResult(

            oldCommit = oldCommit,

            newCommit = newCommit,

            oldContent = oldContent,

            newContent = newContent

        )

    }



    /*fun compare(

        projectId:Int,

        from:String,

        to:String

    ){

    }*/
}