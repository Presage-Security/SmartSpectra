#pragma once

#include <CoreGraphics/CoreGraphics.h>

namespace presage::smartspectra {
struct FrameBuffer;
}

// Copies a borrowed SDK frame into an opaque image. The caller owns the result;
// its pixels remain valid after the frame callback returns. Invalid layouts
// or failed conversions return nullptr.
CGImageRef CreatePreviewImage(const presage::smartspectra::FrameBuffer& frame)
    CF_RETURNS_RETAINED;
