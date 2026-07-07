---
description: 'Angular i18n rules using @ngx-translate for Durion Positivity Frontend. Enforce TranslatePipe usage, translation key conventions, and locale-aware formatting in all feature components.'
applyTo: '**/*.ts, **/*.html'
---

# Angular i18n — @ngx-translate Rules

This project uses `@ngx-translate/core` with `TranslateModule.forRoot()` configured in `app.config.ts`.
Translation files live at `src/assets/i18n/{en-US,es-US,es-MX,fr-CA,fr-FR}.json`.
Supported locales: `en-US`, `es-US`, `es-MX`, `fr-CA`, `fr-FR`.

## Core Rules

### Never Hardcode User-Visible Strings

Do **not** write literal text in templates or TypeScript that a user will see:

```html
<!-- ❌ Hardcoded -->
<h1>Create Commercial Account</h1>
<p>Access Denied</p>
<button>Retry</button>

<!-- ✅ Translated -->
<h1>{{ 'CRM.CREATE_ACCOUNT.TITLE' | translate }}</h1>
<p>{{ 'SYSTEM.ACCESS_DENIED.TITLE' | translate }}</p>
<button>{{ 'COMMON.RETRY' | translate }}</button>
```

In TypeScript, never assign a hardcode string that surfaces in the UI. Use `TranslateService.instant()` only when the value is needed synchronously outside a template.

### Import TranslatePipe in Every Standalone Component

All feature components are standalone. `TranslatePipe` must be declared in `imports`:

```typescript
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  standalone: true,
  imports: [CommonModule, TranslatePipe, /* ... */],
})
export class MyPageComponent { }
```

Do **not** rely on module-wide declarations. Every component that uses `| translate` must explicitly import `TranslatePipe`.

### Inject TranslateService for Dynamic Translations

```typescript
import { TranslateService } from '@ngx-translate/core';

export class MyComponent {
  private readonly translate = inject(TranslateService);

  getLabel(key: string): string {
    return this.translate.instant(key);
  }
}
```

---

## Translation Key Naming Convention

Keys are **SCREAMING_SNAKE_CASE** organized in a three-level hierarchy:

```
<DOMAIN>.<COMPONENT_OR_PAGE>.<KEY>
```

| Segment | Rule | Example |
|---------|------|---------|
| Domain | Matches Angular feature domain name | `CRM`, `WORKEXEC`, `ACCOUNTING`, `BILLING`, `PEOPLE` |
| Component/Page | Matches component purpose in caps | `CREATE_ACCOUNT`, `WORKORDER_DETAIL`, `INVOICE_LIST` |
| Key | Short, descriptive noun or phrase | `TITLE`, `SUBMIT`, `LOADING`, `ERROR_SAVE` |

### Shared Keys

Strings reused across multiple domains go under top-level namespaces:

| Namespace | Usage |
|-----------|-------|
| `COMMON.*` | Generic labels: `COMMON.SAVE`, `COMMON.CANCEL`, `COMMON.RETRY`, `COMMON.LOADING` |
| `SYSTEM.*` | System-level states: `SYSTEM.ACCESS_DENIED.*`, `SYSTEM.NOT_FOUND.*` |
| `VALIDATION.*` | Form validation messages: `VALIDATION.REQUIRED`, `VALIDATION.EMAIL_INVALID` |
| `STATUS.*` | Shared status labels: `STATUS.ACTIVE`, `STATUS.INACTIVE`, `STATUS.PENDING` |

### Examples

