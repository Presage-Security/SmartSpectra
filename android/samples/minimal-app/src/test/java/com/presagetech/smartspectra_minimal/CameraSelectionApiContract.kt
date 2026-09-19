// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_minimal

import android.content.Context
import com.presagetech.smartspectra.CameraInfo
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.SmartSpectraSdk

// Compile a consumer against the public SDK with no access to internal config.
internal suspend fun cameraSelectionApiContract(context: Context, sdk: SmartSpectraSdk): List<CameraInfo> {
    val cameras = SmartSpectraSdk.availableCameras(context)
    sdk.useCamera()
    sdk.config.cameraPosition = CameraPosition.BACK
    sdk.useCamera(CameraSelection.Default)
    sdk.useCamera(CameraSelection.Front)
    sdk.useCamera(CameraSelection.Back)
    cameras.firstOrNull()?.let { sdk.useCamera(CameraSelection.ById(it.id)) }
    return cameras
}
