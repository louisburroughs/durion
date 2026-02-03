## CAP:091 — Backend Contract Update (CRM)

## Inputs

- Manifest: `docs/capabilities/CAP-091/CAPABILITY_MANIFEST.yaml`
- OpenAPI: `durion-positivity-backend/pos-vehicle-inventory/openapi.json`
- Contract guide target: `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md`

## Scope

Update the CRM backend contract guide with CAP:091 coordination links and contract details for vehicle registry endpoints produced by the vehicle inventory service.

Constraints:

- Do not invent behavioral assertions not present in the OpenAPI; use TODOs tied to CAP:091 backend issues when uncertain.
- Provide a patch for the contract guide, but do not apply it until explicit approval.

## Extracted Implementation Links (from manifest)

- Parent capability: https://github.com/louisburroughs/durion/issues/91
- Parent stories:
  - https://github.com/louisburroughs/durion/issues/102
  - https://github.com/louisburroughs/durion/issues/103
  - https://github.com/louisburroughs/durion/issues/104
  - https://github.com/louisburroughs/durion/issues/105
  - https://github.com/louisburroughs/durion/issues/106
- Backend child issues:
  - https://github.com/louisburroughs/durion-positivity-backend/issues/105
  - https://github.com/louisburroughs/durion-positivity-backend/issues/104
  - https://github.com/louisburroughs/durion-positivity-backend/issues/103
  - https://github.com/louisburroughs/durion-positivity-backend/issues/102
  - https://github.com/louisburroughs/durion-positivity-backend/issues/101

## OpenAPI Snapshot Summary (pos-vehicle-inventory)

The provided OpenAPI includes 9 endpoints:

- `GET /v1/vehicles`
- `POST /v1/vehicles`
- `GET /v1/vehicles/{id}`
- `PUT /v1/vehicles/{id}`
- `DELETE /v1/vehicles/{id}`
- `GET /v1/vehicles/vin/{vin}`
- `PUT /v1/vehicles/vin/{vin}`
- `DELETE /v1/vehicles/vin/{vin}`
- `POST /v1/vehicles/vin`

Note:

- `POST /v1/vehicles/vin` no longer uses a VIN path variable; the VIN is provided in the request body as `vin`.

## OpenAPI Delta (Guide vs Provided OpenAPI)

- Added:
  - `GET /v1/vehicles`
  - `POST /v1/vehicles`
  - `GET /v1/vehicles/{id}`
  - `PUT /v1/vehicles/{id}`
  - `DELETE /v1/vehicles/{id}`
  - `GET /v1/vehicles/vin/{vin}`
  - `PUT /v1/vehicles/vin/{vin}`
  - `DELETE /v1/vehicles/vin/{vin}`
  - `POST /v1/vehicles/vin`
- Changed: none detected
- Removed: none detected

## Patch (Applied)

Applied to `domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md` on 2026-02-03.

Note: The applied guide update includes additional boundary clarification and removal of CRM vehicle-only lookup endpoints (`/v1/crm/vehicles`, `/v1/crm/vehicles/{vehicleId}`), keeping vehicle-only lookups in pos-vehicle-inventory.

