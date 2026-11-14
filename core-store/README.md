# core-store

Store de build derrière l'interface `BuildStore` : empreintes de tâches, snapshot de fichiers, durées historiques, état incrémental des compilateurs, index du cache de sorties.

| | |
|---|---|
| Paquet | `jo.joide.store` |
| Nature | JVM pure (principal) + implémentation de bureau (source set `jvm`) |
| Dépendances JoIDE | core-platform |
| Dépendances tierces | sqlite-jdbc 3.49.1.0 (source set `jvm` uniquement) |

## Contenu

- `BuildStore` : le contrat — snapshot par racine, empreintes, durées, état incrémental, cache de sorties avec éviction « le moins récemment utilisé », transactions tout-ou-rien.
- `SqliteStores` / `SqliteBuildStore` (source set `jvm`) : implémentation SQLite en mode journal écriture d'avance, migrations versionnées, contrôle d'intégrité à l'ouverture, reconstruction automatique après corruption.
- `FileStamp` : l'état d'un fichier dans le snapshot (chemin, taille, date, clé, empreinte paresseuse).

## Invariants

Le store est un cache : toute corruption se reconstruit à vide, jamais le build n'échoue pour cela ; transactions courtes et atomiques (une interruption du processus laisse l'état de la dernière transaction validée) ; le store est confiné au fil qui l'a ouvert ; l'interface du source set principal ne référence aucune API JDBC ni Android.

## Ce qui n'y est pas

L'implémentation Android (base système du démon) ; le bench de l'alternative « journal append-only » (décision par mesure à venir).
