#include "PreviewImage.h"

#include <smartspectra/smartspectra_types.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <iostream>
#include <stdexcept>
#include <vector>

namespace {

using presage::smartspectra::FrameBuffer;
using presage::smartspectra::PixelFormat;
using Rgb = std::array<uint8_t, 3>;

void Require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}

void CheckImage(FrameBuffer frame, std::vector<uint8_t>& input,
                const std::vector<Rgb>& expected, int tolerance = 0) {
    const auto original = input;
    CGImageRef image = CreatePreviewImage(frame);
    Require(image != nullptr, "valid frame rejected");
    Require(input == original, "conversion modified borrowed input");
    // The callback's backing storage may be overwritten immediately afterward.
    std::fill(input.begin(), input.end(), 0);
    Require(CGImageGetWidth(image) == static_cast<size_t>(frame.width) &&
            CGImageGetHeight(image) == static_cast<size_t>(frame.height), "image dimensions");
    CFDataRef data = CGDataProviderCopyData(CGImageGetDataProvider(image));
    const auto* pixels = CFDataGetBytePtr(data);
    const size_t stride = CGImageGetBytesPerRow(image);
    Require(CGImageGetAlphaInfo(image) == kCGImageAlphaLast, "image alpha layout");
    for (int y = 0; y < frame.height; ++y) {
        for (int x = 0; x < frame.width; ++x) {
            const auto* pixel = pixels + y * stride + x * 4;
            const auto& color = expected[y * frame.width + x];
            for (int channel = 0; channel < 3; ++channel) {
                if (std::abs(int(pixel[channel]) - color[channel]) > tolerance) {
                    std::cerr << "format=" << static_cast<int>(frame.format)
                              << " x=" << x << " y=" << y << " channel=" << channel
                              << " actual=" << int(pixel[channel])
                              << " expected=" << int(color[channel]) << '\n';
                    throw std::runtime_error("pixel mismatch");
                }
            }
            Require(pixel[3] == 255, "preview must be opaque");
        }
    }
    CFRelease(data);
    CGImageRelease(image);
}

void TestRgb() {
    const std::vector<Rgb> colors{{255, 0, 0}, {0, 255, 0}, {0, 0, 255},
                                  {12, 34, 56}, {255, 255, 255}, {0, 0, 0}};
    for (auto format : {PixelFormat::kRGB, PixelFormat::kBGR,
                        PixelFormat::kRGBA, PixelFormat::kBGRA}) {
        const int channels = (format == PixelFormat::kRGB || format == PixelFormat::kBGR) ? 3 : 4;
        const bool blue_first = format == PixelFormat::kBGR || format == PixelFormat::kBGRA;
        for (int padding : {0, 7}) {
            const int stride = 3 * channels + padding;
            std::vector<uint8_t> bytes(stride * 2, 0xcd);
            for (int y = 0; y < 2; ++y) {
                for (int x = 0; x < 3; ++x) {
                    auto* pixel = bytes.data() + y * stride + x * channels;
                    const auto& color = colors[y * 3 + x];
                    pixel[0] = color[blue_first ? 2 : 0];
                    pixel[1] = color[1];
                    pixel[2] = color[blue_first ? 0 : 2];
                    if (channels == 4) pixel[3] = static_cast<uint8_t>(x * 64);
                }
            }
            CheckImage({bytes.data(), 3, 2, stride, format}, bytes, colors);
        }
    }
}

void TestYuv() {
    // BT.601 limited-range reference patches. Include sub-black luma with
    // non-neutral chroma to catch clamping after (instead of before) conversion.
    struct Patch { uint8_t y, u, v; Rgb rgb; };
    const std::array<Patch, 8> patches{{
        {81, 90, 240, {254, 0, 0}}, {145, 54, 34, {0, 255, 1}},
        {41, 240, 110, {0, 0, 255}}, {16, 128, 128, {0, 0, 0}},
        {235, 128, 128, {255, 255, 255}}, {0, 90, 240, {179, 0, 0}},
        {15, 90, 240, {179, 0, 0}}, {255, 128, 128, {255, 255, 255}}
    }};
    for (auto format : {PixelFormat::kYUYV, PixelFormat::kNV12, PixelFormat::kNV21}) {
        for (int padding : {0, 5}) {
            constexpr int width = 8, height = 4;
            const bool packed = format == PixelFormat::kYUYV;
            const int stride = width * (packed ? 2 : 1) + padding;
            std::vector<uint8_t> bytes(stride * (packed ? height : height * 3 / 2), 0xcd);
            std::vector<Rgb> colors;
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    const auto& patch = patches[(y / 2) * (width / 2) + x / 2];
                    colors.push_back(patch.rgb);
                    if (packed) {
                        bytes[y * stride + x * 2] = patch.y;
                        bytes[y * stride + x * 2 + 1] = x % 2 == 0 ? patch.u : patch.v;
                    } else {
                        bytes[y * stride + x] = patch.y;
                        const int offset = stride * height + (y / 2) * stride + (x / 2) * 2;
                        bytes[offset] = format == PixelFormat::kNV12 ? patch.u : patch.v;
                        bytes[offset + 1] = format == PixelFormat::kNV12 ? patch.v : patch.u;
                    }
                }
            }
            CheckImage({bytes.data(), width, height, stride, format}, bytes, colors, 1);
        }
    }
}

void TestInvalid() {
    std::array<uint8_t, 64> pixels{};
    for (auto format : {PixelFormat::kRGB, PixelFormat::kBGR, PixelFormat::kRGBA,
                        PixelFormat::kBGRA, PixelFormat::kYUYV, PixelFormat::kNV12,
                        PixelFormat::kNV21}) {
        for (auto frame : {FrameBuffer{nullptr, 2, 2, 8, format},
                           FrameBuffer{pixels.data(), 0, 2, 8, format},
                           FrameBuffer{pixels.data(), 2, -1, 8, format},
                           FrameBuffer{pixels.data(), 2, 2, 0, format},
                           FrameBuffer{pixels.data(), 2, 2, 1, format}}) {
            Require(CreatePreviewImage(frame) == nullptr, "invalid layout accepted");
        }
    }
    for (auto format : {PixelFormat::kYUYV, PixelFormat::kNV12, PixelFormat::kNV21}) {
        Require(CreatePreviewImage({pixels.data(), 3, 2, 16, format}) == nullptr,
                "odd subsampled width accepted");
    }
    for (auto format : {PixelFormat::kNV12, PixelFormat::kNV21}) {
        Require(CreatePreviewImage({pixels.data(), 2, 3, 8, format}) == nullptr,
                "odd subsampled height accepted");
    }
    Require(CreatePreviewImage({pixels.data(), 2, 2, 8, static_cast<PixelFormat>(99)}) == nullptr,
            "unknown format accepted");
}

}  // namespace

int main() {
    try {
        TestRgb();
        TestYuv();
        TestInvalid();
        std::cout << "Preview conversion: all seven formats, padding, ownership and invalid layouts passed\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
