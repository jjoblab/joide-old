# ADR-É7 — Écran d'aperçu de mise en page

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/apercu.html`

## Contexte

L'aperçu P8 est un volet booléen dans l'empilement, sans contrôles
visibles, sans stats, sans état d'erreur présenté. Le moteur (lot E :
`ApercuLayouts`, LayoutLib derrière la façade) est complet : c'est
l'habillage qui manque. L'aperçu de mise en page est la signature
produit de JoIDE sur téléphone — il doit être spectaculaire et honnête.

## Décision

L'aperçu est un panneau de l'atelier, lié à l'onglet actif quand
c'est un fichier de mise en page (`estMiseEnPage`, lot F) :
côte à côte sur grand écran (≥ 720 dp, volet droit), plein écran
bascule sur téléphone avec retour.

### Structure

1. **Barre de volet** : « Aperçu — nom du fichier », actions :
   rafraîchir sans délai, basculer plein écran/côte à côte, fermer.
2. **Contrôles** (rangée de puces) : densité segmentée (1×, 2×, 3×),
   mode nuit (puce à bascule), dimensions cibles (360 × 640 par
   défaut) — les réglages réels du `ConfigurationApercu` du lot E,
   rien d'inventé.
3. **Surface de rendu** : cadre arrondi (24 dp) centré, le résultat
   LayoutLib tel quel — jamais retouché, jamais embellie : ce que la
   brique rend est ce que l'écran montre.
4. **Stats de rendu** (légende) : « n vues · durée ms · largeur ×
   hauteur » — les mesures du `EcouteurRendu` du lot E (onSucces
   donne vues, durée, dimensions) ; jamais inventées.

### Franchise (contrats du lot E portés à l'écran)

- **Pendant la frappe** : bannière info « XML en frappe — dernier
  rendu valide conservé » ; le volet ne devient jamais blanc ni
  faux.
- **Ressource manquante** : bannière alerte « @x manquante — repli
  aux ressources natives » ; le rendu continue avec le repli, la
  bannière reste tant que la ressource manque.
- **Classe inconnue** : « classe inconnue — MockView » ; un échec de
  rendu nominal, pas une erreur bloquante.
- **Échec de rendu** : bannière erreur nominale (message de
  `onErreur`), la surface garde le dernier rendu valide — jamais un
  panneau vide qui ment en paraissant vide.
- **Thème** : le nom de thème n'est jamais deviné (lot E) : si le
  modèle ne le fournit pas, la puce thème affiche « repli hôte » et
  le dit.

## États

Rendu nominal (stats + pas de bannière) ; frappe (info) ;
avertissement (alerte, rendu conservé) ; erreur (rouge, dernier rendu
conservé) ; pas de mise en page active — le volet est proposé
(bouton dans la barre d'onglets) mais absent, jamais une surface
figurative.

## Conséquences

- Aucune logique nouvelle : les bannières traduisent des états que
  `ApercuLayouts`/`ConfigurationApercu` exposent déjà ; l'UI reste
  mince.
- Le debouncé 400 ms du lot E est conservé tel quel ; « rendre sans
  délai » reste l'action manuelle explicite.
