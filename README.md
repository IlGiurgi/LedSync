# LedSync

**Make your WLED lights dance to the music playing on your Android phone.**
Spotify, YouTube, any app: LedSync listens to the phone's own audio output, runs the analysis on the phone
and streams the light show to your WLED devices over Wi-Fi. No microphone on the ESP32 needed, no PC, no LedFx server.

[Leggi in italiano](README.it.md)

## Features
- **36 audio-reactive effects** rendered on the phone, many adapted from [LedFx](https://github.com/LedFx/LedFx)
  (Energy, Scroll, Power, Rain, Bands, Wavelength, Melt, Fire, Lava, Equalizer…). Lights go dark in silence and hit hard on the bass.
- **Several devices at once**, each rendered at its own LED count; per-device on/off, effect, direction, name.
- **LED matrices** (e.g. word clocks): equalizer columns, other effects as rings from the centre.
- **Yeelight** strips and bulbs (LAN control, music mode, automatic fallback).
- **Govee** lights with LAN Control, and **Philips Hue** lights through the bridge (one colour per light).
- **ESPHome** lights with the `wled` effect, and any firmware that accepts WLED realtime UDP (DNRGB).
- **Sound Sync mode**: send WLED's audio-sync packets and use WLED's own audio-reactive effects instead.
- 9 colour gradients, brightness, mirror, blur, colour roll, favourites.
- Tempo (BPM) detection with tempo-locked effects and colours that step on every beat.
- Automatic stop after a long silence; English and Italian interface.
- Automatic effect change on the beat, microphone mode (TV/PC/speakers), notification and Quick Settings tile.
- Built-in **connection and latency diagnostics** (frame rate, pauses, ping and packet loss per device).
- No ads, no tracking, no Google libraries, no internet access beyond your local network.

## Requirements
- Android 10 or newer (playback capture needs Android 10).
- WLED 0.14+ on the same Wi-Fi network (or Yeelight / ESPHome as above).
- Apps that block audio capture (some DRM or banking apps) cannot be followed; Spotify, YouTube and most players work.

## How it works
Android lets an app capture other apps' audio with `AudioPlaybackCapture` (the system asks for screen-sharing
consent; nothing is recorded or stored). Every 20 ms LedSync runs a 2048-point FFT, builds a 32-band
log-frequency melbank with auto-balanced lows/mids/highs, renders the effect for each device and sends the
pixels as WLED realtime UDP (DNRGB, port 21324). In Sound Sync mode it sends WLED Audio Sync v2 packets (port 11988).

## Build
```
./gradlew assembleRelease
```
Plain Java, Android framework only (minSdk 29). `tools/build-without-sdk.sh` builds without Gradle/SDK where
Google's Maven is not reachable. `tools/jvm-tests/` holds desktop JVM tests for the analysis and effects.

## License
GPL-3.0-or-later, see [LICENSE](LICENSE). Effects adapted from LedFx (GPL-3.0); see [NOTICE.md](NOTICE.md).
LedSync is an independent project, not affiliated with WLED, LedFx, Yeelight, Govee, Philips or ESPHome.
Privacy: [PRIVACY.md](PRIVACY.md).
