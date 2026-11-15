# ADR-028 — Parseurs de métadonnées maison (XML, JSON) : org.w3c.dom absent du niveau 28

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

P4 doit lire des POM et des maven-metadata.xml (XML), et des fichiers
« .module » de Gradle Module Metadata (JSON). La vérification
`api28-signatures` (ADR-013) balaie le pool de constantes de chaque
classe compilée : toute référence doit exister dans la liste des
signatures de `platform-28_r06`. Or `org.w3c.dom` n'y figure pas —
seuls `javax.xml.parsers` et `org.xml.transform` y sont, avec des
champs typés `org.w3c.dom.Node` : le DOM est donc **inutilisable**
(appeler `DocumentBuilder.parse` produirait des références de classes
rejetées). Aucune bibliothèque de tierce partie n'entre en jeu : le
catalogue n'a pas de dépendance XML/JSON et §10 n'en veut pas sans
ADR.

## Décision

Deux petits analyseurs déterministes et tolérants, sur le modèle du
parseur TOML maison de P1 (ADR-018) :

- **`jo.joide.deps.xml.Xml`** — éléments, attributs quotés, textes,
  CDATA, commentaires, instructions de traitement, cinq entités
  prédéfinies, références numériques décimales et hexadécimales ;
  plafond de nœuds ; les documents mal formés produisent une liste de
  problèmes (position + message) et, quand la racine est lisible,
  l'arbre partiel. Aucune résolution d'espace de noms : les préfixes
  restent dans les noms (POM et maven-metadata n'en usent pas).
- **`jo.joide.deps.json.Json`** — objet, tableau, chaîne (échappements
  complets dont `\uXXXX`), nombre (conservé en texte d'origine : les
  tailles et versions GMM ne demandent pas d'arithmétique), booléen,
  null ; tolère les virgules finales ; plafond de nœuds ; arrêt au
  premier déséquilibre avec problème explicite.

## Options

| Option | Verdict | Raison |
|---|---|---|
| DOM standard (`javax.xml.parsers`) | écartée | `org.w3c.dom` hors liste API 28 : le contrôle `checkApi28` refuse toute référence |
| Tierce partie (gson, jackson, jsoup) | écartée | catalogue sans dépendance XML/JSON ; coût d'obligations NOTICE sans bénéfice |
| Maison (retenue) | retenue | surface exacte des besoins (POM, maven-metadata, GMM), déterminisme, tolérance, aucun symbole hors niveau 28 |

## Conséquences

- La tolérance est symétrique à celle du TOML de P1 : un document
  dégradé dégrade la résolution (problèmes), jamais le build entier.
- L'entité inconnue est conservée telle quelle (jamais d'échec dur) ;
- Les analyses ne valident aucun schéma : un POM sans `<project>` est
  un problème, pas une exception.
- Le coût est borné : ~0,3 ms pour un POM de cent dépendances
  (mesuré sur le harnais de test) ; aucun équivalent DOM à maintenir
  sur ART.
