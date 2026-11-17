# PHASE P2 — RAPPORT

| | |
|---|---|
| Statut | Livré — **arrêt pour validation du propriétaire** (§11.4) |
| Date | 2026-11-15 |
| Machine d'exécution | Sandbox Linux (conteneur partagé) : 2 cœurs, 4 Go de RAM, stockage overlay — inférieure à l'appareil de référence D12 (8 cœurs, 6 Go) ; les mesures temporelles ci-dessous sont donc indicatives, les preuves structurelles sont exactes |
| Outils | Temurin JDK 21.0.13+9 (réinstallé dans le bac à sable après recyclage, risque R7 anticipé — même version que P0/P1), Gradle 9.8.2 (wrapper), SQLite 3.49.1.0 |
| Commits P2 | 12 commits français (`feat(core-store)` ×2, `test(core-store)`, `feat(core-tasks)` ×7, `test(core-tasks)`, `docs`), un par changement, auteur `jjoblab <olson12jb@gmail.com>` vérifié sur chacun |
| Journal clean-room | **Vide — aucune consultation de CodeAssist** (§4.1). Le travail repose sur le prompt, les ADR existants et la documentation publique des outils. |

---

## 1. Ce qui est fait

- **API de tâche** : entrées typées (fichier avec ou sans hachage de contenu, dossier, propriété scalaire pour les classpaths d'ABI jusqu'au pipeline P3), sorties typées, delta par fichier depuis la dernière exécution réussie exposé au contexte, revendications de ressources (cœurs, mémoire, exclusivité), exécution réussie ou échouée à diagnostics structurés, statuts finaux (réussie, à jour, du cache, échouée, annulée, sautée amont).
- **Graphe** : dépendances dures prioritaires ; arêtes « mustRunAfter » d'ordre seul, rejetées quand elles contredisent les dépendances ; inférence **optionnelle** sortie → entrée par trie de préfixes (jamais de comparaison tâche à tâche) ; détection de cycle par un seul Kahn avec reconstruction du chemin exact dans le message.
- **Ordonnanceur** : file de prêtes par compteurs de dépendances — **aucune barrière de niveau** ; priorité par chemin critique restant pondérée par les durées historiques du store (estimation par type par défaut) ; admission de ressources avec plafonds rétrécissables et exclusivité ; une tâche bloquée par le budget n'empêche pas une plus petite de passer ; modes `FAIL_FAST` (défaut) et `KEEP_GOING` (saut en cascade des dépendants d'un échec) ; annulation coopérative par jeton — jamais par annulation de futur, qui interbloquerait la comptabilité des tâches en file ; le retour n'a lieu qu'une fois toutes les tâches terminées **et** tous les vols achevés.
- **Empreintes et état d'exécution** : empreinte SHA-256 d'une chaîne canonique ancrée (chemins relatifs aux racines nommées, version d'outil, options) — **relocalisable par construction** ; hachage stable des fichiers (relectures bornées jusqu'à stabilité) ; réutilisation du hachage précédent sans re-hachage quand taille, date et clé n'ont pas changé ; **un chemin sale n'est jamais servi par un hachage passé** ; enregistrement d'exécution codé en binaire stable (aller-retour testé), rechargé du store en une transaction à froid, gardé en mémoire au chaud.
- **Cache de sorties adressé par contenu** : entrées immuables (zone temporaire + déplacement atomique), restauration exacte vers les emplacements déclarés (y compris depuis un autre emplacement du projet), éviction « le moins récemment utilisé » qui supprime les fichiers, index persisté dans le store, entrée illisible = exécution de la tâche (sur-invalidation, jamais un échec de build).
- **Moteur** : chaîne de décision unique — empreinte, comparaison à l'enregistrement, vérification des sorties **seulement si le journal les signale sales** (détection de falsification), restauration du cache, exécution, hachage des sorties, enregistrement, mise en cache ; **passe à chaud** : si aucun chemin n'est sale et que chaque tâche retrouve son enregistrement avec la même empreinte recalculée en mémoire, le build entier est déclaré à jour sans ordonnancer la moindre tâche ; toutes les écritures du store groupées en une transaction de fin de build, seulement s'il y a quelque chose à écrire.
- **Observabilité** : rapport par tâche dans l'ordre du graphe (statut, diagnostics, durées par étape — empreinte, vérification, exécution, hachage des sorties —, tas observé) ; build scan exporté en JSON détaillé et résumé lisible, écrit à la main, sans dépendance ; pic de tas du build (échantillonneur injectable + relevés par tâche).
- **Store alternatif (item backlog P2)** : prototype complet en journal à ajouts (`JournalBuildStore`) — transactions par blocs avec somme de contrôle, rejet des blocs tronqués, compaction au-delà d'un seuil, mêmes sémantiques que SQLite (transaction abandonnée = état rejoué depuis le disque) ; **mesuré** contre SQLite sur les charges du moteur ; décision consignée ADR-021 : **SQLite conservé par défaut**, le journal reste comme implémentation de référence du contrat.
- **Interface `BuildStore` étendue** : `listCacheEntries()` pour recharger l'index du cache à froid ; `evictCacheBeyond` retourne désormais les entrées évincées (et non plus une somme) pour que l'appelant supprime leurs fichiers — l'évolution est portée par les trois implémentations (SQLite, journal, fixture mémoire).

