# Durion Style Guide

This guide is derived from `durion/theme/durion-theme.css` and the `durion/theme/fonts` + `durion/theme/images` asset folders.

Excluded by request:
- `durion/theme/tiotf-theme.css`

## 1. Brand Foundation

Durion uses a cool industrial palette:
- Blueprint Blues for primary brand actions and navigation
- Graphite + neutral greys for structure and UI chrome
- Electric Teal for accent and secondary emphasis
- Functional colors for alerts and status semantics

## 2. Typography

Primary font families:
- `Noto Sans` (regular + italic, bold + bold italic)

Icon fonts:
- `Material Icons Two Tone`
- `Material Symbols Round`

Global behavior:
- Universal fallback: `Noto Sans, sans-serif`
- Body stack:  `Noto Sans`, sans-serif

## 3. Core Color Tokens

### Blueprint Blues

| Token | Hex | Preview |
| --- | --- | --- |
| `--durion-blue-800` | `#1c2e48` | <span style="display:inline-block;width:64px;height:20px;background:#1c2e48;border:1px solid #ccc;"></span> |
| `--durion-blue-700` | `#2b4c78` | <span style="display:inline-block;width:64px;height:20px;background:#2b4c78;border:1px solid #ccc;"></span> |
| `--durion-blue-600` | `#355d92` | <span style="display:inline-block;width:64px;height:20px;background:#355d92;border:1px solid #ccc;"></span> |
| `--durion-blue-500` | `#4d76b2` | <span style="display:inline-block;width:64px;height:20px;background:#4d76b2;border:1px solid #ccc;"></span> |
| `--durion-blue-400` | `#668fc2` | <span style="display:inline-block;width:64px;height:20px;background:#668fc2;border:1px solid #ccc;"></span> |
| `--durion-blue-300` | `#7fa4d1` | <span style="display:inline-block;width:64px;height:20px;background:#7fa4d1;border:1px solid #ccc;"></span> |
| `--durion-blue-200` | `#aac4e4` | <span style="display:inline-block;width:64px;height:20px;background:#aac4e4;border:1px solid #ccc;"></span> |
| `--durion-blue-100` | `#d3e3f6` | <span style="display:inline-block;width:64px;height:20px;background:#d3e3f6;border:1px solid #ccc;"></span> |
| `--durion-blue-50` | `#f4f8fe` | <span style="display:inline-block;width:64px;height:20px;background:#f4f8fe;border:1px solid #ccc;"></span> |

### Graphite

| Token | Hex | Preview |
| --- | --- | --- |
| `--durion-graphite-800` | `#333842` | <span style="display:inline-block;width:64px;height:20px;background:#333842;border:1px solid #ccc;"></span> |
| `--durion-graphite-700` | `#444a55` | <span style="display:inline-block;width:64px;height:20px;background:#444a55;border:1px solid #ccc;"></span> |
| `--durion-graphite-600` | `#5a616e` | <span style="display:inline-block;width:64px;height:20px;background:#5a616e;border:1px solid #ccc;"></span> |
| `--durion-graphite-500` | `#727986` | <span style="display:inline-block;width:64px;height:20px;background:#727986;border:1px solid #ccc;"></span> |
| `--durion-graphite-200` | `#d7d9dd` | <span style="display:inline-block;width:64px;height:20px;background:#d7d9dd;border:1px solid #ccc;"></span> |
| `--durion-graphite-100` | `#e7e8eb` | <span style="display:inline-block;width:64px;height:20px;background:#e7e8eb;border:1px solid #ccc;"></span> |

### Electric Teal

| Token | Hex | Preview |
| --- | --- | --- |
| `--durion-teal-600` | `#158f83` | <span style="display:inline-block;width:64px;height:20px;background:#158f83;border:1px solid #ccc;"></span> |
| `--durion-teal-500` | `#1fa497` | <span style="display:inline-block;width:64px;height:20px;background:#1fa497;border:1px solid #ccc;"></span> |
| `--durion-teal-400` | `#2bbbad` | <span style="display:inline-block;width:64px;height:20px;background:#2bbbad;border:1px solid #ccc;"></span> |
| `--durion-teal-300` | `#55d7cc` | <span style="display:inline-block;width:64px;height:20px;background:#55d7cc;border:1px solid #ccc;"></span> |
| `--durion-teal-200` | `#a4e9e1` | <span style="display:inline-block;width:64px;height:20px;background:#a4e9e1;border:1px solid #ccc;"></span> |
| `--durion-teal-100` | `#d7f3f0` | <span style="display:inline-block;width:64px;height:20px;background:#d7f3f0;border:1px solid #ccc;"></span> |

