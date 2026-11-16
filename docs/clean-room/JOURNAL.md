# Journal clean-room (§4.1 du prompt)

Toute consultation de CodeAssist est consignée ici, avec : date, point
bloquant, pourquoi la documentation publique et le rapport d'analyse ne
suffisaient pas, fichiers consultés (chemin + commit), note fonctionnelle
produite (`docs/clean-room/NNN-sujet.md`), fichiers JoIDE concernés.
Le journal est reproduit dans chaque PHASE-Pn-RAPPORT.md pour revue du
propriétaire.

| Date | Sujet | Fichiers consultés | Note | Impact JoIDE |
|---|---|---|---|---|
| 2026-11-16 | Écrans de CodeAssist (mandat explicite du propriétaire, ADR-É1) | dépôt public `tyron12233/CodeAssist`, branche `main` commit `d155772` : arborescence complète + 16 fichiers d'écrans (`app/ide-ui-screens`, `app/ide-ui-core`, `app/ide-android`) | `docs/ecrans/001-analyse-codeassist.md` (la note fonctionnelle est l'ADR-É1 elle-même : structure, états, leçons — pas une ligne de code) | série ADR-É2 à É8 + maquettes `design/` (aucun code) |
| 2026-11-16 | Agent IA de CodeAssist (mandat explicite du propriétaire : « pour l'IA tu dois regarder le code ») | même dépôt, même commit : 67 fichiers ≈ 18 700 lignes (`services/agent-api`, `agent-impl`, `agent-mcp`, `agent-ui`, `app/ide-core/agent`, `AgentForegroundService`, `docs/agentic-coding.md`, 5 fichiers de tests) | `docs/ecrans/009-analyse-ia-codeassist.md` (note fonctionnelle : architecture, comportements, constantes — aucun code repris) | plan des lots ADR-É10 (lots N-R), future ADR-É11 |

Consultations **mandatées par le propriétaire** (2026-11-16), non pas
des consultations de blocage au sens du §4.1 : les notes fonctionnelles
vivent dans la série ADR-É (`docs/ecrans/`) que le propriétaire a
demandée. Le travail d'implémentation (lots G-R) restera clean-room :
les ADR-É1 et É9 ne contiennent aucune ligne de code CodeAssist, et
l'implémentation partira de ces notes, jamais du dépôt consulté.
Avant le 2026-11-16, aucune consultation (P0-P8 : prompt, rapport
d'analyse joint et documentation publique des outils uniquement).
