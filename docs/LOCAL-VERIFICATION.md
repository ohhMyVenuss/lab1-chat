# Lab 1 — Local Verification Record

Date: 2026-11-01. Runtime available in the build environment: OpenJDK 21.0.12.

## Verified

- `ChatWire`, `ChatRepository`, and `ChatServerMain` compile with `javac --release 21`.
- Maven POM parses as valid XML.
- Six independent TCP client sockets logged in simultaneously with unique usernames.
- TCP Broadcast was delivered to all six sessions.
- Private message was delivered only to sender and selected recipient.
- Broadcast IMAGE attachment reached all six sessions; received bytes matched the original PNG.
- Private FILE attachment reached only sender and recipient; received bytes matched the original text file.
- Group AUDIO and VIDEO attachments reached all six group members; received bytes matched the original MP3/MP4.
- Server-side files were written to `data/chat-files`.
- DB startup fallback is implemented: if `LTM_CHAT_DB_URL` is set but MySQL cannot initialize, Server logs a warning and switches to disk-only operation. If no DB variables are set, it starts in disk-only mode.
- JavaFX client source passed a Java parser/compiler syntax pass; full compilation could not proceed because JavaFX artifacts are not installed in this environment.

## Not verified here

- Maven `clean package` and JavaFX launch on Windows (Maven/JavaFX are unavailable in this environment).
- Live MySQL persistence (requires a MySQL server and credentials).
- Windows Firewall rules and actual two-physical-PC LAN connectivity.
- UDP Broadcast and IP Multicast delivery across the user's Wi-Fi/router. These are network-dependent and must be verified on the target LAN.
- Audio/video are transferred as files over TCP and opened with the receiver PC's default media application; this is not a live microphone/webcam call.

Do not report the items in the second section as accepted until tested on the actual PCs.
