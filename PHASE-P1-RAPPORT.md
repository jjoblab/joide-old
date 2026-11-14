# PHASE P1 — RAPPORT

| | |
|---|---|
| Statut | Livé — **arrêt pour validation du propriétaire** (§11.4) |
| Date | 2026-11-15 |
| Machine d'exécution | Sandbox Linux (conteneur partagé) : 2 cœurs, 4 Go de RAM, stockage overlay — **inférieure à l'appareil de référence D12** (8 cœurs, 6 Go) ; conséquences sur les mesures temporelles au §5 |
| Outils | Temurin JDK 21.0.13+9 (javac, réinstallé dans le bac à sable après recyclage — même version que P0), Gradle 9.8.2 (wrapper, somme de contrôle épinglée), Python 3.12, SQLite 3.49.1.0 via sqlite-jdbc |
| Commits P1 | 6 commits français (`feat(core-platform)`, `feat(core-store)` ×2, `feat(core-vfs)`, `feat(core-model)`, `docs`), un par changement, auteur `jjoblab <olson12jb@gmail.com>` vérifié sur chacun |
| Journal clean-room | **Vide — aucune consultation de CodeAssist** (§4.1). Le travail repose sur le prompt, le rapport d'analyse joint, les conventions celeris (ADR-007/016) et la documentation publique des outils. |

---

## 1. Ce qui est fait

