// ExpressionTypeExtensions.kt
// Copyright (C) 2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

package com.presagetech.smartspectra_example.util

import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType

/**
 * Human-readable display name for each expression type.
 * Returns null for UNSPECIFIED or UNRECOGNIZED types.
 */
val ExpressionType.displayName: String?
    get() = when (this) {
        ExpressionType.ANGRY -> "Angry"
        ExpressionType.CONTEMPT -> "Contempt"
        ExpressionType.DISGUST -> "Disgust"
        ExpressionType.FEAR -> "Fear"
        ExpressionType.HAPPY -> "Happy"
        ExpressionType.NEUTRAL -> "Neutral"
        ExpressionType.SAD -> "Sad"
        ExpressionType.SURPRISE -> "Surprise"
        else -> null
    }
