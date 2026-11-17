# core-vfs

Snapshot persistant de l'arbre de fichiers, journal de changements fusionné (éditeur, surveilleur, rattrapage) et empreintes d'arbre par dossier.

| | |
|---|---|
| Paquet | `jo.joide.vfs` |
| Nature | JVM pure |
| Dépendances JoIDE | core-platform, core-store |

## Contenu

- `TreeScanner` : parcours parallèle de statuts (sans lecture de contenu), ordre déterministe, liens symboliques non suivis, annulation coopérative.
- `VfsSnapshot` : instantané immuable (états par fichier + empreinte d'arbre par dossier), diff, mises à jour par delta.
- `MerkleTrees` : empreintes d'arbre par dossier, recalcul limité aux dossiers touchés et à leurs ancêtres.
- `StableHasher` : empreinte de contenu avec protection contre les fichiers modifiés pendant la lecture (stat avant/après, relectures bornées).
- `SnapshotUpdater` : rafraîchissement d'un instantané par parcours, empreintes paresseuses (`ensureHashed`), persistance transactionnelle dans le store.
- `ChangeLog` : journal fusionné éditeur + surveilleur, file bornée, débordement ⇒ re-scan exigé, vidage en ensemble de fichiers sales (ensemble vide en temps constant sur démon chaud).
- `TreeWatcher` / `NioTreeWatcher` : surveillance récursive bornée à un budget de dossiers, débordements et budgets signalés, jamais dépassés.

## Invariants

Chemins toujours relatifs à une racine nommée ; empreinte de contenu paresseuse, calculée seulement quand les signaux (taille, date, clé) changent ; sur débordement, sur-invalider (re-scan) plutôt que sous-invalider ; aucun résultat partiel silencieux (erreur de lecture = échec explicite).

## Ce qui n'y est pas

L'implémentation Android du surveilleur (observateur de fichiers système, avec le démon) ; la fusion multi-racines à l'échelle d'un espace de travail complet.
