# ADR-034 — Adaptateurs d'outils Android derrière des interfaces, source set « tools »

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Le pipeline Android pilote D8/R8, apksig et bundletool, publiés par
Google sur Maven. Le contrôle API 28 du source set principal interdit
toute référence aux préfixes `com/android/` ; le code qui parle à ces
outils ne peut donc pas y vivre. À l'inverse, aapt2 est un binaire
natif sans API Java : il ne pose pas ce problème mais doit être
invoqué en processus externe.

## Décision

Le source set principal de build-android ne référence aucun symbole
tiers : il définit les interfaces (`Dexer`, `ApkSignerService`,
`BundleAssembler`) et les pilotes qui n'en ont pas besoin (aapt2 en
processus externe, fork D8/R8 par ligne de commande, écriture zip,
planification des buckets). Un source set `tools`, hors contrôle API
28 (même séparation que le pilote SQLite de core-store), porte les
adaptateurs en processus — D8/R8 chargés par le classloader du build
(compilateurs chauds pour le démon), apksig, bundletool — enregistrés
par ServiceLoader. Le dexage en processus sert les scopes (petites
entrées) ; la fusion et le rétrécissement passent par le fork à gros
tas, dont le pic de mémoire n'atteint jamais la machine virtuelle du
build. Les versions sont dans le catalogue du dépôt.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Références directes dans le source set principal | écartée | préfixe `com/android/` rejeté par le contrôle API 28 |
| Fork systématique | écartée | un démarrage de machine virtuelle par scope ; les compilateurs chauds du démon (P6) exigent l'en processus |
| Interfaces + source set tools (retenue) | retenue | API 28 respectée, les deux modes de dexage disponibles, le démon choisit |
| Réécriture maison des outils | écartée | hors périmètre et hors raison (outils Apache/BSD publics) |

## Conséquences

Les tests exigent l'outillage réel (aapt2, plateforme, jars) :
téléchargés une fois dans un cache local épinglé (ADR-035), ou saut
explicite d'hypothèse si le réseau et le cache manquent. Le démon (P6)
embarquera le source set tools pour les compilateurs chauds.
