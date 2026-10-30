# IMS Tally Bridge — Chrome Extension

A Manifest V3 Chrome extension that lets accountants view and push IMS-approved
Tally vouchers from their browser without switching between applications.

## Developer Install

1. Open Chrome and navigate to `chrome://extensions`.
2. Enable **Developer mode** (toggle in the top-right corner).
3. Click **Load unpacked**.
4. Select the `tally-chrome-extension` folder (this folder).
5. The **IMS Tally Bridge** extension appears in your extensions list and toolbar.

## Configuration

1. Click the **IMS Tally Bridge** icon in the Chrome toolbar.
2. Enter your IMS server URL in the **IMS Server URL** field, for example:
   ```
   http://localhost:8080
   ```
   or for a production deployment:
   ```
   https://erp.example.com
   ```
3. Click **Save & Connect**.
4. The status indicator turns green when the server is reachable and returns voucher data.

## Using the Extension

- **Pending Vouchers** badge shows the count of APPROVED vouchers waiting to be sent.
  The badge auto-updates every 5 minutes in the background.
- The voucher table lists each pending voucher's source number, date, amount, and type.
- Click **Sync All to Tally** to mark all listed vouchers as SENT in IMS.
  The extension calls `PUT /api/integration/tally/voucher-queue/{id}/mark-sent` for each.
- **Last synced** timestamp is stored locally and shown in the footer.

## Permissions Explained

| Permission | Why it is needed |
|------------|-----------------|
| `storage` | Persists the IMS server URL and last-synced timestamp between sessions |
| `activeTab` | Reserved for future direct-to-Tally-tab automation |
| `alarms` | Runs a background poll every 5 minutes to keep the badge count fresh |

## Known Limitations

- **Tally must be open** for the XML payload to be consumed. This extension marks
  vouchers as SENT in IMS; it does not push the XML directly into Tally via a local
  HTTP bridge. Use the Tally TDL (`docker/tally/ims.tdl`) for the Tally-side
  integration.
- The extension does not authenticate with IMS. Ensure your IMS backend is on the
  same machine or within a trusted network, or configure a reverse proxy with TLS.
- CORS must be enabled on the IMS backend for `localhost` or the origin the extension
  runs as (this is already configured in the Spring Security settings for the
  `/api/integration/**` path).

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.0.0 | 2026-10-30 | Initial release — Phase 16 Integrations |
