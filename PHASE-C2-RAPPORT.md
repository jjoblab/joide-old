# Rapport du lot C2 — Les tiroirs qui tuaient l'app : dimensions des zones différées, la feuille détache, le fournisseur IA depuis les Paramètres

**Base :** commit `68f72b9` (lot C1, le garde-crash).
**Origine :** le garde-crash a tenu sa promesse — la remontée du terrain
du 2026-11-19 09:55:35 porte un rapport `joide.crash/1` NOMMÉ :
`java.lang.UnsupportedOperationException: Binary XML file line #14 in
jo.joide.app:layout/tiroir_agent_pousse: You must supply a layout_width
attribute` (motorola moto g06, Android 15 / API 35, build `0.1.0
(68f72b9)`), pile `ViewStub.inflate → LayoutInflater.inflate →
FrameLayout.generateLayoutParams → TypedArray.getLayoutDimension`,
déclenchée au tap d'ouverture du tiroir agent — « plusieurs erreurs de
ce genre » : l'audit en trouve trois armées et une quatrième endormie.

## 1. Constats

- **CONFIRMÉ (la pile nommée par le garde)** :
  `tiroir_agent_pousse.xml` porte sa racine `<TiroirPousse>` et son
  enfant `<VueAgent>` SANS `layout_width`/`layout_height`. L'inflation
  par ViewStub appelle `LayoutInflater.inflate(parser, parent, false)`
  avec `parent = generateLayoutParams(attrs)` :
  `TypedArray.getLayoutDimension(index, name)` JETTE
  `UnsupportedOperationException` quand l'attribut manque (la variante
  à valeur par défaut ne passe jamais ici). « line #14 » = le chevron
  fermant de la balise racine du fichier d'alors.
