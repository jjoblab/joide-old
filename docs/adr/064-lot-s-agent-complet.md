# ADR-064 — Lot S : l'agent complet — menu, conversations, fournisseur in-situ, connexions, usages, attachements

- **Statut** : Accepted (retour du propriétaire sur la V2 livrée,
  message du 2026-11-17 : « Le design est peu brute, manque de
  finesse, compacité. Pour IA, il manque plusieurs écrans et
  fonctionnalités, les écrans popups… Les options de new chat,
  history, manage provider, connection, usages, etc.. Les options de
  add (image, take photo, current file ou projet » — mandat explicite
  de consulter le code de CodeAssist pour ces écrans)
- **Date** : 2026-11-17

## Contexte

La V2 a livré l'agent v1.1 (lots P/Q/R) : un tiroir chat fonctionnel,
trois fournisseurs, la robustesse transverse et le MCP prouvé en JVM.
Le retour du propriétaire nomme les manques : les écrans POPUP de
l'agent (new chat, history, manage provider, connection, usages), les
options d'ajout (image, photo, fichier courant, projet), et une
finesse de design que la v1 brute n'a pas. La note clean-room n° 4
consigne les comportements observés dans le code de CodeAssist
(`ChatDrawer.kt`, les feuilles historique/fournisseurs/connexions/
usages, le menu d'attachement) ; l'implémentation JoIDE reste
clean-room — elle part des notes et des invariants maison (ADR-É11,
ADR-048), jamais du dépôt consulté.

## Décision

### 1. Le menu ⋯ de l'en-tête — la vue ne décide rien

L'en-tête du tiroir gagne un glyphe POINTS. Il ne porte AUCUNE
logique : le clic part par `Auditeur.ouvrirMenuAgent()` et l'atelier
compose le menu (`Dialogue.menu`) — seules les entrées CÂBLÉES y
figurent, jamais d'entrée morte (règle du lot H). Le menu final :
Nouvelle conversation, Historique, Fournisseur et modèle, Connexions
MCP, Usages.

### 2. Les conversations multiples — une conversation, un fichier

`ConversationsAgent` (pur, testé JVM) gère
`filesDir/agent/conversations/` : l'identité d'une conversation est
son nom de fichier (l'instant de naissance en millisecondes), le
format reste `joide.session/1` (SessionsAgent inchangé). Le titre de
l'historique se dérive de la première ligne du premier message
(borné 80 caractères) ; l'ordre va du plus récemment modifié au plus
ancien ; le plafond est de 50 conversations avec éviction de la plus
ancienne ; les noms hostiles sont neutralisés (aucun séparateur, aucun
point). L'unique `session.json` du lot P MIGRE : il devient la
conversation la plus ancienne — rien n'est perdu, rien n'est dupliqué.

Le service garde la vérité : `nouvelleConversation` et
`reprendreConversation` refusent (faux) quand un tour vit — jamais un
tour n'est abandonné en silence ; l'état du tiroir est reconstruit
par rejouage du journal.

Correction d'honnêteté au passage : le restaurage du transcript du
lot P rejouait TOUT en messages utilisateur — une réponse persistée
redevenait une carte « message de l'utilisateur ». L'entrée du journal
gagne le genre réponse (`Entree.reponse`) et le transcript alterné
(message/réponse) rend à chacun son rôle.

### 3. Le fournisseur et le modèle in-situ — l'écart du lot P se ferme

La puce modèle n'ouvre plus les Paramètres : le dialogue in-situ
choisit le fournisseur (point de sélection, état de clé
posée/absente lu du stockage chiffré — jamais la clé), le nom de
modèle et l'URL OpenAI-compatible. La validation écrit les réglages
(Reglages, ADR-054), pousse le couple au service par le binder et
rafraîchit la puce sans quitter la conversation. La gestion COMPLÈTE
des clés (poser, remplacer, effacer, plusieurs clés par
fournisseur) reste dans les Paramètres ; le lien « Gérer les clés… »
y mène.

### 4. L'écran Usages — ce qui est facturé est montré

Usage cumulé de la conversation (les trois pieds du lot P) et état
du banc de clés (ADR-062) : nombre de clés, assises par raison
localisées (limite de débit, limite du jour, quota, clé refusée,
sans droit). Le binder expose `usageConversation`,
`bancParRaison`, `tailleBanc`. Les BANDES proprement dites
(`BandesUsage`, NORMALE/ATTENTION/CRITIQUE) attendent l'exposition
du régulateur — écart consigné au backlog.

### 5. Les connexions MCP — l'hôte câble l'ADR-063

Le GestionnaireMcp du lot R vivait sans hôte. L'atelier lit le
`.mcp.json` du projet (borné 64 Kio, vide si absent) à l'ouverture de
la conversation et le passe au service ; les serveurs déclarés sont
PARSÉS, pas connectés. La règle de sécurité tient : « un dépôt cloné
n'est pas un choix de l'utilisateur » — l'écran Connexions porte le
geste explicite qui seul connecte (Activer/Désactiver les serveurs
de ce projet), montre les états publiés (connecté, connexion,
signature requise, échec, non pris en charge stdio, désactivé), le
motif d'échec, la reconnexion et le compte d'outils MCP en ligne.
Les outils distants approuvés rejoignent le port du tour
(`portOutils`). v1 : l'approbation est par session et les jetons
OAuth en mémoire — la persistance par projet attend le store chiffré
des signatures (backlog lot R).

### 6. Les attachements — le bouton + du composeur

Le menu + ne montre que ce que l'appareil sait faire : une image du
stockage (SAF `ACTION_OPEN_DOCUMENT`, lecture bornée 8 Mio), une
photo (caméra présente), le fichier courant (un fichier est ouvert),
l'arbre du projet. Les images passent par `ReductionImages` avec le
RÉDUCTEUR BitmapFactory promis au lot Q (plus long côté 1 568 px,
ré-encodage JPEG 85, budget 1,5 Mio — une image qui ne passe pas est
refusée nommément). Le fichier courant et l'arbre se FONDENT en
blocs balisés `<file path>` / `<project_tree>` DEVANT la question
(bornés et annoncés — le modèle a `read_file` pour le reste) ;
`PieceJointe` (pur, testé JVM) porte la fusion, les noms et
l'extraction des images.

La caméra sans AndroidX (ADR-É2) : `ACTION_IMAGE_CAPTURE` avec
`EXTRA_OUTPUT` exige une URI `content://` sur API 28 — un
ContentProvider maison (`FournisseurCameraAgent`, autorité
`joide.agent.camera`) sert UN fichier de cache non exporté, l'accès
passe par la permission URI de l'intent.

### 7. Le multimodal jusqu'aux fournisseurs — et les pixels persistent

`BoucleAgent.tour` gagne une surcharge : les parties IMAGE entrent
dans le message utilisateur AVANT le texte (la sérialisation des
trois fournisseurs existait depuis le lot Q ; la compaction ne touche
que les résultats d'outils, les images passent). `SessionsAgent`
sérialise `mediaType` + `donnees` des parties IMAGE (lecture
tolérante des sessions antérieures — les champs absents restent
vides) : l'historique modèle garde ce que le modèle a réellement vu,
la conversation reprise voit encore les images.

### 8. La finesse — compacité, pas décoration

Jetons Graphite uniquement (ADR-048) : en-tête compact (5 dp
verticaux), transcript à 8 dp, bulles utilisateur au coin
bas-droit resserré (16/16/6/16 — la bulle parle du côté de son
auteur) bornées à 300 dp, ligne d'usage en légende discrète, saisie
dans une pilule bordée, état vide à glyphe bulle. La maquette
`agent.html` porte le contrat du lot S (menu ⋯, +, puces de pièces)
et le test de capture l'éprouve.

## Conséquences

- Le tiroir agent couvre les cinq écrans demandés et les quatre
  attachements ; chaque entrée de menu est câblée, chaque état
  honnête (banc pas né, historique vide, serveurs non approuvés).
- `./gradlew :app-android:testDebugUnitTest :agent:test` verts :
  ConversationsAgentTest (9), PieceJointeTest (8), MaquetteAgentTest
  (9, dont le contrat lot S), EtatAgentChatTest étendu (16).
- Écarts consignés au backlog : bandes d'usage (régulateur non
  exposé), approbation MCP persistée par projet, store chiffré des
  jetons OAuth, flux OAuth UI, amarrage grand écran inchangé.
