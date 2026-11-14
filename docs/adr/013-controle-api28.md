# ADR-013 — Contrôle de compatibilité API 28 sans SDK Android

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

D4 : les modules exécutés sur Android n'utilisent que les API du niveau 28. Or le critère P0 exige un check vert SANS SDK Android (les modules purs se construisent et se testent sans SDK). Un contrôle lint NewApi nécessiterait le SDK ; il n'arrive qu'avec les modules Android (P6/P8).

## Décision

Donnée commitée : la liste des signatures java.*/javax.* du platform 28 (gradle/api28-signatures.txt.gz, 63 020 entrées), générée mécaniquement depuis l'android.jar de platform-28_r06 (javap -s -p + fermeture d'héritage lue dans les en-têtes de classes, scripts/generate_api28_signatures.py, sommes de contrôle consignées dans docs/toolchain.md). Contrôle : tâche checkApi28 (build-logic) lit le pool de constantes des classes du source set principal et rejette toute référence java.*/javax.* absente de la liste, tout préfixe interdit (sun/, jdk/, android/, androidx/, com/android/, dalvik/). Les constructions réécrites par D8 (LambdaMetafactory, StringConcatFactory) sont explicitement autorisées : jamais visibles sur ART. Constat clé qui a fondé la fermeture d'héritage : javac émet le type statique du récepteur comme propriétaire (BufferedWriter.write alors que write est déclaré dans Writer) — vérifié par sondage du bytecode. java.lang.Record et System.Logger sont absents du niveau 28 : pas de records/sealed dans le code exécuté sur Android avant validation du désucrage D8 (P5) ; les modules purs journalisent par java.util.logging (même constat que celeris).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Liste de signatures + scanner de pool (retenue) | retenue | fonctionne sans SDK, preuve mécanique, coût nul à l'exécution |
| lint NewApi | différée | exige le SDK ; adaptée aux modules Android en P6/P8 |
| Compilation contre android.jar | écartée en P0 | exigerait le SDK et changerait la sémantique des modules purs JVM |

## Conséquences

Limites documentées : ni réflexion ni chargement dynamique ; ne préjuge pas du désucrage D8 ; la sonde mémoire du harnais utilise Runtime (MemoryMXBean absent du niveau 28). Preuve d'acceptation : fixture HexFormat (compilable en 17, absent du niveau 28) rejetée par checkApi28 (tools-test, TestKit). Ajustement à l'exécution (P0) : tools-bench et tools-test sont « test-only » (§5.1 du prompt), jamais exécutés sur Android — D4 ne les concerne pas ; ils sont exemptés du scan (visible et commenté dans la convention), ce qui autorise le harnais à utiliser les API de mesure de la JVM de bureau (temps CPU du processus, ADR-014). Les douze modules produit restent intégralement scannés.
