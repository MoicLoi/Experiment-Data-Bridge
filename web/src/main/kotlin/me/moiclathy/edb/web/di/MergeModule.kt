package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.merge.MergeEngine
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mergeModule = module {

    singleOf(::MergeEngine)
}