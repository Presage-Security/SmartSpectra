---
title: SmartSpectra MCP Server
description: Connect an AI assistant to your SmartSpectra account with the hosted MCP server — API keys, plan usage, app registrations, and docs search.
---

# SmartSpectra MCP Server

> **Important:** SDK metrics are offered for general wellness and informational purposes only. SDK metrics have not been cleared by the FDA and may not be used for medical diagnosis or treatment.

Presage hosts a [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server at:

```text
https://mcp.presagetech.com/mcp
```

Connect it to an MCP-capable AI assistant — Claude Code, claude.ai, Codex, Cursor, or any
other compliant client — and the assistant can manage your SmartSpectra developer account for you:
fetch or rotate your API key, check plan usage, register apps, and search these docs.

It pairs with the [AI agent skill](agent-skill.md): the skill teaches an assistant how to build
with the SmartSpectra SDK; the MCP server gives it authorized access to your account, so
"set up a new Android app and wire in my API key" works end to end.

## What you need

- A **SmartSpectra account** — the same login as the
  [Developer Admin Portal](https://physiology.presagetech.com/auth/login). Sign-in happens in
  your browser through OAuth; the assistant never sees your password.
- An **MCP client that supports the streamable HTTP transport and OAuth**. The server uses
  standard OAuth 2.1 with dynamic client registration, so no client ID or manual token setup is
  required — just the URL.

## Connect from Claude Code

Add the server, then authenticate:

```bash
claude mcp add --transport http smartspectra https://mcp.presagetech.com/mcp
```

Run `/mcp` inside Claude Code, select `smartspectra`, and choose **Authenticate**. Your browser
opens to the SmartSpectra login; approve the consent screen and the tools become available.

## Connect from claude.ai or Claude Desktop

1. Open **Settings → Connectors → Add custom connector**.
2. Enter `https://mcp.presagetech.com/mcp` as the server URL and save.
3. Click **Connect** and complete the browser login.

## Connect from Codex

Add the server, then authenticate:

```bash
codex mcp add smartspectra --url https://mcp.presagetech.com/mcp
codex mcp login smartspectra
```

`codex mcp login` opens your browser for the SmartSpectra sign-in. Run `codex mcp list` to
confirm the server shows as connected.

## Connect from other MCP clients

Any client that speaks streamable HTTP with OAuth works. For clients configured with JSON
(Cursor and similar), add:

```json
{
  "mcpServers": {
    "smartspectra": {
      "url": "https://mcp.presagetech.com/mcp"
    }
  }
}
```

The client discovers the OAuth endpoints automatically and opens a browser to sign you in.

## Available tools

| Tool | What it does |
| --- | --- |
| `api_keys.get` | Return your current SmartSpectra API key. |
| `api_keys.rotate` | Generate a new API key and invalidate the old one. |
| `usage.get_plan` | Report your plan and current usage. |
| `apps.list` | List your registered app IDs and their verification status. |
| `apps.register` | Register (or update) an Apple or Android app ID. |
| `apps.get_config` | Fetch the SDK configuration for a registered app. |
| `apps.delete` | Delete an app registration. |
| `account.delete` | Permanently delete your SmartSpectra account. |
| `docs.map` | List the pages of this documentation site. |
| `docs.search` | Search this documentation. |
| `docs.read` | Read a documentation page. |

The server also exposes a `using-smartspectra` prompt that loads the
[agent skill](agent-skill.md) content directly, for clients without skill support.

## Confirmations for destructive actions

Tools that change your account — `api_keys.rotate`, `apps.register`, `apps.delete`, and
`account.delete` — never act on the first call. The first call returns a
`confirmation_required` response describing exactly what will happen; the assistant must call
the tool again with the returned confirmation token to proceed. Tokens are short-lived, so a
stale confirmation cannot be replayed later. Assistants surface this as an explicit "confirm
this action" step — if yours does not, decline and run the action from the
[Developer Admin Portal](https://physiology.presagetech.com/auth/login) instead.

> **Important:** `account.delete` permanently deletes your API keys, sign-in, and account data.
> `api_keys.rotate` invalidates the current key immediately — every app using it stops
> authenticating until you deploy the new key.

## Security notes

- `api_keys.get` places your API key in the assistant's conversation context. Treat that
  conversation as sensitive, and rotate the key if you believe it leaked.
- To revoke the assistant's access, remove or disconnect the server in your MCP client's
  settings. Sessions also expire on their own and require re-authenticating.

## Getting Help

- Email: [support@presagetech.com](mailto:support@presagetech.com)
- [Submit a GitHub issue](https://github.com/Presage-Security/SmartSpectra/issues)
- [Docs and FAQ](https://smartspectra.presagetech.com)
- [Developer Admin Portal](https://physiology.presagetech.com/auth/login)
