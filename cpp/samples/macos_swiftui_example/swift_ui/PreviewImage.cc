#include "PreviewImage.h"

#include <Accelerate/Accelerate.h>
#include <smartspectra/smartspectra_types.h>

#include <limits>
#include <array>
#include <vector>

namespace {

using presage::smartspectra::FrameBuffer;
using presage::smartspectra::PixelFormat;

bool ValidLayout(const FrameBuffer& frame) {
    if (!frame.data || frame.width <= 0 || frame.height <= 0 || frame.stride_bytes <= 0) {
        return false;
    }
    size_t bytes_per_pixel;
    switch (frame.format) {
        case PixelFormat::kRGB:
        case PixelFormat::kBGR:
            bytes_per_pixel = 3;
            break;
        case PixelFormat::kRGBA:
        case PixelFormat::kBGRA:
            bytes_per_pixel = 4;
            break;
        case PixelFormat::kYUYV:
            if (frame.width % 2 != 0) return false;
            bytes_per_pixel = 2;
            break;
        case PixelFormat::kNV12:
        case PixelFormat::kNV21:
            if (frame.width % 2 != 0 || frame.height % 2 != 0) return false;
            bytes_per_pixel = 1;
            break;
        default:
            return false;
    }
    return static_cast<size_t>(frame.stride_bytes) >=
           static_cast<size_t>(frame.width) * bytes_per_pixel;
}

vImage_Error CopyRgba(const FrameBuffer& frame, const vImage_Buffer& destination) {
    const vImage_Buffer source{const_cast<uint8_t*>(frame.data),
                              static_cast<vImagePixelCount>(frame.height),
                              static_cast<vImagePixelCount>(frame.width),
                              static_cast<size_t>(frame.stride_bytes)};
    switch (frame.format) {
        case PixelFormat::kRGB:
            return vImageConvert_RGB888toRGBA8888(
                &source, nullptr, 255, &destination, false, kvImageNoFlags);
        case PixelFormat::kBGR:
            // Swapping the first and third channels converts BGR to RGBA too.
            return vImageConvert_RGB888toBGRA8888(
                &source, nullptr, 255, &destination, false, kvImageNoFlags);
        case PixelFormat::kRGBA:
        case PixelFormat::kBGRA: {
            const uint8_t rgba_order[]{0, 1, 2, 3};
            const uint8_t bgra_order[]{2, 1, 0, 3};
            const uint8_t opaque[]{0, 0, 0, 255};
            // The preview has always discarded input alpha. Set the last byte
            // explicitly instead of interpreting camera bytes as transparency.
            return vImagePermuteChannelsWithMaskedInsert_ARGB8888(
                &source, &destination,
                frame.format == PixelFormat::kRGBA ? rgba_order : bgra_order,
                0x1, opaque, kvImageNoFlags);
        }
        default:
            break;
    }

    // OpenCV clamped sub-black luma before conversion. vImage's range fields
    // describe the encoding but do not clamp the input, so do that explicitly.
    static const auto luma_table = [] {
        std::array<uint8_t, 256> table{};
        for (size_t i = 0; i < table.size(); ++i) table[i] = i < 16 ? 16 : i;
        return table;
    }();
    static const auto identity_table = [] {
        std::array<uint8_t, 256> table{};
        for (size_t i = 0; i < table.size(); ++i) table[i] = i;
        return table;
    }();
    const bool packed = frame.format == PixelFormat::kYUYV;
    const size_t clamped_stride = source.width * (packed ? 2 : 1);
    std::vector<uint8_t> clamped_pixels(clamped_stride * source.height);
    vImage_Buffer clamped{clamped_pixels.data(), source.height, source.width, clamped_stride};
    vImage_Error error;
    if (packed) {
        // Each four-byte lookup pixel is Y0 U Y1 V; preserve both chroma bytes.
        auto pairs = source;
        pairs.width /= 2;
        auto output_pairs = clamped;
        output_pairs.width /= 2;
        error = vImageTableLookUp_ARGB8888(
            &pairs, &output_pairs, luma_table.data(), identity_table.data(),
            luma_table.data(), identity_table.data(), kvImageNoFlags);
    } else {
        error = vImageTableLookUp_Planar8(&source, &clamped, luma_table.data(), kvImageNoFlags);
    }
    if (error != kvImageNoError) return error;

    // Match the previous preview's BT.601 limited-range conversion. FrameBuffer
    // carries no colour-space metadata; do not infer a matrix from resolution.
    const vImage_YpCbCrPixelRange range{16, 128, 235, 240, 255, 0, 255, 0};
    vImage_YpCbCrToARGB conversion{};
    const auto format = frame.format == PixelFormat::kYUYV
                            ? kvImage422YpCbYpCr8 : kvImage420Yp8_CbCr8;
    error = vImageConvert_YpCbCrToARGB_GenerateConversion(
        kvImage_YpCbCrToARGBMatrix_ITU_R_601_4, &range, &conversion,
        format, kvImageARGB8888, kvImageNoFlags);
    if (error != kvImageNoError) return error;

    const uint8_t rgba_order[]{1, 2, 3, 0};
    if (frame.format == PixelFormat::kYUYV) {
        return vImageConvert_422YpCbYpCr8ToARGB8888(
            &clamped, &destination, &conversion, rgba_order, 255, kvImageNoFlags);
    }

    // The SDK stores chroma immediately after luma, with the same row stride.
    vImage_Buffer chroma{
        const_cast<uint8_t*>(frame.data) + source.rowBytes * source.height,
        source.height / 2, source.width / 2, source.rowBytes};
    std::vector<uint8_t> uv;
    if (frame.format == PixelFormat::kNV21) {
        uv.resize(static_cast<size_t>(frame.width) * chroma.height);
        const vImage_Buffer swapped{uv.data(), chroma.height, chroma.width,
                                    static_cast<size_t>(frame.width)};
        error = vImageByteSwap_Planar16U(&chroma, &swapped, kvImageNoFlags);
        if (error != kvImageNoError) return error;
        chroma = swapped;
    }
    return vImageConvert_420Yp8_CbCr8ToARGB8888(
        &clamped, &chroma, &destination, &conversion, rgba_order, 255, kvImageNoFlags);
}

}  // namespace

