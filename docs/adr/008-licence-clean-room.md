# ADR-008 — Licence Apache-2.0 et approche clean-room

- **Statut** : Accepted (décision du propriétaire, 2026-11-14)
- **Date** : 2026-11-15

## Contexte

CodeAssist est en GPL-3.0-or-later : tout travail dérivé ne peut pas être distribué sous Apache-2.0 ; renommer des classes ne suffit pas. JoIDE est publié sous Apache-2.0 (D1).

## Décision

Développement clean-room à partir du prompt et du rapport d'analyse uniquement. Les idées générales (DAG, empreintes, ABI, buckets de dex — issues elles-mêmes d'AGP/Gradle) sont libres ; l'expression (code, structure, noms, textes) ne l'est pas. Consultation exceptionnelle de CodeAssist uniquement sur blocage réel, après épuisement de la documentation publique, selon le protocole du §4.1 (docs d'abord, périmètre minimal, note fonctionnelle sans code, implémentation depuis la note, journal docs/clean-room/JOURNAL.md reproduit dans chaque rapport de phase). Dépendances tierces Apache-2.0/BSD/MIT, EPL-2.0 acceptée en dépendance séparée (cas ecj déjà traité dans code-lsp), jamais GPL/AGPL.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Dérivation de CodeAssist | écartée | incompatible GPL/Apache |
| Clean-room avec consultation encadrée (retenue) | retenue | compatible, protocole traçable |
| Séparation stricte lecteur/implémenteur | option future | à envisager si distribution commerciale |

## Conséquences

Le journal clean-room est la preuve de provenance : vide en P0 (aucune consultation). NOTICE et docs/third-party.md obligatoires et tenus à chaque phase. L'auteur de l'ADR n'est pas juriste ; le propriétaire valide les cas limites.
