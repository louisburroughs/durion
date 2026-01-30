# ADR-0010: Frontend Domain Responsibilities Guide

**Status:** ACCEPTED  
**Date:** 2026-01-29  
**Deciders:** Frontend Architecture Team, Platform Lead  
**Affected Issues:** Moqui component modularization, UI domain isolation, API integration patterns

---

## Context

The durion-moqui-frontend project includes multiple Moqui components (durion-crm, durion-workexec, durion-inventory, etc.) with Vue 3 + Quasar + TypeScript 5 UI assets. Each component encapsulates a bounded business domain with dedicated:

- REST API clients (communicating with durion-positivity-backend microservices)
- Vue 3 Composition API screens and components (TypeScript 5)
- Moqui services and entity models (for runtime/orchestration)
- Component-level stores and state management (Pinia)

**Current State**: Components exist but domain boundaries and integration patterns are implicit, leading to potential UI code duplication, API misuse, and inconsistent state management. Clear responsibilities need definition to enable:

- Independent UI feature development within bounded domains
- Clear API contracts with backend services
- Consistent pattern enforcement across all components
- Proper separation of concerns (UI ↔ API Client ↔ State Management ↔ Runtime)

**The Problem**: Without explicit responsibility definitions, developers may create duplicate screens, call wrong backend APIs, maintain duplicate state, or bypass established patterns. Direct backend API calls from individual components create tight coupling, make it difficult to manage authentication/error handling consistently, and prevent centralized API orchestration.

**Drivers**:

- Growing complexity of the Moqui runtime and Vue 3 UI codebase
- Need for consistent component structure and naming
- Clear documentation for onboarding frontend developers
- Alignment between frontend component domains and backend microservice domains
- **CRITICAL: Centralized API routing prevents tight coupling and enables unified auth/error management**
- **CRITICAL: All backend calls MUST be routed through durion-positivity component to maintain architectural integrity**

---

## Decision

Establish and document explicit domain responsibilities for each durion-* component in durion-moqui-frontend, with enforcement of component isolation, API client organization, and state management patterns.

### 1. Domain Responsibility Matrix

**Decision:** ✅ **Resolved** - Define clear ownership boundaries for each frontend component, mapping to corresponding backend domains.

