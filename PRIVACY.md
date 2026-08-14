# AdZero — Privacy Policy

**Last updated: 13 August 2026. Applies to AdZero 1.0.**

The short version: AdZero has no server, no account, and no analytics. Nothing
it learns about you is sent anywhere, because there is nowhere for it to be
sent. Everything below is the long version, and it is checkable — the source
code is public.

## What AdZero can see

AdZero runs a local VPN on your phone. That sounds broad, so here is the exact
limit: **only the tunnel's DNS address is routed into the app.** Your web pages,
messages, photos and video never pass through it. AdZero cannot read them, and
there is no code in it that could.

What it does see:

- **The domain names your phone looks up** — `example.com`, not the pages you
  open on it, not what you send or receive.
- **Which app made each lookup**, when you grant usage access. This is optional.
  Without it AdZero still blocks; it just cannot tell you which game was calling
  which ad network.
- **The apps that have an icon in your launcher**, so it can show their name and
  icon in the list. AdZero deliberately does not request `QUERY_ALL_PACKAGES`,
  which would expose every package on the device.

## What leaves your phone

**To the developer: nothing.** No analytics, no crash reports, no telemetry, no
"anonymous usage statistics". AdZero has zero third-party libraries, so there is
no advertising SDK or tracker embedded in it either.

**To the internet:** a domain name that is *not* an ad server still has to be
resolved, so AdZero forwards that question to **the resolver your phone was
already using** — your ISP's, your router's, your school's, whatever your
network provides — and sends it over that same network. AdZero does not
redirect your DNS. The only exception is when the phone will not name a resolver
at all, in which case AdZero falls back to Cloudflare's public resolver
(`1.1.1.1`) so that your connection keeps working rather than breaking.

You can check which one is in use:

```bash
adb logcat -s AdSilence
```

**One deliberate exception, added in 1.1.** Once a day, AdZero downloads the
list of ad servers from its own public repository on GitHub:

```
https://raw.githubusercontent.com/Ofanon/adzero/main/blocklist.txt
```

This is a plain file request, like a browser loading a page. It carries no
identifier, no account, no cookie, and nothing about you or your apps — GitHub
sees an IP address and the name of the file, as it would for anyone reading the
repository in a browser. Nothing is sent about what you block, what you play,
or what your phone resolves.

The downloaded list can only **add** ad servers. It cannot unblock anything,
change a setting, or disable a feature. Any entry that would touch an essential
domain — a bank, a messaging app, the phone's own services — is refused on your
device before it is ever used. You can switch the whole thing off in the
settings, and AdZero keeps working with the list built into the app.

**Backups are switched off** (`android:allowBackup="false"`). Android would
otherwise copy app data to your Google Drive automatically, which would
contradict everything above.

## What is stored, and where

On your device, in AdZero's private storage, readable by no other app:

| What | Why |
|---|---|
| Domains seen, and which apps asked for them | This is how the blocklist builds itself |
| Domains you silenced or chose to ignore | So your decisions survive a restart |
| Counters: ads stopped, per app, per day | The statistics screen and the widget |

**Uninstalling AdZero deletes all of it.** There is no copy anywhere else, so
there is nothing to ask anyone to delete.

## Permissions, and why each one exists

- **VPN** — the DNS filtering itself. There is no other way for an app to do
  this without rooting the phone.
- **Notifications** — the ongoing notice Android requires while a VPN runs, and
  optional alerts when an app is stalled.
- **Usage access** *(optional)* — to name the app behind a lookup.
- **Display over other apps** *(optional)* — the on-screen banner.
- **Ignore battery optimisation** *(optional)* — Android otherwise stops the
  service, and a blocker that gets stopped protects nobody.
- **Start at boot** — so protection is back after a restart without you doing
  anything.

Every optional one can be refused, and AdZero keeps blocking ads without it.

## Children

AdZero collects nothing, so it collects nothing about children either. It has no
accounts, no chat, no user-generated content and no ads of its own.

## Your rights

The GDPR gives you rights of access, correction, deletion and portability over
personal data held about you. The developer holds none: no data reaches them, so
there is no file to open, correct, export or erase. The data on your phone is
yours, and uninstalling the app removes it.

## Changes

If a future version of AdZero ever sends anything anywhere, this page will say
so before that version is published, and the change will be visible in the
source history.

## Contact

contact.gameskar@gmail.com

Or open an issue at https://github.com/Ofanon/adzero/issues — for anything about
what the app does with your data, a public answer is worth more than a private
one, since whoever asks next can read it too.
