package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.template.repository.MySqlTemplateRepository
import me.moiclathy.edb.core.template.repository.TemplateRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val templateModule = module {

    /*single<TemplateRepository> {

        MySqlTemplateRepository(
            get()
        )

    }*/

    singleOf(::MySqlTemplateRepository) {
        bind<TemplateRepository>()
    }

}