### Neutrals

| Token | Hex | Preview |
| --- | --- | --- |
| `--durion-grey-900` | `#121213` | <span style="display:inline-block;width:64px;height:20px;background:#121213;border:1px solid #ccc;"></span> |
| `--durion-grey-800` | `#1f2022` | <span style="display:inline-block;width:64px;height:20px;background:#1f2022;border:1px solid #ccc;"></span> |
| `--durion-grey-700` | `#3a3a3e` | <span style="display:inline-block;width:64px;height:20px;background:#3a3a3e;border:1px solid #ccc;"></span> |
| `--durion-grey-500` | `#707078` | <span style="display:inline-block;width:64px;height:20px;background:#707078;border:1px solid #ccc;"></span> |
| `--durion-grey-100` | `#f2f2f4` | <span style="display:inline-block;width:64px;height:20px;background:#f2f2f4;border:1px solid #ccc;"></span> |

### Functional

| Token | Hex | Preview |
| --- | --- | --- |
| `--functional-error-red` | `#c84c47` | <span style="display:inline-block;width:64px;height:20px;background:#c84c47;border:1px solid #ccc;"></span> |
| `--functional-warning` | `#e6a540` | <span style="display:inline-block;width:64px;height:20px;background:#e6a540;border:1px solid #ccc;"></span> |
| `--functional-info-blue` | `#355d92` | <span style="display:inline-block;width:64px;height:20px;background:#355d92;border:1px solid #ccc;"></span> |
| `--functional-success` | `#5bbe72` | <span style="display:inline-block;width:64px;height:20px;background:#5bbe72;border:1px solid #ccc;"></span> |

### Brand Semantic Tokens

- `--brand-primary: var(--durion-blue-700)`
- `--brand-primary-soft: var(--durion-blue-50)`
- `--brand-secondary: var(--durion-graphite-700)`
- `--brand-accent: var(--durion-teal-400)`
- `--brand-background: var(--durion-grey-100)`
- `--brand-surface: #ffffff`

## 4. Theme Mapping

Durion theme is applied via:
- `html[data-brand="durion"][data-theme="light"]`
- `html[data-brand="durion"][data-theme="dark"]`

Mapped runtime tokens include:
- Primary: `--primaryA400`, `--primaryA300`, `--primaryA100`, `--primary50`
- Accent: `--accentA400`, `--accentA700`, `--accentA100`
- Layout surfaces: `--themeBackground`, `--navBackground`, `--menuBackground`, `--cardBackground`, `--subMenuBackground`
- Text: `--currentTextColor`, `--contrastTextColor`
- Scrollbars: `--trackColor`, `--handleColor`

Body adopts theme-level background and text color when `data-theme` is present.

## 5. Component Styling Patterns

### Elevation
Utility classes:
- `.dur-elevation-1` through `.dur-elevation-4`

### Alerts
`.alert` variants:
- `.alert-info`, `.alert-success`, `.alert-warning`, `.alert-error`, `.alert-critical`, `.alert-soft`

### Links
- Default links use underline-style border (`border-bottom: 2px`) and theme colors
- Variants: `.accent`, `.white`

### Navigation and Sidebar
- `.dur-navbar` uses themed nav background
- `.dur-navbar.white` swaps to light menu style
- `.dur-sidebar` primary item state uses `primary` token mapping

### Status Chips
- `.dur-status` base style plus variants: `.primary`, `.valid`, `.warn`, `.error`
- Dark theme override present for chip background

### Content, Scrollbars, Tables, Timeline
- Main content follows theme background/text tokens
- Custom scrollbar track/thumb tokens
- Table focus and row hover states mapped to primary tokens
- Timeline dots use primary/accent colors

## 6. Asset Expectations

Theme expects assets under relative paths:
- Fonts under `../assets/fonts/...`
- Icon fonts under `../assets/fonts/icons/...`

When integrating this theme, ensure those assets are available at the expected relative URLs.

## 7. Implementation Notes

- Keep token usage semantic (prefer `--brand-*`, `--primary*`, `--accent*`) over hardcoded hex in component code.
- For new components, support both light and dark mappings by consuming runtime variables (`--themeBackground`, `--currentTextColor`, etc.).
- Reuse existing utility classes (`.dur-elevation-*`, status/link variants) before introducing new variants.