| Component | Domain | Primary Responsibility | Key UI Screens | Backend Integrations | Owns State For |
| --- | --- | --- | --- | --- | --- |
| durion-accounting | Accounting & Financial | Financial transaction display, GL account browsing, audit trail viewing, journal entry review | Transaction List, GL Account Chart, Audit Trail Viewer | pos-accounting REST APIs | Accounting transactions, GL hierarchy, audit logs |
| durion-crm | Customer Relations | Customer profile management, contact info editing, relationship tracking, customer search | Customer Dashboard, Contact List, Relationship Timeline, Customer Search | pos-customer, pos-vehicle-inventory APIs | Customer data, contacts, relationship state |
| durion-inventory | Inventory Management | Stock level viewing, location management UI, cycle count execution, ATP display | Inventory Dashboard, Location Browser, Stock Levels, Cycle Count Entry | pos-inventory, pos-location APIs | Stock levels, location data, adjustment drafts |
| durion-product | Product Catalog | Product catalog browsing, SKU management UI, category navigation, attribute editing | Product Catalog, SKU Browser, Category Tree, Attribute Manager | pos-catalog, pos-pricing APIs | Product data, pricing, SKU attributes |
| durion-sales | Sales & Orders | Order creation, order tracking, fulfillment status, customer order history | Order Entry, Order List, Order Details, Fulfillment Tracker | pos-order, pos-customer APIs | Order state, line items, fulfillment progress |
| durion-workexec | Workorder Execution | Workorder lifecycle UI, job task management, mechanic assignment, work progress tracking | Workorder Dashboard, Job Task List, Assignment UI, Work Progress | pos-workorder, pos-people, pos-shop-manager APIs | Workorder state, task progress, assignments |
| durion-shopmgr | Shop Operations | Shop scheduling, mechanic schedule management, capacity planning, work allocation | Shop Schedule, Mechanic Roster, Capacity View, Work Assignments | pos-shop-manager, pos-workorder APIs | Shop schedule, mechanic availability, capacity |
| durion-hr | Human Resources | Employee profile viewing, time entry submission, work session tracking, payroll information | Employee Dashboard, Time Entry Form, Work Session Log, Payroll Summary | pos-people APIs | Time entries, work sessions, employee data |
| durion-positivity | **CENTRAL Backend API Gateway** | **⚠️ MANDATORY: ALL backend API calls from other components must route through durion-positivity services. Provides unified API client interface, authentication token management, error handling, request logging, rate limiting, and service discovery coordination. NO direct fetch/axios calls allowed from other components.** | Service Status Dashboard (optional) | pos-api-gateway, all pos-* services | Session tokens, service metadata, auth state, request queues |
| durion-experience | Common UX & Shell | Application shell, navigation, layout templates, global error handling, branding/theming | App Shell, Main Nav, Layout Components, Error Boundary | N/A (consumed by all components) | Global UI state (nav, alerts, modals) |
| durion-common | Shared Utilities | Common types, utilities, validation rules, shared hooks, API helpers | N/A (library only) | N/A (library only) | N/A (library) |
| durion-theme | Design System | Quasar theming, CSS variables, component styling, brand colors, typography | N/A (design tokens only) | N/A | N/A |
| durion-chat | AI & Messaging | Chat interface, prompt input, message history, LLM integration | Chat Panel, Message List, Prompt Input | pos-mcp-server (LLM orchestration) | Chat history, message state |
| moqui-agents | Moqui Runtime Agents | AI-driven backend orchestration, workflow automation agents, LLM tool bindings | N/A (backend agents) | pos-mcp-server, all pos-* services | Agent state, tool invocation logs |

### 2. Component Structure & Conventions

**Decision:** ✅ **Resolved** - Establish consistent directory structure for all durion-* components to enable predictable navigation and feature discovery.

**Standard Component Layout:**

```
durion-{domain}/
├── component.xml                    # Moqui component metadata
├── README.md                        # Component purpose and API overview
├── MoquiConf.xml                    # Moqui configuration (if needed)
├── build.gradle                     # Gradle build configuration
├── entity/                          # Moqui entity definitions (if runtime models needed)
│   └── *.xml                        # Entity XML definitions
├── service/                         # Moqui Groovy services (orchestration layer)
│   └── *.xml                        # Service definitions
│   └── *.groovy                     # Service implementation
├── screen/                          # Moqui screen definitions (XML-based rendering)
│   └── *.xml                        # Screen XML (backend-rendered for compatibility)
├── data/                            # Static reference data
│   └── *.xml                        # Seed data
├── script/                          # Utility Groovy scripts
│   └── *.groovy                     # Setup/migration scripts
└── assets/                          # Vue 3 + TypeScript UI assets
    ├── api/                         # Backend API client interfaces & implementations
    │   ├── index.ts                 # Re-exports all API clients
    │   ├── types.ts                 # Shared API response/request types
    │   └── {feature}Client.ts       # API client for specific feature (e.g., OrderClient)
    ├── composables/                 # Vue 3 Composition API hooks
    │   ├── use{Feature}.ts          # Reusable logic (e.g., useOrderList, useCustomerForm)
    │   └── useApi.ts                # API communication hook
    ├── store/                       # Pinia state management
    │   └── {feature}Store.ts        # Domain state (e.g., orderStore, customerStore)
    ├── types/                       # TypeScript interfaces & types
    │   ├── index.ts                 # Barrel export of all types
    │   └── {domain}.types.ts        # Domain-specific types
    ├── views/                       # Top-level Vue screens (routable pages)
    │   ├── {Feature}List.vue        # List/grid views
    │   ├── {Feature}Detail.vue      # Detail/form views
    │   ├── {Feature}Dashboard.vue   # Dashboard views
    │   └── index.ts                 # Lazy-load route definitions
    ├── components/                  # Reusable Vue 3 components
    │   ├── {Feature}{Widget}.vue    # Domain-specific components
    │   └── Common{Widget}.vue       # Generic reusable components
    ├── utils/                       # Utility functions
    │   ├── validators.ts            # Field & form validators
    │   ├── formatters.ts            # Data formatting functions
    │   └── transformers.ts          # API response/request transformation
    └── routes/                      # Vue Router route definitions
        └── index.ts                 # Route configuration for component
```