## 2. Critères d'acceptation P2, un par un

| # | Critère (§7 P2) | Statut | Preuve (commande + sortie) |
|---|---|---|---|
| 1 | **Propriétés de l'ordonnanceur** (graphes aléatoires) : jamais deux tâches dépendantes simultanées, aucun interblocage, résultat déterministe, annulation sans orpheline | ✅ | `./gradlew :core-tasks:test --tests '*SchedulerPropertyTest*'` → **BUILD SUCCESSFUL** ; 4 tests, **1 040 exécutions** de builds de graphes aléatoires à graine fixe 20260115, toutes sous perturbation des délais (exécuteur turbulent à graine affichée) : 320 achèvements complets, 200 vérifications « aucune tâche ne démarre avant la fin de ses dépendances » (horodatées par les événements de l'ordonnanceur, émis sous verrou), 480 comparaisons de déterminisme (statuts identiques à quatre exécutions près, avec tâches en échec et cascades de saut), 40 annulations (le build retourne, la tâche en vol a observé l'annulation, l'exécuteur répond immédiatement à une sonde) |
| 2 | **Barrière supprimée** : sur un niveau avec une tâche lente et une rapide dont dépend une chaîne longue, temps mur ≤ chemin critique + ε | ✅ | `./gradlew :core-tasks:test --tests '*NoBarrierTest*'` → **BUILD SUCCESSFUL** ; sortie : `[barriere] mur 2010 ms ; chemin critique nominal 2000 ms ; une barrière de niveau exigerait au moins 3500 ms` — ε = 10 ms (0,5 %) ; preuve structurelle exacte incluse : la chaîne démarre à 9 999 691 ms d'horloge pendant que la tâche lente (2 s) tourne encore (fin à 9 999 867 ms) — impossible avec une barrière de niveau |
| 3 | **Admission mémoire** : avec un budget artificiel, la somme des revendications en cours ne le dépasse jamais | ✅ | `./gradlew :core-tasks:test --tests '*MemoryAdmissionTest*'` → **BUILD SUCCESSFUL** ; budget de 300 Mo, 20 tâches réclamant 100 Mo chacune : au plus **3 tâches simultanées** observées, mémoire réclamée en cours **jamais au-delà de 300 Mo** ; en outre : une tâche de 30 Mo attend la libération d'une tâche de 80 Mo (preuve par ordre des événements, sans fenêtre d'attente) et une tâche exclusive s'exécute seule (aucune autre tâche comptée pendant son vol) |
| 4 | **Build sans changement sur démon chaud, tier L (graphes de tâches factices)** : aucun accès disque mesuré, ≤ 100 ms | ✅ | `./gradlew :core-tasks:test --tests '*HotNoChangeTest*'` → **BUILD SUCCESSFUL** ; sortie : `[hot] tier L (1200 tâches, 3600 fichiers) : build à chaud en 7 ms, 0 accès au store, budget 100 ms` — 200 modules de 6 tâches chaînés, 3 600 fichiers d'entrée réels ; l'absence d'accès disque est prouvée par une garde armée autour du store qui fait échouer le test au premier appel (le store est le seul canal disque du moteur ; les instantanés sont passés en mémoire, comme le démon les tiendra) |
| 5 | **Empreintes relocalisables** : copier le projet ailleurs ⇒ tout est à jour / restauré du cache | ✅ | `./gradlew :core-tasks:test --tests '*BuildEngineTest*'` → **BUILD SUCCESSFUL** ; `fingerprintsAreRelocatable` : projet et sorties copiés intégralement, moteur neuf sur le même store (état copié avec le projet) → **tout à jour** malgré des numéros d'inode différents (re-hachage de rattrapage, mêmes contenus, mêmes empreintes ancrées) ; `cleanedOutputsRestoreFromCache` et `relocatedCleanOutputsRestoreFromCache` : sorties absentes → **restauration du cache**, contenu identique à l'octet près, y compris depuis l'autre emplacement (clé sans chemin absolu) |
| 6 | **Erreur de lecture d'entrée ⇒ échec explicite** | ✅ | `./gradlew :core-tasks:test --tests '*BuildEngineTest*'` → **BUILD SUCCESSFUL** ; `unreadableInputFailsExplicitly` : entrée = lien symbolique pendant → tâche **échouée** avec le code `input-read-error`, le message nomme le fichier, jamais une empreinte vide silencieuse ; `directoryWhereFileExpectedFailsExplicitly` : un dossier à la place du fichier → même échec explicite |

