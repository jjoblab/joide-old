# ADR-029 — Ordre des versions : spécification maison documentée

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Le résolveur doit ordonner des versions Maven pour arbitrer les
conflits (« la plus récente gagne »), choisir dans les plages et
filtrer `latest.release`. L'ordre canonique de Maven
(`ComparableVersion`) n'est pas publiable tel quel dans un dépôt
Apache-2.0 clean-room sans consultation consignée (§4.1) ; une
spécification propre, plus simple et **testée cas par cas**, suffit
aux usages réels des dépôts.

## Décision

`jo.joide.deps.Version`, ordre documenté dans la Javadoc et vérifié
par `VersionTest` :

1. Tokenisation sur `.` et `-` ; un token tout chiffres est numérique,
   sinon c'est un qualificatif (insensible à la casse).
2. Comparaison d'index en index :
   - numérique contre numérique : en valeur ;
   - qualificatif contre qualificatif : rang des qualificatifs connus
     — `a/alpha` < `b/beta` < `m/milestone` < `rc/cr` < `snapshot` <
     `ga/final/''` (publication) < `sp` ; un **inconnu** est au-dessus
     de `sp` et se compare lexicographiquement entre inconnus ;
   - numérique contre qualificatif : le numérique gagne
     (`1.0.1` > `1.0-alpha`) ;
   - fin de liste contre qualificatif : vaut la publication (donc
     `1.0-alpha` < `1.0`, et `1.0-zebra` > `1.0` par la règle de
     l'inconnu).
3. Égalité : la fin de liste vaut numériquement 0 (`1.0` égale
   `1.0.0`) et la publication vaut `ga/final` — les hachages
   réduisent les zéros de fin en conséquence.
4. L'instantané **littéral** est la forme finissant par `-SNAPSHOT`
   (sans casse) ; un instantané **concret** (`1.0-20260115.142030-7`)
   n'est pas flaggé : son caractère d'instantané vient des
   métadonnées, et l'inconnu (rang au-dessus de `sp`) le place
   au-dessus de l'instantané local de même base — cohérent avec la
   pratique des dépôts.

Les plages (`[a,b]`, `[a,b)`, `(a,b]`, `(a,b)`, `[a]`, `[a,)`, `(,b]`,
`(,)`) et les exigences (`littéral`, `[plage]`, `1.0+`, `+`,
`latest.release`, `latest.integration`) suivent ; la forme `[a]`
pince les deux bornes ; `« (, »` bas ouvert est lu inclus.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Répliquer l'algorithme Maven (listes d'items, alias infinis) | écartée | complexe, clean-room discutable, aucun besoin mesuré au-delà des cas testés |
| Ne comparer que les numéros (ignorer les qualificatifs) | écartée | casserait `1.0-alpha` < `1.0` et l'arbitrage des prépublications |
| Spécification maison testée (retenue) | retenue | totale, décidable, documentée, 20 cas de test couvrant la série et les alias |

## Conséquences

- Un artefact aux versions exotiques (qualificatifs inconnus) est
  ordonné de façon déterministe ; l'écart éventuel avec l'ordre Maven
  est borné aux inconnus et visible dans le lockfile.
- `latest.integration` prend le maximum **toutes formes comprises** ;
  `latest.release` filtre les instantanés littéraux.
- L'escalade d'un doute se fait par test : tout cas non couvert ajoute
  un cas à `VersionTest` avant tout changement d'ordre.