```text
*** Begin Patch
*** Update File: domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md
@@
-**Version:** 1.2  
+**Version:** 1.3  
@@
-**Last Updated:** 2026-02-02  
-**OpenAPI Source:** `pos-customer/openapi.json`
+**Last Updated:** 2026-02-03  
+**OpenAPI Source:** `pos-customer/openapi.json`  
+**Additional OpenAPI Source (Vehicle Registry / CAP:091):** `durion-positivity-backend/pos-vehicle-inventory/openapi.json`  
 **Status:** Accepted
@@
-This domain exposes **27** REST API endpoints:
+This domain exposes **27** CRM REST API endpoints, plus **9** vehicle registry endpoints (CAP:091):
@@
 | PUT | `/v1/crm/{id}` | Update an existing customer |
+
+#### Vehicle Registry Endpoint Summary (CAP:091)
+
+These endpoints are produced by the vehicle inventory service OpenAPI snapshot:
+
+`durion-positivity-backend/pos-vehicle-inventory/openapi.json`
+
+| Method | Path | Summary |
+|--------|------|---------|
+| GET | `/v1/vehicles` | Get all vehicles |
+| POST | `/v1/vehicles` | Create a new vehicle |
+| GET | `/v1/vehicles/{id}` | Get vehicle by ID |
+| PUT | `/v1/vehicles/{id}` | Update vehicle by ID |
+| DELETE | `/v1/vehicles/{id}` | Delete vehicle by ID |
+| GET | `/v1/vehicles/vin/{vin}` | Get vehicle by VIN |
+| PUT | `/v1/vehicles/vin/{vin}` | Update vehicle by VIN |
+| DELETE | `/v1/vehicles/vin/{vin}` | Delete vehicle by VIN |
| POST | `/v1/vehicles/vin` | Create vehicle by VIN |
@@
 #### PUT /v1/crm/{id}
@@
 - `200`: Customer updated successfully.
 - `404`: Customer not found.
+
+---
+
+### Vehicle Registry Endpoints (CAP:091)
+
+#### GET /v1/vehicles
+
+**Summary:** Get all vehicles
+
+**Description:** Retrieve a list of all vehicles in the inventory.
+
+**Operation ID:** `getAllVehicles`
+
+**Responses:**
+
+- `200`: List of vehicles returned successfully.
+
+**Response Schema (JSON Schema, abridged):**
+
+```json
+{
+  "type": "array",
+  "items": {
+    "$ref": "#/components/schemas/VehicleEntity"
+  }
+}
+```
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`
+- TODO (CAP:091): define auth requirements (if any) and error model for `4xx/5xx`
+
+---
+
+#### POST /v1/vehicles
+
+**Summary:** Create a new vehicle
+
+**Description:** Add a new vehicle to the inventory.
+
+**Operation ID:** `createVehicle`
+
+**Request Schema (JSON Schema, abridged):**
+
+```json
+{
+  "$ref": "#/components/schemas/VehicleEntity"
+}
+```
+
+**Responses:**
+
+- `200`: Vehicle created successfully.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`
+- TODO (CAP:091): define `400` conditions (validation) and error payload shape
+
+---
+
+#### GET /v1/vehicles/{id}
+
+**Summary:** Get vehicle by ID
+
+**Description:** Retrieve a vehicle by its unique ID.
+
+**Operation ID:** `getVehicle`
+
+**Parameters:**
+
+- `id` (path, Required, integer): ID of the vehicle to retrieve
+
+**Responses:**
+
+- `200`: Vehicle found and returned.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`, `404`
+
+---
+
+#### PUT /v1/vehicles/{id}
+
+**Summary:** Update vehicle by ID
+
+**Description:** Update an existing vehicle's details by its ID.
+
+**Operation ID:** `updateVehicle`
+
+**Parameters:**
+
+- `id` (path, Required, integer): ID of the vehicle to update
+
+**Request Schema (JSON Schema, abridged):**
+
+```json
+{
+  "$ref": "#/components/schemas/VehicleEntity"
+}
+```
+
+**Responses:**
+
+- `200`: Vehicle updated successfully.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`, `404`
+
+---
+
+#### DELETE /v1/vehicles/{id}
+
+**Summary:** Delete vehicle by ID
+
+**Description:** Delete a vehicle from the inventory by its ID.
+
+**Operation ID:** `deleteVehicle`
+
+**Parameters:**
+
+- `id` (path, Required, integer): ID of the vehicle to delete
+
+**Responses:**
+
+- `204`: Vehicle deleted successfully.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `204`, `404`
+
+---
+
+#### GET /v1/vehicles/vin/{vin}
+
+**Summary:** Get vehicle by VIN
+
+**Description:** Retrieve a vehicle by its VIN.
+
+**Operation ID:** `getVehicleByVIN`
+
+**Parameters:**
+
+- `vin` (path, Required, string): VIN of the vehicle to retrieve
+
+**Responses:**
+
+- `200`: Vehicle found and returned.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`, `404`
+
+---
+
+#### PUT /v1/vehicles/vin/{vin}
+
+**Summary:** Update vehicle by VIN
+
+**Description:** Update an existing vehicle's details by its VIN.
+
+**Operation ID:** `updateVehicleByVIN`
+
+**Parameters:**
+
+- `vin` (path, Required, string): VIN of the vehicle to update
+
+**Request Schema (JSON Schema, abridged):**
+
+```json
+{
+  "$ref": "#/components/schemas/VehicleEntity"
+}
+```
+
+**Responses:**
+
+- `200`: Vehicle updated successfully.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `200`, `404`
+
+---
+
+#### DELETE /v1/vehicles/vin/{vin}
+
+**Summary:** Delete vehicle by VIN
+
+**Description:** Delete a vehicle from the inventory by its VIN.
+
+**Operation ID:** `deleteVehicleByVIN`
+
+**Parameters:**
+
+- `vin` (path, Required, string): VIN of the vehicle to delete
+
+**Responses:**
+
+- `204`: Vehicle deleted successfully.
+- `404`: Vehicle not found.
+
+**Provider Test Hints (ContractBehaviorIT):**
+
+- Add tests for: `204`, `404`
+
+---
+
+#### POST /v1/vehicles/vin
+
+**Summary:** Create vehicle by VIN
+
+**Description:** Add a new vehicle to the inventory using its VIN.
+
+**Operation ID:** `createVehicleByVIN`
+
+**Request Schema (JSON Schema, abridged):**
+
+```json
+{
+  "$ref": "#/components/schemas/VehicleEntity"
+}
+```
+
+**Responses:**
+
+- `200`: Vehicle created successfully.
+
+**Notes:**
+
+- VIN is supplied in the request body as `vin`.
+
@@
 ### UpdateContactRolesResponse
