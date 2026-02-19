---
title: Backend Contract Capability Template
domain: shared
doc_type: backend_contract_template
template_type: capability_section
owner_repo: louisburroughs/durion
path: domains/BACKEND_CONTRACT_CAPABILITY_TEMPLATE.md
last_updated: 2026-02-19
---

# Backend Contract Capability Template

Use this template to add a capability section to a domain backend contract guide.

~~~md
## CAP-XXX: <Capability Name>

Short description of scope and intent.

### Execution Checklist
- Item 1
- Item 2
- Item 3

### Endpoints
- METHOD /path
- METHOD /path/{id}

### Request Schema
```ts
interface ExampleRequest {
  field: string;
}
```

### Response Schema
```ts
interface ExampleResponse {
  id: string;
}
```

### Behavioral Assertions
- Rule 1
- Rule 2

### Events
- EVENT_NAME

### Contract Tests
- CP-XXX-100
- VE-XXX-100
- LC-XXX-100

### Dependencies
- Upstream/downstream dependency notes
~~~