## 3. Détail des tests

Relance intégrale par phase puis `./gradlew check` final : **165 méthodes de test sur 39 classes, 0 échec, 0 erreur**. Répartition : core-platform 30, core-store 17 (dont SQLite 8, harnais de pannes 2 — 85 s, journal 6, benchmark 1), core-vfs 30, core-model 30, **core-tasks 46** (graphe 10, chemins critiques 3, propriétés ordonnanceur 4, barrière 1, admission 3, moteur 16, tier L à chaud 1, cache de sorties 4, codec 3), tools-bench 7, tools-test 6. `./gradlew check` (politique de langue, règle de dépendances, contrôle API 28 sur les source sets principaux, javac `-Xlint:all -Werror`) : **BUILD SUCCESSFUL** sans `ANDROID_HOME`.

## 4. Écarts par rapport au prompt

| Écart | Raison | Statut |
|---|---|---|
| **Critère 4, « aucun accès disque » prouvé par une garde sur le store** | le store est le seul canal disque du moteur (instantanés passés en mémoire par l'appelant, comme le fera le démon) ; la garde armée fait échouer le test au premier accès — plus strict qu'une mesure par interception système, sans dépendre de `strace` | Interprétation consignée ici ; à valider par le propriétaire |
| **Aucun record Java dans les source sets principaux** | le contrôle API 28 (P0) rejette `java.lang.Record`, absent du niveau 28 ; l'item backlog « vérifier le désucrage D8 de records avant usage » (P3/P5) reste ouvert — P2 utilise des classes immuables explicites, comme P1 | Conforme au contrôle existant ; backlog inchangé |
| **Le harnais kill -9 n'a pas été étendu au journal** | la décision de l'ADR-021 est de **ne pas** adopter le journal par défaut ; il passe les tests de sémantique (transaction abandonnée, journal tronqué, bloc corrompu, compaction) mais le harnais de meurtres de processus reste spécifique à SQLite | Consigné ADR-021 ; question au propriétaire (§7) |
| **429 Maven Central pendant la session** | les tests du kit (tools-test) lancent des builds TestKit imbriqués qui résolvent des dépendances réseau ; quelques relances ont été nécessaires (le bac à sable n'a pas de miroir local) | Passager ; `./gradlew check` final vert sans relance |
| Bac à sable recyclé plusieurs fois | risque R7 anticipé au plan P0 ; JDK réinstallé au même emplacement conservé ; les builds d'arrière-plan ont été relancés au premier plan | Sans effet sur le dépôt |

## 5. Mesures de performance

Machine de build (2 cœurs partagés) — le verdissement sur l'appareil de référence est reporté aux phases concernées :

| Mesure | Cible | Obtenu |
|---|---|---|
| Build sans changement, démon chaud, tier L (1 200 tâches, 3 600 fichiers) | ≤ 100 ms, aucun accès disque | **7 ms, 0 accès au store** |
| Barrière levée : mur vs chemin critique (2 000 ms nominal) | ≤ chemin critique + ε | **2 010 ms** (ε = 0,5 %) ; une barrière de niveau exigerait ≥ 3 500 ms |
| Admission mémoire (budget 300 Mo, tâches de 100 Mo) | somme ≤ budget | **3 tâches au plus**, jamais au-delà |
| Store SQLite : 1 200 écritures de tâches / lectures | — | 89 ms / 9 ms |
| Store journal : mêmes charges | — | 14 ms / < 1 ms ; réouverture 176 ms contre 7 ms (SQLite) — décision ADR-021 |
| Suite complète `./gradlew check` | — | 165 tests, 0 échec (harnais de pannes 85 s inclus) |

## 6. Dette et risques ouverts

- Le verrou global du cache de sorties sérialise les copies d'entrées : acceptable aux échelles P2 (copies brèves devant les compilations), à mesurer en P3 (ADR-024).
- L'exactitude du chemin chaud repose sur la fraîcheur de l'ensemble sale fourni par le VFS — la responsabilité du démon (P6) sera de l'alimenter, y compris pour les sorties supprimées.
- Les priorités utilisent les durées des enregistrements d'exécution ; l'estimation par défaut par type (500 ms générique) est grossière — P3 affinera avec de vraies tâches de compilation.
- Le relâchement des cycles d'ordre purs « mustRunAfter » est best-effort (comme les ordonnanceurs équivalents) : jamais d'affamement, mais l'ordre n'est pas garanti dans ce cas précis.
- La mesure de tas par tâche est avant/après ; l'échantillonneur périodique est câblé mais optionnel (le démon décidera de le fournir).

## 7. Questions pour le propriétaire

1. L'arête `core-tasks → core-model` du graphe des modules reste inutilisée en P2 (le moteur consomme les types P1 ; le modèle alimentera les tâches de compilation en P3 via `build-jvm`) — confirmer que c'est acceptable.
2. Le build scan JSON est écrit à la main (schéma simple, sans version) — suffisant pour la console P8, ou préférer un schéma versionné dès maintenant ?
3. `JournalBuildStore` : le garder comme implémentation de référence du contrat (recommandé, coût de maintenance faible) ou l'étendre au harnais kill -9 avant d'envisager son adoption ?

## 8. ADR ajoutés

- **ADR-021** — store alternatif en journal à ajouts : mesuré, écarté pour l'instant (chiffres complets, déclencheur de réévaluation).
- **ADR-022** — ordonnanceur : file de prêtes par compteurs, ordre relâché sans affamement, annulation par jeton et jamais par annulation de futur.
- **ADR-023** — chaîne de décision à jour, passe à chaud sans entrée-sortie, regroupement des écritures, emplacements (état par espace de travail, cache séparé).
- **ADR-024** — cache de sorties adressé par contenu : entrées immuables, index en mémoire, éviction avec suppression des fichiers ; extension de l'interface `BuildStore`.
