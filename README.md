# XScan — Get Your APK in 5 Steps (No Coding Needed)

XScan is a premium document scanner for Android: camera capture with
SureScan (takes 3 shots, keeps the sharpest), drag-the-corners cropping,
brightness/contrast/black-&-white controls, multi-page documents, and
export to PDF or JPEG by email, share, or print.

You don't need to install anything on your computer. GitHub builds the
app for you in the cloud, for free.

---

## Step 1 — Create the repository

1. Go to **github.com** and sign in.
2. Click the **+** in the top-right corner → **New repository**.
3. Name it `XScan`. Leave everything else as-is. Click **Create repository**.

## Step 2 — Upload these files

1. On your computer, **unzip** the XScan.zip you downloaded, and open
   the unzipped `XScan` folder so you can see its contents
   (`app`, `.github`, `build.gradle.kts`, etc).
2. On your new GitHub repository page, click the link that says
   **"uploading an existing file"**.
3. Select **everything inside** the XScan folder and drag it all into
   the upload box on the GitHub page.
   - **Important:** the `.github` folder must be included — it's what
     triggers the automatic build. On Windows it shows up normally.
     On Mac, press **Cmd + Shift + .** in Finder if you can't see it.
4. Scroll down and click **Commit changes**.

## Step 3 — Wait for the cloud build

1. Click the **Actions** tab at the top of your repository.
2. You'll see "Build XScan APK" running with a yellow dot.
3. Wait about 5–8 minutes until it turns into a **green check mark**. ✅

## Step 4 — Download the APK

1. Click the finished build (the green check mark row).
2. Scroll down to the **Artifacts** section.
3. Click **XScan-APK** to download a zip file.
4. Unzip it — inside is **app-debug.apk**. That's your app.

## Step 5 — Install on your phone

1. Get `app-debug.apk` onto your phone — easiest ways:
   - Email it to yourself and open the attachment on the phone, or
   - Upload it to Google Drive and download it on the phone.
2. Tap the file. Android will warn you about installing apps from
   outside the Play Store — tap **Settings** → allow **Install unknown
   apps** for that app (your browser/Files app), then go back and tap
   **Install**.
3. Open **XScan** and allow camera access when asked. Done! 🎉

---

## If the build fails (red ✗ instead of green ✓)

That can happen on a first build. No problem:

1. Click the failed run in the **Actions** tab.
2. Click the **build** job, then click the step that has a red ✗.
3. Copy the error text (the last ~30 lines are usually enough).
4. Paste it back to Claude and ask for a fix — you'll get corrected
   files and exact instructions on where to put them.

## Good to know

- The APK is a **debug build**, perfect for personal use. It won't be on
  the Play Store and your phone may occasionally remind you of that.
- Your scans are stored **only on your phone**. Nothing is uploaded
  anywhere.
- Every time you change a file in the repository, GitHub automatically
  builds a fresh APK.
