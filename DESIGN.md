# Hermes Desktop — Exhaustive Design Spec (millimeter-level)

Source: latest `hermes-agent` repo, `apps/desktop` + `apps/shared`.
Provenance saved at `/root/desktop-app-reference/` (clean copy, no `node_modules`/`build`/`dist`).

- Upstream SHA: `ad03f20dd61919ca2135d6904e787a94284aacaf` (2026-09-11)
- Desktop version: `0.17.2` — "Native desktop shell for Hermes Agent"
- Stack: Electron + Vite + React 19 + `@assistant-ui/react 0.14.24` + nanostores + TanStack Query + Tailwind + Radix + Tabler + Codicon + CodeMirror
- Reference copy: `/root/desktop-app-reference/desktop/` + `/root/desktop-app-reference/shared/`
- Upstream design contracts: `apps/desktop/DESIGN.md` (visual contract) + `apps/desktop/AGENTS.md` (architecture contract) + `apps/desktop/src/AGENTS.md`

This file is the build-to-print spec. If code and this file disagree, fix whichever is wrong in the same change (upstream rule).

---

## 0. What the app is (and is not)

- Native chat surface for Hermes Agent. Not the browser dashboard, does not embed the TUI.
- Three authorities: Electron owns the machine (lifecycle, fs/git/windows, install/update, typed capability bridge). Renderer owns experience (nav, presentation, ephemeral interaction). Agent backend owns work (sessions, tools, models, streaming).
- Thin client: no agent behavior reimplemented in React. Everything agent-shaped goes through the gateway (REST + WS).
- Platforms: macOS + Windows + Linux. Installer handles Python 3.11+, portable Git, ripgrep. `hermes desktop` builds/launches against existing CLI install.

## 1. Window + native shell

- Electron `main.ts` + ~330 `electron/*.ts` modules (lifecycle, hardening, updater, window-state, zoom, workspace-cwd, git ops, fs-read-dir, WSL bridge, Windows CA/sandbox, OAuth net-request, dashboard-token, connection-config, gateway-ws-probe).
- Window minimums: app floor ~400px wide; popped session window enforces ~420px; rail docking floor `SIDEBAR_DOCK_MIN_WIDTH_PX = 640` (`(max-width: 639.98px)` collapses rails to hover-reveal overlay).
- Titlebar: native traffic-lights / Window Controls Overlay measured and reserved. Top-edge panels extend into titlebar band; tab strips stay in own zones; empty header space drags window; tabs/actions `no-drag`. Left cluster: sidebar, settings, layout editor, HUD controls. Right: flip + right-sidebar toggle; haptics in settings. Hold Cmd (Ctrl off macOS) 400ms reveals slot numbers over target strip status dots.
- Glass/translucency: default **29% Tint, Sidebar only**, both appearances, fade 0 so content column + text stay opaque. Saved settings win; defaults never overwrite user. Resolver: `apps/shared/src/translucency.ts` shared by renderer + Electron first paint.
- Zoom: persisted UI scale, re-applied on every full load + after resize. `zoom.ts` + tests.
- Updates: background check, one-click apply, in-place rebuild. `updates-overlay.tsx` + `updater-process.ts` + blockers tests. Boot chain z-ladder: `--z-connecting` → `--z-onboarding` → `--z-setup` → `--z-crash`.
- Hardening: narrow typed preload bridge. Renderer never touches Node/Electron directly.
- Find-in-page native fixture, WSL clipboard-image + path bridge, system-CA, spawn-helper perms, uninstall flow.

## 2. Information architecture + routes

Chat is home. Transcript + composer primary; tools/previews/files/review/terminal complement.

| Route | View | Shell treatment |
|---|---|---|
| `/` (new chat) + `/:sessionId` | `chat` | workspace pane, full chrome |
| `/skills` | `skills` | workspace full page (inside pane) |
| `/messaging` | `messaging` | workspace full page |
| `/artifacts` | `artifacts` | workspace full page |
| `/webhooks` | `webhooks` | overlay card |
| `/command-center` | `command-center` | overlay card |
| `/cron` | `cron` | overlay card |
| `/profiles` | `profiles` | overlay card |
| `/agents` | `agents` | overlay card |
| `/starmap` | `starmap` | overlay card |
| `/settings` | `settings` | overlay card |
| `/session-import` | `session-import` | overlay card |
| plugin `routes` contributions (e.g. `/kanban`) | `extension` | workspace full page |