**Rules:**

- **NO direct backend API calls in individual components** — ALL REST calls to pos-* microservices MUST be routed through durion-positivity API services
- **API Clients** (`api/`) MUST NOT make direct `fetch()` calls to backend; instead call durion-positivity service layer
- **Stores** (Pinia) MUST be scoped to domain; use Pinia modules for isolation
- **Composables** MUST encapsulate business logic; reusable across views within the component
- **Views** MUST be lazy-loaded and routed via the component's `routes/index.ts`
- **Components** MUST be presentation-focused; all logic delegated to composables/stores
- **Utils** MUST NOT import from other components' utils; place cross-component utilities in durion-common
- **Types** MUST be exported from component's `types/index.ts` for external consumption

### 3. API Client & State Management Patterns

**Decision:** ✅ **Resolved** - All API communication routes through durion-positivity; components define domain-specific request/response interfaces but NO direct backend calls.

**⚠️ CRITICAL API Client Pattern (ENFORCED):**

Each component defines a LOCAL API client interface that delegates ALL backend calls to durion-positivity:

```typescript
// ❌ INCORRECT - FORBIDDEN: Direct backend API calls
// durion-crm/assets/api/CustomerClient.ts (WRONG - DO NOT DO THIS)
export class CustomerClient {
  async listCustomers(filters?: CustomerFilter): Promise<Customer[]> {
    const response = await fetch(`/rest/api/v1/customer/list`, { ... });
    return response.json();
  }
}

// ✅ CORRECT - Routes through durion-positivity
// durion-crm/assets/api/CustomerClient.ts
import { usePositivityApiClient } from 'durion-positivity/composables/useApiClient';

export class CustomerClient {
  private apiClient = usePositivityApiClient();

  async listCustomers(filters?: CustomerFilter): Promise<Customer[]> {
    // ALL backend calls go through durion-positivity API gateway
    return this.apiClient.request({
      service: 'pos-customer',
      endpoint: '/list',
      method: 'GET',
      data: filters
    });
  }

  async getCustomer(id: string): Promise<Customer> {
    return this.apiClient.request({
      service: 'pos-customer',
      endpoint: `/${id}`,
      method: 'GET'
    });
  }

  async updateCustomer(id: string, data: Partial<Customer>): Promise<Customer> {
    return this.apiClient.request({
      service: 'pos-customer',
      endpoint: `/${id}`,
      method: 'PUT',
      data
    });
  }
}

export const customerClient = new CustomerClient();
```

**durion-positivity API Gateway Service:**

The durion-positivity component exports a centralized API client that handles all backend communication:

