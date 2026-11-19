# PHASE-C4 — Le corps affamé, le thinking à l'identique, la liste vivante : les écrans de l'agent parités

**Date** : 2026-11-20 · **Lot** : C4 (chantier des vues de l'agent,
 consultation clean-room n° 7) · **Précédents** : C1 garde-crash, C2
 dimensions racines, C3 coffre IV + feuille en fragments.

## Le mandat du terrain (message du propriétaire, 2026-11-20)

1. « Lorsque j'appuie sur les boutons du bottomsheet pour changer
   soit logcat, journal, etc… Rien ne se passe pour la section
   correspondante elle n'est pas visible, afficher. »
2. « Gros problème avec l'écran chat, pour la section thinking elle
   doit être identique à celui ce CodeAssist de tyron12233 branche
   main avec toutes les fonctionnalités inclus. »
3. « De même pour l'écran choisir modèle (le popup) il doit
   comprendre les options de filtrage, charge la liste des modèles,
   etc… Design identique. De même pour l'écran usage même
   fonctionnalités. »

La consultation de CodeAssist est MANDATÉE par ce message (le journal
clean-room la consigne — n° 7) ; l'implémentation reste clean-room :
notes fonctionnelles dans `docs/clean-room/007-thinking-codeassist.md`,
aucune ligne de Kotlin ou de Compose reprise, discipline ADR-085
tenue (layouts XML, bindings, états purs, aucune couleur ni texte
littéral dans les layouts).

## 1. La feuille basse : le corps affamé (le point 1)

**Cause racine, DEUX bugs géométriques.**

- **La racine interne fixe.** `FeuilleBasse` (la vue EXTERNE, celle
  de `activity_atelier.xml`) porte la hauteur ANIMÉE des détentes ;
  le layout gonflé `atelier_feuille.xml` avait sa racine à
  `layout_height="72dp"` FIXE : quelle que soit la hauteur de la vue
  externe, l'intérieur restait 72 dp — poignée 20 + en-tête 52 = la
  barre, et le corps (`0dp` + poids) recevait **zéro pixel à toutes
  les détentes**. Le tap du rail sélectionnait bien la section (la
  zone vivante changeait, le fragment montait) mais le CONTENU
  demeurait invisible : « rien ne se passe pour la section
  correspondante ».
- **L'animation à cheval sur les unités.** `appliquerDetente` lisait
  son départ en PIXELS (`getHeight()`) et passait sa cible en DP
  BRUT à `ValueAnimator.ofInt` : la valeur animée s'écrivait TELLE
  QUELLE dans `params.height` (pixels) — sur la densité du moto g06
  (~2,75), la feuille atterrissait à ~36 % de la hauteur voulue ; et
  une hauteur utile encore nulle (le parent pas encore mesuré)
  calculait mi et plein à ZÉRO dp : la feuille se refermait sur
  elle-même.

**Le fix (trois gestes).** La racine passe à `match_parent`
(l'intérieur suit la hauteur animée de l'extérieur) ; la cible se
CONVERTIT (`GraphiteVue.dp`) avant l'animation (les deux bouts sont
en pixels) ; une détente ouverte ne descend JAMAIS sous la hauteur
repliée (garde : hauteur utile inconnue = feuille à la barre, jamais
avalée) ; et la colonne de `FeuilleBasse` se déclare VERTICALE (la
valeur par défaut d'un LinearLayout est horizontale — la vérité
structurelle se pose, pas un accident).

**Le vérifiable au terrain.** Le tap d'un bouton du rail (logcat,
journal, problèmes, console) ouvre la feuille à mi-hauteur et la
SECTION apparaît avec son contenu (le transcript de la console a sa
hauteur, le journal et le logcat défilent, le logcat démarre au
TAP) ; la poignée et l'en-tête traînent et s'ancrent ; le dépliage
plein et le repli retrouvent leurs vraies hauteurs.

## 2. Le chat : le thinking à l'identique (le point 2)

