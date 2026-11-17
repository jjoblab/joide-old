# ADR-079 — Lot XVII : le manifeste enrichie — aapt2 exige
# l'attribut package qu'AGP synthétise

- **Statut** : Accepted (le terrain du 2026-11-17 : `:app
  :linkAndroidResources` — « link failed: AndroidManifest.xml: error:
  &lt;manifest&gt; must have a 'package' attribute », 5 tâches
  exécutées en 104 s : la chaîne de build TOURNE, l'échec est devenu
  métier)
- **Date** : 2026-11-17

## Contexte

La progression est franche : les murs d'environnement (W^X, JDK,
StAX, stdlib) sont franchis — le graphe exécute maintenant les
tâches Android, et le premier mur MÉTIER apparaît : aapt2 link
refuse un manifeste sans attribut {@code package}.

Le diagnostic (sur les artefacts, pas sur des hypothèses) :

1. le GABARIT du projet de démonstration
   (`assets/modeles/application-android.toml`) écrit un manifeste
   SANS {@code package} — le style AGP 8 (le package vit dans
   {@code namespace} du build.gradle), alors que le paquet SAISI à
   la création ne va que dans {@code MainActivity.kt} ;
2. `Aapt2LinkTask` passait le manifeste SOURCE brut à aapt2 — la
   synthèse d'AGP (le manifeste MERGÉ porte l'attribut) n'existait
   pas dans la chaîne joide ;
3. aapt2 est une couche BASSE : il exige l'attribut, peu importe la
   mode du template ;
4. `Aapt2.LinkOptions.packageName` est `--rename-manifest-package`
   (l'identifiant FINAL de l'APK) — il ne remplace pas l'attribut
   {@code package} que la table de ressources et R exigent.

## Décision

- **Le manifeste ENRICHIE** (la manière AGP) : `Aapt2LinkTask`
  écrit TOUJOURS un manifeste enrichie dans l'outillage du module
  (`$OUT/android/<module>/manifest-enrichi.xml` — JAMAIS dans les
  sources utilisateur) : copie du manifeste source, avec
  {@code package="…"} injecté dans la balise d'ouverture s'il
  manque. Les DEUX liens (APK et proto) consomment le manifeste
  enrichie ; il est déclaré en sortie (l'inventaire du moteur le
  suit).
- **La chaîne de secours** : manifeste {@code package=} →
  identifiant d'application de la variante ({@code identifiantPaquet}
  de SpecsBuild — l'application en a toujours un) → dérivation du
  nom du module, EXACTEMENT la règle d'{@code identifiantPaquet} (« : »
  → « _ », le souligné survit). Une synthèse émet un WARN qui nomme
  le paquet et le correctif recommandé (écrire le paquet dans le
  manifeste).
- **Le gabarit est corrigé** : le manifeste du modèle porte
  désormais `package="{paquet}"` — le paquet saisi à la création
  arrive enfin dans le manifeste (les nouveaux projets sont droits).
- Les projets DÉJÀ créés (le terrain) : la synthèse fait passer le
  lien ; le WARN recommande la ligne dans le manifeste — le paquet
  choisi à la création (la première ligne de MainActivity.kt) est
  la bonne valeur. R.java se génère sous ce paquet : l'activité y
  voit {@code R} sans import.

## Conséquences

- Un projet importé au style AGP 8 (manifeste sans {@code package})
  se lie sans retouche — le comportement d'AGP (manifeste mergé
  enrichi) est reproduit.
- `PackageTask` et `AabTask` lisent le manifeste LIÉ (binaire/proto)
  : inchangés. `SpecsBuild.identifiantPaquet` lit le manifeste
  source : inchangé (l'attribut des nouveaux gabarits le rend
  exact).
- Suites build-jvm, build-android, build-daemon au vert ; cinq tests
  nouveaux (synthèse depuis l'identifiant, dérivation en dernier
  recours, manifeste avec paquet intact, balise multiligne, la règle
  de dérivation EXACTE d'{@code identifiantPaquet}).
