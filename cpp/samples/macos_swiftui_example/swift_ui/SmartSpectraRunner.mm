#import "SmartSpectraRunner.h"

#include <iomanip>
#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include <opencv2/imgproc.hpp>

#include <smartspectra/smartspectra.h>
#include <smartspectra/version.hpp>

namespace ss = presage::smartspectra;

namespace {

std::string FormatFloat(float value, int precision = 1) {
    std::ostringstream out;
    out << std::fixed << std::setprecision(precision) << value;
    return out.str();
}

template <typename Getter>
void AddLatestMeasurementLine(std::vector<std::string>& lines,
                              const std::string& label,
                              int count,
                              const Getter& getter,
                              const std::string& unit) {
    if (count == 0) {
        return;
    }

    const auto& measurement = getter(count - 1);
    lines.push_back(label + ": " + FormatFloat(measurement.value()) + unit);
}

template <typename Getter>
void AddLatestMeasurementWithConfidenceLine(std::vector<std::string>& lines,
                                            const std::string& label,
                                            int count,
                                            const Getter& getter,
                                            const std::string& unit) {
    if (count == 0) {
        return;
    }

    const auto& measurement = getter(count - 1);
    lines.push_back(label + ": " + FormatFloat(measurement.value()) + unit +
                    " (" + FormatFloat(measurement.confidence(), 0) + "%)");
}

NSArray<NSString *> *NSArrayFromLines(const std::vector<std::string>& lines) {
    NSMutableArray<NSString *> *array = [NSMutableArray arrayWithCapacity:lines.size()];
    for (const auto& line : lines) {
        [array addObject:[NSString stringWithUTF8String:line.c_str()]];
    }
    return array;
}

NSArray<NSNumber *> *BreathingTraceFromMetrics(const ss::Metrics& metrics) {
    NSMutableArray<NSNumber *> *array = [NSMutableArray array];
    if (!metrics.has_breathing()) {
        return array;
    }

    const auto& breathing = metrics.breathing();
    for (int index = 0; index < breathing.upper_trace_size(); ++index) {
        [array addObject:@(breathing.upper_trace(index).value())];
    }
    return array;
}

NSArray<NSNumber *> *ArterialPressureTraceFromMetrics(const ss::Metrics& metrics) {
    NSMutableArray<NSNumber *> *array = [NSMutableArray array];
    if (!metrics.has_cardio()) {
        return array;
    }

    const auto& cardio = metrics.cardio();
    for (int index = 0; index < cardio.arterial_pressure_trace_size(); ++index) {
        [array addObject:@(cardio.arterial_pressure_trace(index).value())];
    }
    return array;
}

std::vector<std::string> BuildMetricLines(const ss::Metrics& metrics) {
    std::vector<std::string> lines;

    if (metrics.has_breathing()) {
        const auto& breathing = metrics.breathing();
        AddLatestMeasurementWithConfidenceLine(
            lines,
            "Breathing rate",
            breathing.rate_size(),
            [&breathing](int index) -> const auto& { return breathing.rate(index); },
            " bpm");
        AddLatestMeasurementLine(
            lines,
            "Amplitude",
            breathing.amplitude_size(),
            [&breathing](int index) -> const auto& { return breathing.amplitude(index); },
            "");
        AddLatestMeasurementLine(
            lines,
            "Line length",
            breathing.respiratory_line_length_size(),
            [&breathing](int index) -> const auto& {
                return breathing.respiratory_line_length(index);
            },
            "");
        AddLatestMeasurementLine(
            lines,
            "Inhale/exhale",
            breathing.inhale_exhale_ratio_size(),
            [&breathing](int index) -> const auto& {
                return breathing.inhale_exhale_ratio(index);
            },
            "");
    }

    if (metrics.has_cardio()) {
        const auto& cardio = metrics.cardio();
        AddLatestMeasurementWithConfidenceLine(
            lines,
            "Pulse rate",
            cardio.pulse_rate_size(),
            [&cardio](int index) -> const auto& { return cardio.pulse_rate(index); },
            " bpm");
    }

    return lines;
}

bool CopyFrameToBgr(const ss::FrameBuffer& frame, cv::Mat& output_bgr) {
    if (frame.data == nullptr || frame.width <= 0 || frame.height <= 0 || frame.stride_bytes <= 0) {
        return false;
    }

    switch (frame.format) {
        case ss::PixelFormat::kBGR:
            output_bgr = cv::Mat(frame.height, frame.width, CV_8UC3,
                                 const_cast<uint8_t*>(frame.data),
                                 frame.stride_bytes).clone();
            return true;
        case ss::PixelFormat::kRGB: {
            cv::Mat rgb(frame.height, frame.width, CV_8UC3,
                        const_cast<uint8_t*>(frame.data),
                        frame.stride_bytes);
            cv::cvtColor(rgb, output_bgr, cv::COLOR_RGB2BGR);
            return true;
        }
        case ss::PixelFormat::kBGRA: {
            cv::Mat bgra(frame.height, frame.width, CV_8UC4,
                         const_cast<uint8_t*>(frame.data),
                         frame.stride_bytes);
            cv::cvtColor(bgra, output_bgr, cv::COLOR_BGRA2BGR);
            return true;
        }
        case ss::PixelFormat::kRGBA: {
            cv::Mat rgba(frame.height, frame.width, CV_8UC4,
                         const_cast<uint8_t*>(frame.data),
                         frame.stride_bytes);
            cv::cvtColor(rgba, output_bgr, cv::COLOR_RGBA2BGR);
            return true;
        }
        case ss::PixelFormat::kYUYV: {
            cv::Mat yuyv(frame.height, frame.width, CV_8UC2,
                         const_cast<uint8_t*>(frame.data),
                         frame.stride_bytes);
            cv::cvtColor(yuyv, output_bgr, cv::COLOR_YUV2BGR_YUY2);
            return true;
        }
        case ss::PixelFormat::kNV12:
        case ss::PixelFormat::kNV21: {
            cv::Mat yuv(frame.height + frame.height / 2, frame.width, CV_8UC1);
            for (int row = 0; row < frame.height; ++row) {
                std::memcpy(yuv.ptr(row),
                            frame.data + row * frame.stride_bytes,
                            static_cast<size_t>(frame.width));
            }
            const uint8_t* chroma = frame.data + frame.stride_bytes * frame.height;
            for (int row = 0; row < frame.height / 2; ++row) {
                std::memcpy(yuv.ptr(frame.height + row),
                            chroma + row * frame.stride_bytes,
                            static_cast<size_t>(frame.width));
            }

            cv::cvtColor(yuv, output_bgr,
                         frame.format == ss::PixelFormat::kNV12
                             ? cv::COLOR_YUV2BGR_NV12
                             : cv::COLOR_YUV2BGR_NV21);
            return true;
        }
    }

    return false;
}

NSImage *ImageFromBgrMat(const cv::Mat& bgr) {
    if (bgr.empty()) {
        return nil;
    }

    cv::Mat rgba;
    cv::cvtColor(bgr, rgba, cv::COLOR_BGR2RGBA);

    NSData *data = [NSData dataWithBytes:rgba.data
                                  length:rgba.total() * rgba.elemSize()];
    CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)data);
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGImageRef cgImage = CGImageCreate(
        rgba.cols,
        rgba.rows,
        8,
        32,
        rgba.step[0],
        colorSpace,
        kCGImageAlphaLast | kCGBitmapByteOrderDefault,
        provider,
        nullptr,
        false,
        kCGRenderingIntentDefault);

    NSImage *image = nil;
    if (cgImage != nullptr) {
        image = [[NSImage alloc] initWithCGImage:cgImage
                                            size:NSMakeSize(rgba.cols, rgba.rows)];
    }

    if (cgImage != nullptr) {
        CGImageRelease(cgImage);
    }
    CGColorSpaceRelease(colorSpace);
    CGDataProviderRelease(provider);
    return image;
}

