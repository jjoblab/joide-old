# ADR-041 — Export Gradle déterministe, aller-retour et sémantique AGP 9

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'échappatoire d'export (§6.9) écrit un espace JoIDE comme un vrai
projet Gradle : `settings.gradle.kts`, un `build.gradle.kts` par
module, `gradle.properties`, et `EXPORT-NOTES.md` pour ce qui n'a pas
d'équivalent fidèle. Le prompt exige deux preuves : l'export se
construit dans un vrai Gradle, et l'aller-retour
import(export(modèle)) ≡ modèle.

## Décision

1. **Export en mémoire, déterministe**. `GradleExporter.export` rend
   les fichiers par chemin relatif (la carte entière), sans horodatage
   ni chemin absolu : deux exports du même modèle produisent des
   octets identiques (testé). `write` les pose sur disque. L'ordre des
   dépendances est celui du modèle — il fait partie de son
   identifiant de contenu.

2. **Sous-ensemble représentable, équivalence exacte dessus**.
   L'aller-retour ≡ modèle tient sur le sous-ensemble des modèles
   dont chaque donnée a un équivalent Gradle : modules typés, ensembles
   de sources, dépendances (coordonnées, projets, portées, variantes
   debug/release/parfum), facettes android, versions de greffons.
   Hors du sous-ensemble, l'asymétrie est signalée dans
   `EXPORT-NOTES.md` — jamais silencieuse : réglage d'espace
   `android.min-sdk` distribué aux modules qui ne déclarent pas le
   leur, facettes sans équivalent Gradle (entrées listées), rôles de
   contenu sans conteneur (`generated`, `excluded`).

3. **Ensembles de sources explicites dès qu'ils diffèrent des
   défauts**. Quand les racines d'un ensemble diffèrent de celles que
   l'import dériverait des greffons, l'export écrit un
   `setSrcDirs(listOf(…))` **pour chaque conteneur** — y compris vide
   (`listOf<String>()`, le type explicite étant requis par Kotlin).
   L'import lit ces remplacements exacts : l'aller-retour est l'égalité
   des identifiants de contenu, prouvée sur un modèle à facettes
   multiples (variantes, parfums, chemins imbriqués) et sur
   l'échantillon multi-modules importé.

4. **Versions de greffons portées par une facette `gradle`**. L'import
   enregistre les versions des greffons appliqués (déclarées par
   `id(…) version …` ou résolues du catalogue par `alias(…)`) dans la
   facette `gradle` (`plugin-<id> = <version>`). L'export écrit
   chaque greffon de type avec sa version, puis tout greffon
   supplémentaire porté par la facette (compilateur Compose,
   sérialisation…). C'est ce qui rend l'export Android constructible.

5. **Sémantique AGP 9 apprise du monde réel**. La vérification à vrai
   Gradle a révélé deux faits que l'export respecte désormais :
   - AGP 9 intègre le support Kotlin et **refuse** le greffon
     `org.jetbrains.kotlin.android`. L'import marque donc le support
     Kotlin d'un module android dès que la version AGP est ≥ 9 (le
     greffon explicite reste la source sous AGP 8) ; l'export
     n'écrit le greffon Kotlin que sous AGP < 9. Symétrique des deux
     côtés de l'aller-retour.
   - Kotlin 2.0+ exige le greffon `org.jetbrains.kotlin.plugin.compose`
     quand compose est activé. L'import avertit quand il manque
     (`compose-compiler-plugin-missing`) ; l'export le porte par la
     facette `gradle`.
   - AGP 8.13 utilise des API internes supprimées de Gradle 9.6 :
     l'export vise le couple AGP 9 / Gradle 9 (celui du dépôt), les
     échantillons suivent.

6. **Réglages complets pour construire**. L'export écrit
   `pluginManagement` et `dependencyResolutionManagement`
   (`google()` + `mavenCentral()` quand des modules android existent),
   et `android.useAndroidX=true` le cas échéant : un projet exporté
   se résout et se construit tel quel. Le script
   `scripts/verify-export-gradle.sh` lance la distribution Gradle du
   wrapper sur les exports : JVM (`compileJava` + `jar`, sources
   réelles, dépendance externe et dépendance de module) et Android
   (`assembleDebug` — APK produit). L'init-script
   `scripts/export-verification-init.gradle.kts` n'est qu'un
   contournement de réseau de la machine de développement (miroir de
   Central) : l'export lui-même reste fidèle.

## Conséquences

- L'export d'un projet importé sans perte est un projet Gradle
  complet et constructible ; l'export d'un modèle exotique garde
  toutes ses données signalées dans `EXPORT-NOTES.md`.
- Les projets racine-est-un-module (sans `include`) sont importés
  comme module à la racine puis exportés en sous-répertoire nommé :
  la disposition change (Gradle exige un projet par répertoire),
  l'aller-retour est stable au second tour.
- Cas pathologique documenté : un projet qui déclarerait AGP ≥ 9 **et**
  le greffon Kotlin android (refusé par Gradle) est importé mais son
  aller-retour n'est pas l'identité — la facette `gradle` perd le
  greffon incompatible. Un tel projet ne se construit pas sous
  Gradle ; la perte est dans l'ordre des choses et rapportée.
