# Hermey Beta

Native Android client for [Hermes Agent](https://github.com/nesquena/hermes-agent) — an exact mobile port of the Hermes **desktop app**.

Built with Kotlin + Jetpack Compose + Material 3. A thin gateway client: nothing runs on the phone.

## Connection modes

Remote gateway and cloud gateway only (mirrors the desktop connection switch minus local mode, which cannot exist on mobile):

- **Remote Gateway** — your self-hosted `hermes serve` (`POST /auth/password-login` + cookie, WS via single-use `?ticket=` minted at `POST /api/auth/ws-ticket`, static `?token=` fallback)
- **Cloud Gateway** — Nous-hosted Hermes endpoint, same auth flow

## Features (desktop route parity)

- Chat — streaming transcript, tool calls, clarify + approval prompts, model picker, stop, slash commands with server autocomplete, image/file attachments, voice input, context usage meter, rename/compress/branch/close
- Sessions — list, search, pin, archive, delete
- Skills — browse and toggle, hub search/install/reload
- MCP — servers (toggle/test/delete), catalog install with secret prompts
- Tools — tool + toolset catalogs
- Cron — full CRUD (add/remove/pause/resume)
- Gateways — remote/cloud connections plus server-side profile switching
- Workspace — server file browser, text preview, new folders
- Messaging — channel adapter states
- Pairing — approve/revoke/clear device requests
- Agents — live gateway processes, jump to session
- Starmap — cross-session run history
- Insights — sessions/messages cards, per-model analytics (7/30/90 days)
- Memory — status + scoped reset
- Settings — active model, memory status, appearance (system/light/dark), sign out
- Artifacts — files/images/links aggregated across sessions (filters, search, open-session jump)
- Webhooks — receiver enable + subscription CRUD (create/toggle/delete)
- Command Center — sessions maintenance, system status + logs, usage analytics (7/30/90 days)
- Session Import — Claude/Codex foreign sessions (list/preview/import)

## Design

Exact port of `apps/desktop/DESIGN.md`: flat surfaces, token-derived palette (`ui/theme/HermesTokens.kt` computes the same seeds as `styles.css`), one button primitive (`HermesVariant`), borderless `SearchField`, `SegmentedControl`, `ListRow`, `Loader`/`ErrorState`/`LogView`/`EmptyState`, ~100ms motion, no emoji icons.

## Building

```bash
./gradlew assembleDebug
```

Requires JDK 21, Android SDK 35. CI builds a debug APK on every push to main; tags `v*` build a signed release APK attached to GitHub Releases.

## License

MIT
