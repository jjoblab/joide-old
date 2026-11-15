# Dépendances et apports tiers (P0)

Politique (D1/ADR-008) : licences compatibles Apache-2.0 (Apache-2.0,
BSD, MIT) ; EPL-2.0 acceptée en dépendance séparée avec ses
obligations ; jamais GPL/AGPL ; LGPL seulement pour l'outillage de
build non embarqué. Chaque entrée est aussi dans `NOTICE`.

| Élément | Version | Licence | Usage | Vérification |
|---|---|---|---|---|
| Gradle (wrapper + distribution) | 9.8.2 | Apache-2.0 | build de JoIDE (D13), non embarqué | somme de contrôle du wrapper |
| JUnit Jupiter | 5.14.4 | EPL-2.0 | tests uniquement, aucun artefact runtime | catalogue de versions ; mention EPL dans NOTICE |
| sqlite-jdbc (org.xerial) | 3.49.1.0 | Apache-2.0 (pilote) ; SQLite domaine public (C embarqué) | implémentation de bureau du BuildStore (core-store, source set `jvm`) ; l'implémentation Android utilisera la base système | `gradle/libs.versions.toml` ; page Maven Central du projet xerial |
| scripts/check-language-policy | adapté de jjoblab/celeris@f9001fc | Apache-2.0 | contrôle de langue (D3, ADR-012) | en-tête du script ; dépôt du propriétaire |
| gradle/api28-signatures.txt.gz | platform-28_r06 | données factuelles d'API (liste de signatures générée mécaniquement) | contrôle API 28 (D4, ADR-013) | script de génération + sha256 du android.jar (docs/toolchain.md) |
| gradlew, gradle-wrapper.jar | repris de jjoblab/celeris@f9001fc | Apache-2.0 (artefacts Gradle) | wrapper du build | fichiers Gradle standard |

## Dépendances et apports tiers (P8)

Les trois briques du propriétaire, consommées en binaire depuis le
Maven Local des checkouts épinglés (ADR-044) et redistribuées dans
l'APK de l'application Android via ide-integration — la redistribution
est autorisée par la déclaration du titulaire des droits du 2026-11-16
(D-1 close pour JoIDE, `docs/integration/DEMANDES.md`).

| Élément | Version | Licence | Usage | Vérification |
|---|---|---|---|---|
| celeris (jo.celeris : android-host, android-view, android-widgets, lsp-protocole, core-text, core-syntax, core-language, core-lsp, core-syntax-grammars) | 1.1.0@f9001fc | Apache-2.0 ; transitifs de la brique : joni 2.2.9 + jcodings 1.0.64 (MIT), lsp4j 1.0.0 + jsonrpc 1.0.0 (EPL-2.0 OU BSD-3-Clause), Gson 2.10.1 (Apache-2.0) | éditeur embarqué de l'application (ide-integration) | `docs/integration/celeris.md` ; NOTICE de la brique |
| code-lsp (jo.codeide.lsp : moteurs java/kotlin/xml, socle model/index) | 1.15.0@657d787 | Apache-2.0 ; transitifs : lsp4j 0.21.2 (EPL-2.0), syntax-api 0.3.340 (Apache-2.0), kotlin-metadata-jvm (Apache-2.0), kotlinx-coroutines/serialization (Apache-2.0) | moteurs de langage Java/Kotlin/XML (ide-integration, lot C) | `docs/integration/code-lsp.md` |
| Compagnon ecj patché ART (jo-codeide-lsp-java-ecj-art) | 1.15.0 (ecj 3.40.0) | EPL-2.0 modifié — modifications décrites et rejouables par la brique (patcheur `:ecj-art`, `META-INF/ECJ-ART-MODIFICATIONS.txt`) ; séparé de l'ecj vanille du pipeline JVM (P3) | compilateur du moteur Java code-lsp, classpath runtime uniquement | `docs/integration/code-lsp.md` |
| java-syntax (org.jetbrains, intellij-dependencies) | 0.3.381 | « JetBrains Open-Source Build Terms » — **redistribution à confirmer (D-3, en attente)** | analyse syntaxique du moteur Java (transitive de code-lsp) | `docs/integration/DEMANDES.md` D-3 |
| LayoutLib (com.github.jjoblab.LayoutLib : module-inflater + 5 modules api) | 3.7.0@720e85d | dépôt sans fichier LICENSE (« projet interne — aucun droit sans autorisation explicite ») ; autorisation explicite du titulaire du 2026-11-16 (D-1 close pour JoIDE) | aperçu de layouts XML (ide-integration, lot E) | `docs/integration/layoutlib.md` |

## Dépendances runtime de JoIDE

- P1 : sqlite-jdbc (source set `jvm` de core-store).
- P3/P5 : ecj, kotlin-compiler-embeddable (pipeline JVM) ; D8/R8,
  apksig, bundletool, aapt2, android.jar (pipeline Android) — outillage
  de build, consigné dans `NOTICE`.
- P8 : les trois briques ci-dessus et leurs transitifs — embarquées
  dans l'APK de l'application Android, consignées dans `NOTICE`.
