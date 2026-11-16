# ADR-050 — Lot I : les modèles de projet, format déclaratif dérivé du joide.toml

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot I (ADR-É10) livre l'assistant de création (ADR-É4) : galerie
de modèles, formulaire **piloté par le modèle**, récapitulatif
atomique. Le plan demandait une ADR pour le « format déclaratif des
modèles (dérivé du joide.toml, pas un script) » et laissait la
liste des modèles v2 « à figer en cours de lot ». Deux sujets
exigent une décision numérotée : le format lui-même (comment un
modèle v3 s'ajoute sans toucher l'assistant), et la **franchise**
des cartes — l'ADR-É4 annonçait « exécution et tests sur
l'appareil » pour la console et « l'APK se construit au bureau »
pour Android ; l'état réel de P8 exige des libellés qui ne
promettent rien de plus.

## Décision

### 1. Format : `joide.template/1`, un TOML par modèle dans `assets/modeles/`

Dérivé du joide.toml : les blocs `[[modules]]` reflètent les
`[[sources]]` des joide-module.toml (racines par rôle : `source`,
`test`, `resources`, `android-res`, `assets`, `aidl`), les
dépendances externes sont des `[[dependances]]`, les fichiers
générés des `[[fichiers]]` à **lignes littérales** (chaînes TOML
sans échappement — le contenu Kotlin/XML garde ses guillemets
tels quels ; les lignes avec apostrophe passent en chaînes
basiques).

```toml
schema = "joide.template/1"
id = "console-jvm"
nom = "Console JVM"
description = "Un module :console, un main Kotlin"
categorie = "bibliotheques"   # applications | bibliotheques | vide
tuile = "console"             # android | console | bibliotheque | vide
note = "constructible sur l'appareil"     # ou franchise = "…" (alerte)
fichier-entree = "console/src/main/kotlin/jo/exemple/console/Main.kt"

[[modules]]
id = "console"
type = "jvm-library"
defaut = true
source = ["src/main/kotlin"]
main-class = "jo.exemple.console.MainKt"  # -> [facets.application]
[[fichiers]]
module = "console"
chemin = "console/src/main/kotlin/jo/exemple/console/Main.kt"
lignes = ['package jo.exemple.console', 'fun main() {', …]
```

Règles : `franchise` (texte en couleur alerte, la limite du modèle)
ou `note` (gris, l'absence de limite) — **au moins l'une**, jamais
une promesse muette ; un module sans `defaut = true` n'est pas
proposé par défaut mais reste basculable ; `requiert` déclare les
dépendances de module (le retrait d'un module requis par un module
conservé est refusé, nommément) ; `manifeste` devient
`[facets.android] manifest` ; `main-class` devient
`[facets.application] main-class` — la convention lue par la
classification du lot H (ADR-049). L'analyse (`ModeleProjet`,
pure) refuse un document sans franchise ni note, avec clé
inconnue, module en doublon — problèmes nommés, le modèle n'entre
pas dans la galerie (journal).

Un modèle v3 = un fichier TOML de plus dans `assets/modeles/` ;
l'assistant (`EcranCreation`) liste le dossier, ne connaît aucun
modèle par nom.

### 2. Substitutions à la génération, pas à l'analyse

`{nom}`, `{paquet}`, `{paquet-chemin}` (`.` → `/`), `{min-sdk}` —
dans `chemin` et `lignes`. Le générateur (`GenerateurProjet`, pur)
construit les objets core-model et sérialise le `joide.toml` et les
`joide-module.toml` par les **écrivains canoniques** (P1) : le
fichier de configuration n'est jamais une chaîne du modèle, il est
dérivé des modules choisis — déterministe, relu tel quel par le
lecteur (prouvé par test : génération → disque → `WorkspaceLoader`
sans erreur).

### 3. Écriture atomique : temporaire voisin puis renommage

`EcrireProjet` : l'arborescence complète s'écrit sous
`projets/.tmp-creation-<id>/`, puis `Files.move` atomique vers
`projets/<id>`. Le dossier final existant est refusé (l'id est
unique par construction) ; un chemin qui sort du projet est refusé
(la leçon zip-slip, ADR-032) ; tout échec efface le temporaire et
remonte l'erreur nominale — **un projet à moitié créé n'existe
jamais**.

