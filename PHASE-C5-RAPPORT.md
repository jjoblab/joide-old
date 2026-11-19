# PHASE C5 — les écrans IA REFAITS AU DESSIN DE RÉFÉRENCE, SANS TRACE DU NOM (2026-11-20)

**Mandat du propriétaire, mot pour mot.** « Si je t'ai dis design
identique, même fonctionnalité, alors il faut copier son design… il
faut reproduire tout le des écrans ia ainsi que fonctionnalités.
N'inclut pas de référence à [l'IDE de référence] dans les fichiers,
si tu dois le faire, fait dans fichiers md. » Le lot C4 avait
INTERPRÉTÉ les trois écrans (thinking, sélecteur, usages) dans la
langue du kit : le propriétaire refuse l'interprétation, il veut la
COPIE. Le lot C5 consulte le code de l'IDE de référence (mandat
explicitement autorisé cette fois — consultation directe, plus de
clean-room) et refait les trois écrans sur son dessin : mêmes
proportions, mêmes états, mêmes gestes. Les fichiers de code ne
portent PLUS AUCUNE mention du nom de l'IDE de référence — les
commentaires parlent du « dessin de référence » ou du « terrain » ;
les fichiers `.md` gardent le droit de nommer (ce rapport en fait
usage).

## 1. Le kit étendu (ce que la copie a exigé d'abord)

- **Sept glyphes neufs** dans `ui/Icone.java` : `ETINCELLE`
  (l'étincelle à quatre pointes de la marque de l'IA, quatre
  quadratiques vers le centre), `FLECHE` (l'envoi), `STOP` (le carré
  plein de l'arrêt), `COPIER` (les deux feuillets), `INFO` (le
  cercle barré d'un point), `SUSPENSION` (les trois points
  horizontaux du menu ⋯), `ETOILE_CREUSE` (l'étoile en contour du
  favori non posé). L'`attrs.xml` les expose (`etincelle`,
  `fleche`, `stop`, `copier`, `info`, `suspension`,
  `etoile_creuse`, valeurs 17–23).
- **Quatre jetons tonaux** (`jo_contenant_haut`, `jo_contenant_max`,
  `jo_contenant_secondaire`, `jo_sur_contenant_secondaire`) : les
  marches de surface au-dessus de `surface_haute` et le conteneur
  secondaire des bulles et des sélections — la grammaire du dessin
  de référence (sa « surfaceContainerHigh/Highest » et son
  « secondaryContainer »), posés dans `colors.xml` (sombre
  `#212832/#28303B/#222A3B/#E7ECF3`, clair
  `#E4EAF2/#D8E2EE/#E6ECFA/#1C2430`) et mappés dans les DEUX
  thèmes.
- **`Puce.poserTeinte(fond, texte)`** : la puce se colore par le
  RÔLE qu'elle joue (mode d'agent, « En cours », « Gratuit ») —
  la sélection par catégories reste intacte.
- **`PopupCentre.montrer(…, largeurDp, …)`** et
  **`PortePopups.centrerLarge(carte, titre, largeur, saisie,
  fermeture)`** : les grands dialogues de la famille IA se posent
  plus larges que la carte commune (520 dp pour le sélecteur,
  460 dp pour les usages), la borne écran moins 32 dp s'applique
  toujours, la variante à saisie garde le clavier.

## 2. L'écran de chat — le tiroir refait (tiroir_agent.xml + VueAgent.java)

- **L'en-tête 52 dp sur chrome** : la MARQUE (étincelle 26 dp dans
  sa pastille accent au rayon 10), le titre **« IA »** une ligne,
  le PICKER de modèle DESSOUS (libellé 12 sp sur texte2, ellipsé,
  chevron 12 dp — la manière du dessin de référence : le nom du
  modèle sous le titre, pas une puce à côté), la PUCE DE MODE qui
  se TEINTE par permission (acceptation auto → conteneur réussite ;
  plan seul → conteneur secondaire ; demande → conteneur discret),
  le menu **⋯** (SUSPENSION 28 dp), la fermeture.
