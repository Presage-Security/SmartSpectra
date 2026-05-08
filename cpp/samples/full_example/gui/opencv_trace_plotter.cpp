// opencv_trace_plotter.cpp
// Copyright (C) 2024-2026 Presage Technologies, Inc.
//
// SPDX-License-Identifier: LicenseRef-Proprietary

// standard library includes
#include <algorithm>
#include <limits>

// third-party includes
#include <opencv2/imgproc/imgproc.hpp>

// local includes
#include "opencv_trace_plotter.hpp"
#include "opencv_element_fits.hpp"

namespace presage::smartspectra::gui {


template<typename TMeasurement>
void AppendOverlappingTimeSeries(
    std::vector<TMeasurement>& target_series,
    const std::vector<TMeasurement>& source_series,
    int& target_start_index
) {
    if (!source_series.empty()) {
        int64_t first_source_time = source_series[0].timestamp;
        int i_target_measurement = target_start_index;
        int i_source_measurement = 0;
        if (!target_series.empty()) {
            int64_t current_target_time = target_series[i_target_measurement].timestamp;
            while (current_target_time < first_source_time && i_target_measurement < target_series.size()) {
                current_target_time = target_series[i_target_measurement].timestamp;
                i_target_measurement++;
            }
            int64_t current_source_time = first_source_time;
            int64_t first_target_time = current_target_time;
            // for cases when source data times are earlier than target data times (e.g. calibration trigger / re-trigger),
            // scroll to first source measurement that occurs after or at the first target measurement
            while (current_source_time < first_target_time && i_source_measurement < source_series.size()) {
                current_source_time = source_series[i_source_measurement].timestamp;
                i_source_measurement++;
            }
        }

        // start scanning next time from the source measurement time that started the overlap in this iteration
        target_start_index = i_target_measurement;

        // update existing measurements
        for (; i_target_measurement < target_series.size() &&
               i_source_measurement < source_series.size(); i_target_measurement++, i_source_measurement++) {
            TMeasurement& target_measurement = target_series[i_target_measurement];
            const TMeasurement& source_measurement = source_series[i_source_measurement];
            if (source_measurement.timestamp == target_measurement.timestamp) {
                target_measurement = source_measurement;
            }
        }
        // add new measurements

        for (; i_source_measurement < source_series.size(); i_source_measurement++) {
            target_series.push_back(source_series[i_source_measurement]);
        }
    }
}

void RenderTimeSeries(const std::vector<cv::Point2i>& points, cv::Mat& image, const cv::Scalar& color, int line_width);

/**
 * Update the trace with a range of samples. The range may have overlap with existing values, but must end at or after
 * the last range that was added this way.
 * @param new_values the sample range with updated values.
 */
void OpenCvTracePlotter::TrimBuffer(bool adjust_overlap_cursor) {
    if (this->buffer.size() > this->max_points) {
        int deleted_point_count = this->buffer.size() - this->max_points;
        this->buffer.erase(this->buffer.begin(), this->buffer.begin() + deleted_point_count);
        if (adjust_overlap_cursor) {
            this->last_overlap_area_start = std::max(0, this->last_overlap_area_start - deleted_point_count);
        }
    }
}

void OpenCvTracePlotter::UpdateTraceWithSampleRange(
    const std::vector<TraceSample>& new_values
) {
    AppendOverlappingTimeSeries(this->buffer, new_values, this->last_overlap_area_start);
    TrimBuffer(/*adjust_overlap_cursor=*/true);
}

void OpenCvTracePlotter::UpdateTraceWithSample(const TraceSample& new_value) {
    this->buffer.push_back(new_value);
    TrimBuffer(/*adjust_overlap_cursor=*/false);
}

void OpenCvTracePlotter::UpdateTraceWithSample(const ConfidenceSample& new_value) {
    this->buffer.push_back({new_value.timestamp, new_value.value, new_value.stable});
    TrimBuffer(/*adjust_overlap_cursor=*/false);
}

void OpenCvTracePlotter::UpdateTraceWithSampleRange(
    const std::vector<ConfidenceSample>& new_values
) {
    std::vector<TraceSample> converted;
    converted.reserve(new_values.size());
    for (const auto& mwc : new_values) {
        converted.push_back({mwc.timestamp, mwc.value, mwc.stable});
    }
    AppendOverlappingTimeSeries(this->buffer, converted, this->last_overlap_area_start);
    TrimBuffer(/*adjust_overlap_cursor=*/true);
}

OpenCvTracePlotter::OpenCvTracePlotter(int x, int y, int width, int height, int max_points)
    : plot_area(x, y, width, height), max_points(max_points) {}


template<typename TMeasurement, typename TFunction>
void ApplyToMeasurements(
    const std::vector<TMeasurement>& measurements,
    TFunction&& function
) {
    for (const TMeasurement& measurement: measurements) {
        function(measurement);
    }
}

template<typename TMeasurement>
float GetMaxValue(const std::vector<TMeasurement>& measurements) {
    float max_value = 0.0f;
    auto process_for_max = [&max_value](const TMeasurement& measurement) {
        max_value = std::max(max_value, measurement.value);
    };
    ApplyToMeasurements(measurements, process_for_max);
    return max_value;
}

template<typename TMeasurement>
float GetMinValue(const std::vector<TMeasurement>& measurements) {
    float min_value = std::numeric_limits<float>::max();
    auto process_for_min = [&min_value](const TMeasurement& measurement) {
        min_value = std::min(min_value, measurement.value);
    };
    ApplyToMeasurements(measurements, process_for_min);
    return min_value;
}

template<typename TMeasurement>
std::vector<cv::Point2i> ComputeRenderableTimeSeries(
    const std::vector<TMeasurement>& trace_measurements,
    float value_scale_factor = 1.0, float time_scale_factor = 1.0, float y_offset = 0.0
) {
    float min_value = GetMinValue(trace_measurements);
    float max_value = GetMaxValue(trace_measurements);
    float value_range = max_value - min_value;
    int64_t min_time = trace_measurements[0].timestamp;
    int64_t max_time = trace_measurements[trace_measurements.size() - 1].timestamp;
    int64_t time_range = max_time - min_time;

    std::vector<cv::Point2i> canvas_points;
    for (const TMeasurement& measurement: trace_measurements) {
        int normalized_value = static_cast<int>(
            (max_value - measurement.value) * value_scale_factor / value_range + y_offset);;
        int normalized_time = static_cast<int>(
            static_cast<double>(measurement.timestamp - min_time) * time_scale_factor / static_cast<double>(time_range));
        canvas_points.emplace_back(normalized_time, normalized_value);
    }
    return canvas_points;
}

void RenderTimeSeries(const std::vector<cv::Point2i>& points, cv::Mat& image, const cv::Scalar& color, int line_width) {
    for (int i_point = 1; i_point < points.size(); i_point++) {
        cv::line(
            image, points[i_point - 1], points[i_point], color, line_width, cv::LINE_AA
        );
    }
}

bool OpenCvTracePlotter::Render(cv::Mat& image, const cv::Scalar& color) {
    if (!CheckThatElementFitsImage("OpenCvTracePlotter", this->plot_area, image)) {
        return false;
    }

    // Margins to avoid clipping
    const float trace_width = static_cast<float>(this->plot_area.width) - 1.f;

    if (this->buffer.size() >= 2) {
        auto points =
            ComputeRenderableTimeSeries(
                this->buffer,
                static_cast<float>(this->plot_area.height),
                trace_width,
                static_cast<float>(this->plot_area.y)
        );
        RenderTimeSeries(points, image, color, 1);
    }
    return true;
}

} // presage::smartspectra::gui
