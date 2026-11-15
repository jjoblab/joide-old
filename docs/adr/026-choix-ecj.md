# ADR-026 — Compilateur Java piloté : ecj (EPL-2.0), pas javac

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

§6.4 demande de choisir entre ecj et javac par ADR documenté (le tableau des
arrivages de `docs/toolchain.md` gardait la ligne ouverte). Contraintes :
compilation **en processus** sur JVM de bureau et sur ART (niveau 28),
diagnostics structurés (code, ligne, colonne) par API — jamais par analyse de
texte (§6.3), version de l'outil lue dans l'artefact réellement chargé (§4.2).

## Décision

Piloter **ecj** (`org.eclipse.jdt:ecj` 3.40.0, catalogue `libs.versions.toml`)
par son API de compilation par lots (`org.eclipse.jdt.internal.compiler.batch`
— paquet public documenté du jar). `EcjCompilerDriver` :

- compile en processus, capture exacte des classes écrites (le graphe de
  classes repose sur une lecture de fichiers, pas sur des suppositions) ;
- extrait les diagnostics de l'API de problèmes (code numérique, ligne,
  colonne) — jamais du texte ;
- lit la version dans le manifeste du jar réellement chargé (repli documenté
  si les classes sont exposées hors jar) ;
- résout le module système `jrt` du JDK courant sans `javax.tools`.

`javax.tools.JavaCompiler` (javac) est **écarté** : l'API n'existe pas au
niveau 28 (aucun `javax.tools` sur ART) et lie le pilote à la version du JDK
hôte. ecj est le même artefact que le patcheur `:ecj-art` de `code-lsp`
(EPL-2.0) : sur Android, l'artefact patché remplace le jar d'origine sans
changer une ligne du pilote.

## Options

| Option | Verdict | Raison |
|---|---|---|
| javac via javax.tools | écartée | absent du niveau 28 ; version liée au JDK hôte ; diagnostics corrects mais l'API disparaît sur ART |
| ecj par lots | retenue | en processus, diagnostics structurés par API, artefact unique JVM/ART, précédent `:ecj-art` du propriétaire |
| Les deux derrière un SPI | écartée | double pilote pour aucun besoin mesuré ; le pilote est déjà derrière `CompilerDriver` si le besoin surgit |

## Conséquences

- EPL-2.0 en dépendance séparée : obligations consignées dans `NOTICE` et
  `docs/third-party.md` (§4.1).
- La version d'ecj est figée dans le catalogue ; la version effective de
  l'outil est enregistrée par build dans la spécification de tâche (`spec()`),
  tout écart force la recompilation.
- La qualité des diagnostics Java dépend d'ecj ; l'usage interne du paquet
  `batch` est documenté comme API publique du jar ecj (stable depuis 3.x) —
  vérifiée à l'arrivage (§4.2), réévaluée à chaque montée de version.
