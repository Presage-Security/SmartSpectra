// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
'use strict';
const { SmartSpectraSDK } = require('@smartspectra/node-sdk');
async function main() {
    const sdk = new SmartSpectraSDK({ apiKey: process.env.PHYSIOLOGY_API_KEY });
    sdk.useCamera();
    try {
        const verdict = await sdk.isDeviceCompatible();
        console.log(verdict);
        process.exitCode = verdict === 'ready' ? 0 : 2;
    } finally { await sdk.destroy(); }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
