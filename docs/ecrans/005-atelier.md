# ADR-É5 — Atelier d'édition (écran principal, adaptatif)

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/atelier.html`

## Contexte

L'atelier P8 existe mais tout est à la suite dans un empilement :
boutons Fichiers/Aperçu/Construire en rangée, un seul fichier ouvert
via un dialogue de liste, aperçu en volet booléen, console en zone
exclusive de l'éditeur. CodeAssist (ADR-É1, leçon 4) apporte la structure
adaptative manquante : seuil 720 dp, tiroir poussé sur téléphone,
dock de console, pilule de build en tâche de fond.

## Décision

L'atelier est l'écran de travail : un projet y est monté (moteurs,
sync, éditeur celeris, aperçu LayoutLib — les lots B–E de P8 restent
le moteur ; seul l'habillage change). Adaptatif au seuil **720 dp**
de largeur utile.

### Structure (téléphone, en dessous de 720 dp)

1. **Barre supérieure** : retour (accueil), nom du projet + puce
   « n modules » (tonale), pilule d'état de l'index (phases et totaux
   de l'état CIDX, tap → détail honnête), ⋮ (paramètres, fermer le
   projet avec confirmation — fichiers modifiés comptés, jamais une
   perte silencieuse).
2. **Barre d'onglets** : fichiers ouverts, indicateur accent 2 dp sur
   l'onglet actif, point de modification par onglet, fermeture par
   ×. Multi-fichiers : l'état est faible par choix — le texte vit sur
   le disque (écriture fusionnée, le dernier gagne, lot F) ; changer
   d'onglet rouvre la session celeris du fichier (le coût est le
   rebranchement, pas une reconstruction de moteur).
3. **Éditeur** : vue celeris pleine largeur dans le `puits`
   (`#0B0E12`), numéros de ligne, thème de syntaxe Graphite (ADR-É2).
4. **Tiroir arborescence** (poussé depuis la gauche, 360 dp max) :
   arbre `FichiersProjet`, « Nouveau » (fichier/dossier) en tête,
   entrée active surlignée, **sorties de build estompées** pendant
   qu'elles se construisent (franchise CodeAssist leçon 5), jamais un
   nœud `build-joide` présenté comme source.
5. **Dock de console** (bas, repliable) : poignée « Console — état »
   + chevron ; l'aperçu plein écran et la console sont des panneaux
   basculants, jamais des zones qui se masquent mutuellement comme en
   P8.
6. **FAB Construire** (`reussite`) : lance le build (service `:build`
   par défaut, mode en processus dans les paramètres) ; pendant le
   build le FAB devient pilule d'état « build #n — phase » qui ouvre
   la console ; la mort du service est un événement de flux (« Build
   interrompu : l'IDE survit », existant lot F) — jamais un état
   zombie.

### Grand écran (≥ 720 dp)

Rail léger gauche (icônes : fichiers, console), arborescence amarrée
(240 dp), éditeur ; **aperçu côte à côte** pour le fichier de mise en
page actif (volet droit 320 dp), console amarrée en bas. Le téléphone
bascule l'aperçu en plein écran avec retour.

### Retour système

Ferme d'abord les surcouches dans l'ordre : dialogue ouvert →
palette → console → tiroir → aperçu plein écran → confirmation de
fermeture du projet → accueil. Le dernier état ne saute jamais
d'étape (leçon 4).

## États et franchise

- Projet natif monté : barre supérieure exacte (« 3 modules », index
  PRÊT/PARTIEL avec totaux) ; Gradle : bannière de compatibilité en
  tête d'atelier (mode P7/P8 : import statique hors application,
  échappatoire export), jamais un montage déguisé.
- Écarts de build affichés dans la console AVANT le lancement
  (existant lot F, conservé) ; « Rien à construire » reste un
  refus motivé.
- Onglet de mise en page actif → l'aperçu suit la frappe (dernier
  rendu valide conservé pendant la frappe — contrat lot E) ; les
  avertissements de l'aperçu sont portés par la bannière du volet
  (ADR-É7).

## Conséquences

- L'état d'atelier (onglets, fichier actif, volets ouverts, phase de
  build) est une classe pure testable JVM ; les vues minces lisent
  l'état — la doctrine P8 est reconduite.
- La barre d'onglets multi-fichiers est le seul changement de modèle
  vs P8 (un fichier ouvert) : l'écriture fusionnée et le rebranchement
  celeris existent déjà (lot D/F), l'UI les expose.
- Implémentation en lots futurs ; la maquette montre les trois vues
  (édition, tiroir ouvert, grand écran).
