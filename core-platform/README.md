# core-platform

Socle transversal : hachage, racines de chemins nommées, types de résultat et de problème, annulation coopérative, horloge, pools de fils, écritures atomiques.

| | |
|---|---|
| Paquet | `jo.joide.platform` |
| Nature | JVM pure |
| Dépendances JoIDE | aucune |

## Contenu

- `Hashing` / `Hash128` : empreinte rapide 128 bits (MurmurHash3, x64, vecteurs de référence) pour la détection de changement ; SHA-256 pour la confiance.
- `RootName` / `RootedPath` / `RootSet` : chemins toujours relatifs à une racine nommée (`$PROJECT`, `$SDK`, `$M2`, `$OUT`) ; aucune empreinte ne contient de chemin absolu.
- `Problem` / `Severity` / `Result` : anomalies rapportées sans exception, code court stable et localisation.
- `CancelToken` : annulation coopérative (état, contrôle, écouteurs à exécution unique).
- `Clock` : horloge injectée (nanotime monotone, mur).
- `WorkerPools` : pools de fils démons nommés, jamais créés par le code de production.
- `AtomicFiles` : écriture temporaire + renommage atomique + vidage sur disque.

## Invariants

Aucune dépendance vers un autre module JoIDE ; API 28 stricte (contrôlée à la compilation) ; identifiants anglais ASCII, Javadoc en français.

## Ce qui n'y est pas

Aucune journalisation spécifique (java.util.logging utilisé directement par les modules) ; aucun utilitaire d'entrées-sorties générales.
