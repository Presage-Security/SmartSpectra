---
title: Using the AI agent skill
description: Install the SmartSpectra agent skill in Claude Code or OpenAI Codex to build vitals apps with an AI coding assistant.
---

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

`using-smartspectra` is an [Agent Skill](https://agentskills.io) — a small guide that an AI coding
assistant loads on demand to build apps with the SmartSpectra SDK. It teaches the API model
(getting a key, the config → start/stop lifecycle, choosing metrics) and points the assistant at
these docs for exact, current per-platform detail.

It is one portable `SKILL.md` in the standard Agent Skills format, so the **same skill works in
both Claude Code and OpenAI Codex**. Both are hosted in the public repo
[`Presage-Security/SmartSpectra`](https://github.com/Presage-Security/SmartSpectra).

## Install in Claude Code

Add the repo as a plugin marketplace, then install the plugin:

```text
/plugin marketplace add Presage-Security/SmartSpectra
/plugin install smartspectra-sdk@smartspectra
```

Run `/plugin` to confirm `smartspectra-sdk` is enabled. The skill then loads automatically when a
task matches it; there is nothing else to configure.

## Install in Codex

Install the skill straight from the repo with the Codex skill installer:

```text
$skill-installer Presage-Security/SmartSpectra/.agents/skills/using-smartspectra
```

Codex detects newly installed skills automatically; restart Codex if it does not appear. When you
are working inside a clone of the repo, Codex also discovers the skill automatically from
`.agents/skills/`.

## Use it

Describe what you want to build and let the assistant drive:

```text
Using the SmartSpectra SDK, build a minimal app that measures and displays my pulse.
```

The skill guides the assistant to the correct API for your platform — C++, Swift/iOS,
Kotlin/Android, or Node — and to the matching pages in these docs for exact signatures, install
steps, and sample apps. You review and run the code it writes, the same as any other assistant
output.

## What you need

- An **API key** from the [Developer Admin Portal](https://physiology.presagetech.com/auth/login) —
  the assistant will use it to build a working app. The skill itself does not fetch a key for you,
  but if you also connect the [SmartSpectra MCP Server](mcp-server.md) the assistant can retrieve
  it from your account. Otherwise set it as described on your platform's setup page.
- **Network access** for the assistant while it works: the skill has the assistant read the live
  docs at `smartspectra.presagetech.com` for exact API detail. Without web access it can still
  apply the general model but cannot look up precise signatures.

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [Docs and FAQ](https://smartspectra.presagetech.com)
- [Developer Admin Portal](https://physiology.presagetech.com/auth/login)
