# ADR-065 — Lot T : les popups ancrés et la finition CodeAssist

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « L'écran usages doit similaire a celui de CodeAssist, de même
  pour la section thinking et aussi choix de modèles, utiliser des
  écrans popups ancre. Tu as mon approbation pour le MPC » — le
  mandat consulte le code de CodeAssist et l'autorise l'approche)
- **Date** : 2026-11-17

## Contexte

Le lot S a livré les cinq écrans du menu ⋯ et les quatre
attachements, mais ils VIVAIENT en dialogues centrés
(`AlertDialog`) — le retour du propriétaire nomme le reste : les
écrans doivent être des POPUP ANCRÉES (le menu vit près de son
bouton, le doigt ne quitte pas la conversation), l'écran Usages
doit se lire comme celui de CodeAssist (un compteur par limite,
sa bande, son pourcentage), la section thinking doit être ouverte
tant que le tour vit et repliée à la fin, le choix de modèles doit
se faire d'un tap dans une liste cherchable. L'ADR-064 avait
consigné l'écart : les BANDES d'usage attendaient l'exposition du
régulateur. L'approbation MCP restait par session. La note
clean-room n° 5 consigne les comportements observés dans CodeAssist
(`AgentUsage.kt`, `ModelPickerSheet.kt`, `ChatDrawer.kt` —
ThinkingStep, AgentBusyLine, UsageFooter) ; l'implémentation JoIDE
reste clean-room — elle part des notes et des invariants maison,
jamais du dépôt consulté.

## Décision

### 1. `PopupAncre` — la mécanique seule, jamais le contenu

Une carte arrondie (surface, rayon carte, bord fin, élévation)
accrochée à sa vue : tombée sous l'ancre quand la fenêtre a la
place, montée au-dessus quand l'ancre vit dans la moitié basse —
le composeur ne se fait jamais recouvrir. La hauteur se borne à la
place qui reste et le contenu défilant s'étend à la hauteur posée ;
le tap extérieur ferme. La classe ne décide RIEN du contenu
(invariant du lot H) : les vues des écrans restent composées par
l'atelier, les actions repartent par les gestes câblés.

### 2. Les écrans du tiroir deviennent ancrés

Le menu ⋯, le menu +, l'historique, les connexions, l'écran Usages
et le sélecteur de modèles vivent près de leur appelant : le menu ⋯
et le + tombent de leur icône, l'Usages et le sélecteur tombent de
l'ancre du menu (ou de la puce modèle). Plus AUCUN dialogue centré
dans le tiroir ; le modèle personnalisé garde son dialogue de
saisie (nom libre, URL de base) parce qu'un champ d'écriture mérite
un clavier posé, pas une popup.

### 3. L'écran Usages de la manière CodeAssist — et le régulateur sort

Un COMPTEUR par fenêtre plafonnée : « Requêtes / min » et « Jetons
/ min », chacun avec son pourcentage et sa BANDE colorée (accent
sous 70 %, alerte 70–90 %, erreur au-delà — BandesUsage, ADR-062).
L'écart de l'ADR-064 se ferme : `FournisseurBascule
.occupationModele` expose la plus haute occupation des fenêtres du
modèle courant, le binder la porte à l'écran. Sans plafond appris,
l'écran le dit honnêtement (aucune bande n'invente une référence
mesurée). Les trois pieds de la conversation passent en jetons
compacts (« 940 », « 8.4k », « 1.2M » — l'helper pur
`EtatAgentChat.jetonsCompacts`, testé) ; le banc de clés reste
détaillé par raison d'assise. L'en-tête porte Actualiser (relecture
sans rouvrir) et Fermer — jamais un dialogue à OK seul.

### 4. La réflexion vivante — ouverte tant que ça pense

La manière CodeAssist : la carte de raisonnement est OUVERTE tant
que le tour vit (point accent, légende accent « Réflexion… »),
elle SE REPLIE à la fin du tour (`fermerCartesOuvertes` plie la
carte courante), le tap la rouvre — une réflexion finie ne vaut pas
son espace écran. Le chevron suit l'état. La ligne d'activité
au-dessus du composeur dit ce que le tour fait (« Réflexion »,
« Exécution : outil », « Rédaction ») et depuis combien de temps
(pas d'une seconde) — un pas long ne lit jamais un blocage. Le pied
d'usage de chaque tour passe en jetons compacts.

### 5. Le sélecteur de modèles — un tap, pas un formulaire

La puce modèle ouvre une popup ancrée : la rangée de fournisseurs
(pointée, l'état de clé reste lu du stockage chiffré), la recherche
qui filtre, les sections Favoris / Récents / Suggestions, la coche
sur le modèle courant, l'étoile qui bascule un favori SANS fermer.
Un modèle se choisit d'un tap : écrit au réglage, poussé au service
par le binder, la puce se rafraîchit, la conversation continue. Les
favoris et les récents persistent par fournisseur (clés
`agent.modeles.favoris.*` / `agent.modeles.recents.*` du Source,
séparateur `|`, récents bornés à cinq). Le catalogue est un
catalogue de SUGGESTIONS nommées — honnêteté : jamais une liste
prétendue vivante du fournisseur ; le modèle hors catalogue garde
le geste personnalisé.

### 6. L'approbation MCP se souvient — liée à ce qui a été approuvé

La décision Activer/Désactiver les serveurs du projet persiste PAR
PROJET : la clé de préférence est la signature du `.mcp.json`
(chemin + contenu passés au SHA-256). Le même fichier retrouve ses
serveurs à la réouverture ; un serveur ajouté ou modifié redemande
le geste explicite — un dépôt cloné n'est pas un choix (ADR-063),
la règle tient au-delà de la session. La clé vit dans les
préférences privées : une décision de l'utilisateur n'est pas un
secret ; le store CHIFFRÉ reste réservé aux jetons OAuth (backlog
inchangé sur ce point).

## Conséquences

- Aucun dialogue centré dans le tiroir agent ; les six écrans
  ancrés, chaque entrée câblée, chaque état honnête.
- `./gradlew :app-android:testDebugUnitTest :agent:test` verts
  (MaquetteAgentTest étend aux vues du lot T : menu ancré,
  réflexion vivante, compteurs à bandes).
- Écarts restés au backlog : le flux OAuth UI, le store chiffré des
  jetons, le serveur MCP local démarré par l'hôte, l'amarrage grand
  écran — inchangés.
