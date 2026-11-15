# PHASE P3 — RAPPORT

| | |
|---|---|
| Statut | Livré — **arrêt pour validation du propriétaire** (§11.4) |
| Date | 2026-11-15 |
| Machine d'exécution | Sandbox Linux (conteneur partagé) : 2 cœurs, 4 Go de RAM — inférieure à D12 ; mesures temporelles indicatives, preuves structurelles exactes |
| Outils | Temurin JDK 21.0.13+9 (bac à sable : le JRE système ne fournit pas javac, JDK complet pointé par `JAVA_HOME` et `~/.gradle/gradle.properties`), Gradle 9.8.2 (wrapper), ecj 3.40.0, compilateur Kotlin embarqué 2.3.0, kotlin-metadata-jvm 2.3.0 |
| Commits P3 | 9 commits français (`feat(build-jvm)` ×5, `fix(core-tasks)`, `fix(build-jvm)`, `test(core-tasks)`, `docs`), un par changement |
| Journal clean-room | **Vide — aucune consultation de CodeAssist** (§4.1). Le travail repose sur le prompt, les ADR existants, la documentation publique des outils et les **artefacts réellement chargés** (kotlin-metadata-jvm, kotlin-build-tools-api : surfaces vérifiées par lecture des classes, §4.2). |

---

## 1. Ce qui est fait

