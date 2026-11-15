# Demandes au propriétaire (§6.8 règle 6)

Toute modification d'une brique passe par ce fichier ; JoIDE ne
modifie jamais celeris, code-lsp ou LayoutLib.

| # | Demande | État | Impact sur JoIDE |
|---|---|---|---|
| D-1 | **Ajouter `LICENSE` (Apache-2.0) + `NOTICE` à `LayoutLib`** (§6.8 règle 2a : avant l'intégration). À `720e85d` (v3.7.0) : toujours aucun fichier LICENSE ; README § Licence : « Projet interne — aucun droit accordé sans autorisation explicite ». | **en attente du propriétaire** — GO P8 reçu sans que l'action soit faite | Bloquante pour la **redistribution** des artefacts LayoutLib (APK/AAB de JoIDE, NOTICE). L'intégration est préparée et testable sur le checkout local ; la livraison embarquant LayoutLib reste conditionnée à D-1. |
| D-2 | Clarifier la licence de `com.github.jjoblab.codeide-tooling:protocol` (§6.8 règle 2b, ADR-010). | **close pour JoIDE** — devenue inutile : JoIDE ne dépend d'aucun artefact codeide-tooling (l'AAR `:host` de code-lsp, qui l'expose dans son API publique, n'est pas consommé ; le socle `:model` embarque sa réécriture clean-room du protocole) | Aucune. Le `NOTICE` de code-lsp reste contradictoire en l'état (dit « non consommé » alors que `:host` le consomme) — à corriger côté brique si souhaité, sans urgence pour JoIDE. |
| D-3 | Confirmer les conditions des **binaires JetBrains** (§6.8 règle 2c) : `org.jetbrains:java-syntax` 0.3.381 provient du dépôt intellij-dependencies (« JetBrains Open-Source Build Terms ») et serait redistribué dans l'APK de JoIDE via le moteur Java de code-lsp. P-K2 (31/10/2026) a acté Apache-2.0 pour syntax-api/lang-syntax ; java-syntax reste sous Build Terms. | **en attente du propriétaire** | À confirmer avant livraison d'un APK embarquant le moteur Java ; ne bloque ni la préparation ni les tests. |
| D-4 | Resynchroniser les documents racine de code-lsp avec l'état réel (README : « XML à migrer », « Kotlin non branché » — tous deux en service à 1.15.0 ; NOTICICE : version 1.0.0 vs 1.2.0 consommée par `:host`). | proposition (facultative) | Aucune pour JoIDE — la vérité est dans les sources, consignée dans [code-lsp.md](code-lsp.md). |

## Historique

- 2026-11-16 : ouverture du fichier en début de P8 (revérification des
  constats §6.8 sur les sources, ADR-010 confirmé).
