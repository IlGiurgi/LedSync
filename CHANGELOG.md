# Changelog

## 1.26 (27)
- Language switch (IT/EN button next to the title): automatic, Italian or English.

## 1.25 (26)
- Every effect tile shows a small animated preview, driven by a built-in demo song.
- Brightness per light (device options), on top of the overall brightness.

## 1.24 (25)
- Loading a favourite keeps the screen where it was instead of jumping back to the top.
- The guide explains how to delete a favourite (long-press).

## 1.23 (24)
- The "More effects" page stays open when you tap an effect, so you can try several in a row.

## 1.22 (23)
- The visualiser bars on the main screen take the colours of the effect that is playing (its gradient, or the chosen colour for Bass pulse).

## 1.21 (22)
- 8 new effects: Fireworks, Meteors, Beat ripples, Starry sky, Breathe, Heartbeat, Kaleidoscope, BPM Sweep (36 in total).

## 1.20 (21)
- Welcome pages on first launch that explain how to add and prepare lights (Yeelight/Govee LAN control, Hue button, WLED sync), start, customise, and what to avoid.
- New ⓘ button next to the title reopens the guide at any time.

## 1.19 (20)
- All pop-ups (device options, rename, add IP, favourites, diagnostics…) now use the app's own dark style instead of the phone's system dialogs.

## 1.18 (19)
- The visualiser bars on the main screen use the logo colours.

## 1.17 (18)
- English interface (Italian when the phone is set to Italian).
- Tempo detection: BPM shown while running, beat clock locked to the music.
- New effects: BPM Flash and BPM Chase, locked to the tempo.
- "Colours on the beat": the gradient steps forward on every beat.
- Automatic stop after 5, 15 or 30 minutes of silence.

## 1.16 (17)
- Fix: stopping and restarting quickly could leave the old run sending or stop the new one.
- Fix: a Yeelight still connecting when you pressed Stop could stay in music mode.
- A Wi-Fi drop on a Yeelight can no longer freeze the other lights (writes moved off the audio thread).
- Faster start: LED counts are read in parallel; an invalid address no longer blocks the start.
- Only the app's own Quick Settings tile can auto-start capture.
- Device info refresh no longer blocks WLED commands; fixed a thread leak when applying favourites.

## 1.15 (16)
- Govee lights (LAN API, found by "search network").
- Philips Hue lights through the bridge (press the link button once; ~10 changes per second shared by all lights).
- "Add IP" asks the device type first.

## 1.14 (15)
- Word clocks, ESPHome and other lights without WLED's JSON API now get the app's effects (and brightness) even in WLED Sound Sync mode.
- WLED commands (effects, speed, brightness) are only sent to real WLED devices: no more errors for Yeelight or the clock.

## 1.13 (14)
First public release as LedSync.
- 26 audio-reactive effects (many adapted from LedFx), gradients, mirror, blur, roll, favourites.
- Multiple WLED devices with per-device effect, direction, on/off, name, LED count and matrix layout.
- Yeelight support (music mode with automatic fallback, "test light"), ESPHome via the `wled` effect.
- Automatic effect change on the beat, microphone mode, notification action, Quick Settings tile.
- Connection and latency diagnostics.
