# Dinner Check-in

A small Android app for a household: one person (the **diner**) says whether they'll be home
for dinner and when; the others (the **household**) get notified and can set the dinner time.

It is one app. Each phone picks its role on first launch (changeable in Settings).

| | Diner | Household |
|---|---|---|
| Today | "Will you be at dinner tonight?" Yes / No, plus "home by" time | Big green / red / gray answer, change tonight's dinner time |
| Calendar | Tap a day to answer ahead of time | See the month, tap a day to change its dinner time |
| Settings | Reminders on/off | Usual dinner time (default 5:30 PM) |
| Notifications | Reminders at 9:00 AM, 2:00 PM, 4:00 PM while today is unanswered (with Yes / No buttons); dinner-time changes | Every change to attendance or arrival time; dinner-time changes made by the other person |

Calendar colours: gray = not set, green = coming, red = not coming.

## How data moves

Firebase, on its free allowances:

- **Cloud Firestore** stores the shared data and syncs it live (and works offline).
  - `days/{yyyy-MM-dd}`: `status` (`yes`/`no`, absent = unknown), `arrivalTime`, `dinnerTime` (override), `updatedBy`
  - `config/household`: `defaultDinnerTime`
- **Cloud Functions** (`functions/index.js`) watches those documents and sends a push through
  **Firebase Cloud Messaging** to the `watchers` and/or `diner` topic.
- **Anonymous Auth** gives each install an identity so the database isn't open to the world.
- The 9 / 2 / 4 reminders are local alarms on the diner's phone; no server involved.

## One-time setup

1. Create a project at <https://console.firebase.google.com>.
2. Add an Android app with package name `io.clayson.dinnercheckin`, download its
   `google-services.json` and replace the placeholder at `app/google-services.json`.
3. In the console: **Authentication → Sign-in method → Anonymous → Enable**, and
   **Firestore Database → Create database** (production mode).
4. Switch the project to the **Blaze** plan. Cloud Functions require it (and a card on file),
   but this app's usage is far inside the free quota.
5. Deploy the rules and the function:
   ```sh
   npm install -g firebase-tools
   firebase login
   firebase use --add            # pick your project
   (cd functions && npm install)
   firebase deploy --only firestore:rules,functions
   ```
6. Build and install on all three phones:
   ```sh
   ./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
   ```
   or open the folder in Android Studio and press Run.

On each phone: allow notifications when asked, and pick the role.
If a phone's battery saver is aggressive, exempt the app so reminders and pushes arrive on time.
