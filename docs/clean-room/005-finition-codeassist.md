# Note clean-room n° 5 — Le finition des écrans de CodeAssist : usages, thinking, choix de modèles, popups ancrés

- **Date** : 2026-11-17
- **Mandat** : le propriétaire précise la finition attendue
  (message du 2026-11-17 : « L'écran usages doit similaire a celui
  de CodeAssist, de même pour la section thinking et aussi choix de
  modèles, utiliser des écrans popups ancre. Tu as mon approbation
  pour le MPC »).
- **Dépôt consulté** : `tyron12233/CodeAssist`, branche `main`
  (clone local du 2026-11-17).
- **Fichiers lus** (lecture seule, aucune ligne reprise) :
  `services/agent-ui/src/commonMain/kotlin/dev/ide/agent/ui/`
  (`AgentUsage.kt`, `ModelPickerSheet.kt`, `ChatDrawer.kt`).

## 1. L'écran Usages (`AgentUsage.kt`)

**Comportement observé.** Un dialogue centré, une LIGNE par limite
que le fournisseur rapporte : nom du compteur, « N of M used » avec
le pourcentage à droite, une BARRE pilule (primaire, tertiaire à
partir de 80 %, erreur à 100 %), et « Resets in 2h30m » qui
COMPTE à rebours (horloge re-read chaque minute). Le compteur qui
gène le modèle en cours va EN PREMIER. Une bande discrète
(`UsageWarningStrip`) apparaît au-dessus du composeur dès 80 %
d'une limite atteinte : « what is left and when it comes back »,
tapable vers le dialogue — rien sous le seuil, « it never nags ».
Les notes de pied : « no limits — spent $X today », l'erreur nommée.
Boutons : Actualiser (re-fetch) + Fermer en en-tête, Done en pied.

**Leçon retenue.** Un COMPTEUR par limite, sa bande et son pourcentage ;
la bande PRÉCÈDE l'erreur (le précédent de BandesUsage, ADR-062) ;
sans limite rapportée, l'écran le dit ; l'actualisation sans rouvrir.

## 2. La section thinking (`ChatDrawer.kt`)

**Comportement observé.** Le raisonnement est une ÉTAPE
(`Step.Thinking`) : OUVERTE pendant qu'elle coule (live) — « there
is something to watch » — puis, une fois le tour passé ailleurs,
elle se PLIE à une ligne « Thought » qui s'étend au tap (« a
finished turn's reasoning is rarely worth its screen space »).
L'extension par tap se souvient par message (`rememberSaveable`).
La ligne d'activité (`AgentBusyLine`) vit AU-DESSUS du composeur
pendant tout le tour : un point qui pulse, le libellé du pas courant
(Thinking / Running <outil> / Writing / Waiting) et l'ÉCARTE du
tour (compteur de secondes par message). Le pied de chaque réponse
(`UsageFooter`) porte les jetons compacts « 940 input · 8.4k output
· 1.2k cached » — « enough to read a trend ».

**Leçon retenue.** Réflexion ouverte tant que ça pense, pliée quand
c'est fini, rouvrable au tap ; l'activité du tour se raconte sur
UNE ligne avec son écart ; les jetons du pied compacts.

## 3. Le choix de modèles (`ModelPickerSheet.kt`)

**Comportement observé.** Un dialogue avec RECHERCHE (le champ
prend le focus si la liste dépasse 20 modèles — « a keyboard would
cover it » sinon), des puces de FILTRE (fournisseur en menu déroulant
avec compte par vendeur, tri nom/vendeur, gratuit seulement), et
trois SECTIONS : Favoris (étoile pleine, triées par nom), Récents
(doublon jamais montré), Tout. Chaque ligne : nom + id dessous +
puce « free »/prix + coche du courant + étoile qui bascule le
favori SANS fermer. Le tri est « naturel » (GPT-4.1 avant GPT-5,
Model 2 avant Model 10). Le choix ferme et s'applique.

**Leçon retenue.** Recherche + sections (favoris/récents/tout), la
coche et l'étoile sur la même ligne, le favori bascule sans fermer,
le filtre qui ne sert pas ne s'affiche pas (« a short list stays a
short list »).

## 4. Les popups ancrés

**Comportement observé.** Les menus de l'en-tête et les feuilles
vivent PRÈS de leur appelant (les menus déroulants des filtres du
sélecteur sont ancrés à leur puce). Le transposé Android classique :
`PopupWindow.showAsDropDown` sous l'ancre, au-dessus si l'ancre
vit en bas — le composeur ne se fait jamais recouvrir.

**Leçon retenue.** La carte ancrée tombe de son bouton ; la
mécanique d'ancrage (tombée, montée, hauteur bornée, tap extérieur)
se sépare du contenu — chez JoIDE, `PopupAncre` ne porte que la
mécanique et l'atelier compose.
