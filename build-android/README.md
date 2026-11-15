# build-android

Pipeline Android par variante : aapt2 (compilation incrémentale par
fichier, lien, R), dex par scopes avec cache partageable entre
projets, fusion par buckets, package (zipalign), signature, AAB, R8.

| | |
|---|---|
| Paquet | `jo.joide.build.android` |
| Nature | JVM pure (source set principal sous contrôle API 28) ; adaptateurs d'outils Google dans le source set `tools` |
| Dépendances JoIDE | build-jvm, core-deps |
| Phase de remplissage | P5 |

## Invariants

Travail proportionnel à la modification (aapt2 recompile le fichier
touché, le lien seul est global) ; cache de dex partageable entre
projets (l'empreinte d'un scope externe ne dépend que du contenu de
la bibliothèque, de l'API minimum et de l'outil — deux projets
partageant le cache de sorties ne dexent qu'une fois) ; l'incrémental
est équivalent au propre octet par octet.

## Contenu

- `toolchain` : localisation de l'outillage (variables
  d'environnement, classpath, téléchargement épinglé par SHA-256) et
  extraction protégée contre le zip-slip.
- `aapt2` : pilotage du binaire en processus externe, diagnostics
  structurés.
- `variant` : variante de build (types, signature, minSdk, R8).
- `dex` : lecture d'en-têtes dex, répartition en buckets multidex,
  interface de dexage (Dexer) et fork à gros tas (ForkDexer).
- `packaging` : écriture zip déterministe et alignée (zipalign).
- `bundle`, `sign` : interfaces d'assemblage de bundle et de
  signature.
- `tasks` : tâches du pipeline (aapt2 compile/link, jar des R, dex
  par scope, fusion, package, signature, bundle, rétrécissement) et
  fabrique du graphe (`AndroidTaskFactory`).
- source set `tools` : D8/R8 en processus (compilateurs chauds),
  apksig, bundletool — chargés par ServiceLoader, jamais référencés
  depuis le source set principal (préfixe `com/android/` interdit par
  le contrôle API 28).
