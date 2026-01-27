#!/usr/bin/env python3
"""
Generate BACKEND_CONTRACT_GUIDE.md files from OpenAPI specs.

This script parses OpenAPI JSON specs from pos-* modules and generates
comprehensive backend contract guides for each domain.
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Any, Optional
from datetime import datetime

# Module to domain mapping
MODULE_TO_DOMAIN = {
    'pos-accounting': 'accounting',
    'pos-customer': 'crm',
    'pos-inventory': 'inventory',
    'pos-location': 'location',
    'pos-order': 'order',
    'pos-people': 'people',
    'pos-price': 'pricing',
    'pos-security-service': 'security',
    'pos-shop-manager': 'shopmgmt',
    'pos-work-order': 'workexec',
    'pos-invoice': 'billing',
    'pos-catalog': 'product',
    'pos-event-receiver': 'audit',
}

# Domain titles
DOMAIN_TITLES = {
    'accounting': 'Accounting',
    'crm': 'Customer Relationship Management (CRM)',
    'inventory': 'Inventory Management',
    'location': 'Location Management',
    'order': 'Order Management',
    'people': 'People & Human Resources',
    'pricing': 'Pricing & Price Management',
    'security': 'Security & Authentication',
    'shopmgmt': 'Shop Management',
    'workexec': 'Work Order Execution',
    'billing': 'Billing & Invoicing',
    'product': 'Product Catalog',
    'audit': 'Audit & Event Tracking',
}


def load_openapi_spec(spec_path: Path) -> Optional[Dict]:
    """Load OpenAPI JSON specification."""
    try:
        with open(spec_path, 'r') as f:
            return json.load(f)
    except Exception as e:
        print(f"Error loading {spec_path}: {e}")
        return None


def extract_endpoints(spec: Dict) -> List[Dict]:
    """Extract endpoint information from OpenAPI spec."""
    endpoints = []
    paths = spec.get('paths', {})
    
    for path, methods in paths.items():
        for method, details in methods.items():
            if method.lower() in ['get', 'post', 'put', 'delete', 'patch']:
                endpoints.append({
                    'path': path,
                    'method': method.upper(),
                    'summary': details.get('summary', ''),
                    'description': details.get('description', ''),
                    'operationId': details.get('operationId', ''),
                    'tags': details.get('tags', []),
                    'parameters': details.get('parameters', []),
                    'requestBody': details.get('requestBody', {}),
                    'responses': details.get('responses', {}),
                })
    
    return endpoints


def extract_schemas(spec: Dict) -> Dict[str, Any]:
    """Extract schema definitions from OpenAPI spec."""
    components = spec.get('components', {})
    return components.get('schemas', {})


def extract_enums(schemas: Dict[str, Any]) -> Dict[str, List[str]]:
    """Extract enum definitions from schemas."""
    enums = {}
    
    for schema_name, schema_def in schemas.items():
        if isinstance(schema_def, dict):
            # Check for enum at top level
            if 'enum' in schema_def:
                enums[schema_name] = schema_def['enum']
            
            # Check for enums in properties
            properties = schema_def.get('properties', {})
            for prop_name, prop_def in properties.items():
                if isinstance(prop_def, dict) and 'enum' in prop_def:
                    key = f"{schema_name}.{prop_name}"
                    enums[key] = prop_def['enum']
    
    return enums


def generate_guide_content(domain: str, module: str, spec: Dict) -> str:
    """Generate BACKEND_CONTRACT_GUIDE.md content from OpenAPI spec."""
    
    domain_title = DOMAIN_TITLES.get(domain, domain.title())
    endpoints = extract_endpoints(spec)
    schemas = extract_schemas(spec)
    enums = extract_enums(schemas)
    
    today = datetime.now().strftime('%Y-%m-%d')
    
    content = f"""# {domain_title} Backend Contract Guide

**Version:** 1.0  
**Audience:** Backend developers, Frontend developers, API consumers  
**Last Updated:** {today}  
**OpenAPI Source:** `{module}/target/openapi.json`