**Le bloc de travail (WorkBlock de CodeAssist).** Tout ce qui
précède la DERNIÈRE carte d'outil d'un tour se replie en UNE rangée
(`item_travail_agent.xml`) : le point d'état AGRÉGÉ (en cours >
refus > fini — en cours pulse), « N outils · M fichiers » (les
cibles DISTINCTES des outils du bloc), le TITRE du dernier appel
replié (« folded, the header still says what the run did last »),
le chevron qui tourne. Déplié : le RAIL vertical (1 dp,
`?attr/jo_bord_fin`) porte les étapes DANS L'ORDRE — la carte de
réflexion, chaque carte d'outil (pliable seule), les lignes tonales.
Le pli vit dans l'état pur : `EtatAgentChat.travailDeplie(cle)` —
la CLÉ est l'identifiant d'appel du dernier outil du bloc
(**stable** : les évictions du plafond de cartes décalent les
indices, jamais les identifiants). La DÉCOUPE est pure et testée :
`EtatAgentChat.tranches()` (une question ou une erreur ferment la
tranche — elles ne se replient JAMAIS dans un bloc ; une suite sans
outil n'en forme pas : le raisonnement seul reste au premier plan).

**La carte de réflexion (ThinkingStep à l'identique).** Deux
libellés : « Réflexion… » tant que le tour la remplit (le point
accent PULSE, la carte OUVERTE — « il se passe quelque chose à
regarder »), « Pensée » repliée à UNE ligne quand il est fini (le
tap rouvre) ; le compte de lignes DISPARAÎT de l'en-tête (CodeAssist
n'en montre pas). La pulsation vit par UN animateur (jamais
d'accumulateur sur les rafraîchissements — il s'annule avant chaque
pose).

**La bande d'avertissement (UsageWarningStrip à l'identique)** : au
dessus du composeur (`tiroir_agent.xml`), visible SEULEMENT quand la
fenêtre qui porte le modèle courant est chargée à ≥ 80 % : la marque
triangulaire, « N requêtes restantes » (ou jetons — le genre de la
fenêtre porteuse), « réinitialisé dans 12 s », la mini-bande à 4 dp ;
au plafond, « Limite atteinte » en teinte d'erreur. Le tap ouvre
l'écran Usages (`ouvrirUsages` rejoint l'auditeur de `VueAgent`).
Rien sous le seuil : « jamais de harcèlement ».

## 3. Le sélecteur de modèles : les filtres et la liste vivante (le point 3)

**La LISTE VIVANTE.** `AgentService.listeModeles(fournisseur)` : le
`GET /models` de la passerelle pointée — OpenRouter (l'URL de base
propre, SANS clé : l'endpoint est public), OpenAI et l'API
personnalisée (`Bearer`), Anthropic (`x-api-key` +
`anthropic-version`, `display_name`), Gemini (`x-goog-api-key`,
`models/` retenu, `displayName`). Le transport est celui de l'agent
(D-5 — UN client HTTP), l'appel vit sur le fil DE FOND de l'atelier
(`hote.fond()`), la fusion revient au fil d'UI : vivante devant,
catalogue honnête derrière (les favoris du catalogue restent
visibles) ; l'échec rend la liste vide — le catalogue reste, JAMAIS
une liste inventée. L'IDENTIFIANT et le NOM D'AFFICHAGE remontent
tous deux (`ModeleVivant`).

**Les FILTRES de CodeAssist (`ModelesAgent`, modèle pur testé
JVM).** La RECHERCHE multi-terme (chaque terme séparé par un blanc
doit apparaître — « anthropic sonnet » trouve les Sonnet
d'Anthropic), l'indice porte le COMPTE (« Rechercher parmi 300
modèles… »), au-delà de 20 modèles la recherche PREND LE FOCUS. Le
FOURNISSEUR d'identifiant en menu déroulant (`PortePopups.menu`)
avec le compte par fournisseur ; le TRI nom ↔ fournisseur ; les
GRATUITS seulement (identifiants `:free`) ; CHAQUE contrôle
disparaît s'il ne peut pas rétrécir cette liste (« une liste courte
reste courte »). Les SECTIONS favoris/récents/tout : un modèle se
montre UNE fois, l'en-tête se CACHE quand tout tient en une
section. La LIGNE (`item_modele.xml`) : le nom NU (le préfixe
« Fournisseur : » ôté), la puce « Gratuit », l'IDENTIFIANT dessous
quand il diffère, la coche, l'ÉTOILE. Le TRI NATUREL : les suites de
chiffres se lisent en nombres (« GPT-4.1 » avant « GPT-5 », «
Modèle 2 » avant « Modèle 10 », les zéros de tête ne pèsent pas) ;
par fournisseur d'abord, la chaîne vide en tête. L'état vide se dit
(« Aucun modèle ne correspond »).

## 4. L'écran Usages : les mêmes fonctionnalités

Chaque compteur gagne son PIED complet : « N sur M utilisés » et
« réinitialisé dans X » qui DÉCOMPTE à la seconde (une horloge posée
au bind, retirée à la fermeture) — la relance est HONNÊTE : la
sortie de la PLUS ANCIENNE requête de la fenêtre glissante
(`RegulateurDebit.premiereSortieMs`, exposée par
`FournisseurBascule.relancesModele`), jamais un plafond inventé.
La puce « En cours » marque la fenêtre qui PORTE le modèle courant
(la plus chargée — le chip « In use »). Les données de compte
viennent de `compteursModele()` (requêtes, plafond rpm, jetons,
plafond tpm — la clé la plus chargée parle pour toutes). Le pied de
conversation, le banc de clés et les assises restent (lot T).

## 5. Les preuves

- `:app-android:test` VERT (397 tests, dont les nouveaux :
  `ModelesAgentTest` — 12 éprouves : fournisseur d'identifiant,
  gratuits, nom nu, recherche multi-terme, sections sans doublon,
  récents récents-d'abord, tri naturel (les nombres en nombres, la
  casse, les zéros de tête), tri fournisseur, comptes ; et
  `EtatAgentChatTest` + 5 éprouves : la tranche couvre jusqu'au
  DERNIER outil, sans outil aucune tranche, une question COUPE la
  tranche, les fichiers touchés comptent les cibles distinctes, le
  pli vit par clé).
