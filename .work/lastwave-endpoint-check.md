# LastWave addon compatibility check

Observed during the Sonora integration task.

- Manifest URL: https://lastwaveaddons.clashprojects.qd.je/a/e94e546f32fed26a0b1b72841c2fe1cb6d3dce4b0915e23be8caddcc55d49b1b/manifest.json
  - Returned id `com.lastwave.addon`, name `LastWave`, version `1.0.0`, resources `search` and `stream`, quality select `lossless|atmos|high|low`; no `sonoraAuth` field.
- Search probe URL: https://lastwaveaddons.clashprojects.qd.je/a/e94e546f32fed26a0b1b72841c2fe1cb6d3dce4b0915e23be8caddcc55d49b1b/search?q=Daft%20Punk%20Get%20Lucky&quality=lossless&atmos=none
  - Returned catalog rows for Get Lucky, including track id `20115564` and an Atmos row id `328631601`.
- Lossless stream probe URL: https://lastwaveaddons.clashprojects.qd.je/a/e94e546f32fed26a0b1b72841c2fe1cb6d3dce4b0915e23be8caddcc55d49b1b/stream/20115564?quality=lossless&atmos=none
  - Returned success but pointed `url`/`directUrl`/`streamUrl`/`downloadUrl`/`mediaUrl` to `https://cdn.jsdelivr.net/gh/definatelynagato/pranks-cdn@main/gemi2-remix.mp3`, while declaring `format=flac`, `codec=flac`, `container=flac`, `mimeType=audio/flac`, `quality=Lossless`, 16-bit/44.1 kHz. The same response was seen for unrelated IDs `14830523` and `20115564`.
- LastWave Android client source: `C:\Users\Susil Kumar\Desktop\New folder (3)\LastWave-Native-main\LastWave-Native-main\app\src\main\java\com\lastwave\app\data\addon\AddonClient.kt` sends `X-LW-Intent: stream` and `User-Agent: LastWave-Player/1.0` on playback, and optionally `X-LW-TS`/`X-LW-Sign` when native/build-time signing is available.
- The shared secret `ADDON_CLIENT_SECRET` is a build-time input and is absent from `.env.example`; the provided project contains no server implementation or manifest route.
- Sonora's current source policy rejects addon manifests that do not declare Sonora auth. Its auth protocol sends `X-Sonora-Token` and `X-Sonora-Proof` after the manifest asks for it.
- A local PowerShell attempt to send LastWave playback headers did not complete because of HTTP/TLS client errors; no result from that header variant was observed.

These are diagnostic GETs only; no media bytes were downloaded or played.
