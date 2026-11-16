# Note clean-room n° 3 — Robustesse (failover, pacer, Gemini) et MCP de CodeAssist

- **Date** : 2026-11-16
- **Mandat** : le propriétaire rouvre la consultation du dépôt
  `tyron12233/CodeAssist` pour l'agent IA (message du 2026-11-16 :
  « Pour IA je te donne l'autorisation de regarder le code de
  Codeassist de tyron12233, tu peux même copie l'approche de certains
  aspects de son design notamment pour les lots N/O … Et aussi Go
  pour le lot Q et lot R »).
- **Dépôt consulté** : `tyron12233/CodeAssist`, branche `main`,
  commit `046e3cc98deaffa644c0211b275e69a48fcac3a2` (2026-11-16).
- **Fichiers lus** (lecture seule, aucune ligne reprise) :
  `services/agent-impl/src/main/kotlin/dev/ide/agent/impl/`
  (`KeyFailover.kt`, `RequestPacer.kt`, `GeminiProvider.kt`,
  `GeminiWire.kt`, `LlmErrors.kt`) ;
  `services/agent-mcp/src/main/kotlin/dev/ide/agent/mcp/`
  (`HttpMcpServer.kt`, `CodeAssistMcpServer.kt`, `McpJson.kt`,
  `client/` : `McpClient.kt`, `McpManager.kt`, `McpTransports.kt`,
  `McpOAuth.kt`, `McpServerConfig.kt`, `McpRemoteTool.kt`) ;
  `services/agent-impl/.../KeyFailoverTest.kt` (conventions de test
  à horloge fausse).
- **Note fonctionnelle** : ce document. L'implémentation JoIDE des
  lots Q et R partira de CES notes et des leçons de l'ADR-É9, jamais
  du dépôt consulté (règle clean-room §4.1, ADR-008).

## 1. Le banc de clés et la bascule (`KeyFailover.kt`)

**Comportement observé.** Les clés d'un fournisseur forment un pool
dans l'ordre de l'utilisateur (principale d'abord, secours ensuite).
Chaque requête part vers la PREMIÈRE clé qui n'est pas au banc ; une
clé refusée pour une limite — ou rejetée — s'assoit au banc un
temps, la suivante prend le relais, et la clé principale revient en
première ligne dès son temps écoulé. C'est un failover, pas un
round-robin : aucune rotation par défaut, l'ordre est celui de
l'utilisateur.

**Les bancs différenciés par nature de limite.**
- limite à la minute (429) : assise d'un délai énoncé par le
  fournisseur s'il existe, sinon 60 s, jamais moins d'une seconde ;
- clé rejetée (authentification) : assise À VIE (elle ne guérit pas
  seule — le pool est reconstruit quand l'utilisateur édite les
  clés) ;
- limite du jour, quota, hors plan : assise d'une heure (la borne
  journalière dépend du fournisseur, une heure est le compromis).

**Les genres qui font basculer.** Seuls les refus qui peuvent être
différents sur une autre clé font passer à la suivante : limite de
débit, limite du jour, quota/crédit, hors plan, authentification.
Tout ce qui échouerait pareil partout (requête mal formée, contexte
trop long, réseau) ne bascule pas : la requête s'arrête sur son
erreur propre.

**Jamais de re-émission après sortie.** Dès que le flux a émis un
octet vers l'utilisateur, la requête n'est plus jamais renvoyée sur
une autre clé (l'utilisateur verrait le début de la réponse deux
fois). La bascule ne s'applique qu'aux refus D'AVANT sortie.

**Le message agrégé honnête.** Quand toutes les clés sont au banc :
une erreur qui compte les clés par raison (« N rate limited, M au
plafond du jour… »), dit quand la première revient, et conseille
d'ajouter une clé ou de vérifier le plan. Si la première clé qui
revient n'est assise QUE pour une limite à la minute courte (≤ 120 s
— le plafond que la boucle attend elle-même), l'erreur est une
limite de débit porteuse du délai : la boucle l'attend visiblement
et continue, au lieu d'échouer le tour.

**Testabilité.** Horloge injectée partout ; la bascule est un
décorateur du client ; un pool d'une clé ou zéro clé est un
pass-through simple.

## 2. Le régulateur préventif (`RequestPacer.kt`)

