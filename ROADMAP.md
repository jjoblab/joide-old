# Feuille de route JoIDE — P0 → P8

État des phases du noyau (§11 du prompt : une phase livrée, un rapport
`PHASE-Pn-RAPPORT.md`, arrêt pour validation du propriétaire, puis GO).

| Phase | Périmètre | Modules remplis | État | Rapport |
|---|---|---|---|---|
| P0 | Squelette du dépôt, chaîne de build (règles de dépendances, politique de langue, contrôle API 28), banc d'essais S/M/L/XL | les 14 squelettes + `tools-bench`, `tools-test` | **Validée** | `PHASE-P0-RAPPORT.md` |
| P1 | Socle : empreintes 128 bits, store (SQLite + harnais de pannes), VFS (Merkle, observateur), modèle de projet (TOML maison) | `core-platform`, `core-store`, `core-vfs`, `core-model` | **Validée** | `PHASE-P1-RAPPORT.md` |
| P2 | Moteur de build : graphe de tâches, ordonnanceur à file de prêtes, décision à jour/passe à chaud, cache de sorties, observabilité | `core-tasks` | **Validée** | `PHASE-P2-RAPPORT.md` |
| P3 | Pipeline JVM : compilation incrémentale par ABI (corps inline capturés), compilation jointe Kotlin-puis-Java, jar déterministe, processResources, générateurs | `build-jvm` | **Validée** | `PHASE-P3-RAPPORT.md` |
| P4 | Dépendances : résolveur Maven + GMM, transport HTTPS vérifié, cache partagé adressé par contenu, lockfile `joide.lock`, extraction AAR protégée, hors-ligne strict | `core-deps` | **Validée** | `PHASE-P4-RAPPORT.md` |
| P5 | Pipeline Android : variantes, aapt2 (compilation incrémentale par fichier, lien, R), dex par scopes avec cache partageable entre projets, fusion par buckets, package (zipalign), signature, AAB, R8 | `build-android` | **Validée** | `PHASE-P5-RAPPORT.md` |
| P6 | Daemon : process `:build`, IPC, compilateurs chauds, admission mémoire, fork à gros tas pour D8-merge/R8, reprise après kill — preuves : OOM forcé, kill -9, tas ≤ 700 Mo au tier L, latence mesurée | `build-daemon` | **Validée** | `PHASE-P6-RAPPORT.md` |
| P7 | Import/export Gradle : import statique (sans exécution), SyncReport sans ignorance silencieuse, export d'échappatoire | `import-gradle` | **Validée** (GO du 2026-11-16) | `PHASE-P7-RAPPORT.md` |
| P8 | IDE : application Android (services de premier plan, UI), intégration celeris / code-lsp / LayoutLib derrière interfaces | `app-android`, `ide-integration` | **Livrée** — arrêt pour validation | `PHASE-P8-RAPPORT.md` |
| P9+ | Passage à l'échelle mesuré sur machine de référence, benches L/XL, révisions reportées (ADR-027) | — | Backlog | `docs/backlog.md` |
| V2 | UI v2 (ADR-É1 à É8) + agent IA (ADR-É9) : lots G-R (ADR-É10 acceptée, D-5 : OkHttp) | `app-android` (lots G-M : `ui/`, `accueil/`, `creation/`, `atelier/`, `console/`, `parametres/`), `agent` (lots N/O : noyau + outils), `ide-integration` (port agent sur les briques) | **En cours** — groupes 1, 2 et 3 livrés (G+H+I, J+K+L, M) et lots N+O livrés (amorce du groupe 4, GO combiné du propriétaire), reste P puis Q/R | `docs/ecrans/010-plan-lots.md`, ADR-048 à 059 |

## Règles de passage

1. Une phase = un périmètre fermé à l'avance (§5 du prompt) ; rien
   n'est commencé avant le GO du propriétaire sur la phase précédente.
2. Chaque phase se termine par : `./gradlew check` vert, ADR des
   décisions nouvelles, `CHANGES.md`, backlog mis à jour, rapport
   `PHASE-Pn-RAPPORT.md` (critères un par un, preuves, écarts,
   mesures, dette, questions), push, **arrêt pour validation**.
3. Le journal clean-room (`docs/clean-room/JOURNAL.md`) consigne toute
   consultation de CodeAssist ; les deux consultations à ce jour
   (2026-11-16, écrans puis agent IA) sont mandatées par le
   propriétaire et produites en notes fonctionnelles sans code
   (ADR-É1, ADR-É9) ; l'implémentation reste clean-room.

## Où regarder

- Décisions : `docs/adr/` (numérotées, une par décision).
- Dettes et reports : `docs/backlog.md`.
- Prise en main : `docs/dev.md`.
- Historique des livraisons : `CHANGES.md`.
