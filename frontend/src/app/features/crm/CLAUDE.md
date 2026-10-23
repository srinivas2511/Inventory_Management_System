# features/crm/ CLAUDE.md

Customer Relationship Management: leads, opportunities, contacts, quotations, activities, and pipeline.

## Screens
| Component | Purpose |
|-----------|---------|
| `CrmLeadListComponent` | Lead list with stage filter |
| `CrmLeadDetailComponent` | Lead detail, convert to opportunity |
| `CrmOpportunityBoardComponent` | Kanban board by stage |
| `CrmContactListComponent` | Contact list per customer |
| `CrmActivityPageComponent` | Activity log; `CrmActivityTimelineComponent` shows the timeline |
| `CrmQuotationListComponent` | Quotation list |
| `CrmQuotationFormComponent` | Quotation create/edit with line items |
| `CrmPipelineReportComponent` | Pipeline value summary by stage/owner |
| `CrmCustomerSegmentsComponent` | Customer segmentation view |
| `CrmPriceListComponent` | Price list management |

## API
`crm.api.ts` → `/api/crm/**`; requires `CRM_VIEW` / `CRM_MANAGE`.

## Notes
- Customers shown in CRM are the same entities as in `master/customer/` — CRM adds the relationship layer (activities, leads, pipeline) on top.
- Hidden for plant-scoped users (company-level function).
