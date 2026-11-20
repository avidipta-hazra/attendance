# HMAI Connect

A new Android app shell for the HMAI workflow.

## Pages

- **Attendance** — `https://66wbshpc26.trustwbhmai.in/attendance`
- **Printer Connection** — native LD0801 Bluetooth page + local HTTP print endpoint
- **Chat** — `http://66wbshpc26.trustwbhmai.in/chat`

The bottom navigation follows the supplied mockup: Attendance / Printer Connection / Chat, with a network-status area at the top.

## Build

Open this folder in Android Studio, or use the included GitHub Actions workflow.

GitHub Actions builds `app-debug.apk` and uploads it as an artifact.

## GitHub setup

1. Create a new empty GitHub repository.
2. Upload all files from this project to the repository root.
3. Open **Actions**.
4. Run **Build HMAI Connect APK** or push a commit.
5. Download the `HMAI-Connect-debug` artifact.

## Printer

Pair the LD0801 printer in Android Bluetooth settings first. In the app:

1. Open Printer Connection.
2. Refresh paired printers.
3. Select the LD0801.
4. Connect Printer.
5. Start Local Print Server.

The local endpoint is:

`http://127.0.0.1:9101/print`

This project forwards the POST body to the connected Bluetooth printer. Test the exact raster/protocol with the physical printer before production use.## Printer fixes in this version

- Prevents duplicate `127.0.0.1:9101` binds (`EADDRINUSE`).
- The Stop Local Print Server button now actually closes the ServerSocket.
- Reconnect closes the previous Bluetooth socket cleanly.
- Uses the original LD0801 SPP UUID and DeTong raster protocol.
- Uses 400 × 240 raster labels and the original label-feed `0x0C` command.
- Enables WebView mixed-content access so the HTTPS Attendance page can call the local HTTP printer bridge.
- Adds CORS response headers for browser/WebView print requests.

## Build fix

The network-status Boolean expression has been rewritten in an unambiguous Kotlin form to avoid the Kotlin parser error reported by GitHub Actions.


## AH Connect modern UI

- App name: **AH Connect**
- Transparent upper network indicator.
- Colorful gradient bottom navigation with modern selected state.
- Pull down on Attendance or Chat to refresh the web page.
- Modern printer card and controls.
- Footer: **Designed and Developed by Dr. Avidipta Hazra**.