```typescript
// durion-positivity/assets/composables/useApiClient.ts
import { useAuthStore } from '../store/authStore';
import { useErrorStore } from '../store/errorStore';

interface ApiRequest {
  service: string;  // e.g., 'pos-customer', 'pos-order'
  endpoint: string; // e.g., '/list', '/{id}'
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: Record<string, any>;
  headers?: Record<string, string>;
}

export function usePositivityApiClient() {
  const auth = useAuthStore();
  const errorStore = useErrorStore();
  const baseURL = '/rest/api/v1';

  async function request<T>(req: ApiRequest): Promise<T> {
    try {
      const url = `${baseURL}/${req.service}${req.endpoint}`;
      const response = await fetch(url, {
        method: req.method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${auth.token}`,
          'X-Request-ID': generateRequestId(),
          'X-Service': 'durion-frontend',
          ...req.headers
        },
        body: req.method !== 'GET' ? JSON.stringify(req.data) : undefined
      });

      if (!response.ok) {
        const error = await response.json();
        errorStore.recordError({
          service: req.service,
          endpoint: req.endpoint,
          status: response.status,
          message: error.message
        });
        throw new Error(`${req.service} error: ${error.message}`);
      }

      return response.json() as Promise<T>;
    } catch (error) {
      errorStore.recordError({
        service: req.service,
        endpoint: req.endpoint,
        error: error.message
      });
      throw error;
    }
  }

  return { request };
}
```

**Export from durion-positivity for other components:**

```typescript
// durion-positivity/assets/index.ts
export { usePositivityApiClient } from './composables/useApiClient';
export { useAuthStore } from './store/authStore';
export type { ApiRequest } from './composables/useApiClient';
```

**State Management Pattern (Pinia):**

Each domain maintains its state in a Pinia store with clear action/getter boundaries:

```typescript
// durion-crm/assets/store/customerStore.ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { customerClient } from '../api/CustomerClient';

