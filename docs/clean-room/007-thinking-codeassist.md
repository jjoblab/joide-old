# Note clean-room n° 7 — L'identique demandé : thinking, sélecteur de modèles, usages

- **Date** : 2026-11-20
- **Mandat** : le propriétaire exige la parité fonctionnelle (message
  du 2026-11-20 : « Gros problème avec l'écran chat, pour la section
  thinking elle doit être identique à celui ce CodeAssist de
  tyron12233 branche main avec toutes les fonctionnalités inclus. De
  même pour l'écran choisir modèle (le popup) il doit comprendre les
  options de filtrage, charge la liste des modèles, etc. Design
  identique. De même pour l'écran usage même fonctionnalités »).
- **Dépôt consulté** : `tyron12233/CodeAssist`, branche `main`
  (clone local sparse du 2026-11-20, HEAD du main).
- **Fichiers lus** (lecture seule, aucune ligne reprise) :
  `services/agent-ui/src/commonMain/kotlin/dev/ide/agent/ui/`
  (`ChatDrawer.kt`, `ModelPickerSheet.kt`, `AgentUsage.kt`) et les
  tests (`ModelPickerFilterTest.kt`).

## 1. Le bloc de travail (`ChatDrawer.kt`, WorkBlock)

**Comportement observé.** Tout ce qui précède le DERNIER appel
d'outil du message se replie en UNE rangée : l'icône d'état
agrégée (en cours > erreur > refus > ok), « N tools · M files
changed », le TITRE du dernier appel (« folded, the header still
says what the run did last »), un chevron. Déplié : les étapes
dans l'ordre sur un RAIL vertical de 1 dp (le raisonnement, chaque
appel, la narration estompée), chaque appel ouvrable seul ; l'état
de pli se souvient PAR message. Le chevron tourne (0 → −90°).

**Leçon retenue.** La décision de découpage est PURE et testable ;
la vue ne fait qu'appliquer. La clé de pli doit être STABLE (pas un
indice : les évictions décalent) — l'identifiant d'appel du
dernier outil.

## 2. La carte de réflexion (ThinkingStep)

**Comportement observé.** L'en-tête : la marque PULSANTE, « Thinking »
en live / « Thought » repliée, le chevron ; OUVERTE tant que le flux
coule (« there is something to watch »), repliée à une ligne au
fin du tour, rouvrable au tap. Le fond s'affiche SEULEMENT
dépliée ; le texte est estompé (onSurfaceVariant). Le compte de
lignes ne s'affiche PAS dans l'en-tête.

**Leçon retenue.** Deux libellés (vivant/fini), pas de compteur ;
la pulsation est la seule animation de l'en-tête.

## 3. La ligne d'activité et le pied d'usage

**Comportement observé.** La ligne sur le composeur : la marque
pulsante, l'action en cours (« Thinking », « Running <outil> »,
« Writing », « Waiting »), l'ÉCOULE du tour qui se met à jour chaque
seconde (« 12 s », puis « 1 min 05 »). Le pied de message : les
jetons COMPACTS (« 940 », « 8.4k », « 1.2M ») — entrées, sorties,
cache ; le cache qui grimpe tour après tour tandis que la facturé
reste petit EST le symptôme visible du préfixe jeté.

**Leçon retenue.** L'écart (jamais un blocage muet) ; les jetons
compacts tendent une tendance en une ligne.

## 4. Le sélecteur de modèles (`ModelPickerSheet.kt`)

**Comportement observé.** La RECHERCHE multi-terme : chaque terme
séparé par un blanc doit apparaître dans l'identifiant, le nom ou
le fournisseur (« anthropic sonnet » trouve les Sonnet
d'Anthropic) ; l'indice porte le COMPTE (« Search among N
models ») ; au-delà de 20 modèles la recherche PREND LE FOCUS. Les
FILTRES (une rangée qui défile) : le fournisseur en menu déroulant
avec le COMPTE par fournisseur, le tri nom ↔ fournisseur, les
gratuits seulement — chaque contrôle DISPARAÎT s'il ne peut pas
rétrécir cette liste (« a short list stays a short list »). Les
SECTIONS : favoris (tri), récents (récent d'abord), tout — un
modèle ne se montre QU'UNE fois ; l'en-tête se cache quand tout
tient en une section (« A heading is noise when everything is in
one section »). La LIGNE : le nom NU (le préfixe « Vendor : » ôté —
sinon un tri par nom serait un tri par fournisseur), la puce FREE
des identifiants :free, l'identifiant nu dessous quand il diffère,
la coche du courant, l'ÉTOILE du favori (bascule au tap). Le TRI
NATUREL : les suites de chiffres se lisent en nombres (« Model 2 »
avant « Model 10 », « GPT-4.1 » avant « GPT-5 ») ; par fournisseur
d'abord, puis nom ; les identifiants sans fournisseur portent la
chaîne vide en tête. La LISTE vient du fournisseur (sa liste
vivante). L'état vide se dit d'un libellé.

**Leçon retenue.** Le filtre, les sections et le tri forment un
MODÈLE PUR (testé) ; le vivant remplace honnêtement le catalogue
quand il répond, le catalogue reste quand il ne répond pas.

## 5. L'écran Usages (`AgentUsage.kt`)

**Comportement observé.** Un dialogue centré : titre, Actualiser,
Fermer. Une LIGNE par limite : le nom, la puce « In use » sur la
limite qui gène le modèle courant, le pourcentage, la barre pilule
(primaire, tertiaire à partir de 80 %, erreur à 100 %), « N of M
used », « Resets in 2h14m » qui DÉCOMPTE (l'horloge relit chaque
minute). La bande d'avertissement (`UsageWarningStrip`) : une
ligne au-dessus du composeur dès 80 % de la limite qui porte le
modèle — « what is left and when it comes back », tapable vers
l'écran ; « nothing shows below the threshold, so it never nags ».
Le pied : « no limits — spent $X today », les notes par genre de
limite. Seuil 0,8 ; « Resets in » : minutes puis heures/jours
(borne 48 h) ; les dollars au cent, « <$0.01 » sous un cent.

**Leçon retenue.** « N de M » et « réinitialisé dans X » ne se
racontent pas : chacun vient d'une donnée HONNÊTE (les fenêtres
glissantes du régulateur donnent le compte et la sortie de la plus
ancienne requête — jamais une limite inventée).

## 6. Ce que JoIDE en fait (le lot C4)

Implémentation Java/XML clean-room depuis ces comportements : la
Tranche PURE dans `EtatAgentChat.tranches()` (clé de pli =
identifiant d'appel du dernier outil, survit aux évictions), le
bloc `item_travail_agent.xml`, la carte de réflexion à deux
libellés avec point pulsant, `ModelesAgent` (filtre, sections, tri
naturel — testé JVM), la liste vivante par le `get` du transport
de l'agent, `RegulateurDebit`/`FournisseurBascule` qui exposent
comptes et relances, la bande d'avertissement dans
`tiroir_agent.xml`. Aucune ligne de Kotlin ou de Compose reprise :
la traduction passe par les notes ci-dessus, dans la discipline
ADR-085 (layouts XML, bindings, états purs).
