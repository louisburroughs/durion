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
- `Michelin Unit Titling` (weights 300, 400, 600, 700, 900)
- `Noto Sans` (regular + italic, bold + bold italic)

Icon fonts:
- `Material Icons Two Tone`
- `Material Symbols Round`

Global behavior:
- Universal fallback: `Noto Sans, sans-serif`
- Body stack: `Michelin Unit Titling`, `Noto Sans`, sans-serif

## 3. Core Color Tokens

### Blueprint Blues
- `--durion-blue-800: #1c2e48`
- `--durion-blue-700: #2b4c78`
- `--durion-blue-600: #355d92`
- `--durion-blue-500: #4d76b2`
- `--durion-blue-400: #668fc2`
- `--durion-blue-300: #7fa4d1`
- `--durion-blue-200: #aac4e4`
- `--durion-blue-100: #d3e3f6`
- `--durion-blue-50: #f4f8fe`

### Graphite
- `--durion-graphite-800: #333842`
- `--durion-graphite-700: #444a55`
- `--durion-graphite-600: #5a616e`
- `--durion-graphite-500: #727986`
- `--durion-graphite-200: #d7d9dd`
- `--durion-graphite-100: #e7e8eb`

### Electric Teal
- `--durion-teal-600: #158f83`
- `--durion-teal-500: #1fa497`
- `--durion-teal-400: #2bbbad`
- `--durion-teal-300: #55d7cc`
- `--durion-teal-200: #a4e9e1`
- `--durion-teal-100: #d7f3f0`

### Neutrals and Functional
- Greys: `--durion-grey-900`, `--durion-grey-800`, `--durion-grey-700`, `--durion-grey-500`, `--durion-grey-100`
- Functional: `--functional-error-red`, `--functional-warning`, `--functional-info-blue`, `--functional-success`

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
- `.mic-elevation-1` through `.mic-elevation-4`

### Alerts
`.alert` variants:
- `.alert-info`, `.alert-success`, `.alert-warning`, `.alert-error`, `.alert-critical`, `.alert-soft`

### Links
- Default links use underline-style border (`border-bottom: 2px`) and theme colors
- Variants: `.accent`, `.white`

### Navigation and Sidebar
- `.mic-navbar` uses themed nav background
- `.mic-navbar.white` swaps to light menu style
- `.mic-sidebar` primary item state uses `primary` token mapping

### Status Chips
- `.mic-status` base style plus variants: `.primary`, `.valid`, `.warn`, `.error`
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
- Reuse existing utility classes (`.mic-elevation-*`, status/link variants) before introducing new variants.
