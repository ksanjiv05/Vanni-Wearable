package com.vaani.data.device.di

import com.vaani.data.device.DeviceLinkImpl
import com.vaani.domain.device.DeviceLink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds
    @Singleton
    abstract fun bindDeviceLink(impl: DeviceLinkImpl): DeviceLink
}
