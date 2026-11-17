# ADR-037 — Package déterministe aligné, schémas de signature explicites, AAB par charge de module

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

L'APK est une archive zip dont la lecture efficace sur appareil exige
des entrées non compressées alignées (la table de ressources et les
dex sont mappés en mémoire, les bibliothèques natives alignées page).
La signature couvre des schémas d'époques différentes (JAR v1 pour
les plateformes avant 24, v2 depuis 24, v3 pour la rotation de clé).
Le bundle (AAB) est un format spécifique (protos + modules) que
bundletool assemble et valide.

## Décision

L'écriture zip est maison : en-têtes locaux écrits à la main pour
placer le remplissage d'alignement dans le champ « extra » (même
méthode que zipalign), entrées triées, horodatages fixes, compression
au niveau du JDK — deux exécutions aux mêmes entrées produisent les
mêmes octets, l'incrémental est comparable au propre. Les schémas de
signature sont explicites par variante (v1, v2, v3 demandés ou non) ;
un APK signé est toujours revérifié avant d'être déclaré bon — une
signature non vérifiable est un échec. La vérification se fait au
niveau minimum de la variante (les schémas v2+ n'y sont contrôlés
qu'au-dessus de 24 : une variante minSdk 28 est vérifiée à 28, une
variante qui exige v1 doit avoir un minSdk sous 24). L'AAB est
assemblé par bundletool depuis une charge de module écrite à la main
(manifeste proto et table issus du lien aapt2 --proto-format, dex,
ressources proto extraites) puis validé par la commande validate. La
fusion de manifestes de bibliothèques est hors périmètre P5 : le
manifeste de l'application est lié tel quel (voir dette du rapport).

## Options

| Option | Verdict | Raison |
|---|---|---|
| zipalign en processus externe (SDK) | écartée | exigence d'un SDK ; l'alignement est une écriture, pas un outil |
| Rééditeur zip du JDK + réalignement | écartée | pas de contrôle du champ extra : l'alignement exact impossible |
| Écriture maison alignée (retenue) | retenue | déterministe, sans dépendance, alignée |
| Signature toujours v1+v2+v3 | écartée | v1 inutile et non vérifiée au-dessus de 24 ; v3 impose la gestion de lignée |
| Schémas explicites + revérification (retenue) | retenue | chaque variante porte sa politique |

## Conséquences

Le package, la signature et le bundle sont reproductibles octet par
octet (prémisses du différentiel incrémental ≡ propre). La rotation
de clé v3 et la signature au niveau fichier (v4) restent au backlog.