CGImageRef CreatePreviewImage(const FrameBuffer& frame) {
    if (!ValidLayout(frame)) return nullptr;
    const size_t row_bytes = static_cast<size_t>(frame.width) * 4;
    if (static_cast<size_t>(frame.height) >
        static_cast<size_t>(std::numeric_limits<CFIndex>::max()) / row_bytes) {
        return nullptr;
    }
    const auto length = static_cast<CFIndex>(row_bytes * frame.height);
    CFMutableDataRef pixels = CFDataCreateMutable(kCFAllocatorDefault, length);
    if (!pixels) return nullptr;
    CFDataSetLength(pixels, length);
    const vImage_Buffer destination{CFDataGetMutableBytePtr(pixels),
                                   static_cast<vImagePixelCount>(frame.height),
                                   static_cast<vImagePixelCount>(frame.width), row_bytes};
    if (CopyRgba(frame, destination) != kvImageNoError) {
        CFRelease(pixels);
        return nullptr;
    }
    CGDataProviderRef provider = CGDataProviderCreateWithCFData(pixels);
    CFRelease(pixels);  // The provider retains the converted pixels.
    if (!provider) return nullptr;
    CGColorSpaceRef color_space = CGColorSpaceCreateDeviceRGB();
    CGImageRef image = nullptr;
    if (color_space) {
        image = CGImageCreate(frame.width, frame.height, 8, 32, row_bytes,
                              color_space, static_cast<CGBitmapInfo>(kCGImageAlphaLast) |
                                               kCGBitmapByteOrder32Big,
                              provider, nullptr, false, kCGRenderingIntentDefault);
        CGColorSpaceRelease(color_space);
    }
    CGDataProviderRelease(provider);
    return image;
}