@@
 | `updatedAt` | string | No |  |
+
+### VehicleEntity
+
+Vehicle record in the vehicle registry service.
+
+**Fields:**
+
+| Field | Type | Required | Description |
+|-------|------|----------|-------------|
+| `id` | integer (int64) | No | Unique identifier for the vehicle |
+| `make` | string | Yes | Vehicle manufacturer (e.g., Toyota, Honda) |
+| `model` | string | Yes | Vehicle model name |
+| `year` | integer | Yes | Manufacturing year of the vehicle |
+| `vin` | string | Yes | Vehicle Identification Number |
+
+**Schema (JSON Schema, abridged):**
+
+```json
+{
+  "type": "object",
+  "properties": {
+    "id": {
+      "type": "integer",
+      "format": "int64",
+      "description": "Unique identifier for the vehicle"
+    },
+    "make": {
+      "type": "string",
+      "description": "Vehicle manufacturer (e.g., Toyota, Honda)"
+    },
+    "model": {
+      "type": "string",
+      "description": "Vehicle model name"
+    },
+    "year": {
+      "type": "integer",
+      "description": "Manufacturing year of the vehicle"
+    },
+    "vin": {
+      "type": "string",
+      "description": "Vehicle Identification Number"
+    }
+  },
+  "required": [
+    "make",
+    "model",
+    "year",
+    "vin"
+  ]
+}
+```
@@
 ## Change Log
@@
 | Version | Date | Changes |
 |---------|------|---------|
+| 1.3 | 2026-02-03 | Add CAP:091 implementation links and document vehicle registry endpoints (pos-vehicle-inventory OpenAPI) |
 | 1.2 | 2026-02-02 | Add CAP:090 implementation links and enrich contact/communication preference contract details |
 | 1.1 | 2026-02-02 | Add CAP:089 implementation links (capability/stories/backend issues) |
 | 1.0 | 2026-01-27 | Initial version generated from OpenAPI spec |
@@
 ## References
 
-- OpenAPI Specification: `pos-customer/openapi.json`
+- OpenAPI Specification (CRM): `pos-customer/openapi.json`
+- OpenAPI Specification (Vehicle Registry / CAP:091): `durion-positivity-backend/pos-vehicle-inventory/openapi.json`
@@
 ### Implementation Links (CAP:090)
@@
 - OpenAPI snapshot used for this update: `durion-positivity-backend/pos-customer/openapi.json`
+
+---
+
+### Implementation Links (CAP:091)
+
+- Capability manifest: `docs/capabilities/CAP-091/CAPABILITY_MANIFEST.yaml`
+- Parent capability: https://github.com/louisburroughs/durion/issues/91
+- Parent stories:
+  - https://github.com/louisburroughs/durion/issues/102
+  - https://github.com/louisburroughs/durion/issues/103
+  - https://github.com/louisburroughs/durion/issues/104
+  - https://github.com/louisburroughs/durion/issues/105
+  - https://github.com/louisburroughs/durion/issues/106
+- Backend child issues:
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/105
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/104
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/103
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/102
+  - https://github.com/louisburroughs/durion-positivity-backend/issues/101
+- OpenAPI snapshot used for this update: `durion-positivity-backend/pos-vehicle-inventory/openapi.json`
*** End Patch
```

## Human Checklist

- Confirm the vehicle registry routes should live at `/v1/vehicles` (vs existing `/v1/crm/vehicles` stubs) and align routing/versioning conventions.
- Confirm request validation rules for VIN (length/charset/check-digit) and document expected `400` error payload.
- Add provider tests for `200/204/404` status codes for the new endpoints, and define validation/error payload behavior for `400` cases.

## JSON Summary

```json
{
  "capability_id": "CAP:091",
  "manifest_path": "docs/capabilities/CAP-091/CAPABILITY_MANIFEST.yaml",
  "backend_issues": [
    "https://github.com/louisburroughs/durion-positivity-backend/issues/105",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/104",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/103",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/102",
    "https://github.com/louisburroughs/durion-positivity-backend/issues/101"
  ],
  "updated_files": [
    "domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md",
    "docs/capabilities/CAP-091/CAP-091-backend-contract.md"
  ],
  "openapi_changes": {
    "added": [
      "GET /v1/vehicles",
      "POST /v1/vehicles",
      "GET /v1/vehicles/{id}",
      "PUT /v1/vehicles/{id}",
      "DELETE /v1/vehicles/{id}",
      "GET /v1/vehicles/vin/{vin}",
      "PUT /v1/vehicles/vin/{vin}",
      "DELETE /v1/vehicles/vin/{vin}",
      "POST /v1/vehicles/vin"
    ],
    "changed": [],
    "removed": []
  },
  "confidence": 96
}
```