- **core-platform** : empreinte rapide 128 bits MurmurHash3 x64 vérifiée par vecteurs de référence + SHA-256 de confiance ; chemins ancrés à racines nommées (`$PROJECT`, `$SDK`, `$M2`, `$OUT`) ; problèmes et résultats sans exception ; jeton d'annulation coopérative à écouteurs à exécution unique ; horloge injectée ; pools de fils démons nommés ; écritures atomiques (temporaire + vidage + renommage, nettoyage des restes).
- **core-store** : interface `BuildStore` (snapshot par racine, empreintes de tâches, durées, état incrémental, index du cache de sorties avec éviction « le moins récemment utilisé », transactions tout-ou-rien) ; implémentation de bureau SQLite en mode journal écriture d'avance dans un source set `jvm` (ADR-019), migrations versionnées, contrôle d'intégrité à l'ouverture, **reconstruction automatique après corruption** (le store est un cache) ; harnais de pannes par meurtres de processus fille (ADR-020).
- **core-vfs** : parcours parallèle de statuts (liens symboliques non suivis, annulation, aucun résultat partiel silencieux) ; instantané immuable avec empreintes d'arbre par dossier recalculées seulement sur les dossiers touchés et leurs ancêtres ; empreinte de contenu paresseuse avec protection contre les fichiers modifiés pendant la lecture (stat avant/après, relectures bornées) ; journal de changements fusionné (éditeur, surveilleur, rattrapage) à file bornée — **débordement ⇒ re-scan exigé** ; surveilleur de bureau récursif borné à un budget de dossiers, débordements et budgets signalés.
- **core-model** : analyseur TOML tolérant sur sous-ensemble standard (**jamais d'exception**, anomalies localisées ligne/colonne, prouvé par 2 000 mutations pseudo-aléatoires à graine fixe) ; hiérarchie immuable Workspace > Project > Module > SourceSet > ContentRoot avec facettes, rôles et types extensibles ; identifiant de contenu par module (empreinte de la définition canonique, **sans chemin absolu** — relocalisabilité prouvée par test de copie) ; topologie (niveaux, dépendances inversées, cycles reconstruits dans le message) ; assemblage déterministe de classpath (ordre par niveaux puis nom, déduplication, promotion de portée, règles de conflit « la plus récente gagne / strictement / échec ») ; dépôt à écriture multi-fichiers en deux passes avec restauration.
- **Schéma canonique des fichiers de projet** (`joide.workspace/1`, `joide.module/1`, ADR-018) : listes en tableaux standard, jamais de clés répétées ; **tools-bench écrit désormais ce schéma** et une validation croisée charge un projet synthétique réel du tier S ; baselines S/M régénérées.
- **Hygiène demandée par le propriétaire en cours de phase** : historique des commits réécrit — les 9 commits (8 de P0 + nettoyage) portent tous l'auteur `jjoblab <olson12jb@gmail.com>` ; toutes les javadocs et tous les commentaires du code ont été débarrassés des références ADR/décisions/phases/sections et des dates (56 fichiers touchés, commit `149985a`) ; les javadocs ne parlent que de ce que fait la classe ou la méthode.

## 2. Critères d'acceptation P1, un par un

| # | Critère (§7 P1) | Statut | Preuve (commande + sortie) |
|---|---|---|---|
| 1 | Chargement tolérant : un `joide-module.toml` corrompu est signalé, le reste charge | ✅ | `./gradlew :core-model:test --tests '*WorkspaceLoaderTest*'` → **BUILD SUCCESSFUL**, 8 tests. `corruptedModuleIsReportedOthersLoad` : workspace à 3 modules dont 1 TOML cassé (chaîne non fermée, signe égal absent) et 1 absent → le module sain charge, les deux anomalies sont des erreurs nommées ; `neverRewritesAnUnreadableFile` : le fichier cassé n'est **jamais réécrit** ; en complément : clés inconnues en information, portée invalide → dépendance écartée et signalée, le module reste chargé |
| 2 | `kill -9` aléatoire pendant 1 000 transactions du store **et** pendant la mise à jour du snapshot → reprise cohérente, aucune corruption (≥ 200 itérations) | ✅ | `./gradlew :core-store:test --tests '*StoreCrashAcceptanceTest*' --rerun` → **BUILD SUCCESSFUL** ; XML : `tests=2, failures=0, errors=0, time=85,4 s`. **200 meurtres** (100 mode empreintes, 100 mode snapshot), chacun dans la fenêtre 50–1000 transactions, instants tirés à graine fixe `20260115` affichée en cas d'échec. Après chaque meurtre : intégrité vérifiée, empreintes = préfixe contigu exact (aucun trou, aucune écriture à moitié appliquée), snapshot = exactement une génération complète, puis **reprise prouvée par écriture**. Complément : `SqliteBuildStoreTest.corruptedDatabaseIsRebuiltEmpty` (base volontairement corrompue → reconstruite vide, utilisable) |
| 3 | Cold-scan de 100 000 fichiers ≤ 5 s (JVM de bureau d'abord, puis appareil) | ✅ (JVM) | `./gradlew :core-vfs:test --tests '*ColdScanTest*' --rerun` → **BUILD SUCCESSFUL** ; sortie : `[cold-scan] 100000 fichiers parcourus en 477 ms` (budget 5 000 ms) et `[cold-scan] snapshot de 100000 fichiers construit en 275 ms` (empreintes d'arbre comprises). La mesure sur l'appareil de référence reste à faire avec le démon (phase P6), comme le prévoit le critère |
| 4 | Journal de changements : événements éditeur + watcher + rattrapage fusionnés ; test de débordement (inondation) → re-scan correct | ✅ | `./gradlew :core-vfs:test` → **BUILD SUCCESSFUL**, 30 tests. Fusion : `ChangeLogTest.editorAndWatcherDeduplicate` (un même chemin vu des deux sources ne compte qu'une fois, changement le plus récent gagnant) ; inondation : `floodingBeyondCapacityRequiresRescan` (500 événements contre capacité 64 → re-scan exigé, consommé au vidage) puis `SnapshotUpdaterTest.floodThenRescanRecoversCorrectDirtySet` : après l'inondation et de vraies modifications sur disque, le re-scan retrouve exactement l'ensemble sale (ajouté/modifié/supprimé) ; surveilleur réel (`NioTreeWatcherTest`) : création/modification/suppression vues, sous-dossiers surveillés à leur création, **budget de surveillance jamais dépassé** et signalé |
| +a | (P0 §6.1) Charger un workspace de 1 000 modules en < 1 s hors I/O froide | ✅ | `ThousandModulesTest` : sortie `[mille-modules] 1000 modules charges en 58 ms` (budget 1 000 ms), topologie de la chaîne complète calculée dans le même test |
| +b | (P0 §6.1) Fuzzing du parseur TOML sans crash | ✅ | `TomlFuzzTest` : 2 000 documents mutés (remplacement/suppression/insertion d'octets, troncatures) à graine fixe → aucune exception, chaque document rend un diagnostic ou un arbre sain |

## 3. Détail des tests

Relance intégrale (`--rerun-tasks`) : **113 méthodes de test sur 31 classes, 0 échec, 0 erreur** sur la machine de build. Répartition : core-platform 30, core-store 11 (dont le harnais de pannes, 85 s), core-vfs 30 (dont le parcours à froid, préparation de 100 000 fichiers exclue de la mesure), core-model 30 (dont fuzz et mille modules), tools-bench 7 (dont la validation croisée), tools-test 6 (contrôles P0, inchangés). `./gradlew check` (politique de langue, règle de dépendances, contrôle API 28 sur les source sets principaux, javac `-Xlint:all -Werror`) : **BUILD SUCCESSFUL** sans `ANDROID_HOME`.

## 4. Écarts par rapport au prompt

| Écart | Raison | Statut |
|---|---|---|
| **Empreinte rapide : MurmurHash3 x64 128, pas XXH3-128** (D9 par défaut) | D9 prévoit « XXH3-128 **ou équivalent rapide 128 bits**, à benchmarker » ; MurmurHash3 est l'équivalent, 200 lignes vérifiables contre un risque de transcription élevé pour XXH3 ; mesuré 3,2 Go/s (§5) | Décision consignée ADR-017 |
| **Arête de test `tools-bench -> core-model`** (graphe §5.1) | la validation croisée « le modèle lit les projets synthétiques » (risque R2 du plan P0) exige un test partagé ; l'arête est descendante, commentée « test uniquement » dans `gradle/module-graph.txt` ; le code principal du banc ne dépend toujours d'aucun module | Consigné ADR-020 ; à mention du propriétaire si le graphe doit rester strictement littéral |
| **Schéma TOML canonique : les fichiers générés par tools-bench changent** (clés répétées → tableaux standard) | le format P0 utilisait des clés répétées, invalides en TOML standard ; le lecteur et le générateur partagent désormais le même sous-ensemble standard | Baselines S/M régénérées ; déterminisme re-vérifié |
| `regressionTestS/M` échoue sur la partie **temporelle** (cpu-ms) dans le bac à sable | ordonnanceur partagé à 2 cœurs : la même charge passe de 147 à 213 ms CPU en quelques minutes ; la partie **exacte** (hash d'arbre, comptages, octets) passe — c'est la porte de non-régression du déterminisme | Conforme à la décision P0 validée (Q5 : baselines temporelles bloquantes sur la **machine de référence** seulement, à partir de P2) |
| JDK du bac à sable réinstallé (le JRE seul survivait au recyclage) | risque R7 anticipé au plan P0 ; Temurin 21.0.13+9 réinstallé à un emplacement conservé (`work/jdk`) | Sans effet sur le dépôt |
| **Poussée GitHub impossible** : le token `ghp_…Cu` est **refusé (401)** par l'API et git | testé par deux canaux (API et git) le 2026-11-15 ; le token avait pourtant été prévu pour tout le travail — il a été révoqué ou a expiré côté GitHub | **Action demandée au propriétaire** (§9) ; l'historique local complet (9 commits, auteur corrigé) est prêt à pousser |

## 5. Mesures de performance

Machine de build (2 cœurs partagés) — le verdissement sur l'appareil de référence est reporté aux phases concernées :

| Mesure | Cible | Obtenu |
|---|---|---|
| Cold-scan 100 000 fichiers (statuts, parallèle) | ≤ 5 s | **477 ms** |
| Construction du snapshot (empreintes d'arbre comprises) | — | **275 ms** |
| Chargement 1 000 modules (analyse + modèle + topologie) | < 1 s | **58 ms** (103 ms à froid) |
| Hachage murmur128 (un fil, 64 Mo en mémoire) | — | ≈ 3,2 Go/s (SHA-256 : ≈ 1,3 Go/s) |
| Harnais kill -9 (200 meurtres, chacun 50–1000 transactions) | — | 85 s au total, 0 corruption |
| Baseline bench S (génération 500 fichiers, 3 runs, déterminisme auto-vérifié) | — | moyenne cpu 147 ms (régénérée : le schéma canonique écrit davantage de lignes) |

## 6. Dette et risques ouverts

| # | Sujet | État |
|---|---|---|
| D1 | Implémentation **Android** du store (base système) et du surveilleur (observateur de fichiers) | prévue avec le démon (P6) — les interfaces (`BuildStore`, `TreeWatcher`) sont déjà libres de toute API Android |
| D2 | Chaîne de traitement « démon chaud » complète (journal + rattrapage en continu) | les briques existent et sont testées séparément ; l'assemblage en service continu arrive avec le démon |
| D3 | Bench de l'alternative « journal append-only » pour le store | prévu par le prompt (§6.3) ; interface prête, à mesurer quand les charges réelles existent (P2+) |
| D4 | `regressionTest*` temporel sur machine partagée | exécuter sur la machine de référence (documenté dans `docs/dev.md`) |
| D5 | Variante de débordement **réelle** du surveilleur système (inotify) | le chemin de débordement est testé par inondation du journal ; un débordement natif réel ne peut pas être déclenché déterministement — le comportement est le même (re-scan du sous-arbre) |
| D6 | Poussée GitHub bloquée (token révoqué) | voir §9 |

## 7. ADR ajoutés

- **ADR-017** — Empreinte rapide 128 bits : MurmurHash3 x64 plutôt que XXH3-128.
- **ADR-018** — Analyseur TOML maison, tolérant, sur un sous-ensemble standard.
- **ADR-019** — Store de bureau : SQLite JDBC dans un source set séparé.
- **ADR-020** — Harnais de pannes : meurtres de processus fille et arêtes de test.

## 8. Journal clean-room

**Aucune consultation de CodeAssist pendant P1.** `docs/clean-room/JOURNAL.md` reste vide. Sources de travail : le prompt, le rapport d'analyse joint, les documents de conception de celeris (AGENT.md, ADR-007 — déjà lus en P0, autorisés), la spécification publique de MurmurHash3 et sa mise en œuvre de référence (liaison mmh3, pour les vecteurs de test uniquement).

## 9. Questions pour le propriétaire

1. **Token GitHub** : `ghp_…Cu` est refusé (401) par l'API depuis ce bac à sable — il semble révoqué ou expiré. Le dépôt local est complet et propre (9 commits, auteur corrigé). **Fournir un nouveau token (lecture/écriture sur jjoblab/joide)** pour pousser, ou indiquer si vous préférez récupérer l'archive ZIP et pousser vous-même.
2. **Arête de test `tools-bench -> core-model`** (§4) : acceptable telle quelle, ou préférez-vous que la validation croisée vive ailleurs (par exemple un script hors test unitaire) pour garder le graphe strictement littéral au §5.1 ?
3. **Schéma canonique** (`joide.workspace/1`, `joide.module/1`) : le format détaillé au README de core-model vous convient-il comme contrat stable pour la suite (l'import Gradle le réutilisera, l'export l'écrira) ?

---

**Arrêt pour validation.** Aucun code de P2 (moteur de tâches) ne sera écrit avant votre réponse.
