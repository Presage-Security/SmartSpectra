// Copyright (C) 2026 Presage Technologies, Inc.
// SPDX-License-Identifier: LicenseRef-Proprietary
'use strict';
function present(result) {
    return { title: result.verdict, reason: result.reason, action: result.reason,
        canProceed: result.verdict === 'ready' };
}
module.exports = { present };
