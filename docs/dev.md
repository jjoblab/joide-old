# Guide de développement

## Prérequis

- JDK 21 (javac requis — par ex. Temurin 21) ; `JAVA_HOME` pointé
  dessus.
- Python 3 (script de politique de langue et génération de signatures,
  stdlib uniquement).
- Réseau au premier lancement (téléchargement de la distribution Gradle
  9.8.2, somme de contrôle vérifiée par le wrapper).
- **Aucun SDK Android requis en P0** : les 14 modules sont purs ; le
  critère « check vert sans SDK » (§7 P0) l'exige.

## Commandes

```bash
export JAVA_HOME=<jdk-21>
./gradlew check                      # tous les modules : tests, règle de
                                     # dépendances, politique de langue, API 28
./gradlew ciLocal                    # check + bench S/M contre les baselines
./gradlew :tools-bench:bench -Ptier=S|M|L|XL   # génère + mesure (3 runs,
                                                # déterminisme auto-vérifié)
./gradlew :tools-bench:benchS                  # variante à tier fixe
./gradlew :tools-bench:regressionTestS         # comparaison à la baseline S
```

`regressionTest*` compare l'exact (hash, comptages, octets) et le temporel (temps CPU, ±10 %). Le temporel n'a de sens que sur une machine de performance stable : exécuter ces tâches sur la machine de référence, pas sur un ordonnanceur partagé (ADR-014).

## Notes P1

- Les tests du store incluent le harnais de pannes (deux cents meurtres
  de machines virtuelles filles) : comptez environ deux minutes sur
  `:core-store:check` ; ils sont reproductibles (graine fixe, ADR-020).
- Le test de parcours à froid crée cent mille fichiers dans un
  répertoire temporaire (quelques dizaines de secondes de préparation,
  hors mesure).
- `core-store` sépare interface (source set principal, API 28) et
  implémentation de bureau (source set `jvm`, pilote SQLite JDBC) ;
  le contrôle API 28 ne voit que le principal (ADR-019).

## Régénérer les signatures API 28

```bash
curl -O https://dl.google.com/android/repository/platform-28_r06.zip
unzip platform-28_r06.zip android-9/android.jar
python3 scripts/generate_api28_signatures.py \
    android-9/android.jar gradle/api28-signatures.txt.gz
```

Le sha256 du android.jar et les comptages attendus sont consignés dans
`docs/toolchain.md` ; une divergence est une alerte, pas un échec
silencieux.

## Régénérer les baselines

```bash
./gradlew :tools-bench:bench -Ptier=S   # écrit baselines/s.toml (3 runs)
./gradlew :tools-bench:bench -Ptier=M   # écrit baselines/m.toml
```

Les baselines temporelles dépendent de la machine : régénérer sur la
machine de référence après un changement de matériel, jamais pour
« faire passer » une régression.

## Conventions

- Commits : `feat(scope):`, `fix(scope):`, `chore(scope):`, `docs:` en
  français, un commit par changement, tout correctif accompagné d'un
  test qui échoue avant.
- Aucun secret, aucun jeton, aucun keystore dans le dépôt.