- `APP_ROUTES`, `OVERLAY_VIEWS`, `isOverlayView`, `hidesFixedTitlebarClusters` in `src/app/routes.ts`. Overlays render as `OverlayView` cards and return to previous route on close. Pickers/dialogs layer above current surface, never a nav stack.
- Workspace pages (`skills/messaging/artifacts/extension`) render INSIDE workspace pane; `$workspaceIsPage` + `syncWorkspaceRoute` + `navigateToWorkspacePage` front the pane. Overlays float above chat (chat stays mounted beneath).
- One action, one home: keyboard + palette + visible button invoke same action/state.
- Projects own workspace cwd: Sidebar → Projects for local folders/worktrees. No per-session folder picker in right rail.
- Background events update badges/caches only. Never replace foreground transcript or steal focus.
- Contrib registries: `routes` (full page) + `sidebar.nav` (row below Artifacts, codicon + label + path). Shell registries are composition seams, not public plugin ABI.

## 3. Shell chrome (sidebar / titlebar / statusbar / palette)

### 3.1 Sessions sidebar (`src/app/chat/sidebar/`)
- Sections: sessions list (search, filter menu, pin, archive, delete, load-more row, reorderable list), Projects (folders + worktrees + dialogs + filters), Cron-jobs section, Gateway groups (group model, preferences, fleet rail), Profile switcher, Connection switcher (local/remote/cloud/ssh glyphs), order + row-geometry + section-states.
- Session rows: title (`sessionTitle`, `NEW_SESSION_TITLE`), unread dots (`session-unread`, `session-dot-state`), pin state on durable lineage-root id (survives auto-compaction id rotation), ownership hint (`getSessionOwnerHint`, `ProfileTag` when >1 profile), sync badge (`ChatSyncBadge`), drag (`$sessionTileDragging`, edge hover), context menu (rename/compress/branch/close/pin/archive/delete).
- Focusing sidebar preserves last active chat emphasis; dimming distinguishes panes without desaturating chat.
- Narrowest dock 640px; below that rails become hover overlay.

### 3.2 Titlebar (`src/app/shell/titlebar*`)
- Session-title dropdown (`TitleMenuTrigger`), model menu content, gateway menu panel, context-usage panel, status snapshot, window-controls-overlay width hook. Overlay open hides fixed clusters (no bleed over card).

### 3.3 Statusbar (`statusbar-controls.tsx`, `use-statusbar-items`)
- Connection, profile, layout, context, system-resources,电源, haptics, tabstrip prefs. Context menu per group. Visibility prefs persisted.

### 3.4 Command palette + Command Center
- `src/app/command-palette/`: global shortcuts entry, highlight-watcher, contrib pages (marketplace-theme, pet-palette), status rows. Single shared layer for shortcuts, never ad-hoc listeners.
- `src/app/command-center/`: sessions maintenance + delete-confirm. Deep-linkable via palette.

## 4. Chat transcript

- Built on `@assistant-ui/react` `Thread`. Extend `src/components/assistant-ui/*` + `src/app/chat/composer/*`. Never fork second markdown/message/tool/approval renderer.
- Markdown via Streamdown; code via `@streamdown/code` + CodeMirror (commands, language-data, lezer highlight); tables/fences/callouts/attachments use `--ui-stroke-tertiary` hairline (not `border-border` — too hot).
- Inline widgets (clarify, artifact card): `WIDGET_SHELL_CLASS` (`src/components/chat/widget-shell.ts`) — shared radius, `--ui-widget-surface-background` fill, no border; actions sit OUTSIDE below panel.
- Tool results may expose inline action opening preview; must NOT auto-open rail (intent before automation).
- Sticky user messages mask scroll with opaque chat surface incl. gap above; `data-glass-opaque` so Glass cannot clear; no gradient/blur.
- Virtualized transcript window (`transcript-window`, `transcript-tail-cache`, `transcript-backfill` with paging: `backfillOlderTranscriptPage`, `mergeOlderTranscriptPage`), scroll-to-bottom button, `$threadScrolledUp`, thread-loading states, read-only transcript mode, reactions (local + enabled flags), vibe-hearts (`HeartField`, `COMPOSER_HEART_CONFIG`), display-timestamps/toggles, human bubble transparency pref.
- Session identities: stable/durable id for nav/pins/persist; runtime tip id for live streaming; lineage root for post-compaction + pins. Translate at boundary.
- Server truth cached not owned: merge don't clobber; optimistic paint + visible rollback; stale-response guards (generation counters/request tokens); only foreground publishes to shared view; coalesce cosmetic noise, flush terminal transitions immediately; preserve ref identity on no-ops.
- Chat header: title, pin toggle, delete, profile tag (multi-profile only), model menu slot, connection glyph.
- Drop overlay: one visual owner per drop region; forgiving geometry; files/sessions/tabs/panes share affordance language; overlapping targets resolve to active.
- `pr-tag.tsx`, `profile-tag.tsx`, `preview-tile.tsx`, `pane-mirror.ts`, `perf-probe.tsx`, `new-session-drag.ts`.

