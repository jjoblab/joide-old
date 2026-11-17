# ADR-078 — Lot XVI : le JAR réel de la stdlib dans le pack — le
# classpath de compilation exige des fichiers

- **Statut** : Accepted (le terrain du 2026-11-17 : `:core:compileKotlin`
  — « Cannot access built-in declaration 'kotlin.Any'. Ensure that you
  have a dependency on the Kotlin standard library. », après le warning
  « Classpath entry points to a non-existent location:
  core/classes-kotlin » et la note « Your JDK doesn't seem to support
  mapped buffer unmapping »)
- **Date** : 2026-11-17

## Contexte

Le couloir franchi : la VM démarre (lot XII/XIII), `-no-jdk` tient
(lot XIV), la pile StAX fait charger les descripteurs de plugins (lot
XV). Le compilateur CRÉE maintenant son environnement — et sèche sur
la stdlib.

Le diagnostic (sur les artefacts, pas sur des hypothèses) :

1. `KotlinArgs` pose `-no-stdlib` et son contrat est explicite :
   « le classpath fourni doit déjà porter la bibliothèque standard » ;
2. le classpath de compile Android (`AndroidTaskFactory
   .registerCompileTasks` : `upstreamKotlin`) porte android.jar, les
   sorties des modules amont, R.jar et les jars externes — la stdlib
   N'Y EST PARTOUT ;
3. kotlinc résout son classpath avec son JAR FS : des ENTRÉES
   FICHIER (jar ou dossier) sur disque. Les classes de la stdlib
   dexées DANS le pack sont invisibles pour ce mécanisme ; sur
   l'appareil, il n'existe AUCUN `kotlin-stdlib.jar` sur disque ;
4. `kotlin.Any`/`kotlin.String` sont des déclarations INTRINSÈQUES :
   elles n'ont pas de `.class` (vérifié dans le jar : 978 classes,
   ZÉRO `kotlin/Any.class`) — leur métadonnée vit dans
   `META-INF/kotlin-stdlib.kotlin_module`, que seul le jar fournit ;
5. la manière CodeAssist : leur outil télécharge le jar RÉEL de la
   stdlib (les artefacts toolchain vivent en fichiers) et le pose au
   classpath — leur compilateur in-process lit des jars réels aussi.

Les deux autres lignes du log, sans gravité, expliquées :

- « mapped buffer unmapping » : la sonde de JAR FS du compilateur
  cherche une optimisation du JDK de bureau (`Unsafe` unmapping) —
  ART n'en a pas, le compilateur prend la version lente du JAR FS :
  un avertissement de performance, pas une erreur ;
- « core/classes-kotlin » inexistant : l'entrée de SORTIE d'un module
  (la sienne propre pour `compileJava`, l'amont si la compile amont
  a échoué) — une entrée fantôme ne fait qu'un warning et une
  résolution qui sèche ; le lot XVI les filtre.

## Décision

- **Le pack porte le jar ORIGINAL de la stdlib** comme ressource
  (`jars/kotlin-stdlib-2.3.0.jar`, +1,68 Mo) : la tâche
  `dexPacksOutils` copie le jar Maven RÉSOLU (pas le patché — le
  compilateur lit du bytecode standard, pas du bytecode ART-rewrité).
- **Aucun nouveau mécanisme d'extraction** : `homeOuvriere` (lot XI)
  extrait déjà TOUTES les entrées non-dex du pack dans
  `kotlin-home/`, la garde étant le SHA-256 épinglé du pack — le jar
  stdlib arrive gratuitement à
  `<cache>/kotlin-home/jars/kotlin-stdlib-2.3.0.jar`.
- **Le pilote forké pose le jar en tête du classpath** :
  `KotlinForkedDriver.compile` assure le pack puis
  `jarStdlib(homeOuvriere(classpath))` AVANT de bâtir les arguments,
  et passe l'entrée à `KotlinArgs.args(request, classpathAvant)` —
  `-no-stdlib` tient son contrat. Hors pack (les essais bureau,
  `joide.kotlinWorkerCp` non-`.dex.zip`) : aucune entrée ajoutée, le
  comportement historique tient.
- **Les entrées de classpath inexistantes se filtrent**
  (`KotlinArgs.classpathEntiere`) : une entrée fantôme ne fait qu'un
  « points to a non-existent location » et une résolution qui sèche ;
  l'ordre de la demande se conserve.
- Les tests bureau restent intacts : `KotlinForkedDriverTest` porte
  la stdlib dans la demande (`jarDe(kotlin.KotlinVersion.class)`)
  et son classpath d'essai n'est pas un `.dex.zip`.

## Conséquences

- La résolution `kotlin.Any` se fait sur le jar réel extrait du pack
  — le prochain terrain doit franchir la compile elle-même.
- Pack nouveau vérifié : 265 entrées (264 → 265),
  `jars/kotlin-stdlib-2.3.0.jar` (1 796 996 octets, 978 classes, les
  3 `.kotlin_module` dont `META-INF/kotlin-stdlib.kotlin_module`),
  SHA-256 épinglé `9a9f48fc…` et vérifié par téléchargement retour de
  l'asset.
- Le marqueur D8/javac ne touche que les packs reconstruits : r8 et
  bundletool (entrées inchangées) sont ressortis identiques octet
  pour octet — aucune republication pour eux.
- Suites build-jvm, build-android, build-daemon au vert ; deux tests
  nouveaux (le filtrage + le préfixage dans `KotlinArgsTest`, la
  recherche du jar stdlib dans `KotlinForkedDriverTest`).
