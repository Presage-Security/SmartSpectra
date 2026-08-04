# SmartSpectra SDK — agent skill

[SmartSpectra](https://smartspectra.presagetech.com/) (Presage Technologies) measures
vitals — pulse, breathing, and more — from an ordinary camera or a recorded video. This
repo ships the `using-smartspectra` agent skill, which teaches a coding agent how to build
apps with the SmartSpectra SDK: getting an API key, the config → start/stop/reset lifecycle,
choosing metrics, and integrating on your platform (C++, Swift/iOS, Kotlin/Android, Node).

The skill is one portable `SKILL.md` in the standard [Agent Skills](https://agentskills.io)
format, so the same skill works in both Claude Code and OpenAI Codex.

## Install — npx (any agent)

The quickest path is the open [`skills`](https://skills.sh) CLI — one command reads the skill
from this repo and installs it, prompting for your agent:

```bash
npx skills add Presage-Security/SmartSpectra --skill using-smartspectra
```

## Install — Claude Code

Add this repo as a plugin marketplace, then install the plugin:

```text
/plugin marketplace add Presage-Security/SmartSpectra
/plugin install smartspectra-sdk@smartspectra
```

The plugin also bundles the hosted SmartSpectra MCP (Model Context Protocol) server. To use it,
run `/mcp`, select `smartspectra`, and choose **Authenticate** — your browser opens to the
SmartSpectra sign-in, and the account tools become available once you approve.

## Install — Codex

Install the skill straight from this repo:

```text
$skill-installer Presage-Security/SmartSpectra/.agents/skills/using-smartspectra
```

Or, when working inside a clone of this repo, Codex discovers it automatically from
`.agents/skills/`.

## Use it

Ask your agent to use SmartSpectra — e.g. *"build a minimal app that measures and displays
my pulse"* — and the `using-smartspectra` skill guides it the rest of the way.

The skill covers getting an API key as a manual step. The MCP server does that part for the agent —
it can fetch or rotate your key, register an app ID, and download its OAuth config file. The Claude
Code plugin bundles it; on the npx and Codex paths, connect it yourself:
<https://smartspectra.presagetech.com/docs/mcp-server>.

Full guide (both agents): <https://smartspectra.presagetech.com/docs/agent-skill>. For SDK API
references and per-platform sample apps, see <https://smartspectra.presagetech.com/>.
