# ADR-É3 — Écran d'accueil « Mes projets »

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/accueil-projets.html`

## Contexte

Aujourd'hui (P8) : un bouton « Ouvrir un projet » qui lance le
sélecteur SAF système, et rien d'autre. La plainte du propriétaire
porte exactement là : SAF dès l'ouverture, aucune création, « ultra
laide ». CodeAssist (ADR-É1, leçon 1) fait l'inverse : un hub de
projets avec la création au premier plan, l'import en action
secondaire visible.

## Décision

L'accueil est le hub de projets de JoIDE, écran de lancement de
l'application. Il ne monte aucun moteur (aucune résolution, aucun
index) : il est instantané et ne quitte jamais la barre
d'application.

Structure, de haut en bas :

1. **Barre d'application** : titre d'écran « Mes projets » (28),
   salutation datée en légende (« Bonjour — lundi 16 novembre »,
   pluriel au féminin le soir), actions recherche et ⋮ (à propos,
   paramètres, journal).
2. **Carte de reprise** : le projet le plus récemment ouvert
   (dernière ouverture > 0), tuile tonale de son type, « Reprendre »
   en légende, une ligne de méta. Taper ouvre l'atelier. Absente s'il
   n'existe aucune ouverture récente — jamais une carte factice.
3. **Actions de démarrage** : bouton primaire « Nouveau projet »
   (accent) et bouton tonal « Importer » côte à côte. L'import ouvre
   le sélecteur SAF : c'est le moyen secondaire d'entrer, présent
   sans creuser de feuille — la leçon `ImportCard` de CodeAssist.
4. **Liste des projets** : en-tête de section « Projets (n) », une
   carte par projet — tuile tonale par type (Android `reussite`,
   console `accent`, bibliothèque violet, vide gris), nom (sous-titre,
   une ligne, ellipsis), **une seule** ligne de méta « type · n
   modules · âge relatif » (leçon 2 : jamais deux actions visibles en
   concurrence), bouton ⋮ (renommer l'affichage, supprimer de la
   liste avec confirmation, jamais de suppression silencieuse). Tri
   par dernière ouverture décroissante.
5. **État vide (première ouverture)** : encadré en pointillés (rayon
   28), grande icône dossier, « Aucun projet pour l'instant », une
   phrase d'aide, puis les deux mêmes actions (créer primaire,
   importer tonal) — l'écran vide est un état de première classe,
   pas un vide. C'est la réponse directe à la plainte : créer
   devient possible dès la première seconde.

Persistance : la liste des projets (nom affiché, type, chemin privé,
dernière ouverture) vit dans un fichier privé de l'application —
classe pure testable JVM, jamais dans les vues. L'import SAF copie
vers le stockage privé (ADR-046) puis inscrit le projet dans la
liste ; les projets Gradle restent en mode compatibilité : la carte
porte la puce « Gradle — compatibilité » et l'ouverture affiche la
bannière d'écart, l'échappatoire reste l'export P7 — jamais de
déguisement.

## États et franchise

- Aucun projet, un projet, plusieurs (la reprise fusionne avec la
  première carte : pas de doublon).
- Suppression : confirmation nommée (« Supprimer "x" de la liste ? »)
  avec distinction claire retirer de la liste / effacer du stockage —
  deux actions distinctes, deux libellés distincts.
- Le compteur de section est exact (« Projets (3) »), jamais arrondi
  ni plafonné en silence.

## Conséquences

- L'écran ne dépend d'aucun moteur : il se rend avant toute
  résolution réseau ; son état pur est testable sans SDK.
- La navigation : Nouveau projet → assistant (ADR-É4) ; une carte ou
  la reprise → atelier (ADR-É5) ; ⋮ → paramètres (ADR-É8).
- Les maquettes montnent les deux états (vide, peuplé) côte à côte.
