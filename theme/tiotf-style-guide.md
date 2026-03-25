# TIOTF Style Guide

This guide is derived from `durion/theme/tiotf-theme.css` and the `durion/theme/fonts` + `durion/theme/images` asset folders.

Excluded by request:
- `durion/theme/durion-theme.css`

## 1. Brand Foundation

TIOTF uses a warm-accessible palette:
- Brand Blues for primary actions and navigation
- Sage tones for calm/inclusion accents
- Coral tones for warmth and human emphasis
- Neutral greys for structure and readability

## 2. Typography

Primary font families:
- `Noto Sans` (regular + italic, bold + bold italic)

Icon fonts:
- `Material Icons Two Tone`
- `Material Symbols Round`

Global behavior:
- Universal fallback: `Noto Sans, sans-serif`
- Body stack: `Noto Sans`, sans-serif

## 3. Core Color Tokens

### Brand Blues

| Token | Hex | Preview |
| --- | --- | --- |
| `--tiotf-blue-700` | `#2f578a` | <span style="display:inline-block;width:64px;height:20px;background:#2f578a;border:1px solid #ccc;"></span> |
| `--tiotf-blue-600` | `#3c6eaf` | <span style="display:inline-block;width:64px;height:20px;background:#3c6eaf;border:1px solid #ccc;"></span> |
| `--tiotf-blue-500` | `#4a82c6` | <span style="display:inline-block;width:64px;height:20px;background:#4a82c6;border:1px solid #ccc;"></span> |
| `--tiotf-blue-400` | `#6897d4` | <span style="display:inline-block;width:64px;height:20px;background:#6897d4;border:1px solid #ccc;"></span> |
| `--tiotf-blue-300` | `#8cb4e5` | <span style="display:inline-block;width:64px;height:20px;background:#8cb4e5;border:1px solid #ccc;"></span> |
| `--tiotf-blue-200` | `#bcd3f0` | <span style="display:inline-block;width:64px;height:20px;background:#bcd3f0;border:1px solid #ccc;"></span> |
| `--tiotf-blue-100` | `#d9e6f7` | <span style="display:inline-block;width:64px;height:20px;background:#d9e6f7;border:1px solid #ccc;"></span> |
| `--tiotf-blue-50` | `#f2f7fd` | <span style="display:inline-block;width:64px;height:20px;background:#f2f7fd;border:1px solid #ccc;"></span> |

### Sage

| Token | Hex | Preview |
| --- | --- | --- |
| `--tiotf-sage-700` | `#5c7963` | <span style="display:inline-block;width:64px;height:20px;background:#5c7963;border:1px solid #ccc;"></span> |
| `--tiotf-sage-600` | `#6f8e77` | <span style="display:inline-block;width:64px;height:20px;background:#6f8e77;border:1px solid #ccc;"></span> |
| `--tiotf-sage-500` | `#84a98c` | <span style="display:inline-block;width:64px;height:20px;background:#84a98c;border:1px solid #ccc;"></span> |
| `--tiotf-sage-300` | `#b8d3bc` | <span style="display:inline-block;width:64px;height:20px;background:#b8d3bc;border:1px solid #ccc;"></span> |
| `--tiotf-sage-200` | `#c5dec8` | <span style="display:inline-block;width:64px;height:20px;background:#c5dec8;border:1px solid #ccc;"></span> |
| `--tiotf-sage-100` | `#cfe4d1` | <span style="display:inline-block;width:64px;height:20px;background:#cfe4d1;border:1px solid #ccc;"></span> |
| `--tiotf-sage-50` | `#edf6ef` | <span style="display:inline-block;width:64px;height:20px;background:#edf6ef;border:1px solid #ccc;"></span> |

### Coral