```json
{
  "COMMON": {
    "SAVE": "Save",
    "CANCEL": "Cancel",
    "RETRY": "Retry",
    "LOADING": "Loading…",
    "SUBMIT": "Submit",
    "BACK": "Back",
    "CONFIRM": "Confirm",
    "DELETE": "Delete",
    "EDIT": "Edit"
  },
  "VALIDATION": {
    "REQUIRED": "This field is required.",
    "EMAIL_INVALID": "Enter a valid email address.",
    "MAX_LENGTH": "Maximum {{max}} characters allowed."
  },
  "CRM": {
    "CREATE_ACCOUNT": {
      "TITLE": "Create Commercial Account",
      "SUBTITLE": "Register a new commercial party in the CRM.",
      "LOADING_TERMS": "Loading billing terms…",
      "ERROR_TERMS": "Could not load billing terms. Please try again.",
      "ACCESS_DENIED": "You do not have permission to create commercial accounts."
    }
  }
}
```

---

## Translation File Updates

When implementing a new component or page:

1. **Add all new keys to all five locale files** before or alongside the implementation:
   - `src/assets/i18n/en-US.json` — primary, must be complete
   - `src/assets/i18n/es-US.json` — Spanish (US), translate or use English as a placeholder marked `// TODO: translate`
  - `src/assets/i18n/es-MX.json` — Spanish (Mexico), same rule
  - `src/assets/i18n/fr-CA.json` — French (Canadian), same rule
  - `src/assets/i18n/fr-FR.json` — French (France), same rule

2. Keys present in `en-US.json` must exist in `es-US.json`, `es-MX.json`, `fr-CA.json`, and `fr-FR.json`. Missing keys cause the UI to show raw key strings.

3. **Do not add placeholder text** like `"TODO"` or `"[key missing]"` as values — use the English string as the fallback value instead, so the app is always readable.

---

## Locale-Aware Formatting

Use Angular's built-in pipes for locale-sensitive data. **Do not hand-format dates or currency in TypeScript.**

```html
<!-- Currency -->
{{ amount | currency:'USD':'symbol':'1.2-2' }}

<!-- Dates -->
{{ date | date:'mediumDate' }}
{{ date | date:'shortTime' }}

<!-- Percentages -->
{{ ratio | percent:'1.0-1' }}
```

For values fetched from the API, format at the template layer — not in service methods or component `.ts` files.

---

## Parameterized Translations

For strings that include dynamic values, use @ngx-translate interpolation syntax:

```json
{
  "WORKEXEC": {
    "WORKORDER_DETAIL": {
      "ITEMS_COUNT": "{{count}} line item(s)",
      "ASSIGNED_TO": "Assigned to {{name}}"
    }
  }
}
```

In template:

```html
<span>{{ 'WORKEXEC.WORKORDER_DETAIL.ITEMS_COUNT' | translate:{ count: items.length } }}</span>
```

In TypeScript:

```typescript
const msg = this.translate.instant('WORKEXEC.WORKORDER_DETAIL.ITEMS_COUNT', { count: 5 });
```

---

## Accessibility and ARIA

Translated strings used in ARIA attributes must also use `TranslatePipe` or `TranslateService`:

```html
<!-- ❌ -->
<button aria-label="Close dialog">×</button>

<!-- ✅ -->
<button [attr.aria-label]="'COMMON.CLOSE_DIALOG' | translate">×</button>
```

---

## Testing

When testing components that use `TranslatePipe`, provide `TranslateModule.forRoot()` in the test `TestBed` configuration:

```typescript
import { TranslateModule } from '@ngx-translate/core';

TestBed.configureTestingModule({
  imports: [MyComponent, TranslateModule.forRoot()],
});
```

`TranslateModule.forRoot()` with no loader returns keys as-is, which is acceptable for unit tests. Do not mock `TranslatePipe` manually unless testing translate-pipe behavior itself.

---

## Retrofit Policy for Previously Built Domains

Domains already implemented (`workexec`, `crm`, `billing`) contain hardcoded strings. These will be retrofitted in a dedicated i18n hardening pass during Stage 4 (re-crawl). Do **not** partially retrofit a domain mid-implementation — either a domain is fully translated or it is queued for the hardening pass.

New domain implementations must follow these rules from the first commit.
