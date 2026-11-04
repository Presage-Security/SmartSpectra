// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
'use strict';
const { SmartSpectraSDK } = require('@smartspectra/node-sdk');
(async () => {
    const sdk = new SmartSpectraSDK({ apiKey: process.env.PHYSIOLOGY_API_KEY });
    sdk.useCamera();
    try {
        const result = await sdk.checkDeviceReadiness();
        console.log(result.verdict, result.reason);
        process.exitCode = result.verdict === 'ready' ? 0 : 2;
    } finally { await sdk.destroy(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
