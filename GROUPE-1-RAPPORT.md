# GROUPE 1 DE LA V2 — RAPPORT (lots G, H, I)

- **Date** : 2026-11-16
- **Périmètre** : le groupe 1 du plan ADR-É10 — lot G (système de
  design Graphite, livré en premier), lot H (accueil « Mes projets »,
  ADR-É3/049), lot I (création de projet, ADR-É4/050).
- **Ce que le propriétaire peut tester** (le jalon « v2 alpha » du
  plan) : l'app s'ouvre sur un accueil moderne, crée un projet,
  l'importe en second.

## 1. Ce qui est fait

### Lot G — le système de design (livré avant ce rapport, ADR-048)

Jetons sombre/clair, kit de composants en vues du cadre sans
AndroidX, écran d'exercice (retiré au lot H comme prévu), parité
`values`/`values-fr` activée. Voir le CHANGES du lot G.

### Lot H — l'accueil « Mes projets »

`MainActivity` est désormais le hub de projets (ADR-É3), écran de
lancement — il ne monte aucun moteur, il est instantané. Barre
d'application (titre 28, salutation datée — Bonjour/Bonsoir selon
l'heure, date localisée), carte « Reprendre » tonale (le plus
récemment ouvert, **fusionnée** avec la première carte : jamais de
doublon, section « Projets (n) » au compteur exact), « Nouveau
projet » primaire + « Importer » tonal côte à côte, une seule ligne
de méta par carte (type · n modules · âge relatif, ou la franchise
Gradle), ⋮ par carte (renommer l'affichage ; supprimer avec ses
DEUX conséquences nommées — retirer de la liste / effacer du
stockage), recherche filtrante (la reprise s'efface pendant la
recherche, le compteur passe à « k/n »), état vide de première
classe (encadré pointillé, deux actions). Persistance : JSON
atomique versionné `projets-recents.json` (ADR-049) ; classification
des cartes sur le modèle déclaratif (`ClassifieurProjet`). L'écran
IDE de P8 déménage dans `ecrans.ide.EcranIde` (racine, bannière
Gradle et fichier d'entrée par extras) sans changement de logique.

### Lot I — la création de projet

`ecrans.creation.EcranCreation` : l'assistant en trois étapes,
progression toujours visible, retour système qui remonte d'une étape
(étape 1 = quitter, rien n'est créé). Étape 1 : galerie des quatre
modèles déclaratifs TOML `joide.template/1` d'`assets/modeles/`
(ADR-050) — recherche, puces de catégorie aux comptes exacts
(Tous 4 / Applications 1 / Bibliothèques 2 / Vide 1), franchise SUR
la carte, Continuer inactif sans sélection. Étape 2 : formulaire
piloté par le modèle — paquet (monospace) et segment minSdk 28/30
seulement pour les modèles Android, puces de modules basculables
avec refus motivé (requis par :x), paquet dérivé tant que non
touché, erreurs en ligne vivantes, le bouton reflète la validité.
Étape 3 : fiche en lignes, **arborescence exacte** (la liste des
fichiers que la génération produira — comptée par test), franchise
reprise telle quelle, « Créer le projet ». La création écrit tout ou
rien (temporaire voisin puis renommage atomique, chemins hors
projet refusés — zip-slip ADR-032), inscrit le projet dans les
récents et ouvre l'atelier **sur le fichier d'entrée** du modèle.

## 2. Critères d'acceptation, un par un

| Critère (plan ADR-É10) | Preuve |
|---|---|
| H : écran d'accueil remplaçant l'ouverture P8 | `MainActivity` réécrit (lanceur) ; l'ouverture SAF vit dans l'action « Importer » |
| H : enregistrement des ouvertures | `ProjetsRecents.ouvrir` + persistance à chaque ouverture ; test `ouvrirMetAJourPuisTriDecroissant` |
| H : détection native/Gradle/inconnu réutilisée | `ProjectDetector` (lot A) : import NATIF/GRADLE, refus nommé de l'inconnu |
| H : capture contre `design/accueil-projets.html` (vide/peuplé) | méthode ADR-048 : `MaquetteAccueilTest` (5 tests — états, fusion, compteur, tuiles, une ligne de méta par carte) ; validation visuelle par le propriétaire via l'APK du CI (pas d'émulateur au bac à sable) |
| H : tests JVM de l'état d'écran (tri, reprise, suppression) | `EtatAccueilTest` (11), `ProjetsRecentsTest` (14 : retrait, renommage, tolérance JSON), `ClassifieurProjetTest` (6) |
| H : ADR-049 (lieu et format des projets récents) | `docs/adr/049-lot-h-projets-recents.md` |
| I : les premiers modèles JoIDE (liste figée en cours de lot) | 4 modèles : Application Android, Console JVM, Bibliothèque JVM, Vide — `assets/modeles/*.toml` (ADR-050 § 4, écarts consignés) |
| I : l'écran en trois étapes | `EcranCreation` + `EtatCreation` (machine à états testée) |
| I : capture contre `design/creation-projet.html` (trois étapes) | méthode ADR-048 : `MaquetteCreationTest` (5 tests — étapes, comptes des catégories, formulaire piloté, fiche, bouton) |
| I : tests JVM (dérivation du paquet, valeurs par défaut, échec atomique) | `GenerateurProjetTest` (8 : paquet, défauts, substitutions, aller-retour disque→lecteur, refus d'identité invalide), `EcrireProjetTest` (5 : atomique, existant refusé, zip-slip, déterminisme), `EtatCreationTest` (9) |
| I : ADR-050 (format déclaratif des modèles) | `docs/adr/050-lot-i-modeles-de-projet.md` |
| Tous : check vert sans SDK **et** avec SDK | `./gradlew check` vert dans les deux modes (JOIDE_SKIP_ANDROID=1) ; lint NewApi bloquant sans baseline — 4 appels hors niveau 28 refusés puis remplacés |
| Tous : tests purs JVM | 118 tests app-android, 0 échec (71 nouveaux) |
| Tous : ADR des décisions nouvelles | ADR-049, ADR-050 (+ ADR-048 au lot G) |
| Tous : CHANGES, backlog, politique de langue verte, commit poussé | CHANGES (entrée groupe 1), backlog (4 entrées V2), script de langue 0 problème, commit ci-joint |

## 3. Détail des tests (app-android : 118 cas, 0 échec)

| Classe | Cas | Ce qui est prouvé |
|---|---|---|
| ProjetsRecentsTest | 14 | aller-retour JSON, échappement, tolérance signalée (entrée invalide sautée, doublon, schéma inconnu), écriture atomique sur disque, ouvrir/renommer/retirer, assainissement et unicité |
| EtatAccueilTest | 11 | tri décroissant, fusion de la reprise (1 projet = pas de section), compteur exact, filtre (« 1/2 », aucun résultat ≠ état vide), âges par seuils, futur jamais négatif, salutation, tuiles |
| ClassifieurProjetTest | 6 | Gradle prioritaire, android > console, facette application, bibliothèque, vide, comptes |
| MaquetteAccueilTest | 5 | la maquette lue par le test : deux états, structure reproduite par l'état pur, quatre tuiles + gris Gradle, une seule ligne de méta par carte |
| ModeleProjetTest | 7 | les 4 assets réels exploitables, franchise/note obligatoires, fichier d'entrée, refus motivés (schéma, clé inconnue, doublon) |
| GenerateurProjetTest | 8 | workspace Android relu sans erreur, substitutions ({paquet-chemin}…), retrait d'un module retire ses fichiers, facette application écrite, Vide = le seul joide.toml, validations, paquet par défaut |
| EcrireProjetTest | 5 | déplacement atomique, dossier existant refusé, zip-slip refusé, échec sans résidu, déterminisme (deux écritures identiques) |
| EtatCreationTest | 9 | progression et retour, défauts uniques, formulaire piloté, refus de bascule nommé, filtre et catégories, récapitulatif exact (une ligne par fichier), minSdk borné |
| MaquetteCreationTest | 5 | la maquette lue par le test : trois étapes, quatre modèles et comptes, segment 28/30 (24 écarté), fiche et arborescence, rien avant le bouton |
| Précédents (lot G et P8) | 47 | inchangés, toujours verts |

## 4. Écarts par rapport aux ADR-É3/É4 et aux maquettes

1. **Exécution et tests sur l'appareil** (tableau ADR-É4) : les mains
   et tests se **compilent** sur l'appareil (racines test au rôle
   source, jars résolus au classpath) mais ne s'y **exécutent** pas —
   aucun bouton Exécuter dans l'UI P8, le protocole du démon ne sert
   pas `JvmRunTask`. Les cartes des modèles ne le prétendent pas
   (« constructible sur l'appareil ») ; backlog : câbler l'exécution.
2. **« L'APK se construit au bureau (P8) »** (tableau ADR-É4) : la
   chaîne P5 est prouvée sur specs forgées, mais aucun outil
   utilisateur ne constitue une spec Android depuis un workspace
   natif. La carte livrée dit « l'APK exige la chaîne de bureau
   (câblage à venir) » ; backlog : l'outil bureau.
3. **minSdk 24** (maquette étape 2) : écarté du segment — ADR-009
   (Java 17 ⇒ minSdk 28), la désucation en dessous n'est pas prouvée
   par le pipeline. Le segment propose 28 et 30.
4. **Bouton « Créer le projet » vert** (maquette étape 3) : l'ADR-É4
   dit « (accent) » — le kit suit l'ADR, pas la maquette.
5. **« + Ajouter »** (maquette étape 2) : les puces de modules
   basculent directement (tous les modules du modèle affichés) ; le
   sélecteur d'ajout attend les modèles v3 à nombreux modules
   optionnels.
6. **Menu ⋮ de l'accueil** (ADR-É3 : à propos, paramètres, journal) :
   seul « À propos » existe — les paramètres arrivent au lot M, le
   journal est au backlog ; jamais d'entrée morte.
7. **Capture sur appareil** : pas d'émulateur au bac à sable (écart
   P8 n° 4, reconduit) — conformité prouvée par tests lisant les
   maquettes (méthode ADR-048), validation visuelle par le
   propriétaire via l'APK du CI.

## 5. Mesures

- APK debug assemblé : **92 Mo, 30 dex**, les quatre modèles dans
  `assets/modeles/`, toutes les classes des lots H/I vérifiées
  présentes dans les dex (`:app-android:assembleDebug`).
- Check complet : vert avec SDK et sans SDK (`JOIDE_SKIP_ANDROID=1`).
- Politique de langue : 0 problème (parité `values`/`values-fr` —
  113 clés environ par langue, identifiants ASCII, messages
  d'exception anglais).
- Lint : 0 erreur (4 NewApi interceptés et corrigés :
  `InputStream.readAllBytes`, `Files.readString`, `Files.writeString`,
  `Stream.toList` — remplacés par les équivalents niveau 28).

## 6. Dette et risques ouverts

- Backlog groupe 1 (nouvelles entrées) : exécution des mains/tests
  sur l'appareil ; outil bureau spec Android depuis un workspace
  natif ; journal dans l'accueil/paramètres ; import d'un projet
  inconnu comme départ Vide.
- L'accueil recharge la liste à chaque `onResume` (fichier petit,
  lecture disque unique : mesuré négligeable ; à revoir si la liste
  dépasse quelques centaines d'entrées).
- La classification console repose sur la facette `application`
  (convention ADR-049/050) : un projet JVM importé sans facette est
  classé bibliothèque — c'est dit, pas deviné.

## 7. Questions au propriétaire

Aucune décision bloquante. Pour information :

- D-8 (rythme par groupe) : le groupe 2 (J atelier, K console,
  L aperçu) démarre au GO du propriétaire sur ce rapport.
- Les libellés de franchise des cartes (écarts n° 1 et 2) sont dans
  les TOML des modèles : un ajustement de wording ne touche aucun
  code.

## 8. Arrêt pour validation

Conformément au plan (D-8 : validation par groupe), le groupe 1
s'arrête ici. L'APK du CI (debug et release signés, workflow « APK
signés ») permet de tester : ouverture sur l'accueil, création
depuis chaque modèle, import d'un dossier, reprise, renommage, les
deux suppressions, la recherche — dans les deux langues, clair et
sombre.
