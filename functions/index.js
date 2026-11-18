// Sends a push message whenever shared data changes in Firestore.
//
// Messages are data-only: the app (PushService.kt) decides the wording and whether to show
// anything, e.g. it stays silent on the phone that made the change (`by`).
//
// Topics: "watchers" = the household phones, "diner" = the person coming home.

const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

function send(topics, data) {
  // FCM data values must be strings; leave out anything unset.
  const clean = {};
  for (const [key, value] of Object.entries(data)) {
    if (value != null) clean[key] = String(value);
  }
  return Promise.all(
    topics.map((topic) =>
      getMessaging().send({ topic, data: clean, android: { priority: "high" } })
    )
  );
}

exports.onDayChanged = onDocumentWritten("days/{date}", async (event) => {
  const before = event.data.before.data() || {};
  const after = event.data.after.data() || {};
  const date = event.params.date;
  const sends = [];

  // Attendance (or arrival time) changed -> tell the household.
  if (before.status !== after.status || before.arrivalTime !== after.arrivalTime) {
    sends.push(
      send(["watchers"], {
        kind: "status",
        date,
        status: after.status,
        arrivalTime: after.arrivalTime,
        by: after.updatedBy,
      })
    );
  }

  // Dinner time for that day changed -> tell everyone.
  if (before.dinnerTime !== after.dinnerTime) {
    sends.push(
      send(["watchers", "diner"], {
        kind: "dinnerTime",
        date,
        dinnerTime: after.dinnerTime,
        by: after.updatedBy,
      })
    );
  }

  await Promise.all(sends);
});

exports.onConfigChanged = onDocumentWritten("config/household", async (event) => {
  const before = event.data.before.data() || {};
  const after = event.data.after.data() || {};
  if (before.defaultDinnerTime === after.defaultDinnerTime) return;
  await send(["watchers", "diner"], {
    kind: "defaultDinnerTime",
    dinnerTime: after.defaultDinnerTime,
    by: after.updatedBy,
  });
});