## 5. Composer (ChatBar) — the most exacting surface

Location: `src/app/chat/composer/` (~120 files). Props: `ChatBarProps` (busy, cwd, sessionId, state, onSubmit/onCancel/onSteer/onEdit/onReload/onRestore/onRetryResume, attachments fns, voice fns, URL fns).

- Layout: floating dock (`composer-dock`: `composerFill`, `composerFloatingStrip`, `composerSurfaceGlass`). CSS vars: `--composer-*` (control gap/size, input min/max height, shell pads, width 640px-ish, ring strength, popout width). Never `transition-all` on hot path; name props.
- Controls row (`controls.tsx` + `ComposerControls`): Send (ArrowUp), Stop (StopFilled, sync UI clear even if cleanup async), mic/dictation, attach (files/folders/images), URL dialog, model pill, conversation pill/indicator, auto-speak, wake-word, HUD window buttons. Gaps via `--composer-control-gap`.
- Model pill (`model-pill.tsx`): current model, catalog menu (`model-catalog-menu`, search-fold, edit-submenu), visibility dialog, model-visibility overlays. Model search treats `-_. ` equivalently, preserves spelling in highlights; no dictionary spellcheck on ids.
- Input: rich editor (`rich-editor.ts`: `RICH_INPUT_SLOT`, normalize DOM, insert at caret, delete chip/selection, plain-text extract). IME composition guarded (`enter-stale-ime-flag`, `ime-composition-dom-repro`); Enter-submit DOM race tested; sanitized (`composer-input-sanitize`); text guards; history browse (Up/Down, `composer-input-history`); undo/redo (`undo-history`, shortcuts); drafts per session scope (`use-composer-draft`, `migrateSessionDraft`, route is source of truth for scope key); queue (park/migrate/unpark, `queue-panel.tsx`, pause/resume preserves disclosure); popout (drag, width `POPOUT_WIDTH_REM`, preference persisted); metrics/placeholder/focus chords.
- Completions: `@` (files, folders, symbols, contrib; folder keyboard nav), `/` slash (catalog descriptions single-line ellipsized + full text in themed tooltip sized to window, no selection intercept; `slash-refs`, `implicitSlashAcceptIndex`, `slashArgStage`), `:emoji:`, live-completion adapter. Trigger popover with parity tests + description tests.
- Refs: inline-refs (`droppedFileInlineRefs`, `pathifyRefs`, `chipTypedPathOnSpace`), URL refs (`linkifyUrls`, `chipTypedUrlOnSpace`, `url-dialog.tsx`), PR comment URLs (`PR_COMMENT_URL_RE`), pasted clipboard images → focus, drag-drop (`use-composer-drop`, `use-file-drop-zone`), attachments list with previews (`attachments.tsx`, image height/max-width vars).
- Directives: chips expose action on hover, 500ms grace crossing to floating pill; scope dialog; directive-actions.
- Voice: recorder hooks (`use-mic-recorder`, `use-voice-recorder`, `use-voice-conversation`, rearm), activity UI (`voice-activity.tsx`), playback, transcription (`onTranscribeAudio`), TTS lease/speak, auto-speak replies toggle, wake-word, voice providers settings, stop-word intercept. Max 120s default.
- Micro-actions + action badges, suggestion pills, help-hint, context-menu, contrib middleware (`COMPOSER_AREAS`, `runComposerMiddleware`), tour markers.
- Status stack (`status-stack/`): collapsed by default except todos; CodingRow, PreviewRow (file/preview links pinned at stack bottom below queue + groups), GoalIndicator, SessionControl (+Loop/Heartbeat/utils), SubagentControls/Section/Snapshot/Transcript (hydration + lifecycle tests), collapsed-indicator, polling-guard, preview-order. Error banners meet stack top edge, no blank strip.
- Guards: `shouldDisableComposerInput`, `sessionBlockingPrompt`, `sessionCompacting`, focus (`focus.ts`, `focus-chord.ts`, `paste-to-focus.ts`), Esc cancels active interaction OR closes topmost dismissable — never both/neither.
- Haptics on send/success; completion sound; keyboard-first resolvers.

