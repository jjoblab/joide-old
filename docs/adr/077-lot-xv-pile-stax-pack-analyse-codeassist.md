# ADR-077 — Lot XV : la pile StAX dans le pack — l'analyse complète
# du moteur de CodeAssist

- **Statut** : Accepted (le terrain du 2026-11-17 : la VM démarre,
  le compilateur se charge, et s'arrête sur
  `NoClassDefFoundError: Lorg/jetbrains/kotlin/org/codehaus/stax2/
  typed/TypedXMLStreamReader` depuis `PluginDescriptorLoader
  .loadDescriptorFromDir`)
- **Date** : 2026-11-17

## Contexte

Le couloir du terrain : 134 W^X (lot XIII, levé) → pas de JDK sous
ART (lot XIV, levé) → le compilateur se charge MAIS le
`PluginDescriptorLoader` d'IntelliJ (shadé dans l'embeddable :
`org.jetbrains.kotlin.com.intellij.ide.plugins`) échoue en
`NoClassDefFoundError` sur `TypedXMLStreamReader`.

Le diagnostic matériel (fait sur les ARTEFACTS, pas sur des
hypothèses) :

1. le pack `kotlin-compiler-2.3.0.dex.zip` publié contient BIEN les
   18 classes stax2 shadées ET `TypedXMLStreamReader` (diff complet
   contre `kotlin-compiler-embeddable-2.3.0.jar` : 0 manquante) ;
2. mais il ne contient AUCUNE classe `javax.xml.stream.*` DEFINIE (les
   8 descripteurs présents ne sont que des références) et AUCUN
   fichier `META-INF/services/javax.xml.stream.*InputFactory` ;
3. l'aalto shadé de l'embeddable est PARTIEL : 51 classes internes,
   sans `stax/InputFactoryImpl` ni usines ;
4. au bureau, le JDK fournit l'API StAX ET son parseur par défaut —
   rien ne se voit ; sur ART, `javax.xml.stream` n'EXISTE PAS (les
   API XML d'Android : org.xmlpull, org.w3c.dom, JAXP — PAS StAX) ;
   la vérification de la classe échoue sur sa hiérarchie
   (`TypedXMLStreamReader` → `XMLStreamReader2` →
   `javax.xml.stream.XMLStreamReader`) → NoClassDefFoundError.

## L'analyse du moteur de CodeAssist (la référence demandée)

Ce que CodeAssist porte réellement pour compiler Kotlin/Java sur
l'appareil (lu dans `lang/kotlin-compiler-deps`, `libs.versions.toml`,
`ArtKotlinPluginLoader`, `ForkedKotlincArtSpike`, `BuiltInPlugins`) :

| Pièce | Chez CodeAssist |
|---|---|
| Compilateur Kotlin | `-for-ide` SPLIT (common/fir/ir/fe10/cli) NON shadé + les vrais jars IntelliJ platform, fusionnés en UN jar dédupliqué (`mergeUnshadedCompiler`, services CONCATÉNÉS, remap de la façade coroutines, streamex multi-release plié dedans) — tourné EN PROCESSUS dans l'app (dexé), pas dans une VM forkée |
| StAX | **« StAX on ART (which ships none): the standalone JSR-173 API, and aalto as its implementation. Used by the on-device Kotlin compiler (IntelliJ-core's descriptor parsing) and by R8's resource shrinker »** — `javax.xml.stream:stax-api:1.0-2` + `com.fasterxml:aalto-xml:1.3.0` + `org.codehaus.woodstox:stax2-api:4.2.1` |
| Racines Java | `-no-jdk` avec android.jar au classpath ; leur commentaire KSP : ART's java.home n'expose pas `lib/jrt-fs.jar`, `jdkHome` reste null, `java.*` vient d'android.jar |
| Args du compilateur | `-no-jdk -no-stdlib -no-reflect`, `-Xuse-fir-lt=false` (la publication -for-ide exclut light-tree2fir), `-Dkotlin.colors.enabled=false`, PSI plutôt que LightTree |
| VM forkée (spike) | `dalvikvm64` sondé par candidats (`PROBE_ARGS = -version -no-jdk -no-stdlib -no-reflect` : « un exit non-nul veut dire le classpath, pas les arguments »), échelle de tas 768→4096 Mo, compile à 1536 Mo |
| W^X | `setReadOnly()` AVANT le move atomique (`ArtKotlinPluginLoader`) et à l'extraction (`ForkedKotlincArtSpike`) — porté par joide au lot XIII |
| Autres outils | aapt2 (binaire hôte Maven, classifier linux), bundletool 1.18.3 + guava, apksig 8.7.3, d8/r8 (AGP), desugar_jdk_libs 2.1.5, bouncycastle (keystore en processus — pas de keytool sur ART), trove4j/jna/log4j/caffeine (deps IntelliJ), ASM 9.6.1 relocalisé, KSP2 avec `jdkHome` null sur ART |

La leçon : là où le compilateur SHA DÉ de joide
(`kotlin-compiler-embeddable`) repose sur le JDK hôte pour
`javax.xml.stream`, la pile de CodeAssist embarque TOUT — c'est la
raison pour laquelle leur moteur démarre sur ART et le pack de joide
pas.

## Décision

- Les trois jars StAX de CodeAssist (mêmes versions) entrent dans la
  fermeture `jarsKotlin` du pack : API (`stax-api:1.0-2`), impl
  COMPLÈTE unshadée (`aalto-xml:1.3.0` — les usines que la copie
  shadée n'a pas) et son API (`stax2-api:4.2.1`). Ils traversent la
  pipeline existante (patcheur ART → assainir → dexage par lots →
  ressources non-class) : les `META-INF/services/javax.xml.stream.*`
  d'aalto partent dans le pack AVEC les usines qu'ils désignent.
- Le pack se RÉGÉNÈRE réellement dans la pipeline
  (`:app-android:dexPacksOutils` contre android.jar 35) et les
  constantes épinglées se reportent (taille + SHA-256 des TROIS packs
  — le marqueur D8/javac de la machine de génération fait dériver
  aussi r8/bundletool de quelques octets ; la doctrine du dépôt :
  les constantes reflètent les packs PUBLIÉS, les trois assets sont
  remplacés ensemble).
- Les classes `javax.xml.namespace.QName` de stax-api restent
  masquées par le bootclasspath d'ART (qui porte javax.xml.namespace)
  — délégation parentale, sans conflit.
- NOT : shimer javax.xml.stream (des stubs) — CodeAssist embarque la
  vraie API ; NOT : retirer l'aalto shadé de l'embeddable — il est
  référencé par du code compilé, inoffensif à côté.

## Conséquences

- Le prochain terrain doit aller PLUS LOIN que jamais : le
  `PluginDescriptorLoader` trouve l'API (les dexes du pack), l'usine
  (ServiceLoader → services du pack → aalto unshadé) et parse les
  descripteurs de plugins.
- La vérification matérielle du pack nouveau : 37 descripteurs
  `javax.xml.stream` DEFINIS (dont `XMLStreamReader`), les trois
  usines aalto unshadées (`InputFactoryImpl`, `OutputFactoryImpl`,
  `EventFactoryImpl`), les trois fichiers de services corrects,
  264 entrées (contre 253).
- Suites build-jvm, build-android et build-daemon au vert ; les
  constantes épinglées recalculées indépendamment (SHA-256) collent.
