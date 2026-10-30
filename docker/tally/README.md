# IMS Tally TDL Extension

## What this TDL does

This TDL (Tally Definition Language) file extends **Tally Prime 3.x** to integrate
with the IMS (Inventory Management System) ERP backend.

Key features:
- Adds an **IMS Reference Number** field to every Voucher object so that vouchers
  generated or approved in IMS can be cross-referenced with the corresponding
  Tally entry.
- Adds a persistent **IMS API URL** variable (configurable by the user within Tally)
  that stores the base URL of your IMS backend.
- Adds an **IMS Sync** report (accessible from Gateway of Tally → IMS Sync) that
  lists all vouchers with their IMS reference numbers, the configured server URL,
  voucher number, date, party name, and amount.
- Adds an **IMS Sync** menu item to the Gateway of Tally for quick access.

## Requirements

- **Tally Prime 3.x** (Release 3.0 or later). This TDL uses Prime 3 syntax and has
  not been tested on Tally.ERP 9.
- The IMS backend must be reachable from the machine running Tally (default:
  `http://localhost:8080`).

## Installation

1. Copy `ims.tdl` to a local folder accessible by Tally, for example:
   ```
   C:\TallyPrime\TDL\ims.tdl
   ```
2. Open Tally Prime.
3. Go to **Help → TDL & Add-ons**.
4. Press **F4** (Manage Local TDLs) or choose **Manage Local TDLs**.
5. Select **Load** and browse to `ims.tdl`.
6. Accept and restart Tally Prime if prompted.
7. After restart, **IMS Sync** will appear in the **Gateway of Tally** menu.

## Configuration

Once the TDL is loaded:

1. Go to **Gateway of Tally → IMS Sync**.
2. The report header shows the currently configured IMS Server URL.
3. To change the URL, alter the `IMSApiURL` variable (accessible via TDL variable
   management or by modifying the default value in `ims.tdl` and reloading).

The default URL is `http://localhost:8080`. For production, set it to your IMS
server's base URL, for example `https://erp.example.com`.

## Usage

1. After approving a voucher in IMS, note the IMS Reference Number from the
   **Tally Voucher Queue** screen in IMS.
2. Open the matching voucher in Tally Prime.
3. The **IMS Reference No** field is now available in the voucher's key list.
   Enter the reference number from IMS.
4. Use the **IMS Sync** report (Gateway of Tally → IMS Sync) to audit which
   vouchers have been linked.

## Version

**1.0.0** — Initial release for IMS Phase 16 (Integrations).

## Troubleshooting

| Symptom | Action |
|---------|--------|
| "IMS Sync" does not appear in Gateway of Tally | Verify the TDL loaded without errors: Help → TDL & Add-ons → Show Loaded TDLs |
| `IMS Reference No` field is missing on voucher | Ensure Tally has restarted after loading the TDL |
| Report shows no vouchers | Check that vouchers exist in the current company for the active date range |
| Cannot reach IMS backend | Verify network connectivity to the URL shown at the top of the IMS Sync report |
