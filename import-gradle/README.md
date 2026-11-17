# import-gradle

Lecture statique et tolérante des projets Gradle (settings, build, catalogues) et export vers Gradle.

| | |
|---|---|
| Paquet | `jo.joide.imports.gradle` |
| Nature | JVM pure |
| Dépendances JoIDE | core-model, core-deps |
| Phase de remplissage | P7 (livrée) |

## Invariants

Aucun script de build exécuté (ADR-039) ; tout point non résolu figure
au SyncReport, avec un niveau de confiance par module (ADR-040) ;
l'export est déterministe et signale ses limites dans
`EXPORT-NOTES.md` (ADR-041).

## Contenu

- `script/` — parseur statique et tolérant des deux dialectes
  (Groovy, Kotlin DSL) : lecteur lexical, instructions et expressions
  en forme tolérante, jamais d'exception.
- `GradleProperties`, `VersionCatalog`, `GradleSettings`,
  `GradleBuildFile` — lecteurs des fichiers d'un projet Gradle :
  propriétés, catalogue de versions (tables en ligne), réglages
  (modules inclus), scripts de build (greffons, bloc `android`,
  dépendances, `ext`, ensembles de sources).
- `Interpolation` — résolution `$var`/`${expr}` contre les portées
  connues (propriétés, ext, versions du catalogue).
- `GradleImporter` + `SyncReport` + `ModuleConfidence` — import
  complet d'un répertoire Gradle : espace de travail JoIDE et rapport
  exhaustif.
- `GradleExporter` — export déterministe vers un projet Gradle
  constructible, `EXPORT-NOTES.md` pour ce qui n'a pas d'équivalent.

## Vérification à vrai Gradle

`scripts/verify-export-gradle.sh` lance la distribution Gradle du
wrapper sur les exports (JVM : `compileJava` + `jar` ; Android :
`assembleDebug`). Hors appareil — machine de développement ou CI.
