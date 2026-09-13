# Hermey Beta — Handoff

## What this is
Native Android client for Hermes Agent — exact mobile port of the desktop app.
Kotlin + Jetpack Compose + Material 3. Thin gateway client: nothing runs on the phone.

## Repo
- `https://github.com/kyssta-exe/hermey-beta.git` (origin, push+fetch)
- Working dir: `/root/hermey-beta`

## Connection modes
- **Remote gateway** — self-hosted `hermes serve`, POST login + cookie, WS via `?ticket=`, static `?token=` fallback
- **Cloud gateway** — Nous-hosted endpoint, same auth flow
- No local/SSH mode (impossible on mobile)

## Feature parity (desktop → Android)
Chat · Drawer (all screens) · Tasks/Kanban · Sessions · Skills · MCP · Tools · Cron · Gateways · Workspace · Messaging · Pairing · Agents · Starmap · Insights · Memory · Settings · Artifacts · Webhooks · Command Center · Session Import

## Key files
- `app/src/main/java/com/kyssta/hermeybeta/` — source
- `app/src/test/` — unit tests (34 green)
- `DESIGN.md` — exhaustive desktop spec (32K), also at `/root/desktop-app-reference/DESIGN_EXHAUSTIVE.md`
- `README.md` — build + connection docs

## Releases
Tags `v*` → signed release APK on GitHub Releases. Debug APK on every push to main.

## Current state
Latest: `d1609d4` — Drawer everywhere + live chat 0.8.0 (clean working tree).

## Build
```bash
./gradlew assembleDebug   # JDK 21, Android SDK 35
```

## Ponytail notes
- `# ponytail: fixed handshake grace; upgrade to onOpen-gated dial when reconnect logic lands.` (WsRpc.kt)
- `# ponytail: teal has no Hermes token; spec mandates #2DD4BF for the TODO dot.` (TasksScreen.kt)
