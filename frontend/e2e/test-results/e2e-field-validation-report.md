# E2E Field Validation Report

**Date:** 2026-10-27  
**Purpose:** Verify every form field was filled and validated during E2E testing

---

## Key Forms Tested

### Master Data — Material Create/Edit
| Field | Value Used | Validation |
|-------|-----------|------------|
| Material Code | E2E-TEST-MATERIAL-001 | Required, unique ✅ |
| Description | E2E Test Material - Wire Rod Grade 2 | Required ✅ |
| UOM | KG | Required dropdown ✅ |
| Material Type | RAW_MATERIAL | Required dropdown ✅ |
| Min Stock | 50 | Numeric ✅ |
| Reorder Point | 100 | Numeric ✅ |
| Standard Cost | 850.00 | Money ✅ |
| Lead Time Days | 14 | Integer ✅ |
| UOM Conversions Tab | Alt UOM + factor | Numeric ✅ |
| Purchasing Tab | Purchase UOM, tax code, supplier | Dropdown ✅ |

### Master Data — Supplier Create/Edit
| Field | Value Used | Validation |
|-------|-----------|------------|
| Name | E2E Supplier Pvt Ltd | Required ✅ |
| Code | SUPP-E2E-001 | Required, unique ✅ |
| GSTIN | 29AABCT1332L1ZD | Format validated ✅ |
| Email | procurement@e2e-supplier.com | Email format ✅ |
| Phone | +91-9876543210 | Text ✅ |
| Payment Terms | NET_30 | Dropdown ✅ |
| Approved Materials Tab | Material chip added | Multi-select ✅ |

### CRM — Lead Create
| Field | Value Used | Validation |
|-------|-----------|------------|
| Source | WEB | Required dropdown ✅ |
| Status | NEW | Required dropdown ✅ |
| Customer | E2E Customer | Required ✅ |
| Contact | Rajesh Kumar | Text ✅ |
| Email | rajesh@e2ecustomer.com | Email ✅ |
| Est. Value | 125000 | Numeric ✅ |
| Description | E2E test lead | Text ✅ |
| Notes | Comprehensive note | Textarea ✅ |

### HRM — Employee Create
| Field | Value Used | Validation |
|-------|-----------|------------|
| Employee Code | EMP/26/00002 | Auto-generated ✅ |
| First Name | Priya | Required ✅ |
| Last Name | Sharma | Required ✅ |
| Email | priya.sharma@mfg.local | Email ✅ |
| Phone | +91-9876543210 | Text ✅ |
| Department | PRODUCTION | Dropdown ✅ |
| Date of Joining | 2026-10-27 | Date picker ✅ |
| Designation | Production Engineer | Text ✅ |
| Skills Tab | CNC Machining, Welding | Multi-value ✅ |
| Training Tab | Safety training added | Date range ✅ |

### Finance — Manual Journal
| Field | Value Used | Validation |
|-------|-----------|------------|
| Posting Date | 2026-10-27 | Required date ✅ |
| Description | E2E Test Manual Journal | Required ✅ |
| Line 1 Account | 5201 Salary Expense | Required dropdown ✅ |
| Line 1 Debit | 50000 | Numeric, Dr/Cr ✅ |
| Line 2 Account | 2401 GR/IR Clearing | Required dropdown ✅ |
| Line 2 Credit | 50000 | Numeric ✅ |
| Balance Check | Dr = Cr = 50000 | Enforced ✅ |

### Controlling — Overhead Allocation Rule
| Field | Value Used | Validation |
|-------|-----------|------------|
| Cost Centre | CC-PROD-01 | Required dropdown ✅ |
| Allocation Base | PRODUCTION_QTY | Required dropdown ✅ |

### Overhead Allocation Run
| Field | Value Used | Validation |
|-------|-----------|------------|
| Period | Period 7 (OCT-2026) | Required dropdown ✅ |
| Result | ₹75.44 allocated | Computed ✅ |

### WMS — Transfer Order Create
| Field | Value Used | Validation |
|-------|-----------|------------|
| Warehouse ID | 1 (MAIN-WH) | Numeric required ✅ |
| Item Type | MATERIAL | Dropdown ✅ |
| Item ID | 1 (DEMO-WR-65SI2) | Numeric required ✅ |
| Batch ID | 9 (RM-BATCH/26/00009) | Numeric required ✅ |
| From Bin ID | 3 (A-9VIHH) | Numeric required ✅ |
| To Bin ID | 6 (B-AZBFE) | Numeric required ✅ |
| Quantity | 2 | Numeric required ✅ |

### PLM — ECO Create
| Field | Value Used | Validation |
|-------|-----------|------------|
| Title | E2E Test ECO - Spring Wire Rod... | Required ✅ |
| Change Category | BOM_CHANGE | Required dropdown ✅ |
| Urgency | NORMAL | Required dropdown ✅ |
| Effectivity Date | 2026-11-15 | Date ✅ |
| Reason | Updated material specification... | Textarea ✅ |
| Description | This ECO updates the wire rod... | Textarea ✅ |
| Impact Assessment | Impact: BOM revision required... | Textarea ✅ |
| Affected Item Type | PRODUCT | Required dropdown ✅ |
| Affected Product | DEMO-CS-25X3 | Required autocomplete ✅ |
| Change Description | Update wire rod grade... | Textarea ✅ |

### Admin — Create User
| Field | Value Used | Validation |
|-------|-----------|------------|
| Username | e2e_test_user | Required, unique ✅ |
| Full Name | E2E Test User | Required ✅ |
| Email | e2e.test.user@mfg.local | Email format ✅ |
| Phone | +91-9876543210 | Text ✅ |
| Employee Code | EMP-E2E-001 | Text ✅ |
| Password | E2eTest@2026! | Policy: min 10 chars ✅ |
| Roles | [] (no roles) | Optional ✅ |

---

## Field Validation Issues Found

1. **WMS Transfer numeric IDs**: The form uses numeric DB IDs for item/bin selection rather than human-readable codes. Users must look up IDs elsewhere. (Accepted limitation per decisions.md)

2. **Document Upload MIME validation**: A plain text blob with `.pdf` extension is rejected with HTTP 415 "The file content does not match its .pdf extension". Proper PDF header bytes required. (Correct behavior — server-side validation is working as designed)

3. **Budget vs Actual period selection**: The `periodChanged()` method receives period ID as a number but `comp.periodId` stores it as signal. Correct values are period IDs 1-12 corresponding to APR-2026 through MAR-2027. (No defect)
