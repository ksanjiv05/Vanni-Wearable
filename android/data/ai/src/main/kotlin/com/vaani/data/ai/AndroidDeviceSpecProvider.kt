package com.vaani.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.vaani.domain.ai.DeviceSpec
import com.vaani.domain.ai.DeviceSpecProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the running device's ABI + total RAM into a [DeviceSpec] for the recommender. */
@Singleton
class AndroidDeviceSpecProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceSpecProvider {
    override fun current(): DeviceSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val totalRam = am?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem } ?: 0L
        return DeviceSpec(
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            totalRamBytes = totalRam,
        )
    }
}
