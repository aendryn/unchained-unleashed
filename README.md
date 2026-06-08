<p align="center">
  <img width="300" src="https://raw.githubusercontent.com/aendryn/unchained-unleashed/main/extra_assets/graphics/logo.svg">
</p>

# Unchained Unleashed

> **Unchained Unleashed** is a vibecoded community fork of [**Unchained for Android**](https://github.com/LivingWithHippos/unchained-android) by [LivingWithHippos](https://github.com/LivingWithHippos) that adds **[TorBox](https://torbox.app/)** support alongside Real-Debrid. All credit for the original application goes to the upstream project and its contributors; this fork only layers TorBox on top with some additional UI changes, QOL enhancements and security patches. See **What's new in this fork** below.

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)   [![API](https://img.shields.io/badge/API-22%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=22)

App to interact with the [Real-Debrid](https://real-debrid.com/) and [TorBox](https://torbox.app/) APIs.

### What is Real-Debrid / TorBox :question:

[Real-Debrid](https://real-debrid.com/) and [TorBox](https://torbox.app/) are *debrid* services: they
download files from hosting websites and the torrent network onto their own servers, which you can
then download or stream at high speed without needing premium accounts on every hoster.
**N.B. both are (cheap) paid services.** This fork lets you sign in to either one, or both at the
same time.

### What's new in this fork :rocket:

Everything from upstream Unchained, plus first-class **TorBox** support:

- [x] sign in with **Real-Debrid and/or TorBox** — the app works with either alone or both together
- [x] unified torrents list across both services, with a per-row badge showing which one each item is on
- [x] per-download **service selector** when adding a magnet/torrent (Real-Debrid / TorBox / Both)
- [x] TorBox torrent details: file list, pause / resume / delete, and on-demand link resolution
- [x] resolved TorBox links handed to the in-app downloader (with per-file progress notifications)
- [x] foreground torrent notifications cover **both** services
- [x] redesigned User and Settings screens with distinct per-service cards/sections

### Quality-of-life enhancements :sparkles:

- [x] **faster torrents list** — each service is paged a chunk at a time and fetched concurrently, so results show up quickly and fill in as you scroll instead of waiting for the whole account to load
- [x] TorBox's list is served from cache on routine opens but always refreshed after you add, delete, pause or resume (or pull-to-refresh), so the list reflects its true state without the wait
- [x] tapping a file in the TorBox torrent details sends it **straight to the Downloads tab**, matching Real-Debrid behaviour
- [x] unified **Downloads tab** with in-app playback and automatic re-resolution of expired TorBox links
- [x] TorBox deletes are reflected in the torrents list **immediately**

### Security :lock:

- [x] the Real-Debrid token and TorBox API key are **excluded from Android cloud backup and device-to-device transfer**
- [x] remote-service credentials (e.g. Kodi password, service API tokens) are **encrypted at rest** with a non-exportable Android Keystore key

### Features :memo:

- [x] magnets/torrents support
- [x] file hosting services support (Real-Debrid)
- [x] streaming support (needs a player that supports streaming like mpv or VLC)
- [x] search websites for files with plugins
- [x] containers support
- [x] user info
- [x] themes

### Screenshots :iphone:

| User | Torrents | Download Details | New Download |
| ---- | -------- | ---------------- | ------------ |
| <img width="150" src="extra_assets/graphics/user.jpg?raw=true" alt="User screen with connected Real-Debrid and TorBox accounts"> | <img width="150" src="extra_assets/graphics/downloads.jpg?raw=true" alt="Unified torrents list with a per-row badge showing Real-Debrid or TorBox"> | <img width="150" src="extra_assets/graphics/details.jpg?raw=true" alt="Download details screen with share, stream and send-to-player actions"> | <img width="150" src="extra_assets/graphics/new.jpg?raw=true" alt="New download screen with a Real-Debrid / TorBox / Both service selector"> |

### Media

Logo and symbols inspired by [minimal logo design set](https://www.rawpixel.com/image/843352/minimal-logo-designs-set) offered by [rawpixel.com](https://www.rawpixel.com)
Icons by [Fluent UI](https://www.svgrepo.com/collection/fluent-ui-icons-outlined/) offered by [SVG Repo](https://www.svgrepo.com/)
Backgrounds courtesy of [haikei](https://haikei.app/) and [SVG Backgrounds](https://www.svgbackgrounds.com/)
