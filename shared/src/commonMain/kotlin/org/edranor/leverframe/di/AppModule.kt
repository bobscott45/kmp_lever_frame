/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * This project is dual-licensed to balance open-source collaboration with 
 * ecosystem compatibility:
 *
 * * Source Code: The source code in this repository is licensed under the 
 *   GNU General Public License v3 (GPLv3). You are free to copy, modify, 
 *   and self-compile the code, provided any distributions remain open-source 
 *   under the same terms.
 * * Compiled Binaries & Storefronts: As the sole copyright owner of this 
 *   codebase, the author reserves the right to distribute compiled binaries 
 *   (such as on the Apple App Store, Google Play, or other platforms) under 
 *   separate, proprietary, or storefront-specific licenses.
 *
 * Note: If you wish to contribute code to this project via a Pull Request, you 
 * agree to grant the author a non-exclusive, perpetual license to distribute 
 * your contributions under both the GPLv3 and our storefront distribution licenses.
 */
/**
 * Koin dependency injection module configuring the service locators and view model
 * provisioning for the core shared logic.
 */
package org.edranor.leverframe.di

import org.edranor.leverframe.ConfigurationRepository
import org.edranor.leverframe.StatePersistenceRepository
import org.edranor.leverframe.ConfigManager
import org.edranor.openlcb.LccNetworkClient
import org.edranor.leverframe.LccNode
import org.edranor.leverframe.AppViewModel
import org.edranor.leverframe.NetworkEventProcessor
import org.edranor.leverframe.services.ConfigurationService
import org.edranor.leverframe.services.InterlockingService
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<ConfigurationRepository> { ConfigManager }
    single<StatePersistenceRepository> { ConfigManager }
    single<LccNetworkClient> { LccNode }
    single { NetworkEventProcessor(get(), get()) }
    single { ConfigurationService(get()) }
    single { InterlockingService(get(), get(), get(), get(), get()) }
    single { org.edranor.leverframe.services.NxRoutingService(get(), get()) }
    viewModelOf(::AppViewModel)
}
