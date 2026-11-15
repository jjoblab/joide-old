# ADR-010 — Intégration des briques existantes derrière des interfaces JoIDE

- **Statut** : Accepted (confirmé au début de P8 — constats revérifiés sur les sources, docs/integration/ ; 2026-11-16)
- **Date** : 2026-11-15

## Contexte

Le propriétaire maintient déjà l'éditeur (celeris), les serveurs de langage (code-lsp) et l'aperçu de layouts (LayoutLib) ; JoIDE ne doit pas les réécrire.

## Décision

Un seul module, ide-integration, connaît ces briques ; aucun type tiers ne fuit dans l'API des modules core. Adaptateur core-model → ProjetLsp (contrat v11, snapshot immuable, empreintes cohérentes). Pas de dépendance à codeide-tooling:protocol (licence non clarifiée d'après code-lsp/NOTICE). Réutiliser avant d'écrire : index CIDX et ecj-art de code-lsp. LayoutLib : modules de rendu uniquement (pas l'app). Les constats du §6.8 du prompt seront REVÉRIFIFS sur les sources au début de P8 : si un constat est faux, c'est le dépôt qui a raison.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Intégration derrière interfaces (retenue) | retenue | couplage minimal, briques non modifiées |
| Fork des briques | écartée | coût de maintenance, dérive |
| Réécriture | écartée | hors périmètre (§13 du prompt) |

## Conséquences

Actions propriétaire avant P8 : LICENSE + NOTICE de LayoutLib ; clarification codeide-tooling ; conditions des binaires JetBrains ; tout besoin de changement des briques passe par docs/integration/DEMANDES.md.
