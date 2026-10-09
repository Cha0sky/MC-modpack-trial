# Project instructions

This is a user-authorized NeoForge 1.21.1 implementation by DeepSeek Harness, supervised and independently accepted by Codex. Work directly in implementation mode. Do not delegate to other agents or spawn agents. Do not install plugins or upgrade Harness.

Allowed modifications: this evolvingblade directory and its build/run/test outputs only. Read-only input: ../_agent-team/SPEC.md and ../_agent-team/inputs/mdk/MDK-1.21.1-ModDevGradle-main. Never modify sibling projects, Minecraft instances, saves, launcher settings, credentials, shared Harness files, or user settings. Codex handles installation and shared status updates.

Use the official ModDevGradle template as the basis, Java 21, Minecraft 1.21.1, NeoForge 21.1.248. Do not mix in newer Minecraft APIs. Keep all gameplay authority on the server and all client class references in client-only classes. Do not use mixins unless an independently reproduced vanilla/NeoForge gap makes one necessary and Codex approves its exact scope.

Use UTF-8 and Chinese user-facing text. Preserve provenance/license of the template. Do not claim a test passed without executing it; report blocked commands with exact errors. Never print or read API keys or full credential/config documents. Follow the reviewed task in each prompt; the complete target behavior is in SPEC.md.
