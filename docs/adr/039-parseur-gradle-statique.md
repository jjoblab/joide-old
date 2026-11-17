# ADR-039 — Parseur de scripts Gradle statique et tolérant

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'import Gradle (§6.9 du prompt) doit lire `settings.gradle(.kts)`,
`build.gradle(.kts)` et `gradle.properties` **sans jamais les
exécuter** — la moindre évaluation d'un script de build serait une
faille (un script importé peut contenir `System.exit`, de l'écriture
de fichiers, du réseau) et une dépendance au runtime Gradle (interdit
dans les modules purs). Les deux dialectes (Groovy et Kotlin DSL)
partagent les mêmes formes de surface : blocs à accolades, chaînes,
commentaires, appels parenthésés ou nus, affectations. Un vrai Gradle
évalue ; JoIDE ne peut que lire.

Les solutions existantes (GroovyShell, Kotlin scripting) évaluent
par construction : exclues. Un parseur complet de Groovy ou de Kotlin
serait une copie de compilateur : hors de propos pour extraire des
déclarations.

## Décision

1. **Lecteur lexical commun, sans sémantique**. `ScriptLexer` produit
   des jetons (identifiants, cinq styles de chaînes, nombres,
   ponctuation) en sautant les commentaires `//`, `/* */` et `#`. Il
   ne lève jamais : une chaîne non fermée produit un jeton au mieux et
   un avertissement, la lecture continue. Les chaînes gardent leur
   contenu brut — l'interpolation `$var`/`${expr}` est résolue plus
   tard, contre les portées connues (`Interpolation`).

2. **Parseur par blocs à accolades, conscient des chaînes et
   commentaires**. `ScriptParser` produit une suite d'instructions en
   forme tolérante : appels (avec récepteur, arguments et bloc en
   fermeture), affectations (`=`, `+=`), et instructions non comprises
   conservées brutes avec leur ligne. Les formes des deux dialectes
   sont ramenées aux mêmes instructions : `minSdk 24` et
   `minSdk = 24`, `implementation 'g:a:v'` et
   `implementation("g:a:v")`, `free { }` et `create("free") { }`,
   `getByName("main") { }` et `main { }`. Les virgules finales
   Kotlin (`setOf(a, b,)`), les paramètres génériques
   (`listOf<String>()`), les chaînes d'appels
   (`libs.versions.x.get().toInt()` devient un appel imbriqué dont la
   structure est préservée) et les arguments nommés Groovy
   (`apply plugin: 'x'`) sont reconnus.

3. **Frontières strictes contre l'avalage**. Tout ce qui n'est pas
   compris est consommé jusqu'à une frontière — virgule, parenthèse
   ou crochet fermant, point-virgule, accolade fermante — jamais
   au-delà. Ce point est né du réel : une consommation gloutonne
   jusqu'à la fin de ligne faisait avaler au parseur tout le reste du
   fichier `build.gradle.kts` de `architecture-samples` après un
   `setOf(…)` à virgule finale ; la frontière d'expression est la
   leçon.

4. **Ceinture et bretelles**. `parse` attrape toute exception
   interne et rend un script vide avec un problème d'erreur : le
   contrat « jamais d'exception » est structurel (test de fuzz à cinq
   cents entrées aléatoires) et défensif.

5. **Aucune API d'exécution dans le module**. Le test
   `NoExecutionDisciplineTest` scanne les sources principales et
   rejette `ProcessBuilder`, `Runtime`, `System.exit`,
   `javax.script`, `GroovyShell`, `groovy.lang`, `Class.forName`,
   `URLClassLoader`, `ScriptEngine`, `.exec(` : ce qui n'est pas dans
   le code ne peut pas s'exécuter. La preuve comportementale — des
   scripts empoisonnés (exit, marqueurs, exec) importés sans effet —
   vit dans `GradleImporterTest`.

6. **Tables en ligne du parseur TOML (core-model)**. Le catalogue
   `libs.versions.toml` exige les tables en ligne
   (`{ module = "g:a", version.ref = "v" }`) ; le parseur TOML maison
   (ADR-028) les signalait et les sautait. Il les lit désormais, avec
   clefs pointées, en tolérance : ce qui est lu avant une anomalie
   est conservé avec un problème. Le changement est additif — les
   fichiers de projet JoIDE n'utilisent pas de tables en ligne, aucun
   lecteur existant n'est affecté (tests de core-model verts).

## Conséquences

- L'import lit les deux dialectes avec le même code ; les différences
  (noms `is`-préfixés du Kotlin DSL, appels nus Groovy) sont
  normalisées dans les lecteurs, pas dans le parseur.
- Le sous-ensemble reconnu est documenté par les tests ; ce qui sort
  du sous-ensemble devient une instruction `UNKNOWN` rapportée — le
  SyncReport (ADR-040) en fait une entrée, jamais un silence.
- Le parseur ne connaîtra jamais tout Groovy ni tout Kotlin : les
  fermetures à paramètres (`all { test -> … }`) sont tolérées comme
  du bruit rapporté, les instructions lambda Kotlin (`val x by
  getting { }`) sont ignorées avec information. C'est le contrat :
  statique, tolérant, honnête.