- **Le transcript respire** (retraits 14/14/14/2), les cartes se
  succèdent à 12 dp.
- **Le message UTILISATEUR en BULLE** : ≤ 320 dp, conteneur
  secondaire, coins **20/20/6/20** (le petit coin côté queue, en
  bas à droite), 15 sp sur conteneur secondaire, les pièces en
  puces dessous, le **BOUTON COPIER** (26 dp, la coche revient
  1,5 s — `brancherCopie()`).
- **La réponse pleine largeur** : 15/22 sans bulle, le **CARET
  7×14 dp** au rayon 2 qui PULSE pendant l'écriture (la couleur
  glisse de texte2 vers l'accent — deux couleurs OPAQUES, jamais
  un alpha), le pied [copier + légende des jetons].
- **La carte de RÉFLEXION à l'identique du ThinkingStep** : en-tête
  [étincelle 12 dp · « Réflexion » | « Pensée » · chevron], OUVERTE
  tant que le tour la remplit (l'étincelle PULSE), repliée à UNE
  LIGNE quand il est fini — le tap rouvre ; ouverte, la carte prend
  le conteneur tonal haut au rayon 14 et le rembourrage 12/10 ;
  repliée, fond retiré, rembourrage 2/4.
- **Le BLOC DE TRAVAIL du tour à l'identique du WorkBlock** : la
  rangée [**l'icône d'état agrégé 14 dp** (la PROGRESSION
  INDETERMINÉE teintée accent tant qu'un appel tourne — le
  `ProgressBar` du cadre — puis la coche réussite / la croix
  erreur) · « N outils · M fichiers » 13 sp · le DERNIER appel
  ellipsé · chevron 14 dp], le pressed au rayon 8 ; déplié, le
  RAIL de 1 dp (bord fin) à 8 dp du bord porte les étapes à 19 dp
  dans l'ordre.
- **La ligne d'APPEL D'OUTIL à l'identique du ToolCallRow** : [état
  14 dp · le TITRE où le VERBE (premier mot) vit en gras et couleur
  secondaire (un `SpannableString`) · le DÉTAIL replié (première
  ligne du résultat, ≤ 160 dp) · chevron 12 dp] ; déplié, la
  section **« Résultat »** : étiquette 11 sp semi-grasse et BLOC
  MONOSPACE sur conteneur tonal haut (rayon 8, rembourrage 10/8,
  hauteur bornée 320 dp, il DÉFILE dedans).
- **La ligne d'ACTIVITÉ** : l'étincelle 14 dp qui pulse, ce que le
  tour fait (« Réflexion… » / « Exécution : x » / « Rédaction… » /
  « Attente… »), l'écart qui tic à la seconde.
- **Le composeur au dessin de référence** : [bouton + 36 dp (le
  menu d'ajout)] [champ au RAYON 24, conteneur tonal max, liseré
  1 dp bord-fin → **2 dp ACCENT au focus** (le sélecteur d'état du
  drawable), indice « Posez une question sur le code, ou décrivez
  un changement », 15 sp, 5 lignes] [**le bouton d'envoi ROND
  48 dp** plein accent à ondulation circulaire, glyphe 20 dp — la
  FLÈCHE devient le CARRÉ D'ARRÊT quand un tour vit (un seul
  bouton, jamais deux), activé si envoi possible ou tour vivant,
  atténué sinon].
- **La bande d'avertissement d'usage** : conteneur tonal haut au
  rayon 14 (tonal erreur au plafond), icône INFO → TRIANGLE au
  plafond, mini-bande 4 dp, tap → l'écran Usages.

## 3. Le sélecteur de modèles — la grande carte centrée (popup_modeles.xml)

