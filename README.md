# JoIDE

IDE Android qui tourne sur Android, avec son propre moteur de build sans
Gradle, conçu pour les très gros projets (centaines de modules, dizaines de
milliers de fichiers) sur un téléphone milieu de gamme.

- **Noyau Java 17** construit au JDK 21 (D2), `minSdk` 28 (D4), Apache-2.0,
  développement clean-room (D1, ADR-008).
- **Périmètre** : P0-P7 = noyau (modèle, VFS, moteur de tâches, pipelines
  JVM et Android, dépendances, daemon, import/export Gradle) ; P8+ = IDE.
- **Documentation** : `docs/adr/` (décisions), `docs/dev.md` (prise en main),
  `PHASE-P0-PLAN.md` et `PHASE-P0-RAPPORT.md` (validation par phase, §11).

## Commandes (P0)

```bash
export JAVA_HOME=<jdk-21>
./gradlew check            # tests + règle de dépendances + langue + API 28
./gradlew ciLocal          # check + bench S/M + comparaison aux baselines
./gradlew :tools-bench:bench -Ptier=L   # génération + mesure (S|M|L|XL)
```

Aucun SDK Android n'est requis en P0 : les modules purs seuls sont livrés
(critère d'acceptation §7 P0).
