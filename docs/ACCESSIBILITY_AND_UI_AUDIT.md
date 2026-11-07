# HMX Arena — Accessibility and UI Audit

Date: 2026-11-07  
Branch: `feature/hmx-arena-android-production`  
Commit: `16bf24e`

## 1. Scope

Reviewed the shared design system (`Theme.kt`, `Glass.kt`, `LiquidGlass.kt`,
`HmxBackdrop.kt`, `ui/common/HmxGlassComponents.kt`, `CommonUi.kt`), the shell
(`GlassBottomBar.kt`), and the 24 screen files for:

- colour contrast and theme correctness;
- touch targets;
- semantics (labels, roles, selected state);
- font scaling and text overflow;
- keyboard/IME and scroll behaviour;
- adaptive layout and large-window use;
- motion and reduced-animation behaviour.

## 2. Colour and contrast

### 2.1 Theme tokens

| Token | Light | Dark |
| --- | --- | --- |
| Canvas | `#FDFDFE` | `#09090A` |
| Glass | white translucent | charcoal translucent |
| Ink (primary text) | near-black | near-white |
| InkSecondary / InkTertiary | grey ramp | grey ramp |
| OnInk (text on an ink capsule) | white | near-black |

Both schemes were designed separately rather than by colour inversion. The dark
scheme was hardened in an earlier pass: the bloom alpha is `0.035`, the canvas is
near-black, and the glass fill is charcoal, so no white wash sits behind light
text. `ThemeTokensTest` asserts the dark canvas luminance and that the dark
palette does not reintroduce a bright sheen/edge.

### 2.2 Contrast

- Primary body text uses `Ink` on `canvas`/`Glass`, which is a very high contrast
  pair in both themes.
- `InkTertiary` is used only for eyebrow labels and metadata (short, non-critical
  strings), not for input values or actions.
- Text placed on a filled control uses `OnInk`, which flips with the theme; the
  selected bottom-bar label is black-on-ink in light and near-black-on-ink in
  dark, matching the capsule.
- No gradient is painted behind critical text except the glass surface itself,
  whose tint is the same fill family and keeps the text on a predictable base.

**Limitation.** Automated WCAG contrast measurement was not run. The palette was
reviewed token-by-token and the design-system assertions guard the dark-mode
regression, but a formal contrast audit (e.g. a Compose screenshot + pixel
sampler) is a next-phase item.

## 3. Touch targets

`HmxDimens`:

| Token | Value |
| --- | --- |
| `iconButton` | 48 dp |
| `touchTarget` | 48 dp |
| `buttonHeight` | 54 dp |
| `fieldHeight` | 56 dp |
| bottom bar inner height | 58 dp (68 dp total with padding) |

- `GlassIconButton` renders at 48×48 dp.
- Back, theme-switch, share and overflow controls all use `GlassIconButton` or a
  Material `IconButton` (48 dp minimum).
- Bottom-bar tabs divide the bar into equal slices; on the rail each tab gets a
  full-height slice, so the target grows with the window rather than shrinking.
- Row-level `clickable` items (support entry, list cards) are padded to a
  comfortable height; no 20–30 dp tap-only control was found in the audit.

## 4. Semantics

- Bottom bar and rail tabs declare `Role.Tab` and `selected` state, and expose a
  content description of the label. A screen reader announces "Home, tab,
  selected".
- The theme switch exposes `"Switch theme (currently dark/light)"` so its state
  is discoverable without sight; the visual test harness relies on this string.
- The segmented theme selector declares `contentDescription = "Theme"` and each
  option exposes its own label.
- The password field has a show/hide toggle with a description; the search field
  has a "Search tournaments" description.
- Decorative icons (brand mark inside a titled card, status icon next to a
  status title) intentionally use `contentDescription = null` so they are not
  read twice. 36 such icons were counted; each was checked to be adjacent to a
  text label.
- Buttons built from `GlassSurface` + `clickable` set `Role.Button`.
- Dialogs use `HmxLiquidGlassDialog` with a title, message and labelled
  confirm/dismiss actions.

**Gap.** A few custom list rows are `clickable` without an explicit `Role`, and
the `contentDescription` of a 5-star or stat icon is `null` by design. Android
TalkBack still announces the row's text; the role is not essential for
navigation but could be added.

