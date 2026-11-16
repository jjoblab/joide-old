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
| 2026-11-16 | Robustesse et MCP de CodeAssist — revue des lots N/O et design des lots Q/R (mandat explicite du propriétaire : « tu peux même copie l'approche de certains aspects de son design notamment pour les lots N/O … Et aussi Go pour le lot Q et lot R ») | même dépôt, NOUVEAU commit `046e3cc98deaffa644c0211b275e69a48fcac3a2` : `services/agent-impl/…/impl/KeyFailover.kt`, `RequestPacer.kt`, `GeminiProvider.kt`, `GeminiWire.kt`, `LlmErrors.kt` + `services/agent-mcp/…/mcp/HttpMcpServer.kt`, `CodeAssistMcpServer.kt`, `client/` (McpClient, McpManager, McpTransports, McpOAuth, McpServerConfig, McpRemoteTool) + le test `KeyFailoverTest.kt` — ≈ 5 300 lignes | `docs/clean-room/003-robustesse-mcp.md` (note fonctionnelle : banc de clés et bascule, pacer apprenant, wire Gemini et cache de contexte, raffinement des quotas, client et serveur MCP — comportements et constantes, aucune ligne reprise) | lot Q (ADR-061/062) : FournisseurGemini, BancCles/FournisseurBascule, RegulateurDebit, bandes d'usage, images ; révision N/O : ErreursLlm (précédence, quotas portés) ; lot R (ADR-063) : client MCP (config .mcp.json, transports sur le port TransportHttp étendu, OAuth 2.1, normalisation de schémas, outils distants) et serveur MCP local |

Consultations **mandatées par le propriétaire** (2026-11-16), non pas
des consultations de blocage au sens du §4.1 : les notes fonctionnelles
vivent dans la série ADR-É (`docs/ecrans/`) que le propriétaire a
demandée. Le travail d'implémentation (lots G-R) restera clean-room :
les ADR-É1 et É9 ne contiennent aucune ligne de code CodeAssist, et
l'implémentation partira de ces notes, jamais du dépôt consulté.
Avant le 2026-11-16, aucune consultation (P0-P8 : prompt, rapport
d'analyse joint et documentation publique des outils uniquement).
