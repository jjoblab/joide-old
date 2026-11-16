# Écrans de JoIDE — série ADR-É et maquettes

Cette série définit l'architecture des écrans de l'application Android
JoIDE après le premier test du propriétaire (2026-11-16) : l'UI livrée
en P8 est fonctionnelle mais minimale (sélecteur SAF unique, aucun
écran de création, apparence brute du cadre). Elle est redessinée ici
sur la base d'une analyse de CodeAssist de tyron12233, avant toute
implémentation.

Chaque écran a son ADR (décision, états, franchise) et sa maquette
HTML autonome dans `design/` — le `design/index.html` est le preview
principal, les autres fichiers sont les previews écran par écran.

| Fichier | Sujet | Maquette |
|---|---|---|
| `001-analyse-codeassist.md` | inventaire et leçons des écrans de CodeAssist | — |
| `002-systeme-design.md` | palette, typographie, composants, stratégie d'implémentation | jetons dans `design/index.html` |
| `003-accueil-mes-projets.md` | Accueil « Mes projets » | `design/accueil-projets.html` |
| `004-creation-projet.md` | Assistant de création de projet | `design/creation-projet.html` |
| `005-atelier.md` | Atelier d'édition (adaptatif) | `design/atelier.html` |
| `006-console-build.md` | Console de build | `design/console.html` |
| `007-apercu-mise-en-page.md` | Aperçu de mise en page | `design/apercu.html` |
| `008-parametres.md` | Paramètres | `design/parametres.html` |
| `009-analyse-ia-codeassist.md` | Analyse du code IA de CodeAssist (agent, LLM, MCP, UI, tests) | — |
| `010-plan-lots.md` | Plan des lots d'implémentation : UI v2 (G-M) et agent IA (N-R) | — |

Statut : design accepté en revue interne ; l'implémentation viendra en
lots futurs. Le code existant (P8) n'est pas touché par cette série.

Révision du 2026-11-16 : le propriétaire rouvre le sujet IA (mandat :
« Étendre l'analyse pour les fonctionnalités de AI — pour l'IA tu dois regarder le
code »). L'ADR-É9 analyse le code de l'agent de CodeAssist ; l'ADR-É10 propose le
plan des lots d'implémentation (UI v2 G-M, agent IA N-R) avec ses questions D-1 à
D-9 au propriétaire. L'implémentation attend son GO.
