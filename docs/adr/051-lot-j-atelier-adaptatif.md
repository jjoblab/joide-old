# ADR-051 — Lot J : l'atelier adaptatif — onglets à état faible, surcouches, pilule de build

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot J (ADR-É10) livre l'atelier adaptatif (ADR-É5) qui remplace
l'empilement programmatique de P8. Le design fait autorité sur la
structure (barre supérieure exacte, tiroir, dock, FAB, seuil 720 dp,
ordre de fermeture du retour) ; cette ADR consigne les décisions
techniques que le design ne tranchait pas : la forme exacte de l'état
d'onglets, le plafond de la barre, l'écriture disque multi-fichiers,
et la nomination de la machine à états des surcouches. Un lot ne
casse jamais ce qui existe : les montages des lots B-F (sync, moteurs,
éditeur celeris, aperçu, build service) sont repris tels quels — seul
l'habillage change.

## Décision

### 1. Onglets à état faible, plafond 10, éviction honnête

`EtatAtelier` (pur, package `jo.joide.app.android.atelier`) porte les
onglets : liste dans l'ordre d'ouverture, fichier actif, ensemble des
fichiers **marqués modifiés**. L'état est faible par choix (ADR-É5) :
le texte vit sur le disque, changer d'onglet rouvre la session celeris
(le coût est le rebranchement de popups, pas une reconstruction de
moteur — le lot D l'a prouvé).

- Le **point de modification** marque un fichier édité depuis
  l'ouverture de son onglet. Tout est déjà écrit sur le disque
  (écriture fusionnée, point 3) : il n'existe jamais de perte
  possible ; le point compte les fichiers touchés de la session, la
  confirmation de fermeture le dit en toutes lettres.
- **Plafond 10 onglets** (`ONGLETS_MAX`). Au-delà, le plus ancien
  onglet NON modifié est **évincé** — son texte est sur le disque,
  le fichier reste dans l'arborescence : rien n'est perdu, et
  l'éviction est retournée à l'appelant (l'écran peut la montrer).
  Si tous les onglets sont modifiés, l'ouverture est **refusée avec
  sa raison** (`RaisonRefus.TOUT_MODIFIE`) — jamais une perte
  silencieuse, jamais une éviction forcée.

### 2. La machine à états des surcouches et le retour système

`EtatAtelier.surRetour()` rend UNE action (`ActionRetour`) :
`FERMER_CONSOLE` → `FERMER_TIROIR` (écran compact seulement :
l'arborescence amarrée en grand écran est un réglage du rail, pas une
surcouche) → `FERMER_APERCU` (plein écran) → `DEMANDER_FERMETURE` →
`QUITTER` (aucun projet monté). Le dialogue ouvert est absorbé par le
cadre avant l'activité ; la complétion celeris se ferme d'elle-même au
déplacement du caret. L'écran déroule l'action — jamais deux choses à
la fois, jamais un état zombie.

### 3. Écriture disque fusionnée multi-fichiers

La doctrine du lot F (l'écriture fusionnée, le dernier gagne) est
étendue aux onglets par `EcrituresFusionnees` (pur) : chaque frappe
pose `(fichier, texte)` dans une carte concurrente ; le fil écrivain
draine les paires et écrit chaque fichier avec **son** dernier texte.
Le retrait est conditionné à la valeur lue (`remove(cle, valeur)`) :
une pose concomitante repart au tour suivant — aucune perte, au pire
un tour de retard. La sérialisation fil UI (frappes) / fil écrivain
(drains) garantit qu'un texte n'est jamais écrit dans le mauvais
fichier.

### 4. Le tiroir arborescence et les nouveaux fichiers

`Arborescence` (pur) compose l'arbre visible depuis la liste plate de
`FichiersProjet.lister` : dossiers avant fichiers à niveau égal,
entrée active surlignée, et le nœud `build-joide` en fin de racine —
**présent mais non dépliable, marqué sorties**, estompé pendant le
build, avec sa raison affichée (« jamais présentées comme sources »,
leçon 5 de l'ADR-É1). L'affichage du tiroir est plafonné à 400 lignes
visibles (`VueArbre.PLAFOND_NOEUDS`) avec une note qui l'annonce — un
plafond n'est pas un mensonge.

« Nouveau » (fichier/dossier) crée dans le **dossier du fichier
actif** (la racine à défaut), cible AFFICHÉE dans le dialogue ; le nom
est validé par `NouveauNom` (pur) : non vide, 100 caractères au plus,
aucun séparateur de chemin (jamais un chemin déguisé), pas `.` ni
`..`, aucun caractère de contrôle, pas de point final. Un refus ne
crée jamais rien sur le disque.

### 5. La pilule de build : lancement, état et arrêt

Le FAB Construire (rond `reussite`, glyphe lecture) devient **pilule
d'état** pendant le build : « build #n — phase », tap → la console,
croix intégrée → **arrêt** (`DaemonClient.cancel` ; `Frames.write` est
synchronisée, l'annulation depuis le fil UI pendant la lecture du fil
de fond est sûre — le critère « le build se lance et s'arrête depuis
la pilule » est satisfait sans toucher au démon). La mort du service
reste un événement de flux (« interrompu », lot F) ; le réglage « en
processus » du lot F vit transitoirement dans le menu ⋮ de l'atelier
— le lot M (ADR-É8) l'emménagera dans Paramètres.

### 6. L'aperçu dans l'atelier

Grand écran : volet droit 320 dp automatique quand l'onglet actif est
une mise en page, fermable, reproposé ; téléphone : bouton dans la
barre d'onglets, plein écran avec retour. Le détail du volet est le
lot L (ADR-053).

## Conséquences

- `EcranIde` (P8) est **remplacé** par `ecrans/atelier/EcranAtelier`
  (mêmes extras : racine, bannière Gradle, fichier d'entrée) ;
  `MainActivity` et `EcranCreation` y sont rebranchés. La bannière de
  compatibilité Gradle reste un affichage sans montage (mode P7/P8).
- Tests JVM : `EtatAtelierTest` (15), `ArborescenceTest` (7),
  `NouveauNomTest` (2), `EcrituresFusionneesTest` (4),
  `MaquetteAtelierTest` (6) — la « capture » suit la méthode ADR-048
  (le test lit la maquette et prouve que l'état la reproduit).
- L'index CIDX se raconte dans la pilule de la barre supérieure (phase
  + totaux au tap) — l'état honnête du lot C, sans polling.
- Les chaînes de l'atelier vivent en parité `values`/`values-fr`
  (ADR-048) ; les chaînes mortes de l'écran P8 sont retirées.