- **CONFIRMÉ (l'audit exhaustif des 90 layouts)** : trois autres
  fichiers de zone portent le même défaut — `vue_console_zone.xml`
  (racine `VueConsole`, le `stub_console`),
  `vue_apercu_zone.xml` (racine `VueApercu`, le `stub_apercu`) ;
  `vue_arbre_zone.xml` (racine `VueArbre`) est gonflé SANS parent
  (non fatale aujourd'hui — le piège reste armé pour tout ViewStub
  futur). Les 35 autres signalements de l'audit sont des `<include>`
  légitimes (ils n'ont pas à porter de dimensions).
- **LA CHAÎNE DES TROIS SYMPTÔMES DU TERRAIN** :
  1. *tiroir agent* : le tap → `monterTiroirAgent()` →
     `stub_tiroir_agent.inflate()` → plantage (le rapport nommé).
  2. *« le bottomsheet n'affiche rien, que ce soit pour build,
     journal »* : la CONSOLE est la section PAR DÉFAUT de la feuille
     (`EtatFeuille`) — tout dépliage passe par `majCorps →
     surOuverture() → monterConsole() → stub_console.inflate()` → LE
     MÊME plantage. La feuille ne s'est JAMAIS ouverte sans mourir :
     « n'affiche rien » = l'app meurt avant le premier rendu du corps,
     les sections journal/logcat n'étaient jamais atteintes.
  3. *« la clé API n'est pas chargée, ni le provider »* : le SEUL
     sélecteur de fournisseur vivait dans la puce du tiroir agent
     (`ControleurModelesAgent`) — tiroir mort-né, sélecteur
     inaccessible : les requêtes partaient chez le fournisseur PAR
     DÉFAUT avec la clé posée pour un autre (« no key for
     anthropic », le fix du 2026-11-17 restait inatteignable). La
     SAISIE des clés est saine (audit C1 : `popups.saisie → poserCle →
     relire`, le coffre chiffre et relit, le repli d'amorce survit au
     redémarrage) — ce que le terrain nommait « pas sauvegardée » était
     le tiroir qui tuait l'app avant la première requête.
  4. **SECOND piège (nommé par l'audit, jamais atteint : le premier
     plantage le masquait)** : la console gonflée arrive ATTACHÉE à la
     racine (le ViewStub la pose à sa place — « z-plan bas »), puis
     `FeuilleBasse.majCorps → receptacle.addView(contenu)` jetterait
     `IllegalStateException: The specified child already has a parent`
     (AOSP `ViewGroup.addViewInner`). Le lot A8 a déjà son helper
     `detacher()` à l'écran pour exactement ce cas — la feuille ne
     l'avait pas.

## 2. Décision

- **Les dimensions PASSEPARTOUTS (pas des déclarations d'identité)** :
  `match_parent` sur les racines des quatre layouts de zone (et sur
  l'enfant `VueAgent` du tiroir). Ces dimensions ne DÉCIDENT rien — le
  ViewStub les remplace par les siennes (`replaceSelfWithView`), le
  panneau adopte l'enfant au MATCH_PARENT
  (`TiroirPousse.onFinishInflate`), la feuille repose son contenu
  (`majCorps`) : la géométrie reste celle du MONTAGE, l'identité reste
  celle de la zone. Le commentaire de chaque layout le consigne —
  le prochain déplacement ne ré-ouvrira pas le piège.
- **La feuille détache ELLE-MÊME** (`FeuilleBasse.majCorps`) : avant
  `addView`, `contenu.getParent() instanceof ViewGroup → removeView` —
  le même geste que `detacher()` de l'écran (lot A8), côté feuille.
  Couvre tout montage à venir (la console d'aujourd'hui, une zone
  demain) : la vue d'une section de feuille arrive de partout sans
  parent.
- **Le fournisseur IA POSABLE depuis les Paramètres** : la rangée
  « Fournisseur IA » (première de la carte IA, `item_param_action`)
  ouvre le MENU ANCRÉ du porte-popups (le gabarit du menu de clé, lot
  A7) avec les quatre fournisseurs — les mêmes noms que le tiroir
  (`agent_fournisseur_*`). Le choix PERSISTE
  (`Reglages.ecrireFournisseurIa`) et NORMALISE le modèle
  exactement comme la puce du tiroir (fix 2026-11-17) : le modèle posé
  pour lui, sinon le modèle partagé s'il lui appartient
  (`modeleAppartient`), sinon le premier de son catalogue (le MIROIR
  de `ControleurModelesAgent.catalogueModele`). Plus jamais un
  sélecteur unique derrière un crash : le tiroir blessé laisse les
  Paramètres vivants. La recherche des Paramètres le trouve
  (`libelles()` : « fournisseur »), la structure le porte
  (`EtatParametres.R_FOURNISSEUR_IA`, Genre.ACTION, légende).

## 3. Preuves

- L'audit scripté des layouts : 39 signalements sans dimensions, 35
  `<include>` légitimes, **4 défauts réels** — les quatre corrigés.
- AOSP `ViewGroup.addViewInner` : `IllegalStateException` sur parent
  non retiré — le second piège n'était pas une hypothèse.
- Les gardes : `vingtDeuxRangeesEtDeuxActionsDestructricesExactement`
  (`EtatParametresTest`) et
  `lesReglagesReelsSeulementAucuneRangeeOrpheline`
  (`MaquetteParametresTest`) passent de 21 à 22 rangées, messages
  documentant le lot C2 ;
  `ArchitectureVuesTest.chaqueLayoutAUnConsommateurBinding` : aucun
  layout nouveau, aucun binding orphelin ;
  `aucuneCouleurLitterale/aucunTexteLitteral` : les passepartouts ne
  portent ni couleur ni texte.
- La construction (assembleDebug / assembleRelease, lint,
  `verifierOmbreApk`) part à la CI du dépôt (ADR-048) — l'écart P8
  n° 4 tient : pas d'appareil au bac à sable, le garde et la CI
  signent la suite.

## 4. La revue sur appareil (le propriétaire)

1. Ouvrir le tiroir agent : il se monte (360 dp poussé droite), le
   service d'agent démarre, le transcript et le composeur vivent.
2. Déplier la feuille : le corps se montre, la CONSOLE se monte au
   premier dépliage (le stub), les sections journal/logcat suivent
   les rails — « build » vit enfin dans la feuille.
3. L'aperçu plein écran d'un fichier de mise en page : il se monte.
4. Paramètres → carte « Agent IA » : la rangée « Fournisseur IA »
   montre le fournisseur courant, le menu ancré le change, la rangée
   se relit ; la clé posée pour le fournisseur choisi se consomme à
   la requête suivante.
