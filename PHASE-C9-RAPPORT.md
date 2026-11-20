# PHASE C9 — le 429 patienté proprement, le 400 nommé, la réponse rendue lisible, le pack qui porte tout le POM (2026-11-22)

**Mandat du propriétaire, mot pour mot.** « Je panse que
premièrement les réponses sont pas tous bien représenté que se soit
dans le section thinking ou encore dans le chat, elle doit être plus
moderne, plus intelligent, rendus propre. Il y a un message spam
limite de débit reprise dans n(S) — c'est peu professionnel et en
plus le cadre erreur est affiche 2 fois. […] Il y a aussi cette
erreur : Réponse illisible (données cassées ou incohérentes)
unexpected response (400) {"error":{"message":"Provider returned
error","code":400,"metadata":{"raw":"{\"error\":{\"code\":\"malformed_json\",\"message\":\"json:
cannot unmarshal object … Le build pause encore des problème[…].
app:bundle[debug] : NoClassDefFoundError: Failed resolution of:
Lcom/android/aapt/Resources$XmlNode; Consultez [l'IDE de référence]
pour voir comment ils ont fait. » Quatre terrains, quatre
correctifs ; le code de l'IDE de référence consulté à chaque
arbitrage (mandat autorisé), aucune trace de son nom dans les
fichiers de code — le présent rapport, fichier `.md`, garde le droit
de le nommer.

## 1. Le 429 patienté proprement (le spam de « reprise dans N s »)

**Le terrain.** Toute limite de débit égrenait un événement d'attente
PAR SECONDE ; l'état en faisait une carte tonale CHACUNE : trente
secondes de 429 laissaient trente lignes dans le transcript, et la
fin du tour ajoutait la carte d'erreur par-dessus.

**Le geste de l'IDE de référence.** Son AgentLoop émet UN événement
`Waiting(clock + délai, "…")` par pause ; l'horloge qui vit est celle
de la LIGNE D'ACTIVITÉ (labelWaiting + l'écart qui tique), jamais le
transcript. Six attentes maximum par tour, 30 s par défaut, 120 s au
plafond — déjà notre Config.

**Ce qui change.**
- `BoucleAgent.attendre` émet l'événement UNE fois, AVEC le délai
  total, AVANT de dormir (l'annulation traverse toujours, seconde
  par seconde).
- `EtatAgentChat` tient `attenteCourante` : une SEULE carte ATTENTE,
  RAFRAÎCHIE si une pause suit ; le premier événement de progression
  (texte, raisonnement, outil, fichiers, compaction, fin, erreur) la
  RETIRE — le transcript ne garde rien des pauses. `envoyer()` la
  coupe (un tour neuf ne reprend pas la pastille d'avant).
- La pastille vit au dessin de la maquette `.attente`
  (docs/ecrans/design/agent.html) : pilule alignée à gauche, fond
  alerte 8 %, bord alerte 30 %, rayon 999, texte 12 sp mono, POINT
  8 dp qui pulse tant que le tour vit — `item_attente_agent.xml` +
  `drawable/point_attente_agent.xml`, posée par `VueAgent
  .carteAttente`.
- Le libellé final (reprises épuisées) : « Limite de débit (429) —
  les reprises automatiques sont épuisées, réessaie dans un moment ».

**Tests.** `BoucleAgentTest.laLimiteDeDebitSePatienteProperementPuisReussit`
(trois pauses, trois événements, chacun son délai) ;
`EtatAgentChatTest.attenteEtCompactionSontUniquesEtChiffrees`
(unique, rafraîchie, retirée à la progression) ;
`MaquetteAgentTest.lattenteEtLaCompactionDeLaMaquetteSontChiffrees`
(idem, contre la maquette) ; le plafond de cartes s'éprouve sur les
cartes FICHIERS (l'attente n'en crée plus).

## 2. Le cadre erreur affiché 2 fois

**Le terrain.** La boucle annonçait l'événement d'erreur à l'écouteur
puis levait l'exception ; le service rattrape TOUT tour finissait par
re-consigner : deux cartes identiques.

**Le correctif.** `ErreursLlm.ErreurLlm.dejaAnnoncee` (transitif) :
la boucle le pose en annonçant ; les DEUX catches du service
(ErreurLlm et Throwable-enveloppé) le lisent — une carte, la fin de
tour suit. Test `BoucleAgentTest.lErreurNEstAnnonceeQuUneFois`
(UN événement, drapeau posé). Le détail de la carte devient le
DIAGNOSTIC TECHNIQUE : monospace discret sous la raison française
(« rate limited (429): wait the announced delay… » se lit comme un
journal, plus comme une seconde phrase de même taille).

## 3. Le 400 dit enfin son nom (malformed_json)

**Le terrain.** « Provider returned error (400) :
malformed_json : json: cannot unmarshal object… » s'affichait en
« Réponse illisible (données cassées ou incohérentes) ».

**Deux causes, deux correctifs.**
1. **La nôtre (la vraie)** : `tool_calls[].function.arguments`
   partait en OBJET — la spécification Chat Completions exige une
   CHAÎNE JSON ; un serveur Go qui dé-sérialise en string répond
   exactement « json: cannot unmarshal object ». Le round 2 d'un tour
   à outils mourrait à la première re-requête : « hello » passait,
   tout travail réel non. `FournisseurOpenAiCompatible` : les
   arguments repartent en TEXTE BRUT tel que le modèle les a dits
   (argumentsTexte : «{}» si muets) ; et `tools` ne part jamais vide
   (un tableau vide est refusé par des passerelles — la passation du
   plafond n'en porte pas). Tests :
   `lesArgumentsDAppelPartentEnChaineJson`,
   `leChampToolsNePartJamaisVide`.
2. **L'affichage** : un 400 n'est pas une « réponse illisible » — le
   serveur a LU et REFUSÉ. Quinzième genre REQUETE_REFUSEE («
   Requête refusée par le fournisseur (400) — vérifier le modèle et
   la passerelle »), diagnostic brut dans le message ; SAUF le wording
   « too long » (un 400 qui dit la fenêtre reste CONTEXTE_TROP_LONG,
   l'ancien comportement). Tests :
   `un400EstUneRequêteRefuseePasUneRéponseIllisible`,
   `quinzeGenresExactementEtChacunTranche`.

## 4. La réponse rendue lisible — le rendu Markdown

**Le terrain.** « Les réponses sont pas tous bien représentés […] plus
moderne, plus intelligent, rendus propre » — la réponse et la
réflexion s'imprimaient en texte brut : les titres, listes et code
du modèle s'affichaient avec leurs marqueurs.

**Le geste de l'IDE de référence.** Son `Markdown` (module
ide-ui-components) : blocs Heading/Paragraph/Code/Items/Quote/
Divider, inline gras/italique/code/liens, le chat passe une
typographie compacte et le bloc de code devient la carte
syntaxiquement colorée CodeSample.

**Ce qui arrive (le kit Graphite, en Java, sans Compose).**
- `ui/RenduMarkdown.java` : analyseur PUR (zéro dépendance Android,
  testé JVM) — blocs (TITRE niveau 1-6, PARAGRAPHE, CODE clôturé avec
  langue, LISTE puce/ordonnée imbriquée par indentation, CITATION
  récursive, SEPARATEUR) et nettoyage inline en MARQUAGES (GRAS,
  ITALIQUE, CODE, LIEN avec cible). Une clôture manquée va jusqu'à
  la fin ; un marqueur non apparié reste du texte — jamais un mot
  perdu.
- `VueAgent.remplirMarkdown` traduit : paragraphes aux spans
  (StyleSpan, TypefaceSpan monospace + fond haut pour le code en
  ligne, URLSpan souligné accent pour les liens), titres à l'échelle
  (20/18/17/15, graisse 600), listes indentées à marqueur « • » /
  « 1. », citations à barre accent 3 dp, séparateurs 1 dp, blocs de
  code monospace 12 sp sur conteneur haut rayon 10 avec étiquette de
  langue et bouton COPIER (bornés 320 dp, défilants).
- La RÉPONSE (`item_msg_agent.xml` : `texte_reponse` devient
  `blocs_reponse`, conteneur de blocs) ET la RÉFLEXION
  (`item_raisonnement.xml` : `contenu_raisonnement` devient
  `conteneur_raisonnement`, rendu en note 13 sp secondaire). Le caret
  se tait pendant l'attente de débit (contientAttente).
- Tests : `ui/RenduMarkdownTest` — découpage des blocs dans l'ordre,
  imbrication et ordre des listes, clôture manquée, inline nettoyé
  avec positions exactes, marqueurs non appariés, texte libre intact.

## 5. Le pack bundletool porte TOUT le POM (lot XXIII)

**Le terrain.** « app:bundle[debug] : NoClassDefFoundError: Failed
resolution of: Lcom/android/aapt/Resources$XmlNode; » — l'adaptateur
chargeait ENFIN (lot XXII) mais BuildBundleCommand mourrait : le
pack .2 portait bundletool + la fermeture GUAVA, PAS les SIX
dépendances runtime restantes du POM (vérifié au POM de Google
Maven) : aapt2-proto 7.3.0-alpha07-8248216 (les classes
com.android.aapt — le XmlNode du manifeste proto !), dagger 2.28.3,
javax.inject 1, jose4j 0.9.5, slf4j-api 1.7.30,
auto-value-annotations 1.6.2.

**Le correctif.** Le pack versionné `bundletool-1.18.3.3.dex.zip`
(règle dorée : les .0/.1/.2 restent en place) porte les QUINZE jars
assainis + L'ADAPTATEUR, d'un seul D8 (jar r8 9.5.32 du catalogue,
min-api 28, android.jar 37.2) : **5 992 495 octets, SHA-256
151eb62e8ac980321e0240d21ff7b7a9dea02d2af1496f460af8af7371284615**.
Définitions vérifiées au dexdump : com/android/aapt/Resources
$XmlNode, jo/joide/build/android/tools/BundletoolAssembler,
com/google/protobuf/GeneratedMessageV3, BuildBundleCommand,
ValidateBundleCommand, ImmutableList, InternalFutureFailureAccess,
org/slf4j/Logger — TOUTES DÉFINIES. Six entrées au catalogue
(aapt2-proto, dagger, javax-inject, jose4j, slf4j-api,
auto-value-annotations), la tâche :app-android:dexPacksOutils prend
l'ordre canonique complet et le nom .3, `PacksOutils.BUNDLETOOL`
pointe le nouveau fichier épinglé, le script miroir
`scripts/fabrique-pack-bundletool.sh` suit (marqueur XmlNode
exigé). **À publier dans la release toolchain-v1** — l'appareil
télécharge le pack épinglé par taille et empreinte à la première
construction.

## 6. Ce qui reste ouvert

- La publication du pack .3 sur la release GitHub (geste du
  propriétaire ou token reposé) : sans elle, l'appareil reste sur
  le pack .2 et le bouton build garde son XmlNode manquant.
- Le coloriage syntaxique des blocs de code (le CodeSample de
  l'IDE de référence) : le moteur est prêt (la langue est
  étiquetée), la table des couleurs reste à écrire.
- La vérification sur appareil des quatre correctifs (l'APK du
  lot est livré pour cela).

## 7. Livraison

- Modules touchés : `agent` (ErreursLlm, BoucleAgent,
  FournisseurOpenAiCompatible), `app-android` (AgentService,
  EtatAgentChat, VueAgent, RenduMarkdown neuf, quatre gabarits,
  chaînes FR/EN), `build-android` (PacksOutils), `app-android`
  build.gradle.kts + catalogue (six dépendances), le script miroir,
  CHANGES.md et le présent rapport.
- Tests : 41 tests agent verts (dont 4 nouveaux), 34 tests purs
  app-android verts (dont RenduMarkdown 6 neufs, attente unique,
  400 nommé), l'APK `joide-ecrans-ia-C9.apk` livré dans
  download/ avec le pack `bundletool-1.18.3.3.dex.zip` à publier.
