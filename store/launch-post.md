# Textes de lancement

À publier le jour où l'app est validée, pas avant : un lien mort au premier
commentaire tue un fil.

La règle qui gouverne les trois textes : **on raconte la découverte, pas
l'app.** « Voici mon bloqueur de pubs » n'intéresse personne, il en existe
cinquante. « J'ai mesuré que refuser les pubs les fait passer, et que se taire
les arrête » est un résultat, il est à toi, et il a un banc d'essai derrière.
Le lien vient à la fin.

---

## Hacker News — Show HN

**Titre** (80 caractères max, et c'est lui qui décide de tout)

```
Show HN: Blocking mobile game ads makes them load anyway – silence stops them
```

**Premier commentaire**, posté par toi juste après la soumission :

```
I'm 15 and I've been trying to get rid of the ads in mobile games. The usual
approach is a DNS blocklist that refuses the request. It works much less well
than you'd expect, and I wanted to know why.

Mobile games don't ask one ad network, they ask a mediation layer — AppLovin
MAX, ironSource LevelPlay, Unity Ads — which walks a waterfall of networks
until one answers. I set up a mitmproxy bench that could refuse, delay or pass
each ad host on demand, and ran three games ten times each.

Refusing instantly: ads loaded in 3/3 games. Every time.

The refusal is read as "this network is down", so the waterfall immediately
tries the next one, and the next, until a bidder answers. Blocking faster makes
it worse — you're just helping it find a working network sooner.

Never answering at all: 0 ads, at 5s, 15s and 70s of silence.

The waterfall stalls on the first entry. It never reaches the second. Five
seconds is enough, which is what makes it usable rather than a minute of
spinner.

So I built AdZero around that. It's a local VpnService, and the part I'm
happiest with is the routing: only the tunnel's own DNS address is routed into
the app, so DNS queries arrive and every other packet leaves normally. No
userspace TCP/IP stack to maintain, and no measurable battery cost. For an ad
domain it simply writes nothing back.

It also builds its own blocklist: an ad network is queried by dozens of
unrelated apps while an ordinary server is only queried by the app that owns
it, so counting distinct apps per domain finds networks no hand-written list
contains. It suggests, you decide — the same heuristic once proposed
ytimg.com, which would have killed every YouTube thumbnail.

What it can't do: YouTube and Instagram ads come from the same servers as the
videos, so nothing at the DNS layer can touch them without breaking the app.
Private DNS bypasses it entirely.

No server, no account, no analytics, and zero third-party libraries — the
dependency block is empty, the UI is written by hand. GPL-3.0.

Source: https://github.com/Ofanon/adzero
Measurements and method are in the README.
```

---

## Reddit — r/androidapps, r/fossdroid, r/privacy

Poste **un sub à la fois**, à quelques jours d'intervalle. Poster partout le
même jour se voit et se fait supprimer.

**Titre**

```
I measured why ad blockers fail in mobile games: refusing the request makes the ad load anyway
```

**Corps**

```
Mobile games use ad mediation — a waterfall of networks polled until one
answers. A DNS blocker that refuses the request reads as "this network is
down", so the game moves to the next one, and the next, until a bidder
responds. The ad shows up anyway.

I built a mitmproxy bench to test it properly. Three games, ten trials each:

- Instant refusal: ads loaded in 3/3 games
- Silence (no answer at all): 0 ads, at 5s, 15s and 70s

The waterfall stalls on the first entry and never advances.

I turned that into an app. Local VPN, DNS only, nothing leaves the device, no
account, no analytics, no third-party libraries at all. Free and GPL-3.0.

It won't touch YouTube or Instagram ads — those come from the same servers as
the videos, so blocking them breaks the app. I'd rather say that up front than
collect one-star reviews about it.

Source and method: https://github.com/Ofanon/adzero
```

---

## Ce qu'il faut faire une fois posté

**Reste devant pendant deux heures.** Un fil sans réponse de l'auteur meurt.
Les questions viendront sur trois points, prépare-les :

*« Pourquoi pas simplement un Pi-hole / AdGuard ? »* — Parce qu'ils refusent
au lieu de se taire, et c'est mesurable. C'est exactement le sujet du post.

*« VPN local = tu vois tout mon trafic. »* — Non : seule l'adresse DNS du
tunnel est routée dans l'app, tout le reste sort normalement. Le code est
public, `SilenceVpnService.kt`, la boucle fait soixante lignes.

*« Trois jeux, dix essais, c'est peu. »* — C'est vrai, et le README le dit.
Encourageant, pas concluant.

**Ne défends jamais l'app.** Défends la mesure, ou reconnais la limite. Un
auteur qui argumente contre les critiques perd le fil ; un auteur qui dit
« oui, c'est une faiblesse » le gagne.
