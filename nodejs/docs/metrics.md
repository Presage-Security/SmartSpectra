---
title: Configuring Metrics
description: Request and read SmartSpectra metrics from the Node.js SDK.
---

The Node.js SDK passes requested metric codes through to the native SmartSpectra C++ SDK. Omitting `requestedMetrics` uses the default breathing bundle.

## Breathing and Pulse

### Request Metrics

Request the breathing bundle plus the cardio bundle (pulse rate, arterial pressure trace, HRV):

```typescript
import { SmartSpectraSDK, breathingMetrics, cardioMetrics } from '@smartspectra/node-sdk';

const sdk = new SmartSpectraSDK({
  apiKey: 'YOUR_API_KEY',
  requestedMetrics: [...breathingMetrics, ...cardioMetrics],
});
```

The bundle exports (`breathingMetrics`, `cardioMetrics`, `faceMetrics`, `micromotionMetrics`, `edaMetrics`) contain the `MetricType` integer codes defined in `metric_types.proto`. `defaultSupportedMetrics` is the bundle used when `requestedMetrics` is omitted (currently equal to `breathingMetrics`).

### Read Metrics

Read the latest breathing and pulse samples from the `metrics` event:

```typescript
import { decodeMetrics } from '@smartspectra/node-sdk';

sdk.on('metrics', (buf, timestampUs) => {
  const metrics = decodeMetrics(buf) as any;
  if (Buffer.isBuffer(metrics)) return;

  const breathingRate = metrics.breathing?.rate?.at(-1)?.value;
  const chestTrace = metrics.breathing?.upperTrace?.at(-1)?.value;
  const abdomenTrace = metrics.breathing?.lowerTrace?.at(-1)?.value;
  const pulseRate = metrics.cardio?.pulseRate?.at(-1)?.value;
});
```

Cardio fields are empty unless you request a cardio metric such as `PULSE_RATE`.

## Advanced

Combine bundles or hand-pick `MetricType` codes for finer control:

```typescript
import {
  SmartSpectraSDK, breathingMetrics, cardioMetrics, faceMetrics, edaMetrics,
} from '@smartspectra/node-sdk';

const sdk = new SmartSpectraSDK({
  apiKey: 'YOUR_API_KEY',
  requestedMetrics: [
    ...breathingMetrics,
    ...cardioMetrics,
    ...faceMetrics,
    ...edaMetrics,
  ],
});
```

Read the advanced fields from the same decoded metrics object:

```typescript
sdk.on('metrics', (buf, timestampUs) => {
  const metrics = decodeMetrics(buf) as any;
  if (Buffer.isBuffer(metrics)) return;

  const pressureTrace = metrics.cardio?.arterialPressureTrace?.at(-1)?.value;
  const hrvRmssd = metrics.cardio?.hrv?.at(-1)?.rmssd;

  const edaTrace = metrics.eda?.trace?.at(-1)?.value;

  const faceLandmarks = metrics.face?.landmarks?.at(-1)?.value;
  const blinking = metrics.face?.blinking?.at(-1)?.detected;
  const talking = metrics.face?.talking?.at(-1)?.detected;
  const expression = metrics.face?.expression?.at(-1);
});
```

### Advanced Payload Shape

`decodeMetrics` returns the generated protobuf payload as a JavaScript object. Requested advanced metrics populate these fields:

```typescript
type Metrics = {
  breathing?: Breathing;
  eda?: Eda;
  face?: Face;
  cardio?: Cardio;
};

type Cardio = {
  pulseRate?: MeasurementWithConfidence[];
  arterialPressureTrace?: MeasurementWithConfidence[];
  hrv?: Hrv[];
};

type Hrv = {
  rmssd: number;
  meanNn: number;
  sdnn: number;
  baevsky: number;
  timestamp: number;
  confidence: number;
};

type Eda = {
  trace?: Measurement[];
};

type Face = {
  landmarks?: Landmarks[];
  blinking?: DetectionStatus[];
  talking?: DetectionStatus[];
  expression?: Expression[];
};
```

EDA may take longer to produce its first sample than breathing or cardio outputs. See [Data Types](../../docs/data-types.md) for the complete protobuf schema.
