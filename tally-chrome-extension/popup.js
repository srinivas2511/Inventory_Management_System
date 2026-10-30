/* popup.js — IMS Tally Bridge popup logic */

'use strict';

const STATUS_CLASSES = { connected: 'connected', error: 'error', pending: 'pending' };

// DOM refs (set after DOMContentLoaded)
let serverUrlInput, btnConnect, statusDot, statusText, pendingCount,
    voucherBody, btnSyncAll, lastSynced, toast;

/** Read imsUrl from chrome.storage.local. */
function getStoredUrl() {
  return new Promise((resolve) => {
    chrome.storage.local.get(['imsUrl'], (result) => {
      resolve(result.imsUrl || '');
    });
  });
}

/** Persist imsUrl to chrome.storage.local. */
function storeUrl(url) {
  return new Promise((resolve) => {
    chrome.storage.local.set({ imsUrl: url }, resolve);
  });
}

/** Update the status indicator dot + text. */
function setStatus(state, message) {
  statusDot.className = 'status-dot ' + (STATUS_CLASSES[state] || 'pending');
  statusText.textContent = message;
}

/** Show a brief toast notification. */
function showToast(message, durationMs = 2500) {
  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), durationMs);
}

/** Format an ISO date string as DD/MM/YYYY. */
function formatDate(iso) {
  if (!iso) return '-';
  try {
    const d = new Date(iso);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  } catch (_) {
    return iso;
  }
}

/** Format a numeric amount as INR. */
function formatAmount(amount) {
  if (amount == null) return '-';
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: 2,
    }).format(amount);
  } catch (_) {
    return String(amount);
  }
}

/** Render the voucher table from an array of voucher DTOs. */
function renderVouchers(vouchers) {
  pendingCount.textContent = vouchers.length;

  if (!vouchers.length) {
    voucherBody.innerHTML = '<tr><td colspan="4" class="empty-msg">No pending vouchers</td></tr>';
    btnSyncAll.disabled = true;
    return;
  }

  btnSyncAll.disabled = false;

  voucherBody.innerHTML = vouchers.map((v) => `
    <tr data-id="${v.id}">
      <td>${v.sourceId || v.id}</td>
      <td>${formatDate(v.createdAt)}</td>
      <td>${formatAmount(v.amount)}</td>
      <td>${v.voucherType || '-'}</td>
    </tr>
  `).join('');
}

/**
 * Fetch approved (pending sync) vouchers from IMS backend.
 * GET {imsUrl}/api/integration/tally/voucher-queue?status=APPROVED
 */
async function fetchPendingVouchers(imsUrl) {
  setStatus('pending', 'Fetching vouchers...');
  try {
    const response = await fetch(
      `${imsUrl}/api/integration/tally/voucher-queue?status=APPROVED`,
      { method: 'GET', headers: { Accept: 'application/json' } }
    );
    if (!response.ok) {
      throw new Error(`Server returned ${response.status}`);
    }
    const data = await response.json();
    const vouchers = Array.isArray(data) ? data : [];
    renderVouchers(vouchers);
    setStatus('connected', `Connected — ${vouchers.length} voucher(s) pending`);
    return vouchers;
  } catch (err) {
    setStatus('error', `Error: ${err.message}`);
    renderVouchers([]);
    return [];
  }
}

/**
 * Mark a single voucher as SENT via:
 * PUT {imsUrl}/api/integration/tally/voucher-queue/{id}/mark-sent
 */
async function markVoucherSent(imsUrl, id) {
  const response = await fetch(
    `${imsUrl}/api/integration/tally/voucher-queue/${id}/mark-sent`,
    { method: 'PUT', headers: { Accept: 'application/json' } }
  );
  if (!response.ok) {
    throw new Error(`Failed to mark voucher ${id} as SENT (HTTP ${response.status})`);
  }
}

/** Sync all pending vouchers: mark each one as SENT. */
async function syncAllVouchers(imsUrl) {
  btnSyncAll.disabled = true;
  btnSyncAll.textContent = 'Syncing...';

  const rows = Array.from(voucherBody.querySelectorAll('tr[data-id]'));
  if (!rows.length) {
    showToast('No vouchers to sync');
    btnSyncAll.disabled = false;
    btnSyncAll.textContent = 'Sync All to Tally';
    return;
  }

  let successCount = 0;
  let failCount = 0;

  for (const row of rows) {
    const id = row.dataset.id;
    try {
      await markVoucherSent(imsUrl, id);
      successCount++;
    } catch (_) {
      failCount++;
    }
  }

  const now = new Date().toLocaleString('en-IN');
  lastSynced.textContent = now;
  chrome.storage.local.set({ lastSynced: now });

  const msg = failCount === 0
    ? `Synced ${successCount} voucher(s) successfully`
    : `Synced ${successCount}, ${failCount} failed`;
  showToast(msg, 3000);

  // Refresh the list
  await fetchPendingVouchers(imsUrl);
  btnSyncAll.textContent = 'Sync All to Tally';
}

// ---- Boot ----

document.addEventListener('DOMContentLoaded', async () => {
  serverUrlInput = document.getElementById('server-url');
  btnConnect     = document.getElementById('btn-connect');
  statusDot      = document.getElementById('status-dot');
  statusText     = document.getElementById('status-text');
  pendingCount   = document.getElementById('pending-count');
  voucherBody    = document.getElementById('voucher-body');
  btnSyncAll     = document.getElementById('btn-sync-all');
  lastSynced     = document.getElementById('last-synced');
  toast          = document.getElementById('toast');

  // Restore stored values
  const stored = await new Promise((resolve) => {
    chrome.storage.local.get(['imsUrl', 'lastSynced'], resolve);
  });

  if (stored.imsUrl) {
    serverUrlInput.value = stored.imsUrl;
    lastSynced.textContent = stored.lastSynced || 'Never';
    // Auto-fetch on open when we have a URL
    await fetchPendingVouchers(stored.imsUrl);
  }

  // Save & Connect button
  btnConnect.addEventListener('click', async () => {
    const url = serverUrlInput.value.trim().replace(/\/$/, '');
    if (!url) {
      setStatus('error', 'Please enter the IMS server URL');
      return;
    }
    await storeUrl(url);
    await fetchPendingVouchers(url);
  });

  // Sync All button
  btnSyncAll.addEventListener('click', async () => {
    const url = serverUrlInput.value.trim().replace(/\/$/, '');
    if (!url) {
      setStatus('error', 'Please configure the IMS server URL first');
      return;
    }
    await syncAllVouchers(url);
  });
});
