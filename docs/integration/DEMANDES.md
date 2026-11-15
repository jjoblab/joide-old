# Demandes au propriétaire (§6.8 règle 6)

Toute modification d'une brique passe par ce fichier ; JoIDE ne
modifie jamais celeris, code-lsp ou LayoutLib.

| # | Demande | État | Impact sur JoIDE |
|---|---|---|---|
| D-1 | **Ajouter `LICENSE` (Apache-2.0) + `NOTICE` à `LayoutLib`** (§6.8 règle 2a : avant l'intégration). À `720e85d` (v3.7.0) : toujours aucun fichier LICENSE ; README § Licence : « Projet interne — aucun droit accordé sans autorisation explicite ». | **close pour JoIDE** — déclaration du propriétaire du 2026-11-16 (canal zai-web, ouverture des lots B-F) : « Il n'y aura aucun problème juridique pour les dépôts code-lsp, LayoutLib et même celeris, ils m'appartiennent tous, c'est parce qu'ils ne sont encore stables que je ne les ai pas encore publiés sur jitpack. » Le titulaire des droits (auteur des trois dépôts) autorise explicitement l'usage et la redistribution par JoIDE : l'« autorisation explicite » exigée par le README de LayoutLib est donnée. | L'intégration et la redistribution des artefacts LayoutLib dans l'APK/AAB de JoIDE sont **débloquées** — consignées dans le `NOTICE` de JoIDE avec la référence de la déclaration. L'ajout d'un `LICENSE` au dépôt LayoutLib reste souhaitable pour l'extérieur, sans urgence pour JoIDE ; la consommation reste en Maven Local depuis le checkout épinglé (ADR-044 — pas de JitPack tant que les briques ne sont pas stables, selon le propriétaire). |
| D-2 | Clarifier la licence de `com.github.jjoblab.codeide-tooling:protocol` (§6.8 règle 2b, ADR-010). | **close pour JoIDE** — devenue inutile : JoIDE ne dépend d'aucun artefact codeide-tooling (l'AAR `:host` de code-lsp, qui l'expose dans son API publique, n'est pas consommé ; le socle `:model` embarque sa réécriture clean-room du protocole) | Aucune. Le `NOTICE` de code-lsp reste contradictoire en l'état (dit « non consommé » alors que `:host` le consomme) — à corriger côté brique si souhaité, sans urgence pour JoIDE. |
| D-3 | Confirmer les conditions des **binaires JetBrains** (§6.8 règle 2c) : `org.jetbrains:java-syntax` 0.3.381 provient du dépôt intellij-dependencies (« JetBrains Open-Source Build Terms ») et serait redistribué dans l'APK de JoIDE via le moteur Java de code-lsp. P-K2 (31/10/2026) a acté Apache-2.0 pour syntax-api/lang-syntax ; java-syntax reste sous Build Terms. | **en attente du propriétaire** | À confirmer avant livraison d'un APK embarquant le moteur Java ; ne bloque ni la préparation ni les tests. |
| D-4 | Resynchroniser les documents racine de code-lsp avec l'état réel (README : « XML à migrer », « Kotlin non branché » — tous deux en service à 1.15.0 ; NOTICICE : version 1.0.0 vs 1.2.0 consommée par `:host`). | proposition (facultative) | Aucune pour JoIDE — la vérité est dans les sources, consignée dans [code-lsp.md](code-lsp.md). |

## Historique

- 2026-11-16 (lots B-F) : le propriétaire déclare détenir les trois dépôts (celeris, code-lsp, LayoutLib) et annoncer « aucun problème juridique » pour leur usage et leur redistribution par JoIDE ; absence de publication JitPack uniquement parce que les briques ne sont pas encore stables — D-1 close pour JoIDE, la vérification des binaires JetBrains (D-3) reste distincte : elle engage un tiers, pas les dépôts du propriétaire.
- 2026-11-16 : ouverture du fichier en début de P8 (revérification des
  constats §6.8 sur les sources, ADR-010 confirmé).
