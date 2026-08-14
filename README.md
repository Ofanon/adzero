# AdZero

An Android ad blocker that **stays silent** instead of saying no.

## Why silence beats blocking

Most blockers refuse ad requests instantly. That works less well than you would
expect, and measurement shows why.

Mobile games use *mediation* — AppLovin MAX, IronSource LevelPlay, Unity Ads —
which polls a waterfall of ad networks. An instant refusal reads as "this
network is down, try the next one", so the waterfall races down its list and
one bidder eventually answers. The ad shows up anyway.

Leave the request hanging instead and the waterfall **stalls**: every attempt
sits waiting on its timeout, the next network is never reached, and nothing
loads.

Measured on three games with a mitmproxy test bench, ten trials:

| Mode | Result |
|---|---|
| Instant refusal | ads still loaded in 3/3 games |
| Silence (5 s, 15 s, 70 s) | **0 ads loaded**, at every delay |

Five seconds of silence is enough, which is what makes this usable rather than
a minute of spinner.

## How it works

A local `VpnService` — no remote server, and nothing AdZero learns leaves the
device. The trick is the routing: **only the tunnel's DNS address is routed
into the app**, so every other packet leaves normally. No userspace TCP/IP
stack to maintain, and no measurable battery cost.

A question that is *not* for an ad server still has to be answered, so AdZero
passes it to the resolver the phone was already using — your ISP's, your
router's, whatever the network hands out — and sends it over that same network.
It does not redirect your DNS anywhere. A public resolver is used only if the
phone declines to name one at all; see [PRIVACY.md](PRIVACY.md).

For an ad-network domain, the app simply does not answer.

## Features

- **Self-building blocklist.** An ad network is queried by many unrelated apps;
  a legitimate domain only by the app that owns it. Counting distinct apps per
  domain finds networks no hand-written list contains — it caught
  `mtgglobals.com` (Mintegral) and `applvn.com` on the first day of real use.
  Suggestions are never auto-applied: the same heuristic proposed `ytimg.com`,
  which would have killed every YouTube thumbnail. You confirm, and you can
  always undo.
- **Worst offenders.** Counts ad *attempts*, not DNS queries: one ad triggers
  about a dozen, and a blocked SDK retries. Bursts are grouped, and a 20 s
  cooldown per app keeps retry storms from inflating the number.
- **Per-app protection.** Empty selection means everything is protected; pick
  apps to narrow the tunnel via `addAllowedApplication()`.
- **Daily history**, **tracker counter** (AppsFlyer, Adjust, Kochava… counted
  apart from ads), **5-minute pause**, home-screen **widget**, Quick Settings
  tile, and an on-screen alert when ads are killed in the app you just opened.

## Known limits

- **DNS level only.** Ads connecting straight to an IP, or an SDK shipping its
  own DoH resolver, get through.
- **Private DNS bypasses it entirely.** The app detects this and says so.
- **One VPN at a time.** Android allows only one; the app reports the clash.
- **Not Play Store material.** Google's Device and Network Abuse policy forbids
  apps that interfere with other apps' ads. Sideload or F-Droid.
- The measurements come from three games in one session. Encouraging, not
  conclusive.

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK 36. Create `local.properties` with your own
`sdk.dir=` — it is deliberately not committed.

## Tests

```bash
./gradlew testReleaseUnitTest
```

Sixteen tests, on the two pieces of logic where a mistake would be serious
rather than annoying: the validation of the downloaded blocklist, and the order
in which the four blocking sources settle disagreements.

The first exists because a downloaded list is code you run without reading it.
Its job is to prove that no entry — however it got into the repository — can
ever touch a bank, a messaging app or the phone's own services. The second
fixes the rule that a remote list never overrides an explicit choice made by
the person holding the phone.

JUnit is a `testImplementation` dependency: it compiles and runs the tests on a
developer's machine and never enters the APK, so the app itself still ships
with no third-party code at all.

## Localisation

English is the default. French, German, Spanish, Italian, Brazilian Portuguese
and Turkish ship alongside it. A new translation only needs a
`values-<code>/strings.xml` — no code changes.

Note for translators: Android requires apostrophes to be escaped as `\'` even
when written as the `&#39;` XML entity, which is decoded *before* the escaping
rules are applied.

## Test bench

The measurements above came from a separate mitmproxy harness that can silence,
block, or pass ad hosts on demand and record the verdict of each trial. It
lives in `../ad-timeout-test` and is what turned a hunch into a number.

## Privacy

AdZero has no server, no account and no analytics. Nothing it learns about you
leaves the device; a DNS question that is not for an ad server is passed to the
resolver your network already provides, and to nobody else. The details, and
the reasoning, are in [PRIVACY.md](PRIVACY.md).

## License

**GPL-3.0-or-later** ([full text](LICENSE)). A fork has to stay open, which is
the convention in this corner — NetGuard, AdAway and uBlock Origin are all GPL
— and it is the point of an app whose whole claim is that you can check what it
does.

Spelled out with the "or later" because "GPL-3.0" alone does not say whether a
future version of the licence may be used, and the tools that read licences
automatically need an exact identifier.
