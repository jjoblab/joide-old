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
| `011-agent-ecran.md` | L'écran de l'agent : tiroir chat, cartes, permissions | `design/agent.html` |

Statut : design accepté en revue interne ; l'implémentation viendra en
lots futurs. Le code existant (P8) n'est pas touché par cette série.

Révision du 2026-11-16 : le propriétaire rouvre le sujet IA (mandat :
« Étendre l'analyse pour les fonctionnalités de AI — pour l'IA tu dois regarder le
code »). L'ADR-É9 analyse le code de l'agent de CodeAssist ; l'ADR-É10 propose le
plan des lots d'implémentation (UI v2 G-M, agent IA N-R) avec ses questions D-1 à
D-9 au propriétaire. GO du 2026-11-16 : « je te dis GO », D-5 tranchée OkHttp — l'ADR-É10 est acceptée.

Livraisons : lot G (système de design Graphite en code, ADR-048 — jetons
sombre/clair, kit de composants en vues du cadre, parité de langue
values/values-fr activée) ; **groupe 1 complet** : lot H (accueil « Mes
projets », projets récents JSON atomiques, classification des cartes,
ADR-049) et lot I (assistant de création en trois étapes, modèles
déclaratifs joide.template/1 en assets, génération atomique, ADR-050).
Arrêt pour validation du propriétaire (D-8 : rythme par groupe) — la suite
est le groupe 2 (J atelier, K console, L aperçu).

Livraisons du groupe 2 (2026-11-16) : **groupe 2 complet** — lot J
(atelier adaptatif : onglets multi-fichiers à état faible, tiroir
arborescence, FAB/pilule de build avec arrêt, seuil 720 dp, retour
qui ferme les surcouches une à une, ADR-051), lot K (console de
build : transcript coloré par nature en grille deux colonnes, phases
du pipeline cliquables avec durées mesurées, compteur d'erreurs
exact, problèmes ouvrables jusqu'à la ligne, plafond annoncé — le
protocole du démon inchangé, ADR-052) et lot L (aperçu de mise en
page : densité de rendu réelle, dimensions dp, ressources manquantes
détectées par le parseur maison, MockView comptées, bannières de
franchise, ADR-053). Arrêt pour validation du propriétaire — la
suite est le lot M (paramètres) qui complète la v2, puis la piste
agent (N-R).

Livraisons M, N et O (2026-11-16, GO combiné « Go pour M et N/O »)
: **lot M** (Paramètres, ADR-054 — la v2 est complète) ; **lot N**
(noyau agent : module pur, boucle, compaction, taxonomie,
sessions, deux fournisseurs, transport OkHttp D-5, ADR-055 à 058)
; **lot O** (outils v1 et espace de travail sur les briques,
confinement par construction, checkpoints, ADR-059). Arrêt pour
validation — la suite est le lot P (UI du chat, ADR-É11) qui clôture
le groupe 4.

Livraison du lot P (2026-11-16) : **groupe 4 complet « agent
v1 »** — l’ADR-É11 (ci-dessous) et sa maquette
`design/agent.html`, puis l’implémentation : tiroir chat de
l’atelier, cartes d’outils pliables, permission sur diff Myers
déplié (fermer = refuser), questions par cartes, usage par tour,
erreurs actionnables, service de premier plan à bouton Arrêter,
réglages IA à clés chiffrées (ADR-060). Arrêt pour validation —
la suite est le lot Q (robustesse, Gemini) puis R (MCP).

Chantier « Architecture des vues » A0-A9 (2026-11-18 → 2026-11-19,
[ADR-085](../adr/085-architecture-vues-ecrans.md), **Accepted**) :
les directives D1 (vues AndroidX), D2 (Material 3 habillé Graphite) et
D3 (popups, plus aucun dialogue) du propriétaire sont livrées —
chaque écran est une coque mince sur un layout racine (+
`layout-w720dp` pour l'atelier), l'atelier survit aux changements de
géométrie (`configChanges` + ré-inflation sans fermeture d'IDE,
`DecisionConfiguration` pure JVM), les dialogues sont remplacés par des
popups XML à deux gabarits (`PopupAncre`/`PopupCentre`,
`PortePopups`), le kit `ui/` ne garde que les vues sans équivalent
Material (`Icone`, `Nuancier`, `Stepper`, `EtatVide`, `TuileProjet`,
`TiroirPousse`, `Fab` double état, `Pilule` à rôle dynamique,
`Puce` à sélection pure) et le filet `ArchitectureVuesTest` verrouille
l'architecture à **exceptions vides**. Rapports de phase :
`PHASE-A0-RAPPORT.md` à `PHASE-A9-RAPPORT.md` à la racine du dépôt.
