# One To One Android Companion

Native Android client for the One To One premium fitness platform.

The application is built as a mobile companion to the existing Spring Boot One To One website. It gives public users, clients, trainers, and gym accounts a role-aware Android experience while keeping the website and database as the source of truth.

## Current Status

The Android app now includes:

- Public welcome, explore, login, signup, and demo mode.
- Role-aware navigation for client, trainer, and gym accounts.
- Hosted API configuration for the live Render web service.
- Secure remembered sessions using Android Keystore encryption.
- Calendar, day plan, training log, chat, profile, and role-specific list screens.
- Local validation and visible error states for required user input.
- An explicitly labelled sample workspace for exploring the app without an account.

The Android app points at:

```text
https://two025-group14-c24071109-1.onrender.com
```

The hosted server must include the `/api/mobile/**` Spring Boot endpoints for real login, signup, calendar, training, and chat calls to work.

## Repositories

Android application:

```text
Phone-App/application
```

One To One Spring Boot website:

```text
Web_App
```

## Build Commands

From the Android `application` directory:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

The app has been verified with `assembleDebug` and `installDebug` on the configured Android emulator.

## Documentation

- `overview.md` explains the application purpose, users, architecture, and current implementation.
- `requirements.md` lists functional and non-functional requirements.
- `api.md` explains Android APIs, libraries, security, and Spring Boot mobile endpoints.
- `underview.md` summarises how the app uses One To One and how it relates to the assignment.
- `retrospective.md` reflects on the implementation decisions, strengths, limitations, and future work.


## Version 2.0 preview and local verification

The native app is `2.0-preview`, versionCode 2. Its normal server default is the HTTPS address above. To test against the web `local` profile on port 8081 from an Android emulator:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -PoneToOneBaseUrl=http://10.0.2.2:8081 --console=plain
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Use the actual emulator serial from `adb devices`. The local endpoint override is for a debug preview; release validation requires HTTPS. Credentials are encrypted and bound to the selected server, with session preferences excluded from backup/transfer. Website links open a separate website session. Charlie in this native build uses automated template replies; human coaching messages and a connected generative provider are separate acceptance work.

On 2 October 2026 the debug build and four endpoint/routing tests passed; the installed API 35 emulator reached the local client dashboard, restored its session after restart and opened calendar/day data. This does not prove physical-device, TalkBack, complete offline, all-role or production acceptance. The web audit and dated implementation log record ongoing page-by-page progress.

The affiliation follow-up adds a role-specific “Gym connections on website” link to Account for trainers/gym admins, using the validated path allowlist. Its updated debug build and four unit tests pass in 42 seconds. This link build still needs a fresh emulator/physical-device interaction pass.

The next Account follow-up adds a trainer-only “Professional review” section, linking to the validated `/trainer/verification` website path. Website sign-in is required; current review status is read on the website rather than inferred from cached native verification. The updated local debug build and four unit tests passed in 11 seconds (`application/build/v2-native-professional-review-build.log`). This APK was installed on API 35: synthetic trainer login, Account sections/sign-out visibility, exact browser intent and website-login redirect were observed. Native Account retained its position when reopened. Chrome first-run notification onboarding limits further browser proof. Full role/device/theme/TalkBack/offline acceptance and the existing gym-link interaction remain open.
