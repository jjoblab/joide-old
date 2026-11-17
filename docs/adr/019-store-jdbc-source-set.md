# ADR-019 — Store de bureau : SQLite JDBC dans un source set séparé

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

La décision D8 fixe le store derrière l'interface `BuildStore` : SQLite en mode journal écriture d'avance, via JDBC sur la machine de bureau et via la base système Android sur appareil. Le contrôle API 28 de P0 (ADR-013) rejette toute référence à `java.sql` dans les classes du source set principal : l'API JDBC n'existe pas sur Android, où le même contrat sera implémenté avec `android.database.sqlite`.

## Décision

Le module `core-store` sépare ses source sets :

- **principal** (`src/main`) : l'interface `BuildStore`, les types d'échange (`FileStamp`, `StoreWork`, `StoreException`) — API 28 stricte, contrôlée à chaque build ;
- **`jvm`** (`src/jvm`) : l'implémentation de bureau `SqliteBuildStore` + la fabrique `SqliteStores` (pragmas WAL, migrations versionnées, contrôle d'intégrité, reconstruction après corruption), dépendant du pilote `org.xerial:sqlite-jdbc` 3.49.1.0 (Apache-2.0, SQLite embarqué du domaine public — consigné dans `NOTICE` et `docs/third-party.md`).

Le contrôle API 28 s'applique au source set principal ; le source set `jvm` n'est jamais dexé ni embarqué — l'implémentation Android du même contrat vivra avec le démon (processus `:build`), où la base système est disponible.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Source set `jvm` séparé (retenue) | retenue | respecte le contrôle API 28 sans l'affaiblir ; les modules restent au nombre fixé ; la frontière JVM/Android est explicite |
| Nouveau module `core-store-jvm` | écartée | fait passer le compte de modules ; la règle de dépendances et le graphe compliquent pour une simple frontière de source set |
| JDBC dans le source set principal, exempté | écartée | affaiblir le contrôle pour un module l'exposerait à des références Android interdites sans garde-fou |

## Conséquences

Les tests du store (unitaires et harnais de pannes) compilent contre les deux source sets. Une écriture future qui référencerait `java.sql` hors du source set `jvm` échouera au contrôle API 28. Le bench de l'alternative « journal append-only » (prévu par le prompt, §6.3) se fera derrière la même interface, par une deuxième implémentation dans le même source set.
