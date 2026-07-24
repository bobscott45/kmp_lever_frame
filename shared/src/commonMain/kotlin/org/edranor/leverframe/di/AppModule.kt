package org.edranor.leverframe.di

import org.edranor.leverframe.ConfigurationRepository
import org.edranor.leverframe.StatePersistenceRepository
import org.edranor.leverframe.ConfigManager
import org.edranor.leverframe.LccNetworkClient
import org.edranor.leverframe.LccNode
import org.edranor.leverframe.AppViewModel
import org.edranor.leverframe.NetworkEventProcessor
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<ConfigurationRepository> { ConfigManager }
    single<StatePersistenceRepository> { ConfigManager }
    single<LccNetworkClient> { LccNode }
    single { NetworkEventProcessor(get(), get()) }
    viewModelOf(::AppViewModel)
}