## 6. Right-rail previews + files + terminal + review

`src/app/chat/right-rail/` + `src/app/right-sidebar/` + panes stay mounted when hidden (visibility ≠ lifecycle).

- `preview-pane.tsx` + `preview.tsx` + `preview-nav.ts` (history), `preview-act.ts`, `preview-input.ts`, `preview-drive.ts`, `preview-mind.ts`, `preview-nudge.ts`, `preview-tour.ts`.
- Types: `preview-browser-bar` (URL bar + titles), `preview-file` (text/code/image/video with seek via `fetchLocal` ranges), `preview-artifact`, `preview-console` (state + store), `preview-annotate-card/host`, `preview-reader` (readability), `preview-script-runner`, real-profile-consent dialog.
- Files pane (`right-sidebar/files/` + `file-actions.tsx`): browse workspace cwd, row height `--file-tree-row-height`, new folders, text preview, open-in-browser.
- Terminal pane: persistent, focus owns keys (shell shortcuts must not steal terminal/editor bindings), backend panel + font setting.
- Review pane: git diffs (`tool-diffs`), worktree ops, PR helpers.
- `preview-persistence.ts`, `preview-status.ts`, `preview-edit.ts`, `preview-open-browser` tests.

## 7. Capabilities pages (workspace full pages)

### Skills (`src/app/skills/`, modes: skills/toolsets/mcp/plugins/collective)
- Master/detail (`master-detail.tsx`): `ListColumn` + `DetailColumn`, `CapRow`, `ListStrip` (+buttons/menus), `ToolChip`.
- Skills tab: installed list, enable toggle (`setSkillEnabled`), content view (`getSkillContent`), official skills, usage analytics (365-day scan, 10-min TTL, per-scope cache), search shell, refresh hotkey, profile-switch hook.
- Toolsets tab: catalog, enable, config panel, model assignments, usage badges, desktop-toolset visibility filter.
- MCP tab: servers (toggle/test/delete), catalog install with secret prompts, deeplink install, health, setup.
- Plugins tab: packages, install modal, enable, socket scope tests.
- Collective tab: shared skills.
- Editors: wisdom-file + wisdom-manifest (+validation), code editor, archive-skill confirm.
- Hub: `EmbeddedHubPicker`, hub-actions store (install/preview/scan/search/sources), slash-cache invalidation on change.

### Messaging (`src/app/messaging/`)
- Platforms list, adapter states, env-var info, home channels, tests, updates; Telegram QR setup (`telegram-qr-setup.tsx`), onboarding start/apply/status; platform icons.

### Artifacts (`src/app/artifacts/`)
- Aggregated across sessions (`loadArtifactsForSessions`, `artifact-utils`): filters (all/images/files/links), image src resolver (gateway media download, remote-gateway aware), pagination (7-window with ellipsis, range labels), zoomable images, copy buttons, row buttons, link titles (`useLinkTitle`, short host labels), remote-open, search shell, refresh hotkey, open-session jump.

### Webhooks (`src/app/webhooks/`, overlay)
- Routes CRUD, enable toggle, create payload/response, REST scope tests.

### Command Center (`src/app/command-center/`)
- Sessions maintenance (bulk ops), delete-confirm. Statusbar integration.

### Cron (`src/app/cron/`, overlay, Panel* primitives)
- List (PanelList/Row/Meta/Pill/SectionLabel), states (`jobState`, `jobTitle`, `STATE_DOT`, tones: good/warn/muted/bad), focus id store, live-sync ticks.
- Editor: name, prompt (Textarea), schedule presets (daily/weekdays/weekly/monthly/hourly/every-15m/custom cron expr) + validation, delivery targets (`local` default, checkboxes, parse/toggle), model override (sentinel `__default__`), provider, blueprints (slots, help, errors, instantiate), script-only detection.
- Actions: create/update/delete/pause/resume/trigger + refresh (`cron-actions.ts`), profile scope (`cronProfileForScope`, ALL_PROFILES).