**Le problème traité.** Un tour d'agent est une rafale : un message
utilisateur se démultiplie en une douzaine de requêtes en quelques
secondes, chacune renvoyant tout le contexte. Sur un palier gratuit
mesuré, la rafale est exactement ce qui déclenche les plafonds
requêtes/minute et jetons/minute, et chaque refus est un aller-retour
gaspillé qui fait croire que la clé est « cassée ». La régulation
préventive transforme la même charge en un run légèrement plus lent
qui FINIT.

**Le mécanisme.** Une fenêtre glissante de 60 s porte les requêtes
envoyées (horodatage + estimation de jetons d'entrée). Avant chaque
requête, `delayFor(estimation)` calcule combien de temps attendre
pour tenir sous les plafonds connus — sans rien enregistrer ; après
l'envoi réel, `record(estimation)` pose la requête dans la fenêtre.
La fenêtre est élaguée à chaque lecture.

**Les limites, du plus spécifique au plus général.** Valeurs
configurées par l'utilisateur ; sinon valeurs ÉNONCÉES par le
fournisseur dans une erreur de quota (adoptées telles quelles) ;
sinon, si une erreur à la minute n'énonce rien, le plafond observé —
ce qui était en vol au moment du déclenchement — devient la borne
(jetons : ×9/10 ; requêtes : en vol − 1), pour rester juste sous ce
qui a déclenché. Sans limite connue, le pacer n'attend jamais.

**La portée.** Un pacer par clé + modèle — c'est la portée que le
fournisseur compte. Partagé par toutes les conversations qui usent
du même couple. Thread-safe, horloge injectée.

**L'estimation des jetons.** ≈ 4 caractères par jeton sur
système + outils + parties ; chaque image compte ~1 500 jetons.

## 3. Le fournisseur Gemini (`GeminiProvider.kt`, `GeminiWire.kt`)

**Le wire-protocol.** `streamGenerateContent?alt=sse` sur
`generativelanguage.googleapis.com` (clé par en-tête
`x-goog-api-key`, jamais dans l'URL). Rôles `user`/`model` ; pas de
rôle système en conversation : l'ancrage stable part en
`system_instruction` (préfixe cachable) et une instruction
opérateur volatile se plie en part `<system-reminder>` du dernier
tour utilisateur — derrière le système et les outils cachés, sans
dérive du préfixe. Les résultats d'outils sont des parts
`functionResponse` (le nom = l'identifiant de corrélation avant
`#`), les appels sont des parts `functionCall` (arguments en objet,
entiers — jamais diffusés), et la `thoughtSignature` reçue par part
doit revenir sur la `functionCall` correspondante au tour suivant,
sans quoi l'outil est refusé (« missing a thought signature »).

**Les identifiants de corrélation.** Gemini n'émet pas d'identifiant
d'appel : le nom de l'outil sert d'identifiant, dédoublonné par un
suffixe `#N` quand le même outil est appelé plusieurs fois dans le
tour.

**L'usage.** `usageMetadata` : `promptTokenCount` CONTIENT les
lectures du cache (`cachedContentTokenCount`) — le modèle neutre
gardant les entrées non cachées, la soustraction se fait côté
adaptateur. Pas d'événement terminal : usage et complétion s'émettent
à la fin du flux (`finishReason` : STOP → fin de tour ;
MAX_TOKENS ; SAFETY/RECITATION/BLOCKLIST/PROHIBITED_CONTENT →
refus).

**Les budgets de réflexion.** Gemini 3 prend un NIVEAU (minimal/
low/medium/high) et rejette un budget ; la famille 2.5 prend un
budget et rejette un niveau — envoyer le mauvais échoue la requête
entière. Sur un modèle Pro, `minimal` n'existe pas (minimum forcé
128). `maxOutputTokens` plafonne réflexion ET réponse ENSEMBLE : la
marge de réflexion s'ajoute au plafond demandé (plafond absolu
65 536) — sinon un effort élevé peut dépenser tout le plafond en
réflexion et rendre un tour vide coupé à MAX_TOKENS.

**Le cache de contexte serveur (`cachedContents`).** État
porté PAR SESSION (pas par requête) : l'instruction système + les
déclarations d'outils — le plus gros envoyé à chaque itération —
sont posés côté fournisseur et référencés par `cachedContent`.
Politique conservatrice : rien au premier tour (un chat à un seul
tour ne doit pas payer une création de cache), pas de création sous
le minimum cachable du fournisseur (2 048 jetons estimés), repli
inline sur tout échec ET échec mémorisé par clé de charge utile
(jamais une retente de création à chaque tour), TTL 3 600 s avec
recréation une minute AVANT l'expiration (un tour long ne référence
jamais un nom expiré). La clé de cache : modèle + empreinte du
système + surface d'outils.

