package me.moiclathy.edb.core.project

//TODO 这个好像也是0.17重构完剩下的单独类
sealed class ProjectResult<out T> {


    data class Success<T>(
        val data:T
    ):ProjectResult<T>()



    object NotFound:
        ProjectResult<Nothing>()



    object Forbidden:
        ProjectResult<Nothing>()



    data class Failure(
        val message:String
    ):ProjectResult<Nothing>()

}