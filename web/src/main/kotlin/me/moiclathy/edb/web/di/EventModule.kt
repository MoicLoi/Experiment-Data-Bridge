package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.event.CoroutineEventBus
import me.moiclathy.edb.core.event.EventBus
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

val eventModule = module {
    single<EventBus> { CoroutineEventBus() } withOptions {
        onClose { eventBus -> eventBus?.close() }
    }
}
