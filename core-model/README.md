# core-model

Modèle de projet déclaratif : espace de travail, projets, modules, ensembles de sources, facettes, topologie et classpath déterministe.

| | |
|---|---|
| Paquet | `jo.joide.model` (+ `jo.joide.model.toml`) |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform, core-vfs |

## Contenu

- `Toml` / `TomlDocument` / `TomlValue` : analyseur TOML tolérant (sous-ensemble standard : tables, séries de tables, chaînes, entiers, booléens, tableaux) — jamais d'exception, chaque anomalie devient un problème localisé.
- `Workspace` / `Project` / `Module` / `SourceSet` / `ContentRoot` / `Facet` : hiérarchie immuable ; rôles et types de modules ouverts et extensibles ; chaque module porte un identifiant de contenu (empreinte de sa définition canonique, sans chemin absolu).
- `WorkspaceLoader` : chargement tolérant — un `joide-module.toml` corrompu est signalé, le reste charge ; clés inconnues en information, jamais de réécriture d'un fichier illisible.
- `ModuleWriter` / `WorkspaceRepository` / `WorkspaceEdit` : écriture canonique et transaction de sauvegarde multi-fichiers (temporaires puis renommages, restauration en cas d'échec), publication aux abonnés.
- `ModuleTopology` : niveaux topologiques, dépendances inversées, détection de cycle avec chemin reconstruit (Kahn + parcours en profondeur).
- `ClasspathBuilder` / `Classpath` / `ClasspathEntry` : assemblage déterministe (ordre par niveaux puis nom, déduplication, promotion de portée, règle de conflit « la plus récente gagne / strictement / échec »), identifiant de contenu stable.

## Schémas de fichiers

- `joide.toml` (schéma `joide.workspace/1`) : `schema`, `name`, `[android] min-sdk`, `[modules] include = [...]`.
- `joide-module.toml` (schéma `joide.module/1`) : `schema`, `name`, `type`, `[[sources]]` (nom + rôles → chemins), `[facets.<domaine>]`, `[[dependencies]]` (`path` ou `coordinates`, `scope`, `variants`).

## Invariants

Immuabilité stricte (les lecteurs voient des instantanés) ; chargement tolérant sans perte de diagnostic ; identifiants de contenu relocalisables (aucun chemin absolu) ; écritures multi-fichiers atomiques au mieux avec restauration ; ordre de sortie déterministe (tri par nom à chaque niveau).

## Ce qui n'y est pas

Les variantes par dimension autres que le nom (flavors complets) ; la résolution des coordonnées externes (dépendances, phase suivante) ; les projets multiples nommés (le schéma actuel regroupe les modules dans un projet implicite).