| Token | Hex | Preview |
| --- | --- | --- |
| `--tiotf-coral-600` | `#b76044` | <span style="display:inline-block;width:64px;height:20px;background:#b76044;border:1px solid #ccc;"></span> |
| `--tiotf-coral-500` | `#cb6f4f` | <span style="display:inline-block;width:64px;height:20px;background:#cb6f4f;border:1px solid #ccc;"></span> |
| `--tiotf-coral-400` | `#de8467` | <span style="display:inline-block;width:64px;height:20px;background:#de8467;border:1px solid #ccc;"></span> |
| `--tiotf-coral-300` | `#eaa189` | <span style="display:inline-block;width:64px;height:20px;background:#eaa189;border:1px solid #ccc;"></span> |
| `--tiotf-coral-200` | `#f3bba9` | <span style="display:inline-block;width:64px;height:20px;background:#f3bba9;border:1px solid #ccc;"></span> |
| `--tiotf-coral-100` | `#fbe7e1` | <span style="display:inline-block;width:64px;height:20px;background:#fbe7e1;border:1px solid #ccc;"></span> |
| `--tiotf-coral-50` | `#fff5f2` | <span style="display:inline-block;width:64px;height:20px;background:#fff5f2;border:1px solid #ccc;"></span> |

### Neutrals

| Token | Hex | Preview |
| --- | --- | --- |
| `--tiotf-grey-900` | `#1f1f1f` | <span style="display:inline-block;width:64px;height:20px;background:#1f1f1f;border:1px solid #ccc;"></span> |
| `--tiotf-grey-800` | `#2a2a2a` | <span style="display:inline-block;width:64px;height:20px;background:#2a2a2a;border:1px solid #ccc;"></span> |
| `--tiotf-grey-700` | `#3b3b3b` | <span style="display:inline-block;width:64px;height:20px;background:#3b3b3b;border:1px solid #ccc;"></span> |
| `--tiotf-grey-600` | `#6b6b6b` | <span style="display:inline-block;width:64px;height:20px;background:#6b6b6b;border:1px solid #ccc;"></span> |
| `--tiotf-grey-500` | `#8c8c8c` | <span style="display:inline-block;width:64px;height:20px;background:#8c8c8c;border:1px solid #ccc;"></span> |
| `--tiotf-grey-300` | `#c9c9c9` | <span style="display:inline-block;width:64px;height:20px;background:#c9c9c9;border:1px solid #ccc;"></span> |
| `--tiotf-grey-200` | `#e0e0e0` | <span style="display:inline-block;width:64px;height:20px;background:#e0e0e0;border:1px solid #ccc;"></span> |
| `--tiotf-grey-100` | `#f3f3f3` | <span style="display:inline-block;width:64px;height:20px;background:#f3f3f3;border:1px solid #ccc;"></span> |
| `--tiotf-grey-50` | `#fafafa` | <span style="display:inline-block;width:64px;height:20px;background:#fafafa;border:1px solid #ccc;"></span> |

### Functional

| Token | Hex | Preview |
| --- | --- | --- |
| `--functional-error-red` | `#c84c47` | <span style="display:inline-block;width:64px;height:20px;background:#c84c47;border:1px solid #ccc;"></span> |
| `--functional-warning` | `#e6a540` | <span style="display:inline-block;width:64px;height:20px;background:#e6a540;border:1px solid #ccc;"></span> |
| `--functional-info-blue` | `#3c6eaf` | <span style="display:inline-block;width:64px;height:20px;background:#3c6eaf;border:1px solid #ccc;"></span> |
| `--functional-success` | `#5bbe72` | <span style="display:inline-block;width:64px;height:20px;background:#5bbe72;border:1px solid #ccc;"></span> |

### Brand Semantic Tokens

- `--brand-primary: var(--tiotf-blue-600)`
- `--brand-primary-soft: var(--tiotf-blue-50)`
- `--brand-secondary: var(--tiotf-sage-500)`
- `--brand-accent: var(--tiotf-coral-400)`
- `--brand-background: var(--tiotf-grey-50)`
- `--brand-surface: #ffffff`

## 4. Theme Mapping

TIOTF theme is applied via:
- `html[data-brand="tiotf"][data-theme="light"]`
- `html[data-brand="tiotf"][data-theme="dark"]`

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
