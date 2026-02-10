# CAP-054 Backend Implementation

## Capability Information

- **Capability ID:** CAP:054
- **Capability Name:** Period Close, Adjustments, and Reporting
- **Domain:** accounting
- **Parent Issue:** [durion#54](https://github.com/louisburroughs/durion/issues/54)
- **Backend Child Issue:** [durion-positivity-backend#125](https://github.com/louisburroughs/durion-positivity-backend/issues/125)
- **Contract Guide:** `domains/accounting/.business-rules/BACKEND_CONTRACT_GUIDE.md`
- **Contract Status:** stable-for-ui
- **Feature Branch:** `cap/CAP054`

## Story Summary

**Title:** Reporting: Produce Core Financial Statements with Drilldown

**Intent:** Generate core financial statements (P&L and Balance Sheet) for specified accounting periods with drilldown capability to source transactions.

**Key Requirements:**

1. Generate Income Statement (P&L) from posted GL entries for a date range
2. Generate Balance Sheet from posted GL entries as of a specific date
3. Support drilldown: statement line → accounts → journal lines → source events
4. Configurable Chart of Accounts mapping with GAAP default
5. Role-based access control (view, generate, export permissions)
6. CSV and PDF export formats for v1.0
7. Single entity, single currency for v1.0
8. Reports must be reproducible for the same parameters
9. All posted entries only (exclude DRAFT)

## Implementation Checklist

### ✅ Phase 1: Data Model & Entities

- [ ] Create `StatementLineMapping` entity for configurable COA to statement line mapping
- [ ] Create `StatementLineMappingRepository`
- [ ] Create migration for `statement_line_mappings` table
- [ ] Create migration for `report_audit_log` table

### ✅ Phase 2: DTOs & Response Models

- [ ] Create `IncomeStatementReport` DTO
- [ ] Create `BalanceSheetReport` DTO
- [ ] Create `StatementLineDetail` DTO
- [ ] Create `AccountDrilldownResponse` DTO
- [ ] Create `JournalLineDrilldownResponse` DTO
- [ ] Create `ReportExportRequest` DTO

### ✅ Phase 3: Service Layer

- [ ] Create `FinancialReportingService` interface
- [ ] Implement `FinancialReportingServiceImpl`:
  - `generateIncomeStatement(startDate, endDate)`
  - `generateBalanceSheet(asOfDate)`
  - `drilldownToAccounts(statementLineCode, startDate, endDate)`
  - `drilldownToJournalLines(accountId, startDate, endDate)`
  - `drilldownToSourceEvents(journalLineId)`
- [ ] Create `ReportExportService`:
  - `exportToPDF(report)`
  - `exportToCSV(report)`

### ✅ Phase 4: Controller Layer

- [ ] Create `FinancialReportingController`:
  - `GET /v1/accounting/reports/income-statement`
  - `GET /v1/accounting/reports/balance-sheet`
  - `GET /v1/accounting/reports/drilldown/accounts/{statementLineCode}`
  - `GET /v1/accounting/reports/drilldown/journal-lines/{accountId}`
  - `POST /v1/accounting/reports/{reportId}/export`
- [ ] Add OpenAPI annotations (`@Operation`, `@ApiResponse`)
- [ ] Add `@EmitEvent` annotations for audit

### ✅ Phase 5: Security & Access Control

- [ ] Define permissions:
  - `reporting:view:financial-statements`
  - `reporting:generate:financial-statements`
  - `reporting:export:financial-statements`
- [ ] Add `@PreAuthorize` annotations to controller methods

### ✅ Phase 6: Testing

- [ ] Create `FinancialReportingContractBehaviorIT`:
  - Test income statement generation (happy path)
  - Test balance sheet generation (happy path)
  - Test drilldown to accounts
  - Test drilldown to journal lines
  - Test unauthorized access (403)
  - Test invalid parameters (400)
  - Test reproducibility (same params = same result)
- [ ] Add unit tests for service logic
- [ ] Add ArchUnit tests if new packages introduced

### ✅ Phase 7: Configuration & Event Registration

- [ ] Add event type registrations:
  - `REPORT_INCOME_STATEMENT_GENERATED`
  - `REPORT_BALANCE_SHEET_GENERATED`
  - `REPORT_EXPORTED`
- [ ] Add configuration properties for reporting (if needed)

### ✅ Phase 8: Documentation

- [ ] Update OpenAPI spec (should auto-generate from annotations)
- [ ] Document COA mapping strategy in code comments
- [ ] Add README section for financial reporting module

---

## File Paths to Create/Modify

### New Files

**Entities:**

- `pos-accounting/src/main/java/com/positivity/accounting/internal/entity/StatementLineMapping.java`

**DTOs:**

- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/IncomeStatementReport.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/BalanceSheetReport.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/StatementLineDetail.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/AccountDrilldownResponse.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/JournalLineDrilldownResponse.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/dto/ReportExportRequest.java`

**Enums:**

- `pos-accounting/src/main/java/com/positivity/accounting/internal/enums/StatementType.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/enums/OperationType.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/enums/ExportFormat.java`

**Repositories:**

- `pos-accounting/src/main/java/com/positivity/accounting/internal/repository/StatementLineMappingRepository.java`

**Services:**

- `pos-accounting/src/main/java/com/positivity/accounting/service/FinancialReportingService.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/service/FinancialReportingServiceImpl.java`
- `pos-accounting/src/main/java/com/positivity/accounting/internal/service/ReportExportService.java`

**Controllers:**

- `pos-accounting/src/main/java/com/positivity/accounting/internal/controller/FinancialReportingController.java`

**Tests:**

- `pos-accounting/src/test/java/com/positivity/accounting/contract/FinancialReportingContractBehaviorIT.java`
- `pos-accounting/src/test/java/com/positivity/accounting/service/FinancialReportingServiceTest.java`

**Migrations:**

- `pos-accounting/src/main/resources/db/migration/V{next}_create_statement_line_mappings.sql`
- `pos-accounting/src/main/resources/db/migration/V{next}_create_report_audit_log.sql`
- `pos-accounting/src/main/resources/db/migration/V{next}_seed_statement_line_mappings.sql`

---

## Implementation Details

### Data Model

#### StatementLineMapping Entity

```java
package com.positivity.accounting.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.NonNull;
import java.util.UUID;

@Entity
@Table(name = "statement_line_mappings", indexes = {
    @Index(name = "idx_statement_type", columnList = "statement_type"),
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_statement_line_code", columnList = "statement_line_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementLineMapping {
    
    @Id
    @Column(name = "mapping_id", columnDefinition = "UUID")
    private UUID mappingId;
    
    @NonNull
    @Column(name = "account_id", length = 100, nullable = false)
    private String accountId;  // GL Account ID
    
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_type", length = 50, nullable = false)
    private StatementType statementType;  // INCOME_STATEMENT, BALANCE_SHEET
    
    @NonNull
    @Column(name = "statement_line_code", length = 100, nullable = false)
    private String statementLineCode;  // e.g., "PL_REVENUE_SALES", "BS_ASSETS_CURRENT"
    
    @Column(name = "line_description", length = 255)
    private String lineDescription;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "parent_line_code", length = 100)
    private String parentLineCode;  // For hierarchical subtotals
    
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", length = 50)
    private OperationType operation;  // SUM, SUBTRACT, NEGATE
}
```

#### Migration SQL

```sql
-- V{next}_create_statement_line_mappings.sql
CREATE TABLE statement_line_mappings (
    mapping_id UUID PRIMARY KEY,
    account_id VARCHAR(100) NOT NULL,
    statement_type VARCHAR(50) NOT NULL,
    statement_line_code VARCHAR(100) NOT NULL,
    line_description VARCHAR(255),
    display_order INT,
    parent_line_code VARCHAR(100),
    operation VARCHAR(50)
);

CREATE INDEX idx_statement_type ON statement_line_mappings(statement_type);
CREATE INDEX idx_account_id ON statement_line_mappings(account_id);
CREATE INDEX idx_statement_line_code ON statement_line_mappings(statement_line_code);

-- V{next}_create_report_audit_log.sql
CREATE TABLE report_audit_log (
    audit_id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    generated_by VARCHAR(100),
    generated_at TIMESTAMPTZ NOT NULL,
    start_date DATE,
    end_date DATE,
    as_of_date DATE,
    net_income DECIMAL(19, 2),
    total_revenue DECIMAL(19, 2),
    total_expenses DECIMAL(19, 2),
    total_assets DECIMAL(19, 2),
    total_liabilities DECIMAL(19, 2),
    total_equity DECIMAL(19, 2)
);

CREATE INDEX idx_generated_at ON report_audit_log(generated_at);
CREATE INDEX idx_report_type ON report_audit_log(report_type);
```

---

### Service Implementation

#### FinancialReportingService Interface

```java
package com.positivity.accounting.service;

import com.positivity.accounting.internal.dto.*;
import org.jspecify.annotations.NonNull;
import java.time.LocalDate;
import java.util.List;

public interface FinancialReportingService {
    
    @NonNull
    IncomeStatementReport generateIncomeStatement(
        @NonNull LocalDate startDate, 
        @NonNull LocalDate endDate
    );
    
    @NonNull
    BalanceSheetReport generateBalanceSheet(@NonNull LocalDate asOfDate);
    
    @NonNull
    List<AccountDrilldownResponse> drilldownToAccounts(
        @NonNull String statementLineCode,
        @NonNull LocalDate startDate,
        @NonNull LocalDate endDate
    );
    
    @NonNull
    List<JournalLineDrilldownResponse> drilldownToJournalLines(
        @NonNull String accountId,
        @NonNull LocalDate startDate,
        @NonNull LocalDate endDate
    );
}
```

#### Key Service Logic

```java
@Service
@RequiredArgsConstructor
public class FinancialReportingServiceImpl implements FinancialReportingService {
    
    private final JournalEntryRepository journalEntryRepository;
    private final StatementLineMappingRepository statementLineMappingRepository;
    private final GLAccountService glAccountService;
    
    @Override
    @Transactional(readOnly = true)
    public IncomeStatementReport generateIncomeStatement(LocalDate startDate, LocalDate endDate) {
        // 1. Load mappings for INCOME_STATEMENT
        List<StatementLineMapping> mappings = statementLineMappingRepository
            .findByStatementTypeOrderByDisplayOrder(StatementType.INCOME_STATEMENT);
        
        // 2. For each mapping, aggregate posted JE lines within date range
        Map<String, BigDecimal> lineItems = new HashMap<>();
        
        for (StatementLineMapping mapping : mappings) {
            BigDecimal balance = journalEntryRepository
                .sumPostedBalanceForAccount(
                    mapping.getAccountId(),
                    startDate,
                    endDate
                );
            
            // Apply operation (SUM, SUBTRACT, NEGATE)
            BigDecimal lineAmount = applyOperation(balance, mapping.getOperation());
            lineItems.put(mapping.getStatementLineCode(), lineAmount);
        }
        
        // 3. Calculate totals
        BigDecimal totalRevenue = lineItems.getOrDefault("PL_REVENUE", BigDecimal.ZERO);
        BigDecimal totalExpenses = lineItems.getOrDefault("PL_EXPENSES", BigDecimal.ZERO);
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        
        // 4. Audit log
        auditReportGeneration("INCOME_STATEMENT", startDate, end Date, netIncome);
        
        // 5. Build response
        return IncomeStatementReport.builder()
            .startDate(startDate)
            .endDate(endDate)
            .generatedAt(Instant.now())
            .lineItems(lineItems)
            .totalRevenue(totalRevenue)
            .totalExpenses(totalExpenses)
            .netIncome(netIncome)
            .build();
    }
    
    @Override
    @Transactional(readOnly = true)
    public BalanceSheetReport generateBalanceSheet(LocalDate asOfDate) {
        // Similar to income statement, but filter by asOfDate and include all accounts
        // Ensure Assets = Liabilities + Equity
        // ...
    }
}
```

---

### Controller Implementation

```java
package com.positivity.accounting.internal.controller;

import com.positivity.accounting.internal.dto.*;
import com.positivity.accounting.service.FinancialReportingService;
import com.positivity.events.EmitEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/accounting/reports")
@Tag(name = "Financial Reporting", description = "Generate core financial statements with drilldown")
@RequiredArgsConstructor
public class FinancialReportingController {
    
    private final FinancialReportingService financialReportingService;
    
    @GetMapping("/income-statement")
    @EmitEvent(id = "REPORT_INCOME_STATEMENT_GENERATE", apiVersion = "1")
    @PreAuthorize("hasAnyAuthority('reporting:view:financial-statements', 'reporting:generate:financial-statements')")
    @Operation(
        summary = "Generate Income Statement (P&L)",
        description = "Generate Profit & Loss statement for the specified date range from posted GL entries"
    )
    @ApiResponse(responseCode = "200", description = "Income statement generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid date range")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    public ResponseEntity<IncomeStatementReport> generateIncomeStatement(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate endDate
    ) {
        IncomeStatementReport report = financialReportingService.generateIncomeStatement(startDate, endDate);
        return ResponseEntity.ok(report);
    }
    
    @GetMapping("/balance-sheet")
    @EmitEvent(id = "REPORT_BALANCE_SHEET_GENERATE", apiVersion = "1")
    @PreAuthorize("hasAnyAuthority('reporting:view:financial-statements', 'reporting:generate:financial-statements')")
    @Operation(
        summary = "Generate Balance Sheet",
        description = "Generate Balance Sheet as of the specified date from posted GL entries"
    )
    @ApiResponse(responseCode = "200", description = "Balance sheet generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid date")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    public ResponseEntity<BalanceSheetReport> generateBalanceSheet(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NonNull LocalDate asOfDate
    ) {
        BalanceSheetReport report = financialReportingService.generateBalanceSheet(asOfDate);
        return ResponseEntity.ok(report);
    }
    
    @GetMapping("/drilldown/accounts/{statementLineCode}")
    @PreAuthorize("hasAuthority('reporting:view:financial-statements')")
    @Operation(
        summary = "Drilldown to accounts",
        description = "Drill down from a statement line to see contributing GL accounts"
    )
    public ResponseEntity<List<AccountDrilldownResponse>> drilldownToAccounts(
        @PathVariable @NonNull String statementLineCode,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<AccountDrilldownResponse> accounts = financialReportingService.drilldownToAccounts(
            statementLineCode, startDate, endDate
        );
        return ResponseEntity.ok(accounts);
    }
    
    @GetMapping("/drilldown/journal-lines/{accountId}")
    @PreAuthorize("hasAuthority('reporting:view:financial-statements')")
    @Operation(
        summary = "Drilldown to journal lines",
        description = "Drill down from an account to see individual journal lines"
    )
    public ResponseEntity<List<JournalLineDrilldownResponse>> drilldownToJournalLines(
        @PathVariable @NonNull String accountId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<JournalLineDrilldownResponse> lines = financialReportingService.drilldownToJournalLines(
            accountId, startDate, endDate
        );
        return ResponseEntity.ok(lines);
    }
}
```

---

### Test Implementation

```java
package com.positivity.accounting.contract;

import com.positivity.accounting.internal.dto.*;
import com.positivity.accounting.internal.entity.*;
import com.positivity.accounting.internal.enums.*;
import com.positivity.accounting.internal.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class FinancialReportingContractBehaviorIT {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private JournalEntryRepository journalEntryRepository;
    
    @Autowired
    private GLAccountRepository glAccountRepository;
    
    @Test
    @WithMockUser(authorities = {"reporting:generate:financial-statements"})
    void testGenerateIncomeStatement_HappyPath() {
        // Given: Posted GL entries with revenue and expense accounts
        GLAccount revenueAccount = createGLAccount("4000", "Sales Revenue", AccountType.REVENUE);
        GLAccount expenseAccount = createGLAccount("5000", "Salaries", AccountType.EXPENSE);
        
        JournalEntry revenueEntry = createPostedJournalEntry(
            LocalDate.of(2026, 1, 15),
            revenueAccount,
            BigDecimal.valueOf(10000)
        );
        
        JournalEntry expenseEntry = createPostedJournalEntry(
            LocalDate.of(2026, 1, 20),
            expenseAccount,
            BigDecimal.valueOf(3000)
        );
        
        // When: Request income statement for January 2026
        String url = "/v1/accounting/reports/income-statement?startDate=2026-01-01&endDate=2026-01-31";
        ResponseEntity<IncomeStatementReport> response = restTemplate.getForEntity(url, IncomeStatementReport.class);
        
        // Then: Report generated successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        IncomeStatementReport report = response.getBody();
        assertThat(report).isNotNull();
        assertThat(report.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(report.getTotalExpenses()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(report.getNetIncome()).isEqualByComparingTo(BigDecimal.valueOf(7000));
    }
    
    @Test
    @WithMockUser(authorities = {})  // No reporting permissions
    void testGenerateIncomeStatement_Unauthorized() {
        // When: User without permissions attempts to generate report
        String url = "/v1/accounting/reports/income-statement?startDate=2026-01-01&endDate=2026-01-31";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        // Then: Access denied
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    @WithMockUser(authorities = {"reporting:generate:financial-statements"})
    void testGenerateBalanceSheet_HappyPath() {
        // Given: Posted GL entries with asset, liability, and equity accounts
        // ...
    }
    
    @Test
    @WithMockUser(authorities = {"reporting:view:financial-statements"})
    void testDrilldownToAccounts() {
        // Given: Posted revenue entries across multiple accounts
        // When: Drill down on "PL_REVENUE" statement line
        // Then: All contributing accounts and their balances returned
    }
    
    @Test
    @WithMockUser(authorities = {"reporting:view:financial-statements"})
    void testDrilldownToJournalLines() {
        // Given: Posted entries for specific account
        // When: Drill down on account "4000"
        // Then: All journal lines for that account returned
    }
    
    @Test
    @WithMockUser(authorities = {"reporting:generate:financial-statements"})
    void testReportReproducibility() {
        // Given: Posted GL entries for a period
        // When: Generate same report twice with same parameters
        // Then: Results are identical (same line items, same totals)
    }
}
```

---

## Event Type Registration

Add to `AccountingEventTypes.java`:

```java
EventTypeRegistration.search("REPORT_INCOME_STATEMENT_GENERATE", "Generate Income Statement").build(),
EventTypeRegistration.search("REPORT_BALANCE_SHEET_GENERATE", "Generate Balance Sheet").build(),
EventTypeRegistration.write("REPORT_EXPORT", "Export financial report").build()
```

---

## Configuration

No additional configuration required for v1.0 (single entity, single currency, GAAP default).

Future enhancements (v2.0):

```yaml
accounting:
  reporting:
    standard: "US_GAAP"  # or "IFRS"
    current-entity-id: "entity-001"
    primary-currency: "USD"
    features:
      multi-entity-consolidation: false
      multi-currency-reporting: false
```

---

## Completion Criteria

- [ ] All files created and implemented as per checklist
- [ ] All tests passing (unit + contract behavior)
- [ ] Code follows package conventions (internal/ for controllers, DTOs, entities; service/ for public API)
- [ ] OpenAPI annotations complete
- [ ] Event logging in place
- [ ] Security annotations applied
- [ ] Changes committed to feature branch `cap/CAP054`
- [ ] Pull request created against `main`

---

## PR Description Template

```markdown
## Summary

Implements financial reporting capability (CAP-054) with Income Statement and Balance Sheet generation plus drilldown support.

## Changes

- Added `FinancialReportingService` and implementation
- Added `FinancialReportingController` with REST endpoints
- Added `StatementLineMapping` entity for configurable COA mapping
- Added DTOs for reports and drilldown responses
- Added contract behavior tests covering happy paths, authorization, and reproducibility
- Added event type registrations for report generation

## Endpoints Added

- `GET /v1/accounting/reports/income-statement` - Generate P&L
- `GET /v1/accounting/reports/balance-sheet` - Generate Balance Sheet
- `GET /v1/accounting/reports/drilldown/accounts/{statementLineCode}` - Drilldown to accounts
- `GET /v1/accounting/reports/drilldown/journal-lines/{accountId}` - Drilldown to journal lines

## Testing

- [x] Contract behavior tests pass
- [x] Unit tests pass
- [x] ArchUnit tests pass
- [x] Manual testing complete

## Related Issues

- Parent: #54
- Backend Story: #125

## Checklist

- [x] Code follows internal package structure
- [x] OpenAPI annotations present
- [x] Events logged with @EmitEvent
- [x] Security configured with @PreAuthorize
- [x] Tests cover happy path + error cases
- [x] Database migrations included
```

---

**Status:** Ready for implementation
**Estimated Effort:** 4-6 hours
**Next Step:** Begin Phase 1 (Data Model & Entities)