### 4. La liste des quatre modèles, figée — la franchise d'abord

| Modèle | Contenu | Note de carte |
|---|---|---|
| Application Android | `:app` (android-application, manifeste, mise en page, activité Kotlin) + `:core` (JVM), `:app` requiert `:core` | franchise : « JVM sur l'appareil ; l'APK exige la chaîne de bureau (câblage à venir) » |
| Console JVM | `:console` (jvm-library, main Kotlin, facette application) | note : « constructible sur l'appareil » |
| Bibliothèque JVM | `:socle` (jvm-library, ensemble de test, dépendance JUnit 5 en `test_implementation`) | note : « constructible sur l'appareil » |
| Vide | `joide.toml` minimal sans module | note : « point de départ libre » |

**Écarts assumés contre l'ADR-É4 et la maquette** (le tableau de
l'ADR-É4 annonçait « exécution et tests sur l'appareil » et « l'APK
se construit au bureau (P8) ») :

- l'**exécution** du main sur l'appareil n'est pas câblée (aucun
  bouton Exécuter dans l'UI P8 ; la tâche `JvmRunTask` existe dans
  le pipeline) — la carte console promet la construction (compile +
  jar, vrai par le démon local), pas l'exécution ;
- les **tests** JUnit se compilent sur l'appareil (racines `test`
  au rôle source, jar résolu au classpath) mais ne s'y **exécutent**
  pas — la carte de la bibliothèque ne le prétend pas ;
- l'APK au bureau : la chaîne P5 est prouvée, mais aucun outil
  utilisateur ne constitue aujourd'hui une spec Android depuis un
  workspace natif — la franchise le nomme (« câblage à venir »),
  le backlog porte l'outil (voir ci-dessous) ;
- la maquette offrait minSdk 24/28/30 : **24 est écarté** (ADR-009,
  Java 17 exige minSdk 28 ; la désucation en dessous n'est pas
  prouvée par le pipeline) — le segment propose 28 et 30 ;
- la maquette montrait un bouton « Créer le projet » vert
  (réussite) : l'ADR-É4 dit « (accent) » — le kit suit l'ADR ;
- les puces de modules de l'étape 2 basculent directement (tous les
  modules du modèle sont affichés, sélectionnés ou non) : la
  maquette montrait un bouton « + Ajouter », utile seulement aux
  modèles v3 à nombreux modules optionnels.

### 5. Le récapitulatif est la génération

La fiche et l'arborescence de l'étape 3 viennent de
`GenerateurProjet.generer` — exactement les fichiers qui seront
écrits, comptés par le test (une ligne par fichier, ni plus ni
moins). Le récapitulatif ne promet rien que la création ne tienne
pas ; la franchise du modèle y est reprise telle quelle (ADR-É4).
Après création : inscription dans les projets récents (ADR-049) et
ouverture de l'atelier **sur le fichier d'entrée** du modèle
(extra `joide.fichier` de `EcranIde` — jamais un atelier vide).

## Conséquences

- Le kit gagne deux composants : `Icone` (glyphes dessinés au
  canvas — recherche, ⋮, chevrons, coche, alerte) et `Segment`
  (options exclusives, minSdk) avec leurs choix purs
  (`ChoixSegment`) ; `Carte.setTonale` porte l'accentuation
  (reprise de l'accueil, modèle choisi de l'assistant).
- La validation au fil de la saisie vit dans `EtatCreation` (pur) :
  le bouton Continuer reflète la validité de l'étape courante, le
  paquet suit le nom tant que l'utilisateur ne l'a pas touché,
  l'ordre des modules choisis suit le modèle (joide.toml
  déterministe, bascules après bascules).
- Backlog (ajouté) : câbler l'exécution des mains/tests sur
  l'appareil (bouton Exécuter → `JvmRunTask`, protocole du démon) ;
  constituer la spec Android depuis un workspace natif côté bureau
  (l'outil qui manque à la promesse complète de la carte Android) ;
  le relais reste l'export Gradle P7.
- Un modèle v3 s'ajoute sans toucher l'assistant ni cette ADR :
  seule une rupture de `joide.template/1` en écrirait une autre.
