# ADR-005 — Daemon de build dans un process :build dès la v1

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Constat 10 de la revue : build et éditeur partagent le même tas dans l'IDE analysé ; un OOM de build tue l'IDE.

## Décision

Le noyau tourne dans un process :build (service de premier plan), IPC Binder/AIDL pour le contrôle et les petits événements, données volumineuses via le disque uniquement. Le daemon reconstruit son contexte depuis le disque ; la mort du daemon (OOM) devient « Build interrompu : mémoire » et l'UI survit. Compilateurs chauds, pré-chauffage à l'ouverture, arrêt sur inactivité. Fork de VM à gros tas pour D8-merge/R8 avec échelle de repli (fork, in-process, erreur explicite). Mode in-process équivalent conservé pour très petits appareils et tests, sans bascule automatique silencieuse.

## Options

| Option | Verdict | Raison |
|---|---|---|
| In-process | écartée | un OOM de build tue l'IDE |
| Process séparé (retenue) | retenue | plafonds séparés, reprise propre ; coût : deux runtimes |
| Build déporté seul | écartée | hors-ligne impossible |

## Conséquences

P6 livrera les preuves : OOM forcé, kill -9 en plein build, pic de tas ≤ 700 Mo au tier L, latence premier build mesurée.
