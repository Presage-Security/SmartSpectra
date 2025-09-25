# SmartSpectra SDK

This repository hosts SmartSpectra SDK from PresageTech for measuring vitals such as pulse, breathing, and more using a camera. The SDK supports multiple platforms, including Android, iOS, and C++ for Mac/Linux.

## Table of Contents

- [SmartSpectra SDK](#smartspectra-sdk)
  - [Getting Started](#getting-started)
  - [Features](#features)
  - [Authentication](#authentication)
  - [Platform-Specific Guides](#platform-specific-guides)
    - [Android](#android)
    - [iOS](#ios)
    - [Mac/Linux](#maclinux)
  - [Bugs & Troubleshooting](#bugs--troubleshooting)

## Getting Started

To get started, follow the instructions for one of our currently supported platforms. Each platform has its own integration guide and example applications to help you get up and running quickly.
Checkout documentation here: https://docs.physiology.presagetech.com/

## Features

- **Cardiac Waveform**  
  Real-time pulse pleth waveform supporting calculation of pulse rate and heart rate variability.

- **Breathing Waveform**  
  Real-time breathing waveform supporting biofeedback, breathing rate, inhale/exhale ratio, breathing amplitude, apnea detection, and respiratory line length.

- **Myofacial Analysis**  
  Supporting face-point analysis, iris tracking, blinking detection, and talking detection.

- **Relative Blood Pressure Waveform**  
  Relative blood pressure waveform (Contact support@presagetech.com for access)

- **Electrodermal Activity**  
  Electrodermal Activity real-time waveform (Contact support@presagetech.com for access)

- **Integrated Quality Control**  
  Confidence and stability metrics providing insight into the confidence in the signal. User feedback on imaging conditions to support successful use.

- **Camera Selection**  
  Front or rear facing camera selection on iOS or Android and specification of camera input for applications using the C++ SDK.

- **Continuous or Spot Measurement**  
  Options for continuous measurements or variable time window spot measurements to support varied use cases.

## Authentication

- We support API key authentication for CPP, iOS and Android. We also support Oauth authentication for iOS and Android. For detailed instructions on how to setup Authentication, refer to the dedicated [Authentication Docs](docs/authentication.md)

## Platform-Specific Guides

### Android

For Android integration, refer to the [Android README](android/README.md). The guide includes:

- Prerequisites and setup instructions.
- Integration steps for your app.
- Example usage and troubleshooting tips.

### iOS

For iOS integration, refer to the [iOS README](swift/README.md). The guide includes:

- Prerequisites and setup instructions.
- Integration steps for your app using Swift Package Manager.
- Example usage and troubleshooting tips.

### Mac/Linux

For C++ integration on Mac/Linux, refer to the [C++ README](cpp/README.md). The guide includes:

- Supported systems and architectures.
- Installation and build instructions.
- Example applications and troubleshooting tips.

## Bugs & Troubleshooting

For additional support, contact <support@presagetech.com> or [submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues).