### Profiles (`src/app/profiles/`, overlay)
- List, create/rename/delete dialogs, switch with failure handling, remote-override dialog, share, select-source, agent-activation, scope tests. Live swap activates other profile socket; background keeps streaming; lists merge.

### Agents (`src/app/agents/`, overlay)
- Live gateway processes, jump-to-session, notices, delegation.

### Starmap (`src/app/starmap/`, overlay)
- Cross-session run graph: `star-map.tsx` (render/simulation/geometry/color/text/time-axis), timeline, node context menu, share-code/controls, types + constants.

### Session import (`src/app/session-import/`, overlay)
- Import flow + route.

## 8. Gateway + connections + profiles (transport)

- Modes: `local` | `remote` | `cloud` | `ssh` (+ env override). Android keeps **remote + cloud only** (no local runtime, no SSH on phone).
- Remote: self-hosted `hermes serve`; `POST /auth/password-login` + cookie; WS single-use `?ticket=` minted at `POST /api/auth/ws-ticket`; static `?token=` fallback. OAuth vs token auth modes; keychain secure storage (plain-text warning when unavailable); URL scheme coercion; probe (must exercise WS/auth leg, not just HTTP — HTTP-only pass is false positive); one-time tickets never reused (mint fresh every dial; mint failure = reauth, not cached-URL fallback).
- Cloud: Nous-hosted endpoint, org select, discovery lifecycle (idle/loading/done/error), same ticket flow.
- Registry: `$connectionsRegistry`, `$activeConnectionId`, `selectConnection`, `refreshConnectionsRegistry`, connection-scoped + profile-scoped request routing (`get/setApiRequestConnection/Profile`, `profileScopeKey`). After any swap active socket + active profile + connection atoms must agree.
- Switch shapes: connection/mode apply = soft re-home (shell stays, gateway-bound stores wiped, reconnect — query invalidation alone insufficient); `HERMES_HOME` change = hard re-home (reload); live profile swap = background keeps streaming, foreground draft only on explicit select.
- Ladders everywhere: precedence written as data/pure fn; validate at boundary (existence ≠ proof); failed read falls through, failed authoritative write surfaces/rolls back; missing capability vs transient failure distinguished; bounded retries ending in real recovery affordance.
- Backend compat: preserve feature on older runtimes, narrow fallback tied to identified version + test.

## 9. Settings (overlay, `OverlaySplitLayout` + `OverlaySidebar`/`OverlayMain`)

Nav groups: config sections + Notifications + Billing + Providers (Accounts/API keys/Custom endpoints/Local[flag-gated]) + Gateway (+legacy `connections` alias → gateway) + Keybinds + Keys (Tools) + Vault (+Browser-adjacent) + Sessions + About. Deep-linkable `?tab=` + `?pview=`/`?kview=`; moved `mcp|plugins` tabs redirect to Capabilities. Search (`settings-search`, `use-settings-search`, deep-link highlight).

- Config sections (`SECTIONS`, `config-settings.tsx`, `config-field.tsx`): model, browser (+real-profile panel, vault adjacency), computer-use, terminal (backend + font), toolsets, sessions, custom endpoints, env credentials, credential-key UI, SSH host select, pool limits, quick-entry, voice providers/fields, fallback models, uninstall section, import/export (`hermes-config.json`), reset-to-defaults (ConfirmDialog).
- Providers: accounts (OAuth), API keys, custom endpoints (validation), local models (behind `--local` flag only).
- Keys: tool approvals wiring.
- Vault: owner key, save-login/unlock/code prompts, model-blind fill.
- Notifications + native notifications, completion sound.
- Billing: entitlement, payment methods, policy, usage analytics.
- Sessions: density, color, retention, removal.
- Appearance (`appearance-settings.tsx`): color mode (SegmentedControl), themes (presets, install, user-themes, backend sync, retint, VSCode import, accent override dev-only), translucency (tint/sidebar/fade), HUD, pet, language.
- Keybinds: rebindable, `bindingsFor`, `KbdCombo`, composer-focus keys, type-to-focus, `TipKeybindLabel` in tooltips (never hardcoded combos).
- About: version, BrandMark, update channel, diagnostics (`send-diagnostics-dialog`), managed-updates section.
- Rows: `ListRow` (label/description/action), flat flush-left; dividers only when needed (`--ui-stroke-tertiary` hairline). Gutters: `PAGE_INSET_X = px-[clamp(1.25rem,4vw,4rem)]`, bleed `PAGE_INSET_NEG_X`, inner cap `PAGE_MAX_W = max-w-[75rem]`.

