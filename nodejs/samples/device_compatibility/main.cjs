// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
'use strict';
const { SmartSpectraSDK } = require('@smartspectra/node-sdk');
const { writeFileSync } = require('node:fs');
(async () => {
    const sdk = new SmartSpectraSDK({ apiKey: process.env.PHYSIOLOGY_API_KEY });
    sdk.useCamera();
    try {
        await sdk.isDeviceCompatible();
        const result = sdk.getDeviceCompatibilityResult();
        console.log(result.verdict, result.reason);
        if (process.argv[2]) writeFileSync(process.argv[2], result.diagnostics, { flag: 'wx', mode: 0o600 });
        process.exitCode = result.verdict === 'ready' ? 0 : 2;
    } finally { await sdk.destroy(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
