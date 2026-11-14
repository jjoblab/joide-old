# Dépendances et apports tiers (P0)

Politique (D1/ADR-008) : licences compatibles Apache-2.0 (Apache-2.0,
BSD, MIT) ; EPL-2.0 acceptée en dépendance séparée avec ses
obligations ; jamais GPL/AGPL ; LGPL seulement pour l'outillage de
build non embarqué. Chaque entrée est aussi dans `NOTICE`.

| Élément | Version | Licence | Usage | Vérification |
|---|---|---|---|---|
| Gradle (wrapper + distribution) | 9.8.2 | Apache-2.0 | build de JoIDE (D13), non embarqué | somme de contrôle du wrapper |
| JUnit Jupiter | 5.14.4 | EPL-2.0 | tests uniquement, aucun artefact runtime | catalogue de versions ; mention EPL dans NOTICE |
| sqlite-jdbc (org.xerial) | 3.49.1.0 | Apache-2.0 (pilote) ; SQLite domaine public (C embarqué) | implémentation de bureau du BuildStore (core-store, source set `jvm`) ; l'implémentation Android utilisera la base système | `gradle/libs.versions.toml` ; page Maven Central du projet xerial |
| scripts/check-language-policy | adapté de jjoblab/celeris@f9001fc | Apache-2.0 | contrôle de langue (D3, ADR-012) | en-tête du script ; dépôt du propriétaire |
| gradle/api28-signatures.txt.gz | platform-28_r06 | données factuelles d'API (liste de signatures générée mécaniquement) | contrôle API 28 (D4, ADR-013) | script de génération + sha256 du android.jar (docs/toolchain.md) |
| gradlew, gradle-wrapper.jar | repris de jjoblab/celeris@f9001fc | Apache-2.0 (artefacts Gradle) | wrapper du build | fichiers Gradle standard |

## Dépendances runtime de JoIDE

**Une en P1 : sqlite-jdbc** (source set `jvm` de core-store). Les
arrivages futurs (ecj, kotlinc, D8/R8, aapt2, apksig, bundletool,
briques P8) seront consignés ici et dans `NOTICE` au fil des phases,
avec licence et vérification.