## 10. Visual system (tokens over literals)

- Rule: flat not boxed (whitespace + single hairline, never card-in-card); borderless elevation for floats (`shadow-nous` + `--stroke-nous` hairline); one primitive per concern; style lives in primitive (call sites pass variant/size, never `h-*`/`px-*` overrides); tokens not literals.
- Palette seeds: `--theme-background-seed/bubble-seed/card-seed/elevated-seed`, `--theme-asset-bg`, `--theme-accent-soft`, control mixes; mapped to `--dt-*` (background/foreground/card/popover/primary/secondary/muted/accent/destructive/input/ring/sidebar/composer-ring/user-bubble/scrollbar) + `--ui-*` (text primary/secondary/tertiary, strokes primary→quaternary, bg-quaternary, widget-surface, accent) + `--chrome-action-hover`. White tile in BrandMark is the one sanctioned literal.
- Strokes: `--ui-stroke-tertiary` = default in-panel divider + every bordered transcript surface; `--stroke-nous` = overlay hairline (pairs `shadow-nous`); menus/popovers `shadow-md` + `--ui-stroke-secondary`.
- Shadows: `--shadow-nous` (down-weighted layered contact→ambient), `--shadow-composer`, `xs/sm/md/lg`. Tune in `styles.css` once.
- Radius: `--radius-*` (sm/md/lg/xl/2xl/3xl/4xl + scalar); buttons `rounded-[2.5px]` text / `4px` icon; text buttons square (padding + line-height, no fixed heights).
- Spacing: `--spacing-mul`, `--dt-spacing-mul`; composer vars (`--composer-control-gap/size`, input min/max, shell pads, width); file-tree row height; chat min width; conversation line-heights + turn/scaffold gaps; HUD band/bar heights.
- Typography: display `Collapse` (wordmark, `Collapse-Bold.woff2`), sans system, mono `JetBrains Mono` (Regular/Bold/Italic bundled), kbd font var; sizes via `--conversation-text-font-size/tool-font-size/caption`, `--dt-base-size`, letter-spacing/line-height vars.
- Motion: ~100ms functional on controls; `prefers-reduced-motion` beyond fade; AnimatedInt spring (direct DOM, no per-frame React); onboarding matrix stagger (outer fade delayed so inner plays); motion follows state never delays it; no `transition-all` on hot geometry; windows keep animating when unfocused (hidden/minimized may pause; polling focus-gated).
- Loader: `Loader` animated math/ascii `lemniscate-bloom` for long ops. Never literal "Loading…".
- z-ladder vars: `--z-modal-backdrop/modal/modal-popover`, `--z-over-modal(+content)`, `--z-switcher(-backdrop)`, `--z-connecting→onboarding→setup→crash`. Plain `z-10/20` only within one component.

## 11. Primitives (one per concern)