export const useCustomerStore = defineStore('crm-customer', () => {
  // State
  const customers = ref<Customer[]>([]);
  const currentCustomer = ref<Customer | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Getters
  const customerCount = computed(() => customers.value.length);
  const sortedCustomers = computed(() =>
    [...customers.value].sort((a, b) => a.name.localeCompare(b.name))
  );

  // Actions (mutations + async operations)
  async function fetchCustomers(filters?: CustomerFilter) {
    loading.value = true;
    error.value = null;
    try {
      customers.value = await customerClient.listCustomers(filters);
    } catch (e) {
      error.value = e.message;
    } finally {
      loading.value = false;
    }
  }

  async function fetchCustomer(id: string) {
    loading.value = true;
    try {
      currentCustomer.value = await customerClient.getCustomer(id);
    } catch (e) {
      error.value = e.message;
    } finally {
      loading.value = false;
    }
  }

  async function saveCustomer(customer: Customer) {
    try {
      const updated = await customerClient.updateCustomer(customer.id, customer);
      // Update local store
      const idx = customers.value.findIndex(c => c.id === updated.id);
      if (idx >= 0) customers.value[idx] = updated;
      currentCustomer.value = updated;
      return updated;
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  return {
    customers,
    currentCustomer,
    loading,
    error,
    customerCount,
    sortedCustomers,
    fetchCustomers,
    fetchCustomer,
    saveCustomer
  };
});
```

**Composables Pattern:**

Wrap store operations and UI logic in reusable composables:

```typescript
// durion-crm/assets/composables/useCustomerList.ts
import { ref, onMounted } from 'vue';
import { useCustomerStore } from '../store/customerStore';

export function useCustomerList() {
  const store = useCustomerStore();
  const searchQuery = ref('');
  const pageSize = ref(25);

  const filteredCustomers = computed(() =>
    store.sortedCustomers.filter(c =>
      c.name.toLowerCase().includes(searchQuery.value.toLowerCase())
    )
  );

  const paginatedCustomers = computed(() =>
    filteredCustomers.value.slice(0, pageSize.value)
  );

  onMounted(() => {
    store.fetchCustomers();
  });

  return {
    customers: paginatedCustomers,
    loading: store.loading,
    error: store.error,
    searchQuery,
    pageSize,
    refresh: () => store.fetchCustomers()
  };
}
```

### 4. Centralized API Routing & Backend Integration

**Decision:** ✅ **MANDATORY ENFORCED** - All backend REST calls must route through durion-positivity component. Direct fetch/axios calls from other components are prohibited.

**Why This Matters:**

- **Unified Authentication**: One place to manage auth tokens, refresh tokens, and session state
- **Centralized Error Handling**: Consistent error responses, retry logic, and error logging
- **Request Tracking**: Correlation IDs, request logging, and observability
- **Rate Limiting & Backpressure**: Handle API limits uniformly
- **Service Discovery**: One place to manage backend service URLs
- **API Versioning**: Handle multiple API versions transparently
- **Security**: Prevent XSS via centralized request validation

**Refactoring Pattern: Converting Direct API Calls to Route Through durion-positivity**

If you find a component with direct backend API calls, follow this refactoring pattern:

**Before (WRONG - FORBIDDEN):**

```typescript
// durion-inventory/assets/api/InventoryClient.ts
import { useAuthStore } from 'durion-positivity/store/authStore';

export class InventoryClient {
  private baseURL = '/rest/api/v1/inventory';

  async getStock(locationId: string): Promise<StockLevel> {
    // ❌ DIRECT FETCH CALL - VIOLATES ARCHITECTURE
    const response = await fetch(`${this.baseURL}/${locationId}`, {
      headers: { 'Authorization': `Bearer ${useAuthStore().token}` }
    });
    return response.json();
  }
}
```

**After (CORRECT - ROUTED THROUGH durion-positivity):**

```typescript
// durion-inventory/assets/api/InventoryClient.ts
import { usePositivityApiClient } from 'durion-positivity';

export class InventoryClient {
  private apiClient = usePositivityApiClient();

  async getStock(locationId: string): Promise<StockLevel> {
    // ✅ ALL CALLS GO THROUGH durion-positivity API gateway
    return this.apiClient.request({
      service: 'pos-inventory',
      endpoint: `/stock/${locationId}`,
      method: 'GET'
    });
  }

  async updateStock(locationId: string, quantity: number): Promise<StockLevel> {
    return this.apiClient.request({
      service: 'pos-inventory',
      endpoint: `/stock/${locationId}`,
      method: 'PUT',
      data: { quantity }
    });
  }
}
```

**Validation Checklist: Is Your Component Routing Correctly?**

- [ ] Component API files import `usePositivityApiClient` from durion-positivity
- [ ] No `fetch()` or `axios()` calls anywhere in the component (except durion-positivity)
- [ ] No direct `Authorization` header construction (handled by durion-positivity)
- [ ] All service calls pass `service` name (e.g., `'pos-inventory'`) for routing
- [ ] Error handling relies on durion-positivity error store, not try/catch
- [ ] README documents which backend services it uses, not HOW to call them

**Audit Command for Finding Violations:**

```bash
# Find any direct fetch calls in components (should return only durion-positivity)
grep -r "fetch(" --include="*.ts" --include="*.vue" runtime/component/ |\
  grep -v "durion-positivity" |\
  grep -v "node_modules"

# Find any axios imports in components
grep -r "from 'axios'" --include="*.ts" --include="*.vue" runtime/component/ |\
  grep -v "durion-positivity"

# Find any Authorization header construction
grep -r "Authorization.*Bearer" --include="*.ts" --include="*.vue" runtime/component/ |\
  grep -v "durion-positivity"
```

### 5. Component Isolation & Cross-Component Communication

**Decision:** ✅ **Resolved** - Enforce component boundaries; cross-component communication happens via backend APIs (routed through durion-positivity), shared events, or parent component orchestration, never direct imports.

**Rules:**

- **NO direct imports** between durion-* components (e.g., no `import { useCustomerStore } from 'durion-crm'` from durion-sales)
- **Shared data** (e.g., Customer, Product) accessed via backend APIs through durion-positivity, not component re-exports
- **Cross-component events** routed through durion-experience (app shell) or event bus pattern
- **Shared utilities/types** live in durion-common, not duplicated
- **⚠️ CRITICAL: Component consumers (durion-sales using customer data) MUST call through durion-positivity API gateway, never make direct fetch calls or call durion-crm API clients**

**Event Bus Pattern (for component communication):**

```typescript
// durion-common/assets/utils/eventBus.ts
import { createEmitter } from 'tiny-emitter';

export const eventBus = createEmitter();

// Events fire from anywhere in the app
eventBus.emit('customer:updated', { customerId: '123', changes: { name: 'New Name' } });

// Listeners registered in other components
onMounted(() => {
  eventBus.on('customer:updated', (event) => {
    // Refresh local customer data if needed
  });
});
```

### 6. Vue 3 + TypeScript 5 Code Standards

**Decision:** ✅ **Resolved** - Enforce consistent Vue 3 and TypeScript patterns across all components.

**Composition API Standards:**

- **Setup function pattern** (Composition API over Options API)
- **Reactive references** for state: `ref()`, `reactive()`, `computed()` with proper type annotations
- **Lifecycle hooks** in logical order: `onMounted`, `onUpdated`, `onUnmounted`
- **Extract reusable logic** into composables (hooks)
- **Avoid watchers where possible**; prefer computed properties for derived state

**TypeScript 5 Standards:**

- **Strong typing** on all functions and variables (no implicit `any`)
- **Strict null checks** enabled (`strict: true` in tsconfig.json)
- **Type exports** from each component's `types/index.ts`
- **Discriminated unions** for API responses (e.g., `{ success: true, data: T } | { success: false, error: string }`)
- **Enum usage** for domain-specific constants (e.g., `OrderStatus`, `WorkorderType`)

**Template Standards:**

- **No inline event handlers** (use named methods from composables)
- **No business logic** in templates (filters/computed properties only)
- **Accessibility first**: proper ARIA labels, semantic HTML
- **Quasar components** for consistent UI (no unstyled native elements)

See [TypeScript 5 + ES2022 Guidelines](../instructions/typescript-5-es2022.instructions.md) and [Vue.js 3 Guidelines](../instructions/vuejs3.instructions.md) for detailed standards.

### 7. API Documentation & Contract Stability

**Decision:** ✅ **Resolved** - Frontend components must document their backend API dependencies; API changes require coordinated updates.

**Frontend API Declaration:**

Each component's README includes a section listing backend APIs it depends on:

```markdown
## Backend API Dependencies

- **pos-customer Service**: `GET /rest/api/v1/customer/list`, `GET /rest/api/v1/customer/{id}`, `PUT /rest/api/v1/customer/{id}`
- **pos-vehicle-inventory Service**: `GET /rest/api/v1/vehicle/{vehicleId}`

### API Contract Expectations

- Customer endpoints return `Customer` objects with `id`, `name`, `email`, `phone`, `createdAt`, `updatedAt`
- Errors returned as `{ message: string, code: string, timestamp: Instant }`
- All dates in ISO 8601 format with UTC timezone
```

**Backward Compatibility:**

- Backend API changes MUST be backward-compatible for one release cycle
- Deprecated fields/endpoints MUST be documented with removal date
- Frontend components MUST support both old and new API versions during transition period

### 8. Moqui Service Layer & Runtime Integration

**Decision:** ✅ **Resolved** - Moqui services used for orchestration and backend-side business logic; REST APIs preferred for UI interaction.

**Service Layer Conventions:**

- **Service definitions** in `service/*.xml` with clear purpose and parameter documentation
- **Service implementations** in `service/*.groovy` using Moqui DSL
- **Cross-component service calls** via Moqui's `ec.service.sync()` (within Moqui runtime only)
- **Backend service calls** via REST APIs from Vue UI layer (not Groovy services)

**Runtime-Specific Logic:**

- **Entity definitions** (`entity/*.xml`) for data models specific to Moqui persistence (use sparingly; prefer backend microservices)
- **Screen definitions** (`screen/*.xml`) for server-side rendering (legacy compatibility; prefer Vue for new UI)
- **Scheduled jobs** in Moqui (`service/*.xml` with `frequency` attribute) for periodic tasks
- **Security policy** integration via Moqui's permission system

### 9. Testing & Quality Standards

**Decision:** ✅ **Resolved** - Establish consistent testing patterns and quality gates for frontend components.

**Unit Testing (Jest) - Mocking durion-positivity API Client:**

```typescript
// durion-crm/assets/composables/__tests__/useCustomerForm.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useCustomerForm } from '../useCustomerForm';
import { usePositivityApiClient } from 'durion-positivity';

// Mock the durion-positivity API client (not direct HTTP)
vi.mock('durion-positivity', () => ({
  usePositivityApiClient: vi.fn(() => ({
    request: vi.fn()
  }))
}));

describe('useCustomerForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize form with default values', () => {
    const { form } = useCustomerForm();
    expect(form.name).toBe('');
    expect(form.email).toBe('');
  });

  it('should validate email format', async () => {
    const { validateForm } = useCustomerForm();
    const result = await validateForm({ email: 'invalid-email' });
    expect(result.valid).toBe(false);
    expect(result.errors.email).toBeDefined();
  });

  it('should submit form and call API through durion-positivity', async () => {
    const mockApiClient = usePositivityApiClient();
    mockApiClient.request.mockResolvedValue({
      id: '1',
      name: 'Test',
      email: 'test@example.com'
    });

    const { submitForm } = useCustomerForm();
    const result = await submitForm({ id: '1', name: 'Test', email: 'test@example.com' });

    // Verify call went through durion-positivity
    expect(mockApiClient.request).toHaveBeenCalledWith({
      service: 'pos-customer',
      endpoint: '/1',
      method: 'PUT',
      data: expect.any(Object)
    });
    expect(result.success).toBe(true);
  });
});
```

**Component Integration Tests:**

```typescript
// durion-crm/assets/views/__tests__/CustomerDetail.test.ts
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import CustomerDetail from '../CustomerDetail.vue';

describe('CustomerDetail.vue', () => {
  it('should render customer form', () => {
    const wrapper = mount(CustomerDetail, {
      props: { customerId: '1' }
    });
    expect(wrapper.find('form').exists()).toBe(true);
  });

  it('should display loading state', () => {
    const wrapper = mount(CustomerDetail, {
      props: { customerId: '1' }
    });
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(true);
  });
});
```

**ESLint & Prettier:**

- All components must pass ESLint checks (no warnings)
- All code must be formatted with Prettier before commit
- TypeScript strict mode enforced (`strict: true`)

**Coverage Requirements:**

- Minimum 70% code coverage for all components
- 100% coverage for critical paths (API clients, state stores)
- **All API client tests must verify durion-positivity routing (no direct HTTP mocks)**

---

## Alternatives Considered

1. **Monolithic UI Bundle**: Single Vue app with all features in one codebase
   - **Rejected**: Difficult to maintain, slow development cycles, tight coupling with backend

2. **Separate Frontend Repos**: Each component as its own repository
   - **Rejected**: Complex dependency management, duplicate build tooling, hard to share utilities

3. **State Management at App Shell Only**: Centralized Pinia store for all data
   - **Rejected**: Large central store becomes unmanageable; component-level stores better encapsulate domains

4. **GraphQL for All APIs**: Frontend uses GraphQL instead of REST
   - **Rejected**: Moqui/backend primary uses REST; GraphQL layer adds complexity; REST sufficient for current needs

---

## Consequences

### Positive ✅

- **Clear Domain Ownership**: Each team/developer knows which component owns which UI features
- **Reduced Code Duplication**: Shared utilities centralized in durion-common; no copy-paste between components
- **Easier Testing**: Component isolation enables focused unit/integration tests without mocking entire app
- **Better Onboarding**: New developers follow predictable patterns across all components
- **Independent Development**: Teams work in parallel on different components with minimal conflicts
- **Type Safety**: Strong TypeScript typing catches errors at development time
- **API Contract Clarity**: Explicit backend API dependencies enable early detection of breaking changes
- **⚠️ Centralized API Gateway**: All backend calls routed through durion-positivity enables unified auth, error handling, logging, and observability
- **⚠️ Security & Compliance**: Single point of enforcement for authentication, encryption, and audit trails
- **⚠️ Easier Debugging**: Centralized request/response logging simplifies troubleshooting

### Negative ⚠️

- **Cross-Component Communication Complexity**: Events/API calls require careful coordination
  - *Mitigation*: Use event bus pattern; centralize cross-domain logic in durion-positivity bridge component

- **Store Proliferation**: Many Pinia stores to maintain
  - *Mitigation*: Enforce consistent store patterns; use code generation if needed for boilerplate

- **Type Definition Duplication**: API response types duplicated across components
  - *Mitigation*: Generate TypeScript types from backend OpenAPI specs (future enhancement)

- **Testing Burden**: More unit tests required due to component isolation
  - *Mitigation*: Provide test utilities and composables reuse; automate test generation

### Neutral

- Documentation maintenance overhead (offset by reduced code duplication and clearer architecture)
- Build complexity from multiple component builds (offset by faster incremental builds per component)

---

## Implementation Roadmap

### Phase 1: Documentation & durion-positivity API Gateway (CURRENT - CRITICAL)

- **IMMEDIATE**: Build durion-positivity `usePositivityApiClient()` composable with auth/error handling
- **IMMEDIATE**: Audit ALL existing components for direct fetch/axios calls (use grep commands above)
- Document component domain responsibilities in this ADR
- Create durion-common component for shared utilities/types
- Update component READMEs to specify which backend services they use (not HOW to call them)

### Phase 2: API Client Refactoring (MANDATORY)

- **CRITICAL**: Refactor all existing direct API calls to route through durion-positivity
- Update all component API clients to use `usePositivityApiClient()` pattern
- Add automated linting rule to reject `fetch()` calls in component code (allow only in durion-positivity)
- Implement type-safe API clients for all backend integrations
- Generate TypeScript types from backend OpenAPI specs (optional)

### Phase 3: State Management Refactoring (Next)

- Migrate existing state to Pinia stores (component-level) using standard pattern
- Consolidate duplicate state across components
- Implement event bus for cross-component communication

### Phase 4: Testing & Quality (Final)

- Implement Jest test suite for all components
- **ENFORCE**: All API client tests must mock durion-positivity, not HTTP
- Enforce ESLint + Prettier linting
- Achieve minimum 70% code coverage
- Integrate tests into CI/CD pipeline

---

## References

- **Moqui Framework**: <https://moqui.org/>
- **Vue 3 Composition API**: <https://vuejs.org/guide/extras/composition-api-faq.html>
- **Pinia State Management**: <https://pinia.vuejs.org/>
- **TypeScript 5 Handbook**: <https://www.typescriptlang.org/docs/>
- **Quasar Framework**: <https://quasar.dev/>
- **Related ADRs**:
  - [ADR-0009: Backend Domain Responsibilities Guide](0009-backend-domain-responsibilities-guide.adr.md)
  - [ADR-0003: CRM Navigation Patterns](0003-crm-navigation-patterns.adr.md)
- **Related Documentation**:
  - `/durion/docs/` — Architecture and design documents
  - `durion-moqui-frontend/AGENTS.md` — Frontend developer guidance
  - `durion-moqui-frontend/.github/instructions/vuejs3.instructions.md` — Vue 3 code standards
  - `durion-moqui-frontend/.github/instructions/typescript-5-es2022.instructions.md` — TypeScript standards
