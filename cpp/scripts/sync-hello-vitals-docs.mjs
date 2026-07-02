#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const cppRoot = resolve(__dirname, "..");
const helloVitalsDir = resolve(cppRoot, "samples", "hello_vitals");

const blockSources = {
  hello_vitals_cpp: {
    language: "cpp",
    path: resolve(helloVitalsDir, "hello_vitals.cpp"),
  },
};

const targetDocs = [
  resolve(cppRoot, "docs", "linux", "ubuntu-22-04.md"),
  resolve(cppRoot, "docs", "linux", "ubuntu-24-04.md"),
  resolve(cppRoot, "docs", "macos.md"),
  resolve(cppRoot, "docs", "windows", "index.md"),
];

const args = new Set(process.argv.slice(2));
const checkOnly = args.has("--check");

function renderFence(language, sourcePath) {
  const body = readFileSync(sourcePath, "utf-8").trimEnd();
  return `\`\`\`${language}\n${body}\n\`\`\``;
}

function replaceBlock(text, blockName, content) {
  const start = `<!-- BEGIN ${blockName} -->`;
  const end = `<!-- END ${blockName} -->`;
  const startIdx = text.indexOf(start);
  const endIdx = text.indexOf(end);
  if (startIdx === -1 || endIdx === -1 || endIdx < startIdx) {
    throw new Error(`Missing sync markers for ${blockName}`);
  }

  const before = text.slice(0, startIdx + start.length);
  const after = text.slice(endIdx);
  return `${before}\n${content}\n${after}`;
}

const renderedBlocks = Object.fromEntries(
  Object.entries(blockSources).map(([name, config]) => [
    name,
    renderFence(config.language, config.path),
  ]),
);

const updatedFiles = [];
for (const docPath of targetDocs) {
  let next = readFileSync(docPath, "utf-8");
  for (const [blockName, content] of Object.entries(renderedBlocks)) {
    next = replaceBlock(next, blockName, content);
  }

  const current = readFileSync(docPath, "utf-8");
  if (next !== current) {
    updatedFiles.push(docPath);
    if (!checkOnly) {
      writeFileSync(docPath, next);
    }
  }
}

if (checkOnly && updatedFiles.length > 0) {
  console.error("hello_vitals quickstart docs are out of sync:");
  for (const docPath of updatedFiles) {
    console.error(`- ${docPath}`);
  }
  console.error(
    "Run `node smartspectra/cpp/scripts/sync-hello-vitals-docs.mjs` and commit the regenerated docs.",
  );
  process.exit(1);
}

if (!checkOnly) {
  console.log(
    updatedFiles.length === 0
      ? "hello_vitals quickstart docs are already in sync."
      : `Updated ${updatedFiles.length} hello_vitals quickstart doc file(s).`,
  );
}
