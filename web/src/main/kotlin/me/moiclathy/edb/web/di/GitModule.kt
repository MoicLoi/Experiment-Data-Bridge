package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.git.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val gitModule = module {

    single {
        GitRepositoryService(
            customRootDirectory = null
        )
    }
    singleOf(::GitCommitService)
    singleOf(::GitHistoryService)
    singleOf(::GitFileLoader)
    singleOf(::GitPreviewService)
    singleOf(::GitDiffService)
    singleOf(::GitRestoreService)
}