# Baseline Permissions Manifest

## Historical Status

This file is historical and not authoritative for current permission coverage or runtime authorization behavior.

Use instead:

- [Authorization Model](../../../docs/architecture/AUTHORIZATION_MODEL.md)
- [ADR-0040](../../../docs/adr/0040-roles-jwt-permission-governance-policy.adr.md)
- live service manifests and permission registration code in `durion-positivity-backend`

## Why It Is No Longer Authoritative

The earlier baseline captured an early policy-era permission taxonomy. It does not prove:

- that the listed permissions are complete
- that they are registered in the current services
- that they are the permissions emitted into access tokens
- that they match the current gateway permission catalog

## Safe Use Of This File

Treat it as historical naming guidance only. Any remediation work should validate against live code and generated permission artifacts before using names from this document.