- `Button` (`button.tsx`): variants `default/destructive/secondary/outline/ghost/link/text/textStrong`; sizes `default/xs/sm/lg/inline/micro/icon/icon-xs/icon-sm/icon-lg/icon-titlebar`. SVGs inherit `size-3.5` (`size-3` at xs). `asChild` polymorphism.
- `Badge`: `default/muted/warn/destructive/outline/solid`; sizes `default/xs/overlay`.
- Forms: `controlVariants` (`control.ts`) for Input/Textarea/SelectTrigger; `SearchField` (borderless, underline-on-focus, auto-width — the ONLY search; empty lists hide it); `SegmentedControl` (mutually-exclusive sets); `Switch size=xs` bare + aria-label.
- Rows/layout: `ListRow`, `OverlaySplitLayout` + `OverlaySidebar/Main`, `Panel*` (cron), `MasterDetail` (skills), `RowButton`, `CopyButton`, `GenerateButton`, `Pagination`, `Tabs`, `Sheet`, `Popover`, `Select`, `Checkbox`, `Progress`, `Skeleton` (+CountSkeleton), `ScrollArea`, `Separator`, `AvatarChip`, `StatusPulse`, `StatusDot`, `DiffCount/AnimatedInt`, `Kbd/KbdCombo`, `FadeScroll/FadeText`, `DecodeText`, `HighlightMatches` (model opt-in hyphen/dot/underscore/space equivalence), `FileTypeIcon`, `ToolIcon`, `ConnectorCard/Logo`, `ProfileGlyph`, `Favicon`, `Zoomable`.
- Dialogs: base `Dialog` (shadow-nous + stroke-nous) + `ConfirmDialog` (ONLY "are you sure"; opens focused on Confirm so Enter=confirm Esc=cancel; pending→done→close + inline error; one `secondaryAction` slot; never `window.confirm`; inline callers use `confirm()` store → single `ConfirmHost`); `notify()` → single host for toasts.
- States: `Loader`, `ErrorState` (+canonical `ErrorIcon`, no chip; Radix Title/Description passthrough), `LogView` (no bg, hairline, tight mono), `EmptyState` (page bodies) / `PanelEmpty` (overlay master/detail + icon + action), `PageLoader`.
- Overlays: `OverlayView`, `OverlayIconButton`, `Panel*`, `Dialog*`, prompt overlays (clarify/approve/vault), `ConfirmHost`, boot/connecting/onboarding/install/update/reauth surfaces sharing primitives but distinct recovery semantics.

## 12. Iconography + brand (no emoji anywhere)

- Tabler default chrome set via `src/lib/icons.ts` curated aliases + `iconSize` (`xs 12 / sm 14 / md 16 / lg 20 / xl 24`). ~130 aliases: Activity, AlertCircle/Triangle, AppWindow, Archive, ArrowUp/Right, AtSign, AudioLines, BarChart3, Bell, Bookmark, Box, Brain, Bug, Check, Chevron*, Clipboard, Clock, Cloud, Command, Copy, CornerDownLeft, Cpu, CreditCard, Download, Ear, Egg, Eject, ExternalLink, Eye, FileImage/FileText, FolderOpen, GitBranch/Fork, Globe, Hash, HelpCircle, ImageIcon, Info, Keyboard, KeyRound, Layers3, LayoutDashboard, Link, Loader2, Lock, LogIn, Mail, Maximize, Message*, Mic, Monitor(+Play), Moon, MoreH/V, Network, NotebookTabs, Package, Palette, Panel*, Pause, PawPrint, Pencil, Pin, Play, Plus, Power, QrCode, RefreshCw, Save, Search, Send, Settings(2), ShieldLock, Sliders, SmilePlus, Square, Starmap, SteeringWheel, StopFilled, Sun, Terminal, Trash2, Upload, Users, Volume2/X, Wrench, X, Zap(+Filled), ZoomIn/Out. Never import icon packages directly in feature code.
- Codicon compact editor/tool/status vocab via `codicon.tsx` + `codiconIcon()` adapter (font-based `codicon-*`, spin modifier). Pick by semantic context; reuse existing icon per action; no third set; no mixed styles in one group.
- BrandMark (`brand-mark.tsx`): `nous-girl.jpg` on white tile, softly rounded (`rounded-md`), `size-14` default, identical light/dark. Replaces scattered Sparkles. Hero/brand moments only. App icons: `assets/icon.{png,icns,ico}` + `public/nous-girl.jpg`, `nous-badge.png`, `intro-nous-girl.png`, `hermes.png/sprite`, apple-touch-icon.
- Tooltips (`Tip`, 200ms first-open / 300ms warm-instant / immediate close; `OverflowTip` longer): ONLY when hover teaches something new (toolbar/titlebar/statusbar icons, keybind/detail, ownership chips, icon grids, slash full descriptions). NEVER on menu triggers/kebabs, close X (`aria-label` only), or label-redundant controls. Never native `title=` (themed Tip only; lint-enforced). Keybind hints via `TipKeybindLabel`/`useKeybindHint`, never hardcoded.
- Cursor `pointer` at primitive; global focus-ring reset; titlebar actions no active-bg; Esc closes every dismissable (install/onboarding excluded); close = x-icon not word.

## 13. Data + API domains

`src/api/` split by domain behind `@/hermes` barrel; `client.ts` owns profile/connection/capability scoping (single owner). `src/types/hermes` canonical shapes. React Query for request-shaped data; nanostores for shared UI truth; refs for hot non-paint coordination.