## 5. Font scaling

- All text uses Material 3 `sp` typography; no fixed `px` text sizes.
- `HmxTypography` defines explicit line heights so 1.0× and 1.3× scale keep
  rhythm.
- The bottom bar uses `labelSmall` inside a 58 dp inner bar; at 200 % scale the
  label line (≈26 dp), 20 dp icon and 4 dp gap sum to ≈50 dp, so the row still
  fits. The rail has more vertical room per tab.
- Long titles use `maxLines = 1` + ellipsis in the top bar and `maxLines` in
  cards; no unbounded single-line text was found in a constrained row.
- Forms use `verticalScroll`, lists use lazy containers, so a large font scale
  cannot clip the primary action.

**Residual risk.** Above 200 % font scale the horizontal bar can become tight on
a 320 dp-wide phone. The labels remain readable in the rail on larger windows,
and the bar could switch to intrinsic height in a future pass.

## 6. Keyboard, IME and focus

- The main activity uses `adjustResize` and `enableEdgeToEdge()`; content is
  inset with system bars.
- Fields use the Material text-field semantics, so focus order follows layout
  order.
- The bottom bar is hidden behind the IME on form screens rather than floating
  over it, because the scaffold's content is resized.
- No custom `focusRequester` chain that could trap focus was found; navigation
  uses standard `NavHost` destinations.

## 7. Adaptive layout and large windows

See `ADAPTIVE_DEVICE_LAYOUT_REPORT.md` for the full detail. Accessibility-relevant
points:

- content is capped at 760 dp / 980 dp, which keeps line length readable;
- the rail gives larger tap targets, not smaller ones;
- the tournament grid keeps a 12 dp gap and full-width state rows;
- one navigation component serves both orientations, so semantics do not
  diverge between phone and tablet.

## 8. Motion

- The selected-tab capsule uses a spring on the `Full` glass tier and snaps on
  `Reduced`.
- The theme change animates the glass material in place; no long transition
  blocks input.
- There is no parallax, autoplay video or infinite animation.
- The app does not read `Settings.Global.ANIMATOR_DURATION_SCALE`; Compose
  respects the system animator scale for its own animations, and the reduced
  tier already removes the only spring.

## 9. Dark / light correctness

- `HmxThemeBootstrap` applies an explicitly chosen theme before Compose starts,
  so the splash matches the in-app theme.
- `ThemeViewModel` persists `SYSTEM`/`LIGHT`/`DARK` in DataStore and mirrors it
  to a file read at process start.
- A `key`/`collectAsStateWithLifecycle` path re-renders on change without
  recreating the activity, preserving typed form input.
- The visual workflow checks:
  - Light and Dark screenshots for every screen;
  - an immediate in-place switch on the Arena screen with search text retained;
  - persistence of an explicit Dark choice across `am force-stop` and relaunch.

## 10. Findings summary

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| U-01 | P2 | Tablets/foldables previously got a stretched phone layout | Fixed (adaptive shell) |
| U-02 | P2 | Theme choice could be lost on process death in the capture harness | Harness fixed; product persistence was already correct |
| U-03 | P2 | Header divider / grey band under the top bar | Fixed earlier (`HmxGlassEdge.Top`) |
| U-04 | P3 | Header brand mark illegible in light theme | Fixed earlier |
| U-05 | P3 | Chips sized to fill row instead of content | Fixed earlier |
| U-06 | P3 | Some custom rows lack an explicit `Role.Button` | Open, low impact |
| U-07 | P3 | Formal WCAG contrast measurement not run | Open, tooling gap |
| U-08 | P3 | >200 % font scale can crowd the horizontal bottom bar | Open, documented |

## 11. Verification

| Item | Evidence |
| --- | --- |
| Theme tokens / dark regression | `ThemeTokensTest` (CI) |
| Adaptive breakpoints | `AdaptiveLayoutTest` (CI) |
| Light/Dark screenshots | `Liquid Glass visual verification` workflow |
| Theme switch + persistence | Visual workflow harness |
| TalkBack manual pass | **Not performed** (no device) |
| Contrast measurement | **Not performed** (no device/tooling) |

No visual identity change was introduced: the monochrome Liquid Glass palette,
shapes and typography are the same tokens as before the audit.
