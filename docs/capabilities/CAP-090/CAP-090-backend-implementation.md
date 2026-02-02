# CAP-090 Backend Implementation Record

## Metadata
- **Capability**: CAP-090 - Contact Management (Roles, Preferences, and Consent)
- **Domain**: CRM
- **Date**: 2026-02-02
- **Branch**: cap/CAP090
- **Status**: IN_PROGRESS

## Implementation Summary

### Completed
1. ✅ Created `ContactRole` enum with 5 business roles (BILLING, PAYMENT_AUTHORIZER, OPERATIONS, PRIMARY_BUSINESS_CONTACT, TECHNICAL)
2. ✅ Created `ContactRoleAssignment` entity with composite primary key (contactId, customerAccountId, roleName)
3. ✅ Created `ContactRoleAssignmentRepository` with findBy queries
4. ✅ Created `CommunicationPreference` entity with consent flags and preference tracking
5. ✅ Created `CommunicationPreferenceRepository`
6. ✅ Updated `CrmContactsController` with proper OpenAPI annotations
7. ✅ Updated `CrmCommunicationPreferencesController` with proper OpenAPI annotations

### In Progress
8. 🔄 Creating service layer implementations (ContactRoleService, CommunicationPreferenceService)
   - **Blocker**: Party entity uses Long ID, not UUID. Need to resolve ID mapping strategy.

### Pending
9. ⏳ Complete service implementations with proper ID conversion
10. ⏳ Write ContractBehaviorIT provider tests
11. ⏳ Build and verify ArchUnit compliance
12. ⏳ Push branch and create PR

## Technical Notes

### ID Type Mismatch Issue
**Problem**: Contract guide and issue descriptions specify UUIDs for partyId/contactId, but:
- `Party` entity uses `Long` ID (@GeneratedValue(strategy = GenerationType.IDENTITY))
- `Person` entity uses `UUID` ID (@GeneratedValue(strategy = GenerationType.UUID))
- `CommunicationPreference` needs to store `party_id` - should this be Long or UUID?

**Resolution Options**:
1. Store Long ID in CommunicationPreference (matches Party entity)
2. Add UUID field to Party entity for external API use
3. Convert Long to String for API contracts (current approach in existing code)

**Decision**: Following existing pattern in codebase - use Long for Party ID internally, convert to String for API.

### Entity Relationships
- `ContactRoleAssignment`: Links Person (UUID) to CustomerAccount/Party (need to clarify which)
- `CommunicationPreference`: Stores preferences per Party (Long ID)
- `ContactPoint`: Existing entity links to Person (UUID)

## Files Created
- `pos-customer/src/main/java/com/positivity/customer/internal/entity/ContactRole.java`
- `pos-customer/src/main/java/com/positivity/customer/internal/entity/ContactRoleAssignment.java`
- `pos-customer/src/main/java/com/positivity/customer/internal/repository/ContactRoleAssignmentRepository.java`
- `pos-customer/src/main/java/com/positivity/customer/internal/entity/CommunicationPreference.java`
- `pos-customer/src/main/java/com/positivity/customer/internal/repository/CommunicationPreferenceRepository.java`

## Files Modified
- `pos-customer/src/main/java/com/positivity/customer/internal/controller/CrmContactsController.java`
- `pos-customer/src/main/java/com/positivity/customer/internal/controller/CrmCommunicationPreferencesController.java`

## Next Steps
1. Resolve ID type mapping strategy with team/architect
2. Complete service layer implementations
3. Write integration tests
4. Verify against contract guide
5. Create PR with full implementation

## Related Issues
- #108: Contact Role Management
- #107: Communication Preferences
- #106: Multiple Contact Points

## Contract Guide Reference
- `durion/domains/crm/.business-rules/BACKEND_CONTRACT_GUIDE.md` v1.2