- **Lecture des fichiers de classe** (`ClassFileReader`, `ClassModel`) : pool de constantes complet (références des corps comprises), membres, attributs d'ABI (annotations des quatre rétentions, signatures, constantes, `MethodParameters`), UTF-8 modifié exact (clés pour les métadonnées Kotlin), extraction de `@kotlin.Metadata`, condensé 128 bits du fichier entier (`fileDigest`).
- **ABI par classe** (`ClassAbi`, `KotlinFacts`) : rendu canonique haché en 128 bits — membres non privés, annotations brutes, constantes inlinables, faits Kotlin (visibilité interne, inline, const, scellage **et liste des sous-classes scellées**, noms de paramètres). **Corps des membres inline dans l'ABI** (ADR-025) : condensé du fichier de classe entier pour les classes à membres inline — la mesure fondatrice (ci-dessous §4) montre que les métadonnées 2.1.0 ne portent pas les corps. Manifestes immuables adressés par condensé (`AbiManifest`), calcul par entrée de classpath avec garde-fous anti-bombe (`EntryAbi`), cache persistant adressé par contenu (`AbiCache`).
- **Graphe classe-classe** (`CompileState`, `ImpactAnalysis`, `SourceHeaderScanner`) : scan d'en-têtes tolérants (paquet, imports simples et à la demande, chaînes brutes Kotlin), état incrémental codé en binaire déterministe avec somme de contrôle (corruption = recompilation complète, jamais un échec), analyse d'impact à point fixe par index (références, sous-classes du module, paquet et importateurs étoile), seuil de repli, processeur agrégeant.
- **Pilotes de compilation en processus** (`EcjCompilerDriver`, `KotlinCompilerDriver`, ADR-026) : ecj par lots (module système `jrt`, capture exacte des classes écrites, diagnostics codés ligne/colonne depuis l'API de problèmes) et compilateur Kotlin embarqué (racines Java étalées pour la résolution jointe, `-no-stdlib`, diagnostics du collecteur) ; versions d'outils lues dans l'artefact réellement chargé.
- **Tâches du pipeline** (`JvmTaskFactory` et tâches) : compilation incrémentale par ABI — diff de manifestes de classpath (manifestes immuables, pointeurs stables pour l'aval), lots au point fixe, repli complet seuillé ou sur suppression ; compilation jointe Kotlin-puis-Java (sorties Kotlin au classpath Java) ; jar déterministe (entrées triées, horodatage fixe) ; `processResources` ; générateurs de sources (SPI `SourceGenerator`) ; `run` JVM ; fabrique de graphe relocalisable (`$OUT`/`$M2`) avec cache ABI des entrées externes.
- **Correctif du moteur (core-tasks)** : trois défauts de correction révélés par le pipeline — entrées de dossier imbriquées, empreintes des propriétés scalaires + hachage symétrique des entrées couvrant des sorties du graphe, delta d'entrées développant les dossiers (`bc1670b`, tests de regression livrés à `3ac8103`).

## 2. Critères d'acceptation P3, un par un

| # | Critère (§7 P3) | Verdict | Preuve (commande, sortie, mesure) |
|---|---|---|---|
| 1 | **Test différentiel ≥ 200 séquences aléatoires** (corps de méthode, signature, ajout/suppression de classe/fichier, constante, `inline`, renommage de paquet) : `incrémental ≡ propre` | ✅ | `./gradlew :build-jvm:test --tests "*DifferentialTest"` — 3 modules (base/mid/app), Java+Kotlin mélangés, graine 20260311 affichée à l'échec ; `tests=1 failures=0`, **200 séquences**, classes et jars comparés octet pour octet (manifestes et état exclus comme métadonnées d'adressage, comparés via les classes qu'ils décrivent) ; 136 s puis 233 s (exécution à froid) sur le sandbox 2 cœurs |
| 2 | **Corps de méthode amont → pas de recompilation aval** (mesuré par tâches/classes recompilées) | ✅ | `PipelineAcceptanceTest.methodBodyChangeUpstreamKeepsDownstreamUpToDate` (0 tâche aval recompilée) ; `GraphTest.bodyChangeInHubAffectsReferencersOnly` ; garanti à l'échelle ABI : le corps est exclu du rendu canonique (`InlineBodyAbiTest.javaMethodBodyChangeKeepsAbi`) |
| 3 | **Signature publique → les dépendants et eux seuls** ; repli complet testé aux seuils | ✅ / ⚠️ | `PipelineAcceptanceTest.publicSignatureChangeRecompilesDependentsOnly` ; `GraphTest.signatureChangePropagatesToFixedPoint` ; seuils : `GraphTest.thresholdTriggersFullFallback` et `aggregatingProcessorForcesFull` ; **écart : pas d'exécution M/L complète** — voir §4 |
| 4 | **Diagnostics structurés (ligne/colonne/code) sur erreurs Java et Kotlin** | ✅ | `CompilerDriverTest.ecjReportsStructuredErrorWithLineColumnAndCode`, `kotlinReportsStructuredError` ; `PipelineAcceptanceTest.compileErrorFailsExplicitlyWithDiagnostics` — jamais par analyse de texte |
| — | Livrable : ADR du choix ecj/javac | ✅ | `docs/adr/026-choix-ecj.md` — ecj retenu, `javax.tools` absent du niveau 28 |
| — | Livrable : ADR du spike Kotlin (API Build Tools vs incrémental maison) | ✅ | `docs/adr/027-spike-kotlin-buildtools.md` — maison retenue, surface Build Tools vérifiée dans l'artefact 2.4.10, révisable à P9 sur appareil |

Vérification globale : `./gradlew check` — **BUILD SUCCESSFUL**, 206 tests, 0 échec (après reprise d'un 429 transitoire de Maven Central sur une résolution TestKit — documenté `docs/dev.md`).

## 3. Détail des tests ajoutés en P3

| Suite | Cas |
|---|---|
| `DifferentialTest` (1 cas, 200 séquences intégrées) | incrémental ≡ propre, octet pour octet, éditions kinds 0-7 (corps, constante, signature, corps inline, ajout/suppression, renommage de paquet) |
| `InlineBodyAbiTest` (3 cas) | `inlineBodyChangeChangesAbi` (**échoue avant le correctif ADR-025**), `sameInlineBodyCompilesToSameAbi` (déterminisme), `javaMethodBodyChangeKeepsAbi` (compile avoidance des corps) |
| `CompilerDriverTest` (6 cas) | diagnostics structurés ecj et Kotlin, résolution jointe Java→Kotlin |
| `GraphTest` (11 cas) | propagation à point fixe, seuils, processeur agrégeant, état corrompu |
| `AbiTest` (9 cas) | faits Kotlin, déterminisme des manifestes, aller-retour |
| `PipelineAcceptanceTest` (5 cas) | build propre multi-modules, sans changement entièrement à jour, jar déterministes, diagnostics d'échec |
| `ClassFileReaderTest` (4 cas) | lecture, métadonnées runtime, rejets explicites |
| core-tasks (2 cas) | `NestedDirInputTest`, `IntraBuildChainTest` — régressions du correctif `bc1670b` (omises au commit d'origine, livrées à `3ac8103`) |

## 4. Écarts par rapport au prompt

| Écart | Raison | Statut |
|---|---|---|
| **Critère 3 « sur M et L » non exécuté à cette échelle** | la granularité dépendants-seuls est prouvée sur le projet différentiel (3 modules, la propagation et les seuils par `GraphTest`) ; une exécution pipeline complète d'un projet synthétique M (50 modules) puis L (200/30 000) exige un adaptateur générateur→`ModuleSpec` et une arête de test `tools-bench → build-jvm` (graphe des modules) — réalisable, estimé à ~15 min d'exécution L sur ce sandbox | **Signalé** ; alternative proposée au propriétaire (§7, question 1) |
| **Corps inline : la conception initiale « par hachage des métadonnées » reposait sur une hypothèse fausse** | mesure exécutable (`InlineBodyAbiTest`) : deux corps différents → mêmes métadonnées brutes → même manifeste → l'aval gardait l'ancien corps copié ; l'édition du test différentiel (kind 4) l'a masqué jusqu'au correctif `3c54160` (ADR-025) | Corrigé, test de régression livré |
| **Escalade par paquet pour les inline (essayée d'abord)** | un appelant purement inline ne référence aucune classe du paquet : insuffisant (défaut « source vanished » à la séquence 99) ; remplacée par recompilation complète du consommateur | Consigné ADR-025 |
| Records Java refusés par le contrôle API 28 | `java.lang.Record` hors niveau 28 (P0) : `ClasspathDiff`/`BatchOutcome` en classes immuables explicites — conforme à la règle, backlog « désucrage D8 » inchangé | Conforme |
| 429 Maven Central pendant `check` | résolution de classpath de compilateur par TestKit ; reprise automatique au-delà des retries configurés | Passager ; `check` final vert |

## 5. Mesures de performance

Sandbox 2 cœurs partagés — indicatif ; le verdissement sur l'appareil de référence est pour les phases concernées :

| Mesure | Cible | Obtenu |
|---|---|---|
| Test différentiel complet (200 séquences, chacune incrémental + build propre de référence) | — | 136 s (chaud) / 233 s (froid), ~0,7 s par séquence |
| Édition de corps en amont | 0 tâche aval | 0 tâche aval recompilée (`PipelineAcceptanceTest`) |
| Édition de signature publique | dépendants seuls | dépendants seuls, repli aux seuils testé |
| Édition de corps inline | incrémental ≡ propre | recompilation complète du consommateur (ADR-025), ≡ propre octet pour octet |
| Compilation jointe Java+Kotlin (résolution croisée) | — | racines Java étalées au compilateur Kotlin, sorties Kotlin au classpath Java (`CompilerDriverTest`) |
| `./gradlew check` complet | — | **206 tests, 0 échec** ; 206 contre 165 en P2 |

## 6. Dette et risques ouverts

- **Exécution M/L du pipeline** (voir écart ci-dessus) : adaptateur générateur synthétique → `ModuleSpec`, arête de test à ajouter au graphe des modules — proposée au propriétaire.
- Éditions différentielles dédiées `sealed` et `@JvmInline` (la liste scellée est déjà dans l'ABI ; `const` et `inline` sont couverts).
- Sources **Kotlin générées** : le SPI `SourceGenerator` ordonne la génération avant les compilations ; la validation sur un générateur réel reste à faire.
- L'escalade inline recompile entièrement le module consommateur : coût borné aux classes à membres inline (rares) — à surveiller sur projets réels en P9.
- Durées historiques par défaut (500 ms générique) : affinables maintenant que de vraies tâches de compilation existent.

## 7. Questions pour le propriétaire

1. **M/L** : voulez-vous l'exécution pipeline complète d'un projet synthétique M puis L (signature publique → dépendants seuls, mesuré en tâches) comme addendum P3, ou est-ce reportable à P4/P9 avec le reste du passage à l'échelle ?
2. L'escalade inline (recompilation complète du consommateur) est le prix de la correction — acceptable, ou souhaitez-vous un travail supplémentaire de granularité (par ex. journal des appelants inline par module) ?
3. ADR-027 : la réévaluation sur appareil est placée à P9 avec trois mesures convenues (temps mur d'édition, pic de tas, comportement sous budget réduit) — convient-il ?

## 8. ADR ajoutés

- **ADR-025** — ABI des membres inline : condensé du fichier de classe pour les classes à membres inline, drapeau inline dans le manifeste, recompilation complète du consommateur, attribution élargie sans pseudo-source orphelin (mesure fondatrice incluse).
- **ADR-026** — ecj retenu pour la compilation Java pilotée (javac écarté : `javax.tools` absent du niveau 28).
- **ADR-027** — spike Kotlin : incrémental maison retenu, API Build Tools écartée pour le noyau ; révisable à P9 sur appareil.