void DispatchFailure(__weak SmartSpectraRunner *weakRunner, NSString *message) {
    dispatch_async(dispatch_get_main_queue(), ^{
        SmartSpectraRunner *runner = weakRunner;
        [runner.delegate smartSpectraRunnerDidFail:message];
    });
}

void DispatchDiagnostics(__weak SmartSpectraRunner *weakRunner, NSString *diagnostics) {
    dispatch_async(dispatch_get_main_queue(), ^{
        SmartSpectraRunner *runner = weakRunner;
        [runner.delegate smartSpectraRunnerDidUpdateDiagnostics:diagnostics];
    });
}

}  // namespace

@interface SmartSpectraRunner ()
@property(nonatomic, assign) BOOL running;
@end

@implementation SmartSpectraRunner {
    std::mutex mutex_;
    std::unique_ptr<ss::SmartSpectra> spectra_;
    std::atomic<long long> frame_count_;
    std::atomic<long long> frame_sent_count_;
    std::atomic<long long> frame_blocked_count_;
}

- (nullable NSString *)startWithAPIKey:(NSString *)apiKey {
    if (self.running) {
        return nil;
    }

    if (apiKey.length == 0) {
        return @"Missing API key.";
    }

    frame_count_ = 0;
    frame_sent_count_ = 0;
    frame_blocked_count_ = 0;

    ss::SmartSpectraConfig config;
    config.api_key = std::string(apiKey.UTF8String);
    config.requested_metrics = ss::SmartSpectraConfig::DefaultSupportedMetrics();
    config.AddMetrics(ss::SmartSpectraConfig::CardioMetrics());

    auto spectra = std::make_unique<ss::SmartSpectra>(std::move(config));
    __weak SmartSpectraRunner *weakSelf = self;

    spectra->SetOnProcessingStatusChanged([weakSelf](ss::ProcessingStatus status) {
        NSString *processing = [NSString stringWithUTF8String:ss::ToString(status)];
        dispatch_async(dispatch_get_main_queue(), ^{
            SmartSpectraRunner *runner = weakSelf;
            [runner.delegate smartSpectraRunnerDidUpdateStatus:processing validation:@""];
        });
    });

    spectra->SetOnValidationStatusChanged(
        [weakSelf](const ss::ValidationStatus& status, int64_t /*timestamp_us*/) {
            std::string status_text = ss::ToString(status);

            NSString *validation = [NSString stringWithUTF8String:status_text.c_str()];
            dispatch_async(dispatch_get_main_queue(), ^{
                SmartSpectraRunner *runner = weakSelf;
                [runner.delegate smartSpectraRunnerDidUpdateStatus:@"" validation:validation];
            });
        });

    spectra->SetOnMetrics([weakSelf](const ss::Metrics& metrics, int64_t timestamp_us) {
        std::vector<std::string> metric_lines = BuildMetricLines(metrics);
        NSArray<NSNumber *> *breathing_trace = BreathingTraceFromMetrics(metrics);
        NSArray<NSNumber *> *arterial_pressure_trace = ArterialPressureTraceFromMetrics(metrics);
        NSArray<NSString *> *lines = NSArrayFromLines(metric_lines);
        dispatch_async(dispatch_get_main_queue(), ^{
            SmartSpectraRunner *runner = weakSelf;
            [runner.delegate smartSpectraRunnerDidUpdateMetrics:lines timestampUs:timestamp_us];
            [runner.delegate smartSpectraRunnerDidUpdateBreathingTrace:breathing_trace
                                                 arterialPressureTrace:arterial_pressure_trace
                                                           timestampUs:timestamp_us];
        });
    });

    spectra->SetOnVideoOutput([weakSelf](const ss::FrameBuffer& frame, int64_t /*timestamp_us*/) {
        SmartSpectraRunner *strongSelf = weakSelf;
        if (strongSelf == nil) {
            return;
        }

        const long long frame_count = ++strongSelf->frame_count_;
        if (frame_count % 2 != 0) {
            return;
        }

        cv::Mat bgr;
        if (!CopyFrameToBgr(frame, bgr)) {
            return;
        }

        NSImage *image = ImageFromBgrMat(bgr);
        if (image == nil) {
            return;
        }

        dispatch_async(dispatch_get_main_queue(), ^{
            SmartSpectraRunner *runner = weakSelf;
            [runner.delegate smartSpectraRunnerDidUpdateFrame:image];
        });

        if (frame_count % 30 == 0) {
            NSString *diagnostics = [NSString stringWithFormat:@"Frames: %lld | accepted: %lld | blocked: %lld",
                                     frame_count,
                                     strongSelf->frame_sent_count_.load(),
                                     strongSelf->frame_blocked_count_.load()];
            DispatchDiagnostics(weakSelf, diagnostics);
        }
    });

    spectra->SetOnFrameSentThrough([weakSelf](bool sent_through, int64_t /*timestamp_us*/) {
        SmartSpectraRunner *strongSelf = weakSelf;
        if (strongSelf == nil) {
            return;
        }

        if (sent_through) {
            ++strongSelf->frame_sent_count_;
        } else {
            ++strongSelf->frame_blocked_count_;
        }

        const long long total = strongSelf->frame_sent_count_.load() +
                                strongSelf->frame_blocked_count_.load();
        if (total % 30 == 0) {
            NSString *diagnostics = [NSString stringWithFormat:@"Frames: %lld | accepted: %lld | blocked: %lld",
                                     strongSelf->frame_count_.load(),
                                     strongSelf->frame_sent_count_.load(),
                                     strongSelf->frame_blocked_count_.load()];
            DispatchDiagnostics(weakSelf, diagnostics);
        }
    });

    spectra->SetOnError([weakSelf](const ss::SmartSpectraError& error) {
        std::string message = error.FullMessage();
        DispatchFailure(weakSelf, [NSString stringWithUTF8String:message.c_str()]);
    });

    if (auto error = spectra->UseCamera(0).SetResolution(1280, 720).SetFps(30).Build();
        !error.ok()) {
        return [NSString stringWithUTF8String:error.FullMessage().c_str()];
    }

    if (auto error = spectra->Start(); !error.ok()) {
        return [NSString stringWithUTF8String:error.FullMessage().c_str()];
    }

    {
        std::lock_guard<std::mutex> lock(mutex_);
        spectra_ = std::move(spectra);
    }

    self.running = YES;
    return nil;
}

- (void)stop {
    std::unique_ptr<ss::SmartSpectra> spectra;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        spectra = std::move(spectra_);
    }

    if (spectra) {
        (void)spectra->Stop();
    }
    self.running = NO;
}

- (void)dealloc {
    [self stop];
}

@end
