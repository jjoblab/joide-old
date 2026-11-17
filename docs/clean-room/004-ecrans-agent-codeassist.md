# Note clean-room n° 4 — Les écrans de l'agent IA de CodeAssist (menu, historique, fournisseurs, connexions, usages, attachements)

- **Date** : 2026-11-17
- **Mandat** : le propriétaire rouvre la consultation du dépôt
  `tyron12233/CodeAssist` pour les ÉCRANS de l'agent (message du
  2026-11-17 : « Pour IA, il manque plusieurs écrans et
  fonctionnalités, les écrans popups, c'est pour ça que je t'ai
  demandé de consulter CodeAssist, de regarder son code. Les options
  de new chat, history, manage provider, connection, usages, etc..
  Les options de add (image, take photo, current file ou projet »).
- **Dépôt consulté** : `tyron12233/CodeAssist`, branche `main`
  (clone local du 2026-11-17).
- **Fichiers lus** (lecture seule, aucune ligne reprise) :
  `services/agent-ui/src/commonMain/kotlin/dev/ide/agent/ui/`
  (`ChatDrawer.kt`, `AgentWidgets.kt`, `AgentHistorySheet.kt`,
  `AgentProvidersSheet.kt`, `AgentConnectionsSheet.kt`,
  `AgentUsage.kt`, `ModelPickerSheet.kt`, `AgentHomeTab.kt`,
  `FreeAi.kt`) ; `app/ide-core/src/main/kotlin/dev/ide/core/agent/`
  (`AgentBackend.kt`, `AgentSessionStore.kt`,
  `AgentRecentChats.kt`) ; `app/ide-ui-components/.../components/`
  (`CodeSample.kt`, `Primitives.kt`) ; `docs/agentic-coding.md`.
- **Note fonctionnelle** : ce document. L'implémentation JoIDE du lot
  S part de CES notes et des invariants maison (ADR-É11, ADR-048),
  jamais du dépôt consulté (règle clean-room §4.1, ADR-008).

## 1. Le menu ⋯ de l'en-tête du chat

**Comportement observé.** L'en-tête compact (52 dp) porte : badge
animé + titre, une PASTILLE de modèle (nom + chevron qui pivote), une
PUCE de mode de permission qui teinte l'en-tête (Ask each /
Auto-accept / Plan only), et un menu déroulant « ⋯ » dont les entrées
sont : *New chat / History / Manage providers / Connections /
Usage*. Les mêmes fonctions sont atteignables par slash-commandes
(`/resume`, `/model`, `/mcp`…). Chaque entrée est câblée ; le drawer
se déclare « hospitable » (paramètres d'hôte : fichier actif,
actions de fichiers, retour).

**Leçon retenue pour JoIDE.** Un SEUL point d'entrée ⋯ porte les
cinq écrans ; la vue ne décide rien du contenu du menu (l'hôte le
compose) ; le mode de permission reste une puce distincte.

## 2. New chat et historique

**Comportement observé.** `newSession()` vide le transcript. La
feuille d'historique est un dialogue CENTRÉ (max 460 dp) : liste
défilante, chaque ligne = titre (première ligne du premier message,
2 lignes max) + « N messages » + bouton poubelle ; la session
courante est surlignée. La persistance : UN fichier JSON par
conversation (transcript vu par l'utilisateur ET historique côté
modèle), écrit ENTIER après chaque tour par fichier `.tmp` +
déplacement atomique ; un index global `recent-chats.json` (max 100)
pour l'onglet d'accueil. Le titre est borné à 80 caractères.

**Leçon retenue.** Une conversation = un fichier ; l'identité peut
être le nom de fichier ; le titre se DÉRIVE (jamais demandé) ;
l'écriture reste atomique ; la reprise reconstruit les deux rôles.

## 3. Manage providers (gérer les fournisseurs)

