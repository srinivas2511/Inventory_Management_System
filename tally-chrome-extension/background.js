/* background.js — IMS Tally Bridge service worker */

'use strict';

const ALARM_NAME = 'ims-poll';
const POLL_INTERVAL_MINUTES = 5;

/** Update the extension badge with the pending voucher count. */
function setBadge(count) {
  const text = count > 0 ? String(count) : '';
  chrome.action.setBadgeText({ text });
  chrome.action.setBadgeBackgroundColor({ color: count > 0 ? '#c62828' : '#757575' });
}

/** Fetch pending voucher count from IMS and update the badge. */
async function pollPendingCount() {
  const stored = await new Promise((resolve) => {
    chrome.storage.local.get(['imsUrl'], resolve);
  });

  const imsUrl = stored.imsUrl;
  if (!imsUrl) {
    setBadge(0);
    return;
  }

  try {
    const response = await fetch(
      `${imsUrl}/api/integration/tally/voucher-queue?status=APPROVED`,
      { method: 'GET', headers: { Accept: 'application/json' } }
    );
    if (!response.ok) {
      setBadge(0);
      return;
    }
    const data = await response.json();
    const count = Array.isArray(data) ? data.length : 0;
    setBadge(count);
  } catch (_) {
    // Network error — clear badge, do not crash the service worker
    setBadge(0);
  }
}

// ---- Lifecycle hooks ----

/** On install: create the periodic polling alarm. */
chrome.runtime.onInstalled.addListener(() => {
  chrome.alarms.create(ALARM_NAME, {
    delayInMinutes: POLL_INTERVAL_MINUTES,
    periodInMinutes: POLL_INTERVAL_MINUTES,
  });
  // Initial poll
  pollPendingCount();
});

/** On alarm: poll the IMS backend and refresh the badge. */
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === ALARM_NAME) {
    pollPendingCount();
  }
});

/** When storage changes (e.g. user saved a new URL), re-poll immediately. */
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && changes.imsUrl) {
    pollPendingCount();
  }
});
