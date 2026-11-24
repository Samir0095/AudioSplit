# AudioSplit (experimental Android app)

AudioSplit detects connected audio input/output devices, provides a microphone-level test, and experimentally requests an Android communication output route.

## Your goal
- Listening: HOCO W35 Max Bluetooth headphones
- Microphone: BOYA BY-M1S wired lavalier
- App of interest: WhatsApp on Samsung Galaxy A04

## Important limitation
Android's public communication-routing API requests a communication OUTPUT device; Android automatically selects the corresponding input route. This app cannot guarantee cross-app routing or force WhatsApp to use a wired microphone while Bluetooth call output is active. This is an experimental diagnostic app, not a guaranteed WhatsApp routing fix.

## Build APK without editing code
A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`. After this project is uploaded to a GitHub repository, the workflow builds the APK automatically. The resulting `app-debug.apk` can be downloaded from the workflow's Artifacts section and installed on the phone.

Alternatively, open the project in Android Studio, allow it to install the required Android SDK/build tools, then use **Build > Build APK(s)**.

## Features
- Connected audio-device inventory
- Microphone input level test
- Experimental communication-output route request
- Clear experimental route

## Permissions
Microphone permission is used only for the live input-level test. Bluetooth device access is used to inspect connected audio devices.