- **La CARTE CENTRÉE 520 dp** (rayon 24, conteneur tonal haut,
  liseré bord-fin) : le titre **« Choisir un modèle »** 22 sp et sa
  fermeture 30 dp ; la note de clé du fournisseur pointé SOUS le
  titre (le tap ouvre les réglages IA — l'emplacement de la note
  du dessin de référence) ; la RECHERCHE EN PILULE (loupe 16 dp,
  l'indice porte le COMPTE, la CROIX qui vide ne vit que pendant
  la frappe, focus automatique au-delà de 20 modèles) ; la rangée
  des fournisseurs puis la rangée des FILTRES (fournisseur
  d'identifiant en menu déroulant avec compte, tri nom/fournisseur
  NATUREL, gratuits seulement) ; la LISTE qui DÉFILE DEDANS
  au-delà de 460 dp (`bornerListe()`) ; le modèle personnalisé au
  pied.
- **La ligne de modèle à l'identique du ModelRow** : le NOM NU 15 sp
  demi-gras, la PUCE « Gratuit » (conteneur réussite), l'IDENTIFIANT
  NU 12 sp dessous quand il diffère, la COCHE du courant, l'ÉTOILE
  (PLEINE accent posée, CONTOUR bord sinon) ; la ligne
  sélectionnée prend le conteneur secondaire au rayon 14.

## 4. L'écran des usages — la grande carte (popup_usages.xml)

- **La CARTE CENTRÉE 460 dp** : le titre **« Limites d'usage »**,
  l'Actualisation et la Fermeture 30 dp ; le sous-titre honnête
  (« Fenêtres glissantes du régulateur — mises à jour après chaque
  message ») ; UN COMPTEUR par fenêtre plafonnée — le nom 14 sp
  semi-gras, la puce « En cours » sur la fenêtre qui PORTE le modèle
  (elle passe AUSSI en premier), le pourcentage, la BANDE 8 dp
  (accent, ALERTE au seuil de 80 %, erreur au plafond, remplissage
  jamais sous 2 %), le pied « N sur M utilisés » + « réinitialisé
  dans X » qui DÉCOMPTE à la seconde ; la note des étapes ; le
  bouton **Terminé** plein cadre. Le banc de clés et les assises
  quittent l'écran (le dessin de référence ne les porte pas).

## 5. Le décapage : ZÉRO trace du nom dans les fichiers de code

`scripts` (hors dépôt) : un décapeur remplace les 67 fichiers
porteurs (Java, XML, kts, pro, toml) — « la manière X » → « la
manière éprouvée », « de X » → « de l'IDE de référence », les
identifiants du sélecteur → « le sélecteur éprouvé », les lignes
du compilateur dissocié → « le compilateur Kotlin dissocié », le
bogue #1748 → « le bogue connu du terrain ». **Vérification :
`grep -ri` sur tout le dépôt hors `.md` renvoie ZÉRO occurrence.**
Les fichiers `.md` (rapports, ROADMAP, docs/clean-room) gardent
leurs mentions — le mandat le permet explicitement. La politique
de langue reste VERTE (0 problème).

## 6. Vérifications

- `:app-android:assembleDebug` **VERT** — APK de terrain
  `joide-ecrans-ia-C5.apk` (16 Mo, `0.1.0`, arm64-v8a, debug),
  `verifierOmbreApk` : « APK sans ombre, éléments vitaux présents ».
- `:app-android:testDebugUnitTest --tests *EtatAgentChat*
  --tests *ModelesAgent*` : **35 tests, 0 échec** (16 + 19).
- `scripts/check-language-policy` : **0 problème**.
- La feuille basse du lot C4 (racine `match_parent`, conversion
  px↔dp de la cible d'animation, détente ouverte jamais sous la
  barre) reste EN PLACE — le bouton du rail montre sa section.

## 7. Ce que la copie NE porte pas (les limites honnêtes)

- Le pied du message d'erreur « Réessayer / Utiliser tel modèle » :
  le service ne propose pas encore de modèle de repli — le bouton
  d'action des erreurs reste « Ouvrir les réglages IA ».
- Les images d'un appel d'outil (la vignette 28 dp, le zoom plein
  écran) : les cartes d'outil ne portent pas d'images aujourd'hui.
- Le dépliage sélectif du transcript : le suivi du bas reste le
  `fullScroll` d'hier (suivre tant que le lecteur est en bas reste
  un raffinement à venir).