---

## Overview

This guide standardizes field naming conventions, data types, payload structures, and error codes for the {domain_title} domain REST API and backend services. Consistency across all endpoints ensures predictable API contracts and reduces integration friction.

This guide is generated from the OpenAPI specification and follows the standards established across all Durion platform domains.

---

## Table of Contents

1. [JSON Field Naming Conventions](#json-field-naming-conventions)
2. [Data Types & Formats](#data-types--formats)
3. [Enum Value Conventions](#enum-value-conventions)
4. [Identifier Naming](#identifier-naming)
5. [Timestamp Conventions](#timestamp-conventions)
6. [Collection & Pagination](#collection--pagination)
7. [Error Response Format](#error-response-format)
8. [Correlation ID & Request Tracking](#correlation-id--request-tracking)
9. [API Endpoints](#api-endpoints)
10. [Entity-Specific Contracts](#entity-specific-contracts)
11. [Examples](#examples)

---

## JSON Field Naming Conventions

### Standard Pattern: camelCase

All JSON field names **MUST** use `camelCase` (not `snake_case`, not `PascalCase`).

```json
{{
  "id": "abc-123",
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z",
  "status": "ACTIVE"
}}
```

### Rationale

- Aligns with JSON/JavaScript convention
- Matches Java property naming after Jackson deserialization
- Consistent with REST API best practices (RFC 7231)
- Consistent across all Durion platform domains

---

## Data Types & Formats

### String Fields

Use `string` type for:

- Names and descriptions
- Codes and identifiers
- Free-form text
- Enum values (serialized as strings)

```java
private String id;
private String name;
private String description;
private String status;
```

### Numeric Fields

Use `Integer` or `Long` for:

- Counts (page numbers, total results)
- Version numbers
- Sequence numbers

```java
private Integer pageNumber;
private Integer pageSize;
private Long totalCount;
```

### Boolean Fields

Use `boolean` for true/false flags:

```java
private boolean isActive;
private boolean isPrimary;
private boolean hasPermission;
```

### UUID/ID Fields

Use `String` for all primary and foreign key IDs:

```java
private String id;
private String parentId;
private String referenceId;
```

### Instant/Timestamp Fields

Use `Instant` in Java; serialize to ISO 8601 UTC in JSON:

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
private Instant updatedAt;
```

JSON representation:

```json
{{
  "createdAt": "2026-01-27T14:30:00Z",
  "updatedAt": "2026-01-27T15:45:30Z"
}}
```

### LocalDate Fields

Use `LocalDate` for date-only fields (no time component):

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
private LocalDate effectiveFrom;
private LocalDate effectiveTo;
```

JSON representation:

```json
{{
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31"
}}
```

---

## Enum Value Conventions

### Standard Pattern: UPPER_SNAKE_CASE

All enum values **MUST** use `UPPER_SNAKE_CASE`:

```java
public enum Status {{
    ACTIVE,
    INACTIVE,
    PENDING_APPROVAL,
    ARCHIVED
}}
```

### Enums in this Domain

{_generate_enum_section(enums)}

---

## Identifier Naming

### Standard Pattern

- Primary keys: `id` or `{{entity}}Id` (e.g., `customerId`, `orderId`)
- Foreign keys: `{{entity}}Id` (e.g., `parentId`, `accountId`)
- Composite identifiers: use structured object, not concatenated string

### Examples

```json
{{
  "id": "abc-123",
  "customerId": "cust-456",
  "orderId": "ord-789"
}}
```

---

## Timestamp Conventions

### Standard Pattern: ISO 8601 UTC

All timestamps **MUST** be:

- Serialized in ISO 8601 format with UTC timezone (`Z` suffix)
- Stored as `Instant` in Java
- Include millisecond precision when available

```json
{{
  "createdAt": "2026-01-27T14:30:00.123Z",
  "updatedAt": "2026-01-27T15:45:30.456Z"
}}
```

### Common Timestamp Fields

- `createdAt`: When the entity was created
- `updatedAt`: When the entity was last updated
- `deletedAt`: When the entity was soft-deleted (if applicable)
- `effectiveFrom`: Start date for effective dating
- `effectiveTo`: End date for effective dating

---

## Collection & Pagination

### Standard Pagination Request

```json
{{
  "pageNumber": 0,
  "pageSize": 20,
  "sortField": "createdAt",
  "sortOrder": "DESC"
}}
```

### Standard Pagination Response

```json
{{
  "results": [...],
  "totalCount": 150,
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 8
}}
```

### Guidelines

- Use zero-based page numbering
- Default page size: 20 items
- Maximum page size: 100 items
- Include total count for client-side pagination controls

---

## Error Response Format

### Standard Error Response

All error responses **MUST** follow this format:

```json
{{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "correlationId": "abc-123-def-456",
  "timestamp": "2026-01-27T14:30:00Z",
  "fieldErrors": [
    {{
      "field": "email",
      "message": "Invalid email format",
      "rejectedValue": "invalid-email"
    }}
  ]
}}
```

### Standard HTTP Status Codes

- `200 OK`: Successful GET, PUT, PATCH
- `201 Created`: Successful POST
- `204 No Content`: Successful DELETE
- `400 Bad Request`: Validation error
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Business rule violation
- `422 Unprocessable Entity`: Semantic validation error
- `500 Internal Server Error`: Unexpected server error
- `501 Not Implemented`: Endpoint not yet implemented

---

## Correlation ID & Request Tracking

### X-Correlation-Id Header

All API requests **SHOULD** include an `X-Correlation-Id` header for distributed tracing:

```http
GET /v1/{domain}/entities/123
X-Correlation-Id: abc-123-def-456
```

### Response Headers

All API responses **MUST** echo the correlation ID:

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456
```

### Error Responses

All error responses **MUST** include the correlation ID in the body:

```json
{{
  "code": "NOT_FOUND",
  "message": "Entity not found",
  "correlationId": "abc-123-def-456"
}}
```

**Reference:** See `DECISION-INVENTORY-012` in domain AGENT_GUIDE.md for correlation ID standards.

---

## API Endpoints

### Endpoint Summary

This domain exposes **{len(endpoints)}** REST API endpoints:

{_generate_endpoints_table(endpoints)}

### Endpoint Details

{_generate_endpoint_details(endpoints, schemas)}

---

## Entity-Specific Contracts

{_generate_entity_contracts(schemas)}

---

## Examples

### Example Request/Response Pairs

{_generate_examples(endpoints, schemas)}

---

## Summary

This guide establishes standardized contracts for the {domain_title} domain:

- **Field Naming**: camelCase for all JSON fields
- **Enum Values**: UPPER_SNAKE_CASE for all enums
- **Timestamps**: ISO 8601 UTC format
- **Identifiers**: String-based UUIDs
- **Pagination**: Zero-based with standard response format
- **Error Handling**: Consistent error response structure with correlation IDs

---

## Change Log

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | {today} | Initial version generated from OpenAPI spec |

---

## References

- OpenAPI Specification: `{module}/target/openapi.json`
- Domain Agent Guide: `domains/{domain}/.business-rules/AGENT_GUIDE.md`
- Cross-Domain Integration: `domains/{domain}/.business-rules/CROSS_DOMAIN_INTEGRATION_CONTRACTS.md`
- Error Codes: `domains/{domain}/.business-rules/ERROR_CODES.md`
- Correlation ID Standards: `X-Correlation-Id-Implementation-Plan.md`

---

**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}  
**Tool:** `scripts/generate_backend_contract_guides.py`
"""
    
    return content


def _generate_enum_section(enums: Dict[str, List[str]]) -> str:
    """Generate enum section content."""
    if not enums:
        return "No enums defined in OpenAPI spec. Standard enum conventions apply:\n\n- Use UPPER_SNAKE_CASE\n- Document all possible values\n- Avoid numeric codes"
    
    sections = []
    for enum_name, values in sorted(enums.items()):
        values_list = '\n'.join([f"- `{v}`" for v in values])
        sections.append(f"#### {enum_name}\n\n{values_list}")
    
    return '\n\n'.join(sections)


def _generate_endpoints_table(endpoints: List[Dict]) -> str:
    """Generate endpoints summary table."""
    if not endpoints:
        return "No endpoints defined in OpenAPI spec."
    
    lines = ["| Method | Path | Summary |", "|--------|------|---------|"]
    
    for ep in sorted(endpoints, key=lambda x: (x['path'], x['method'])):
        method = ep['method']
        path = ep['path']
        summary = ep['summary'][:80] + '...' if len(ep['summary']) > 80 else ep['summary']
        lines.append(f"| {method} | `{path}` | {summary} |")
    
    return '\n'.join(lines)


def _generate_endpoint_details(endpoints: List[Dict], schemas: Dict) -> str:
    """Generate detailed endpoint documentation."""
    if not endpoints:
        return "No endpoints defined in OpenAPI spec."
    
    sections = []
    
    for ep in sorted(endpoints, key=lambda x: (x['path'], x['method'])):
        method = ep['method']
        path = ep['path']
        summary = ep['summary']
        description = ep['description']
        operation_id = ep['operationId']
        
        section = f"#### {method} {path}\n\n"
        
        if summary:
            section += f"**Summary:** {summary}\n\n"
        
        if description and description != summary:
            section += f"**Description:** {description}\n\n"
        
        if operation_id:
            section += f"**Operation ID:** `{operation_id}`\n\n"
        
        # Parameters
        params = ep.get('parameters', [])
        if params:
            section += "**Parameters:**\n\n"
            for param in params:
                param_name = param.get('name', '')
                param_in = param.get('in', '')
                param_required = "Required" if param.get('required', False) else "Optional"
                param_desc = param.get('description', '')
                param_type = param.get('schema', {}).get('type', 'string')
                section += f"- `{param_name}` ({param_in}, {param_required}, {param_type}): {param_desc}\n"
            section += "\n"
        
        # Responses
        responses = ep.get('responses', {})
        if responses:
            section += "**Responses:**\n\n"
            for status, response_def in sorted(responses.items()):
                desc = response_def.get('description', '')
                section += f"- `{status}`: {desc}\n"
            section += "\n"
        
        sections.append(section)
    
    return '\n---\n\n'.join(sections)


def _generate_entity_contracts(schemas: Dict[str, Any]) -> str:
    """Generate entity-specific contract documentation."""
    if not schemas:
        return "No schemas defined in OpenAPI spec."
    
    sections = []
    
    for schema_name, schema_def in sorted(schemas.items()):
        if not isinstance(schema_def, dict):
            continue
        
        section = f"### {schema_name}\n\n"
        
        description = schema_def.get('description', '')
        if description:
            section += f"{description}\n\n"
        
        properties = schema_def.get('properties', {})
        required_fields = schema_def.get('required', [])
        
        if properties:
            section += "**Fields:**\n\n"
            section += "| Field | Type | Required | Description |\n"
            section += "|-------|------|----------|-------------|\n"
            
            for prop_name, prop_def in sorted(properties.items()):
                if not isinstance(prop_def, dict):
                    continue
                
                prop_type = prop_def.get('type', 'string')
                prop_format = prop_def.get('format', '')
                if prop_format:
                    prop_type = f"{prop_type} ({prop_format})"
                
                is_required = "Yes" if prop_name in required_fields else "No"
                prop_desc = prop_def.get('description', '')
                
                section += f"| `{prop_name}` | {prop_type} | {is_required} | {prop_desc} |\n"
            
            section += "\n"
        
        sections.append(section)
    
    # Limit to first 10 schemas to keep guide readable
    if len(sections) > 10:
        sections = sections[:10]
        sections.append("*Additional schemas omitted for brevity. See OpenAPI spec for complete list.*")
    
    return '\n'.join(sections)


def _generate_examples(endpoints: List[Dict], schemas: Dict) -> str:
    """Generate example request/response pairs."""
    examples = []
    
    # Find one POST and one GET endpoint as examples
    post_example = next((ep for ep in endpoints if ep['method'] == 'POST'), None)
    get_example = next((ep for ep in endpoints if ep['method'] == 'GET'), None)
    
    if post_example:
        examples.append(f"""#### Example: Create Request

```http
POST {post_example['path']}
Content-Type: application/json
X-Correlation-Id: abc-123-def-456

{{
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE"
}}
```

**Response:**

```http
HTTP/1.1 201 Created
X-Correlation-Id: abc-123-def-456

{{
  "id": "new-id-123",
  "name": "Example",
  "description": "Example description",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:30:00Z"
}}
```""")
    
    if get_example:
        examples.append(f"""#### Example: Retrieve Request

```http
GET {get_example['path']}
X-Correlation-Id: abc-123-def-456
```

**Response:**

```http
HTTP/1.1 200 OK
X-Correlation-Id: abc-123-def-456

{{
  "id": "existing-id-456",
  "name": "Example",
  "status": "ACTIVE",
  "createdAt": "2026-01-27T14:00:00Z",
  "updatedAt": "2026-01-27T14:30:00Z"
}}
```""")
    
    if not examples:
        return "See OpenAPI specification for request/response examples."
    
    return '\n\n'.join(examples)


def generate_guide_for_module(backend_root: Path, workspace_root: Path, module: str):
    """Generate BACKEND_CONTRACT_GUIDE.md for a specific module."""
    
    domain = MODULE_TO_DOMAIN.get(module)
    if not domain:
        print(f"Warning: No domain mapping for module {module}")
        return
    
    spec_path = backend_root / module / 'target' / 'openapi.json'
    if not spec_path.exists():
        print(f"Warning: OpenAPI spec not found at {spec_path}")
        return
    
    print(f"Processing {module} -> {domain} domain...")
    
    spec = load_openapi_spec(spec_path)
    if not spec:
        return
    
    guide_content = generate_guide_content(domain, module, spec)
    
    output_dir = workspace_root / 'domains' / domain / '.business-rules'
    output_dir.mkdir(parents=True, exist_ok=True)
    
    output_path = output_dir / 'BACKEND_CONTRACT_GUIDE.md'
    
    with open(output_path, 'w') as f:
        f.write(guide_content)
    
    print(f"✅ Generated {output_path}")


def main():
    """Main execution function."""
    
    # Paths
    workspace_root = Path(__file__).parent.parent
    backend_root = Path.home() / 'Projects' / 'durion-positivity-backend'
    
    if not backend_root.exists():
        print(f"Error: Backend repository not found at {backend_root}")
        sys.exit(1)
    
    print(f"Workspace root: {workspace_root}")
    print(f"Backend root: {backend_root}")
    print()
    
    # Process modules with existing OpenAPI specs
    modules_with_specs = [
        'pos-accounting',
        'pos-customer',
        'pos-inventory',
        'pos-location',
        'pos-order',
        'pos-people',
        'pos-price',
        'pos-security-service',
    ]
    
    for module in modules_with_specs:
        try:
            generate_guide_for_module(backend_root, workspace_root, module)
        except Exception as e:
            print(f"Error processing {module}: {e}")
            import traceback
            traceback.print_exc()
    
    print()
    print("✅ Guide generation complete!")
    print()
    print("Next steps:")
    print("1. Review generated guides for accuracy")
    print("2. Configure OpenAPI for pos-shop-manager, pos-work-order, pos-invoice")
    print("3. Generate guides for remaining modules")
    print("4. Cross-reference with domain AGENT_GUIDE.md decisions")


if __name__ == '__main__':
    main()