- `:agent:check` VERT (les nouveaux accès `compteurRpm`,
  `compteurTpm`, `premiereSortieMs` suivis par les tests existants
  du régulateur).
- `:app-android:lint` : **zéro erreur nouvelle** (les 3 erreurs
  `DiffUtilEquals` et les avertissements listés préexistent à HEAD —
  aucun dans les fichiers du lot).
- APK de terrain : `joide-thinking-codeassist-C4.apk`
  (`0.1.0`, arm64-v8a, debug).

## 6. Dette préexistante consignée (rien du lot C4)

`./gradlew check` à la racine échouait DÉJÀ à HEAD sur deux fronts
étrangers à ce lot :

1. **La typographie française des Markdown** : 2 152 « espace
   sécable avant : » (CHANGES, rapports, docs) — corrigée d'un trait
   par le `--fix` du script de politique (commit séparé, mécanique).
2. **`:build-android:checkApi28`** : 17 violations — les RECORDS de
   `RBytecode`/`JarWriter` (non couverts par la liste des
   constructions réécrites par D8, qui n'autorise que
   `LambdaMetafactory` et `StringConcatFactory`) et
   `Attributes$Name(String)` absent du fichier de signatures du
   platform 28. La correction appartient au propriétaire : élargir
   `isDesugaredConstruct` aux records (réécrits par D8) et
   régénérer les signatures — aucun des deux gestes n'est du lot C4.

## 7. Ce qui reste au terrain à vérifier

1. La feuille : les quatre sections au tap du rail (contenu VISIBLE,
   journal et logcat qui défilent, logcat qui démarre au tap), la
   traînée et l'ancrage aux trois détentes.
2. Le chat : le bloc de travail replié à une rangée (N outils · M
   fichiers, dernier appel), dépliable au tap avec le rail ; la
   réflexion ouverte tant qu'elle vit puis repliée à « Pensée »
   rouvrable ; le point qui pulse.
3. Le sélecteur : la liste vivante qui joint (l'indice compte les
   modèles), la recherche multi-terme, le menu fournisseur à compte,
   le tri naturel, la puce « Gratuit », l'identifiant sous le nom.
4. L'écran Usages : « N sur M », le décompte qui vit, la puce
   « En cours », la bande à 80 % au-dessus du composeur (visible
   quand un plafond est appris et chargé — sans plafond, rien).