**Les schémas d'outils.** Gemini rejette `additionalProperties` à
toute profondeur — strippé récursivement avant envoi.

**Le choix des modèles.** Flash d'abord : c'est ce qu'une clé
gratuite peut réellement user (Pro 2.5 retiré du palier gratuit,
plafond 0) — un défaut Pro échouerait la première requête d'une clé
fraîche. La préférence auto choisit le flash généraliste le plus
récent (forme `gemini-X.Y-flash`, stable avant `-preview`,
variantes spécialisées écartées).

## 4. Le raffinement des quotas des 429 (`LlmErrors.kt`)

Le cas d'école déjà consigné en ADR-É9 se retrouve dans le code : un
429 du palier gratuit de Gemini emploie le vocabulaire de facturation
d'un quota épuisé — le classificateur commence par le CODE (429 =
transitoire) et raffine par les quotas NOMMÉS : un plafond à zéro
signifie « modèle hors plan » (changer de modèle ou de clé), une
fenêtre journalière est la limite du jour, sinon la limite à la
minute ; et l'extraction « il ne reste de crédit que pour N jetons »
permet une retente au plafond réduit. La métrique (requêtes contre
jetons d'entrée) et la fenêtre (minute contre jour) voyagent avec
l'erreur pour nourrir l'apprentissage du pacer.

## 5. Le client MCP (`client/`)

**Le client (`McpClient.kt`).** Poignée de main `initialize`
(protocole le plus récent déclaré, le serveur répond avec celui
qu'il fixe ; clientInfo ; notification `initialized`), `tools/list`
avec pagination SUIVIE mais BORNÉE (vingt pages — un curseur qui
boucle ne peut pas suspendre le client), `tools/call` retourné
aplati : blocs texte joints, images (base64, mime supportés),
ressources (texte embarqué ou noté binaire), liens de ressources et
audio DÉCRITS plutôt que perdus (le modèle sait qu'ils existent),
contenu structuré employé seulement s'il n'y a ni texte ni image,
plafond 40 000 caractères avec coupure qui dit quoi faire, `isError`
honnête. Le client ne déclare AUCUNE capacité (pas de sampling,
roots, elicitation) : un serveur bien élevé ne demande rien, un
serveur qui demande quand même reçoit un method-not-found ; `ping`
reçoit sa réponse vide. Les délais : 30 s de connexion, 180 s par
appel. Les demandes SERVEUR (requêtes entrantes) répondent sur un
exécuteur dédié ; les notifications `tools/list_changed` remontent
à l'hôte ; la déconnexion complète tous les appels en attente avec
la cause.

**Le gestionnaire (`McpManager.kt`).** `update(configs)` reçoit
l'ensemble ENTIER voulu : les serveurs dont la config est inchangée
gardent leur connexion, les autres ferment ou ouvrent ; la connexion
va en arrière-plan — un serveur lent ou mort ne retient jamais le
chat, ses outils apparaissent quand il répond. États publiés :
désactivé, connexion, connecté, auth requise, échec, non supporté ;
une version d'outils s'incrémente à chaque changement (cache du
hôte). Un appel sur une connexion tombée reconnecte UNE fois ; une
signature expirée se rafraîchit UNE fois ; un serveur qui refuse
encore est marqué auth requise.

**La règle de sécurité centrale.** Un appel n'est retenté que si le
serveur a PROVÉ qu'il n'a pas exécuté l'outil : session expirée
(404 sur une requête porteuse d'identifiant de session) ou
identifiants refusés avant examen. Toute autre rupture EN COURS
d'appel répond honnêtement « la connexion a cassé pendant l'appel :
il a pu s'exécuter ou non » — un outil mutant n'est JAMAIS lancé
deux fois. Une erreur JSON-RPC du serveur ou un délai ne se
retentent pas non plus.

**Les transports (`McpTransports.kt`).** Streamable HTTP : chaque
message est un POST sur l'URL unique, `Accept: application/json,
text/event-stream` ; la réponse vient SUR le POST (corps JSON ou
flux d'événements qui se termine avec elle) ; la lecture du flux
s'arrête dès que la réponse à NOTRE identifiant est passée (le
serveur peut garder le flux ouvert après) ; l'identifiant de session
émis à initialize revient en en-tête `Mcp-Session-Id` à chaque
requête, et l'en-tête `MCP-Protocol-Version` après négociation ; un
DELETE best-effort dit au serveur que la session est finie. PAS de
flux GET permanent : l'agent n'a besoin que des réponses à ses
propres requêtes. Repli : un serveur qui ne parle que le vieux
transport SSE historique refuse le POST — retente une fois sur ce
transport avant d'abandonner (et rapporte l'échec ORIGINAL, le
serveur a plus de chances d'être un Streamable cassé). Le vieux
transport : un GET ouvre le flux, le premier événement `endpoint`
nomme où POSTer, les réponses arrivent sur le flux. Stdio : bureau
seulement (relancé via le shell de login pour que npx/uvx résolvent
depuis une app lancée du Dock) — sur Android, état « non supporté »
honnête.

**Les erreurs de transport.** 401 → auth requise (avec le défi
WWW-Authenticate) ; 403 avec `insufficient_scope` → auth requise
(permissions à renouveler) ; 404 sur requête porteuse de session →
session expirée ; le reste → échec HTTP avec détail tronqué. Un
client HTTP sans délai de lecture (un flux peut rester muet des
minutes).

**La configuration (`McpServerConfig.kt`).** Le fichier `.mcp.json`
AU FORMAT CLAUDE CODE/CURSOR (« une config écrite pour eux marche
ici inchangée ») : racine `mcpServers` (ou `servers`), chaque entrée
nommée porte `type` (http, sse, stdio — `streamable-http` et
variantes acceptés), `url`, `headers`, `command`/`args`/`env`,
`disabled` ; sans `type`, une URL implique HTTP, une commande
implique stdio ; les entrées sans sens (ni URL ni commande) sont
sautées. L'IDENTITÉ du serveur — ce qui lie approbation et
signatures stockées — est l'URL (http/sse) ou la commande + arguments
(stdio) : pointer ailleurs est un AUTRE serveur, qui exige une
nouvelle approbation. Le fichier se réécrit dans le même format.

**Les outils distants (`McpRemoteTool.kt`).** Nommés
`mcp__<serveur>__<outil>` dans les caractères que tous les
fournisseurs acceptent, 64 au plus, dédoublonnés par suffixe (deux
serveurs dont les noms se nettoient pareil survivent tous deux) ;
MUTANTS sauf `readOnlyHint` — donc soumis au même gate que les
écritures locales ; la description préfixée du nom du serveur,
tronquée à 1 000 ; le résumé d'arguments pour la permission porte
les trois premiers arguments scalaires.

**La normalisation des schémas tiers.** Les serveurs écrivent du
JSON Schema complet (`$ref`, `$defs`, `oneOf`, tableaux de types,
formats inconnus de Gemini) ; les fournisseurs acceptent chacun un
sous-ensemble différent et UN MAUVAIS SCHÉMA ÉCHOUE LA REQUÊTE
ENTIÈRE — donc les schémas se réduisent avant envoi au sous-ensemble
qui veut dire quelque chose pour le modèle : `$ref` inlinés bornés en
profondeur (un type récursif finit en objet simple), tableaux de
types réduits au premier non-null, enums de chaînes seulement,
`anyOf` consolidé (une seule option : dépliée ; des nulls écartés),
`items` par défaut pour les tableaux, liste blanche de clés gardées,
et le niveau supérieur FORCÉ en schéma objet (les propriétés du
serveur, quoi qu'il ait envoyé).

**OAuth 2.1 (`McpOAuth.kt`).** Le flux conforme à la spécification
MCP : le 401 du serveur nomme ses métadonnées de ressource protégée
(RFC 9728) qui nomment son serveur d'autorisation (sans elles : le
sien propre) ; les métadonnées de l'autorisation (RFC 8414 ou
OpenID) donnent les points de terminaison ; l'app s'ENREGISTRE
dynamiquement (RFC 7591) comme client public pour l'URI de
redirection de cette signature (un port de bouclage diffère à chaque
fois et les serveurs comparent l'URI exactement) ; le navigateur
fait le code d'autorisation avec PKCE S256 OBLIGATOIRE (un serveur
qui ne le supporte pas est refusé avec un message qui conseille le
jeton en en-tête) et le paramètre `resource` (RFC 8707) ; le code
s'échange contre des jetons, le refresh les renouvelle (rotation
tolérée : l'ancien tient quand aucun nouveau ne vient). Les jetons
portent leurs points de terminaison et leur identifiant client pour
se rafraîchir seuls, et la ressource pour laquelle ils ont été
émis.

## 6. Le serveur MCP (`HttpMcpServer.kt`, `CodeAssistMcpServer.kt`)

**Le transport maison sans conteneur.** Un `ServerSocket` HTTP/1.1
liait 127.0.0.1 UNIQUEMENT (non configurable par intention : le
transport n'authentifie personne et ses outils éditent le projet
ouvert — le port ne doit pas être atteignable hors de l'appareil ;
un client de bureau passe par `adb forward`, qui appelle le
127.0.0.1 de l'appareil lui-même). La vérification locale couvre le
cas que la liaison ne couvre pas : un navigateur braqué sur le port
par un nom d'hôte rebondi (DNS rebinding) — l'en-tête `Host` doit
être local et un `Origin` PRÉSENT doit être local (un client natif
n'en envoie pas : absent = accepté ; présent et étranger = refusé
403). Le corps est plafonné (16 Mio — un Content-Length mensonger ne
doit pas allouer le processus jusqu'à l'OOM), la socket a 30 s de
délai (un pair qui se connecte puis bloque ne cloue pas le fil),
Connection: close sans keep-alive, un fil par connexion.

**La lecture d'octets exacte.** L'en-tête se lit octet par octet en
ISO-8859-1 et le corps prend EXACTEMENT `Content-Length` octets :
ce compteur compte des OCTETS, et décoder d'abord laissait tout
corps à caractère non ASCII (un emoji dans un edit, un chemin
accentué) court de la différence — la lecture bloquait jusqu'au
délai de socket. (Leçon consignée dans le source, à reprendre.)

**Les sessions et le mode requête-réponse.** Sessions créées au
premier `initialize` et clé par l'en-tête `Mcp-Session-Id` du
serveur ; les messages suivants routés par cet en-tête. Les réponses
aux requêtes partent en corps JSON-RPC sur le POST lui-même (mode
requête-réponse) ; le GET des clients qui veulent un flux reçoit
405 (les SDK replient sur le mode requête-réponse) ; le DELETE
ferme la session. Les erreurs de transport partent en enveloppe
JSON-RPC (id null) écrite à la main (les fabriques du SDK refusent
un id null).

**Les jobs longs découplés (`task_status`).** Au-delà de ~90 s, le
travail CONTINUE en tâche de fond et le client reçoit immédiatement
« toujours en cours — appelez `task_status`, ne le relancez pas » :
un premier build qui dépassait le délai du relais mourait avec son
résultat, et un client impatient relançait le même travail. La
consultation de `task_status` rend le résultat quand il est prêt.

## 7. Ce que JoIDE retient pour les lots Q et R

- Le banc et la bascule : ordre utilisateur, bancs différenciés
  (minute → délai énoncé ou 60 s ; jour/quota → 1 h ; auth → à
  vie), bascule seulement sur les genres qui peuvent différer par
  clé, jamais après sortie, message agrégé honnête, seuil 120 s
  pour attendre plutôt qu'échouer.
- Le pacer : fenêtre 60 s requêtes+jetons, attendre avant, enregistrer
  après, limites configurées > énoncées > observées (×9/10), un pacer
  par clé+modèle.
- Gemini : wire complet (functionCall entiers, functionResponse,
  thoughtSignature écho, corrélation par nom#N, usage soustraction
  cache), niveau contre budget selon génération, marge de réflexion
  sur le plafond, cache de contexte par session à politique
  conservatrice, strippage `additionalProperties`, Flash d'abord.
- Les quotas : code d'abord, wording raffine (plafond zéro = hors
  plan ; jour ; minute), métrique+fenêtre+limite portés par l'erreur.
- MCP client : sous-ensemble assumé, capacités non déclarées,
  pagination bornée, aplatissement descriptif, états publiés,
  reconnexion unique, refresh unique, RETRY SEULEMENT PROUVÉ NON
  EXÉCUTÉ, identité = URL ou commande, outils mutants sauf
  readOnlyHint, normalisation de schémas avant envoi, OAuth 2.1
  complet.
- MCP serveur : bouclage non configurable, Host/Origin, corps
  plafonné, lecture d'octets exacte, sessions par en-tête, mode
  requête-réponse, jobs longs découplés par task_status.
