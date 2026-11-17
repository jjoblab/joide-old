# ADR-É10 — Plan des lots : UI v2 et agent IA (après P8)

- **Statut** : Accepted (GO du propriétaire reçu le 2026-11-16 : « Pour
  les 9 questions, je suis d'accord pour tes recommandations, à part
  pour OkHttp je le préfère plutôt de créé une maison. Donc, je te dis
  GO. » — D-1 à D-9 tranchées, D-5 : OkHttp retenu par le propriétaire,
  l'implémentation démarre par le lot G)
- **Date** : 2026-11-16

## Contexte

Trois états font ce plan : P8 est livrée (moteur complet, UI minimale
— arrêt pour validation) ; le design v2 est accepté en revue interne
(ADR-É1 à É8, maquettes `docs/ecrans/design/`) ; l'agent IA de
CodeAssist est analysé en profondeur sur son code (ADR-É9). Le
propriétaire mandate la planification : « Tu peux commencer à
planifier les lots ». Ce document découpe le travail, fixe l'ordre et
les critères ; il ne remplace ni les ADR-É (design fait autorité),
ni les ADR numérotées (chaque lot écrit les siennes pour ses
décisions techniques nouvelles).

## Principes de découpage

Hérités des règles de passage des phases (ROADMAP) :

1. Un lot = un périmètre **fermé à l'avance**, livrable seul : check
   vert sans SDK **et** avec SDK, tests purs JVM pour toute la
   logique, ADR des décisions nouvelles, `CHANGES.md`, backlog mis à
   jour, politique de langue verte, commit poussé.
2. Un lot ne casse jamais ce qui existe : l'UI P8 reste fonctionnelle
   jusqu'à son remplacement écran par écran ; l'agent est un module
   pur sans dépendance Android tant que le lot ne le câble pas.
3. La doctrine « l'UI reste mince » (P8, ADR-É2) s'applique à toute
   nouvelle surface : états d'écran purs testables JVM, vues du cadre
   sans AndroidX, Compose interdit (deux runtimes).
4. Les dépendances restent **uniquement descendantes** (ADR-011) ;
   l'arrivée du module `agent` met à jour le test du graphe.
5. Chaque lot d'UI se vérifie par **capture** contre sa maquette
   (`docs/ecrans/design/`), comme la revue de la série design.
6. La parité `values`/`values-fr` (ADR-012, point d'extension dormant
   depuis P0) se réactive au premier lot d'UI (G) : chaque écran
   livré porte ses chaînes dans les deux langues.

## Vue d'ensemble

Deux pistes, un seul fil conducteur : la piste UI redonne à l'app la
face que le propriétaire a exigée après son premier test ; la piste
agent apporte la fonctionnalnalité analysée en ADR-É9. Les lots
héritent de la numérotation du câblage P8 (A-F) : G à M pour l'UI,
N à R pour l'agent.

| Lot | Sujet | Taille | Dépend de | ADR-É |
|---|---|---|---|---|
| G | Système de design « Graphite » | M | — | É2 |
| H | Accueil « Mes projets » | M | G | É3 |
| I | Création de projet | M | G, H | É4 |
| J | Atelier adaptatif | L | G, lots P8 D/E | É5 |
| K | Console de build | M | J | É6 |
| L | Aperçu de mise en page | M | J | É7 |
| M | Paramètres | S | G | É8 |
| N | Noyau agent (module pur) | XL | — | É9 |
| O | Outils et espace de travail | L | N, briques P8 | É9 |
| P | UI du chat et intégration app | L | N, O, G, J | É9 (+ É11 à écrire) |
| Q | Robustesse et Gemini | M | N | É9 |
| R | MCP (client puis serveur) | L | N, O | É9 |

Tailles relatives au tiers de bench (S/M/L/XL) : effort de conception
+ code + tests, pas une durée calendaire — aucune date n'est promise.

## Piste 1 — UI v2 (lots G à M)

### Lot G — Système de design « Graphite »

- **Périmètre** : les jetons ADR-É2 (12 rôles + syntaxe, rayons
  16/12/999, mouvement 180/240 ms, typographie deux familles avec
  monospace code 13,5/22) ; le kit de composants dessiné (boutons,
  champs, cartes, puces, tiroir poussé, dock, dialogues, états
  vides) ; le clair/sombre ; la parité `values`/`values-fr` posée
  pour tous les lots suivants.
- **Livrables** : composants en vues du cadre dans `app-android`,
  écrans d'exercice du kit (pas encore de navigation), chaînes fr/en.
- **Critères** : capture des composants contre les jetons de
  `design/index.html` ; tests JVM des choix de composants purs (ex.
  état désactivé, troncature) ; check vert deux modes ; lint NewApi
  bloquant.
- **À décider en cours de lot** : rien d'ouvert — ADR-É2 fait
  autorité ; une ADR numérotée seulement en cas d'écart.

### Lot H — Accueil « Mes projets »

- **Périmètre** : le hub d'ADR-É3 — carte « Reprendre », création au
  premier plan, import SAF en action secondaire, état vide de
  première classe, cartes projet à une ligne de méta + menu ⋮
  (renommer, supprimer) ; la persistance des **projets récents**
  (dernière ouverture) qu'il faut créer.
- **Livrables** : écran d'accueil remplaçant l'ouverture P8 ;
  enregistrement des ouvertures ; détection native/Gradle/inconnu
  réutilisée (lot A).
- **Critères** : capture contre `design/accueil-projets.html` (états
  vide / peuplé) ; tests JVM de l'état d'écran (tri, reprise,
  suppression) ; ADR-049 — lieu et format des projets récents
  (fichier JSON atomique par `AtomicFiles`, proposition).

### Lot I — Création de projet

- **Périmètre** : l'assistant d'ADR-É4 — galerie de modèles avec
  franchise sur la carte (builds JVM sur l'appareil, APK au bureau),
  formulaire **piloté par le modèle** (un modèle apporte ses champs
  sans changer l'écran), récapitulatif atomique tout ou rien ;
  l'écriture du projet par `core-model` (`WorkspaceWriter`/
  `ModuleWriter`).
- **Livrables** : les premiers modèles JoIDE (application Android
  vide, bibliothèque JVM, console — liste à figer en cours de lot) ;
  l'écran en trois étapes.
- **Critères** : capture contre `design/creation-projet.html` (trois
  étapes) ; tests JVM (dérivation du paquet, valeurs par défaut du
  modèle, échec atomique) ; ADR-050 — format déclaratif des modèles
  (dérivé du `joide.toml`, pas un script).

### Lot J — Atelier adaptatif

- **Périmètre** : le cœur de la v2 (ADR-É5) — barre supérieure
  exacte, onglets multi-fichiers à état faible, tiroir arborescence
  avec sorties estompées, FAB construire, pilule de build en tâche de
  fond, retour système qui ferme les surcouches une à une, seuil
  720 dp (tiroir poussé dessous, dock console amarré dessus).
- **Livrables** : l'atelier remplace l'empilement programmatique P8 ;
  l'éditeur celeris (lot D) et l'aperçu (lot E) s'y montent.
- **Critères** : capture contre `design/atelier.html` (trois vues) ;
  tests JVM de la machine à états des surcouches (ordre de fermeture,
  onglets) ; le build se lance et s'arrête depuis la pilule ; ADR
  numérotée si la gestion des onglets diverge d'É5.

### Lot K — Console de build

- **Périmètre** : la console d'ADR-É6 — transcript coloré par nature
  en grille à deux colonnes, phases cliquables, problème ouvrable
  jusqu'à la ligne, écarts AVANT lancement conservés du lot F
  (jamais d'omission muette).
- **Livrables** : la console montée dans le dock de l'atelier ;
  extension de `ConsoleBuild` (formatage pur) pour la grille et les
  liens.
- **Critères** : capture contre `design/console.html` (build en
  cours / terminé avec problèmes) ; tests JVM du formatage et des
  liens ligne ; le protocole du démon reste inchangé.

### Lot L — Aperçu de mise en page

- **Périmètre** : l'aperçu d'ADR-É7 — rendu LayoutLib brut sur
  damier, stats de l'écouteur, bannières de franchise (dernier rendu
  valide pendant la frappe, repli natif annoncé, MockView), panneau
  amarré dans l'atelier.
- **Livrables** : le panneau au-dessus de `ApercuLayouts`/`
  ConfigurationsApercu` (lot E), reconfiguration au changement de
  fichier.
- **Critères** : capture contre `design/apercu.html` (valide /
  repli) ; tests JVM des bannières d'état.

### Lot M — Paramètres

- **Périmètre** : le hub d'ADR-É8 — réglages réels seulement, valeurs
  mesurées au disque, actions destructrices à conséquence nominée ;
  la page héberge aussi les réglages IA si la piste 2 a été livrée
  (clés par fournisseur, modèles, limites).
- **Critères** : capture contre `design/parametres.html` ; tests JVM
  des états (modification, conséquence nominée) ; chaînes fr/en.

## Piste 2 — agent IA (lots N à R)

### Lot N — Noyau agent (module pur)

- **Périmètre** : le module `agent` en Java 17, pur JVM, testable
  sans SDK — le modèle neutre (requête, événements, contenu à
  signature opaque), la boucle (prompt coupé par volatilité,
  historique append-only, lecture parallèle / mutation séquentielle,
  réparation d'historique, attentes visibles, plafond d'itérations en
  passation, compaction à hystérésis à décisions permanentes), la
  taxonomie d'erreurs actionnables, les permissions (gate, trois
  modes), la persistance des sessions (double moitié transcript +
  historique modèle, écriture atomique), et **deux** fournisseurs :
  Anthropic (Messages API) et OpenAI-compatible (Chat Completions,
  URL de base libre). Tout ce que JoIDE retient de l'ADR-É9 sans
  une ligne de CodeAssist.
- **Livrables** : module + tests JVM hors ligne (trames SSE rejouées
  via un faux transport, évaluations à modèle scripté jugeant l'état
  final, garde-fous « préfixe stable » et « budget de surface
  d'outils »).
- **Critères** : `check` vert sans SDK (le module ignore Android) ;
  zéro dépendance tierce hors décision D-5 (transport) ; JSON par le
  parseur maison (`core-deps/json`, ADR-028) ; aucune clé
  dans le dépôt ; quatre ADR au premier numéro libre : module dans le
  graphe, transport HTTP/SSE, politique de coûts (caching,
  compaction), stockage chiffré des clés.

### Lot O — Outils et espace de travail

- **Périmètre** : le port `EspaceTravailAgent` (lectures
  overlay-preferred, recherche, diagnostics, écritures par les
  services, exécution bornée) implémenté sur les briques P8 :
  `SessionLangageCeleris` (texte courant), `MoteursLangage`
  (diagnostics, définition, références), VFS/`FichiersProjet`
  (fichiers), `BuildService`/client du démon (`run_task` borné),
  résolveur P4 (`add_dependency` avec lockfile). Les outils v1
  (sous-ensemble assumé des 42 de CodeAssist : lecture 6, écriture 6,
  sémantique 4, build/tests 3, plan, mémoire, web 2), les
  checkpoints au premier toucher avec rapport d'annulation honnête,
  `ask_user`, et le **confinement par construction** (racines nommées,
  aucun chemin absolu hors racine — la leçon du risque n° 2).
- **Critères** : tests JVM sur faux espace de travail + tests
  d'intégration sur vraies briques (le pattern du lot C) ; chaque
  outil porte `mutating` et ses bornes de résultat **côté outil** ;
  ADR dédiée (premier numéro libre) : surface d'outils v1 et confinement ; le contenu web
  revient enveloppé `<contenu-non-fiable source=…>`.

### Lot P — UI du chat et intégration app

- **Périmètre** : d'abord l'ADR-É11 (design de l'écran IA — tiroir
  chat dans l'atelier, cartes d'outils pliables, diff Myers borné,
  dialogue de permission sur diff déplié à défaut sûr, cartes de
  questions, sélecteur de modèles, usage par tour) avec sa maquette,
  sur la méthode de la série ; puis l'implémentation en vues du
  cadre ; le service de premier plan Android piloté par observation
  (notification muette à bouton Stop, héritage ADR-043 pour le
  pattern) ; les réglages IA dans Paramètres (clés **chiffrées**,
  D-6) ; l'entrée « Demander à l'IA » sur la sélection de l'éditeur.
- **Critères** : capture contre la maquette ADR-É11 ; tests JVM des
  états d'écran (transcript, permissions, questions, erreurs
  actionnables « utiliser le modèle X ») ; un tour survit au
  changement d'écran (service de premier plan testé) ; arrêt propre
  mi-outils (historique réparé, conversation reprise).

### Lot Q — Robustesse et Gemini

- **Périmètre** : le troisième fournisseur (Gemini : cache de
  contexte serveur, budgets de réflexion), le basculement multi-clés
  à bancs différenciés, le régulateur préventif RPM/TPM qui apprend
  des erreurs, la comptabilité d'usage complète (entrées/sorties/
  cache, bandes d'alerte), les images (réduction côté client),
  `web_fetch` borné.
- **Critères** : tests à horloge fausse (failover, pacer) ; l'usage
  affiché somme toutes les requêtes du tour ; les limites apprises
  survivent à la conversation.

### Lot R — MCP (client puis serveur) — conditionné par D-4

- **Périmètre** (client d'abord) : lecture `.mcp.json` au format
  Claude Code/Cursor, transport Streamable HTTP (+ repli SSE),
  OAuth 2.1 complet (PKCE, enregistrement dynamique), normalisation
  des schémas tiers avant envoi, outils `mcp__<serveur>__<outil>`
  mutants sauf `readOnlyHint`, **retry seulement prouvé non exécuté**,
  approbation liée à l'identité du serveur. Puis (serveur) :
  exposition des outils JoIDE sur bouclage vérifié `Host`/`Origin`,
  jobs longs découplés (`task_status`, « ne le relancez pas ») ;
  le relais hébergé et le FTP restent à l'étude (D-7).
- **Critères** : tests contre serveurs MCP réels en JVM (le pattern
  `McpClientTest`) ; un outil mutant distant n'est jamais lancé deux
  fois ; ADR dédiée (premier numéro libre) : sécurité MCP locale (auth, secrets hors URL).

## Le module `agent` dans le graphe

`agent` est un module pur, pair de `core-tasks` : il dépend de
`core-platform` (exécuteurs, `CancelToken`, `Result`, `AtomicFiles`)
et du parseur JSON de `core-deps` — jamais d'Android, jamais des
briques (celeris/code-lsp/LayoutLib n'apparaissent que dans
l'implémentation du port, côté `ide-integration`/`app-android`). La
concurrence est explicite (pools bornés, pas de coroutines — ADR-009)
; les identifiants sont en anglais, Javadoc et documentation en
français. L'arrivée du module met à jour `gradle/module-graph.txt`,
le test du graphe (ADR-011) et `settings.gradle.kts`.

## Jalons proposés et arrêts pour validation

Chaque groupe se termine par un rapport d'avancement et un **arrêt
pour validation** du propriétaire (le rythme des phases) — à moins
qu'il ne préfère une validation par lot (D-8) :

| Groupe | Lots | Ce que le propriétaire peut tester |
|---|---|---|
| 1 — v2 alpha | G, H, I | l'app s'ouvre sur un accueil moderne, crée un projet, l'importe en second |
| 2 — v2 bêta | J, K, L | éditer dans l'atelier adaptatif, construire, lire la console, voir l'aperçu |
| 3 — v2 | M | réglages, finition — la v2 est complète |
| 4 — agent v1 | N, O, P | discuter avec l'agent, lui voir éditer le projet avec permission, annuler un tour |
| 5 — agent v1.1 | Q (puis R si GO) | trois fournisseurs, robustesse, usage ; l'écosystème MCP si décidé |

La piste 2 ne dépend de la piste 1 que par le lot P (il lui faut les
jetons G et l'atelier J) : les lots N, O et Q peuvent avancer dès que
le propriétaire l'ordonne, en parallèle du groupe 2 — c'est l'objet
de D-1.

## Décisions du propriétaire — GO du 2026-11-16

Réponse du propriétaire : « Pour les 9 questions, je suis
d'accord pour tes recommandations, à part pour OkHttp je le préfère
plutôt de créé une maison. Donc, je te dis GO. » Chaque recommandation
est donc retenue telle quelle, sauf D-5 où le propriétaire tranche
pour OkHttp.

- **D-1 — Ordre des pistes.** Recommandation : groupe 1 puis 2
  (l'exigence de design est la plus récente et la plus visible),
  pendant que les lots N et O avancent en parallèle dès le groupe 1
  clos ; l'agent v1 (groupe 4) après la v2 complète. Alternative :
  agent d'abord. — **Retenue.**
- **D-2 — Fournisseurs de la v1.** Recommandation : Anthropic +
  OpenAI-compatible (URL de base libre couvre les passerelles et les
  serveurs locaux) ; Gemini au lot Q. BYOK pur. — **Retenue.**
- **D-3 — Offre gratuite.** Hors v1 : c'est un modèle d'affaires
  complet (comptes, points, publicités, backend à créer), pas une
  fonctionnalité d'agent (ADR-É9 § offre gratuite). — **Retenue.**
- **D-4 — MCP.** Recommandation : différer au lot R, client avant
  serveur ; relais hébergé et FTP écartés de v1 (D-7). — **Retenue.**
- **D-5 — Transport HTTP/SSE.** Recommandation : implémentation
  maison sur `HttpURLConnection` avec lecture SSE dédiée (zéro
  dépendance, cohérent avec les parseurs maison ADR-028) ;
  alternative OkHttp (Apache-2.0) si la maintenance du TLS/redirects
  pèse. — **Décision du propriétaire : OkHttp.**
  L'implémentation maison est écartée : `okhttp` entre au
  catalogue au lot N (la seule dépendance tierce du module agent),
  NOTICE et `docs/third-party.md` mis à jour, ADR transport dédiée.
  Le transport du résolveur P4 (ADR-031) n'est pas concerné : il
  reste maison dans `core-deps`.
- **D-6 — Stockage des clés.** Recommandation : chiffré dès la v1 —
  clé dans Android Keystore, fichier AES-GCM via `AtomicFiles`, sans
  AndroidX ; jamais de clés de secours en clair. — **Retenue.**
- **D-7 — Surfaces locales.** Serveur FTP d'assets et relais MCP
  hébergé : écartés de v1 (surfaces d'attaque documentées, utilité
  desktop d'abord). — **Retenue.**
- **D-8 — Rythme de validation.** Par groupe (recommandation) ou par
  lot. — **Retenue : par groupe** (arrêt pour validation après G+H+I, puis J+K+L, puis M, puis N+O+P, puis Q/R).
- **D-9 — Concurrence avec le backlog P8/P9.** Embarquer D8/R8 sur
  appareil, accès SAF direct, stockage importé, débogueur : aucun
  n'entre dans ce plan ; à prioriser séparément si exigé. — **Retenue.**

## Hors périmètre de ce plan

Débogueur (JDWP/DAP, backlog), sub-agents, greffe de plugins
d'agent, magasin communautaire, Git, distribution des APK (D-3 du
rapport P8 inchangée), et tout ce qui touche au moteur de build —
ce plan ne modifie ni le démon, ni les pipelines, ni le modèle de
projet (sauf lecture : `add_dependency` passe par le résolveur P4 et
le lockfile existants).
