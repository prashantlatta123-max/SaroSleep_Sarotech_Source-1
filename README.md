# SaroSleep — by Sarotech
**Developer:** Prashant Latta  
**Theme:** Monochrome White & Black Glassmorphism (Dark Glass + Light Frosted Glass)  
**Tech Stack:** Android (Kotlin), Pure Android SDK (Zero external libraries, ultra-lightweight, fast build)

---

## How Automatic Night Sleep Tracking Works
1. **Evening Start Window (Default 8:00 PM):** After 8:00 PM (customizable from 5 PM to 11 PM), whenever you lock your phone and there is no human activity, SaroSleep records your sleep start time.
2. **Early Lock Protection:** Even if you lock your phone slightly before 8:00 PM and sleep through the night, tracking automatically counts from your start window so your night is never missed.
3. **Smart Human Activity Detection:**
   - Short screen flashes from incoming notifications (< 45s) are ignored.
   - Unlocking your phone in the morning (`ACTION_USER_PRESENT`) ends the sleep session and saves it.
   - Prolonged morning alarm screen-on (>= 45s without unlock) also accurately detects wake time.
4. **Mid-Night Wakeup Merging:** Short awake gaps (<= 60 min, customizable) are merged into the same night while subtracting the awake time and counting wake-ups.

---

## Features Included
- **White & Black Glassmorphism UI** (Dark OLED Glass & Light Frosted Glass toggle)
- **Last Night Hero Card** with Sleep Score (0–100), Goal Progress Bar, Bedtime → Wake Time & Wake-up Count
- **7-Day Insights:** 7-Day Average, Average Score, Best Night, Daily Streak
- **Interactive 7-Night Bar Chart:** Tap any bar to inspect exact bedtime & wake time
- **Manual Sleep Log (`+ Add`):** Add sleep manually anytime if your phone battery died overnight
- **Bedtime Reminder:** Optional daily reminder notification with time picker
- **CSV File Export & Share + JSON Backup & Restore**
- **Auto-Start After Reboot** & Battery Optimization helper

---

## How to Build APK on GitHub (No Android Studio Needed)
1. Create a new repository on GitHub (e.g., `SaroSleep`).
2. Extract this ZIP and upload **all files and folders** (make sure the `.github/workflows/build-apk.yml` file is included!).
3. Commit to the `main` branch.
4. Go to your GitHub repo -> **Actions** tab -> click **Build SaroSleep APK**.
5. Once the green checkmark appears (~1–2 minutes), scroll down to **Artifacts** and download **`SaroSleep-Sarotech-APK`**.
6. Unzip it and install `app-debug.apk` on your Android phone!

---

## First-Time Phone Setup
- Allow **Notifications** when prompted (required for the low-priority tracking notification).
- Tap **Allow Background Running (Recommended)** inside the app so Android does not kill the night tracker.