- sessions (list/search/pin/archive/delete, transcript paging, create/resume/branch/compress/rename/close, messages, runtime info), models (options/catalog/visibility/presets/assignments/auxiliary), config (record/defaults/save/schema/fields), cron, skills, toolsets, MCP (servers/catalog/test), messaging, wisdom (entitlement/sync/mute), profiles, system (status/logs/updates/computer-use/audio/pairing/billing/analytics), plugins, local-models, webhooks.
- Analytics: daily/model/skill entries, totals (sessions/messages cards, per-model 7/30/90d).
- Tolerant decoding; capability probing per surface; older-backend fallbacks narrow + tested.

## 14. Keyboard, cancellation, feedback, i18n, perf

- Focus wins keys; global shortcuts via shared layer; type-to-focus chars; one cancel = one thing (active interaction OR topmost dismissable); sync UI clear on cancel; non-dismissable flows say so.
- States are distinct: empty/loading/reconnecting/degraded-stale/exhausted-recovery each get honest copy + way out.
- Toasts via `notify()/notifyError()`; haptics (`triggerHaptic`) + completion sound; native notifications opt-in.
- i18n: every string via `useI18n()`; locales `en/ja/zh/zh-hant` (+ar/ru/catalog) updated TOGETHER — skipping one is a regression; trailing punctuation + tone consistent.
- Perf: hot state local/derived; no heavy-tree subscription to per-frame; coalesce pointer work; no layout-read-after-style-write; expensive surfaces stay mounted; prove with realistic long transcripts/busy terminals.

## 15. Onboarding / boot / install / auth

- First-run: install (Python/Git/rg) → connect (local auto / remote form / cloud org discovery / SSH) OR free-tier sign-in → provider+model → first message in seconds. Distinct states: install/onboarding/connecting/boot-failure/reauth share primitives, keep recovery semantics. Fake-boot dev flag for iteration.
- Reauth: mint failure → reauthenticate (never cached-URL fallback for one-time creds); long-lived token/local may reuse cached URL as lower rung.

## 16. Voice + media

- Mic/dictation, conversation mode with rearm, activity + playback UI, transcription, TTS lease/speak, auto-speak toggle, wake-word, provider fields. Local media seeking via production `fetchLocal` ranges (chat video seeks).

## 17. Android mapping (Hermey-beta: remote + cloud only)

- Keep: Chat (streaming transcript, tool calls, clarify + approval prompts, model picker, stop, slash autocomplete, image/file attachments, voice input, usage meter, rename/compress/branch/close), Sessions (list/search/pin/archive/delete), Skills/Toolsets/MCP/Plugins/Collective/Hub, Cron CRUD, Gateways (remote/cloud + server profile switch), Workspace (server file browser, text preview, new folders), Messaging states, Pairing approve/revoke/clear, Agents jump-to-session, Starmap history, Insights analytics, Memory status/reset, Settings (active model, memory, appearance system/light/dark, sign out), Artifacts grid.
- Drop: local mode, SSH mode, Electron window mgmt (titlebar/rails/popouts), local-models flag, WSL/Windows CA/sandbox, installer/updater-rebuild, file-system direct access (use server browser), terminal-backend local (server logs only if exposed).
- Tokens: `ui/theme/HermesTokens.kt` computes same seeds as `styles.css`; `HermesVariant` = Button variants; borderless SearchField; SegmentedControl; ListRow; Loader/ErrorState/LogView/EmptyState; ~100ms motion; JetBrains Mono for code; Collapse for wordmark; BrandMark white tile with nous-girl; Tabler-equivalent Material icons (no emoji); tooltips → long-press hints where hover N/A; Esc → system back (one dismiss at a time).
- Auth: Remote `POST /auth/password-login` + cookie, WS `?ticket=` minted per dial + `?token=` fallback; Cloud Nous sign-in + discovery + cascade. 401 → reauth screen, never silent retarget.

## 18. Do-not-regress checklist (upstream, enforced)

Reuse primitive; tokens not literals; no className overriding primitive chrome; tips only where hover teaches; no native title; keybind hints via shared hooks; overlay elevation tokens; flat; no background nav/focus steal/pane auto-open; optimistic paint + rollback; cheap hot paths; correct keyboard/Esc; all locales updated; cursor/focus/Esc behave; primitive/token change updates this file same change.

---

*Generated from separated reference copy. Re-verify against `/root/desktop-app-reference/desktop/src` when code moves.*