**Comportement observé.** Feuille PLEIN ÉCRAN avec flèche retour :
une carte par fournisseur (point radio, nom, chip « Connected » si
clé posée) ; la carte SÉLECTIONNÉE s'étend et révèle le champ de
clé MASQUÉ (bouton Show/Hide), les CLÉS DE SECOURS multi-lignes
(« Add backup key », chacune retirable), et pour la passerelle
personnalisée : URL de base, modèle, bouton « Fetch models » qui
interroge `/models` de l'endpoint. SAUVEGARDE À CHAQUE FRAPPE ; la
flèche retour est la seule sortie. Changer un réglage ne coupe PAS
la conversation.

**Leçon retenue.** L'état de clé (posée/absente) s'affiche JAMAIS la
clé ; la multi-clés par fournisseur est le même concept que le banc
d'ADR-062 ; le dialogue JoIDE reste compact (fournisseur + modèle +
URL + lien vers la gestion complète des clés des Paramètres).

## 4. Connections (connexions MCP)

**Comportement observé.** Feuille plein écran : une carte par
serveur (pastille de statut colorée, switch, « Connected (N tools) »
/ Connecting / Needs auth / Failed), actions par serveur (Approve /
Sign in / Retry / Show tools / Edit / Remove), éditeur de serveur
(nom, transport, URL+en-têtes ou commande). Les serveurs d'un
projet CLONÉ sont marqués NON FIABLES : l'approbation est un geste
explicite.

**Leçon retenue.** « Un dépôt cloné n'est pas un choix de
l'utilisateur » (déjà l'ADR-063) : l'écran porte la bascule
d'activation, les états publiés par le gestionnaire, la
reconnexion, le compte d'outils en ligne.

## 5. Usage (usages)

**Comportement observé.** Dialogue centré : une ligne par limite
avec barre de progression 8 dp (couleur selon seuil 80 %/100 %),
« 42 of 50 used », compte à rebours de réinitialisation re-lu chaque
minute ; rafraîchi après chaque tour ; fallback honnête « ce
fournisseur ne partage pas ses limites ».

**Leçon retenue.** Montrer l'usage CUMULÉ de la conversation et
l'état du banc de clés (assises par raison) ; les bandes de
`BandesUsage` (ADR-062) restent à brancher quand le régulateur sera
exposé.

## 6. Les attachements — le menu « + »

**Comportement observé.** Le bouton + du composeur ouvre un menu
dont les entrées n'apparaissent QUE si la plateforme les sert :
*Image* (sélecteur de fichiers filtré images), *Take photo*
(caméra), *Paste image* (presse-papiers qui contient une image —
vérifié À L'OUVERTURE du menu), *Current file* (le fichier ouvert
passé par l'hôte). Les mentions `@fichier` au caret complètent aussi
les fichiers du projet. L'image passe par une préparation : si le
plus long côté ≤ 1568 px et ≤ 1 Mo → telle quelle ; sinon
mise à l'échelle + ré-encodage JPEG qualité 85 (« une photo de
5 Mo coûte les mêmes jetons que la version réduite »). Côté modèle :
les images partent en PARTIES d'image AVANT le texte ; les
fichiers/sélections en blocs balisés `<file path>` bornés avec note
« read_file has the rest ». Les pièces en attente s'affichent en
PUCES retirables au-dessus du champ, et sur le message envoyé.

**Leçon retenue.** Menu conditionnel aux capacités ; réduction AVANT
l'entrée en conversation (le budget d'ADR-062) ; images multimodales
avant le texte ; fichiers et arbre en blocs balisés DEVANT la
question, bornés et annoncés ; puces nommées retirables.

## 7. La persistance des pixels

**Comportement observé.** Le transcript sauvegardé ne garde PAS les
pixels (étiquette seulement) ; l'HISTORIQUE MODÈLE les garde —
« c'est ce que le modèle a réellement vu ».

**Leçon retenue.** JoIDE persiste `mediaType` + `donnees` dans la
session (lecture tolérante) : une conversation reprise voit encore
ses images.
