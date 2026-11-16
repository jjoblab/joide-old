# ADR-063 — Lot R : le client MCP (client puis serveur) et la sécurité MCP locale

- **Statut** : Accepted (GO du propriétaire pour les lots Q et R,
  message du 2026-11-16 ; D-4 diffère MCP au lot R, client avant
  serveur ; D-7 écarte le relais hébergé et le FTP de v1)
- **Date** : 2026-11-16

## Contexte

Le lot R (ADR-É10) porte le Model Context Protocol dans ses deux
sens : le CLIENT d'abord (lire `.mcp.json` au format Claude
Code/Cursor, transport Streamable HTTP + repli SSE, OAuth 2.1
complet, normalisation des schémas tiers, outils
`mcp__<serveur>__<outil>` mutants sauf `readOnlyHint`, retry
seulement prouvé non exécuté, approbation liée à l'identité du
serveur), puis le SERVEUR (exposer les outils JoIDE sur un bouclage
vérifié Host/Origin, jobs longs découplés `task_status`). La note
clean-room n° 3 (§ 5 et 6) consigne les comportements observés dans
le code de CodeAssist ; l'implémentation JoIDE est propre, pur
module `agent`, zéro dépendance de plus (le JSON est le parseur
maison, le transport est le port OkHttp du lot N).

## Décision

### 1. Un paquet `mcp` dans le module `agent`

Le client ET le serveur vivent dans `jo.joide.agent.mcp` — ils ne
dépendent que de ce que le module dépend déjà (core-platform,
core-deps, OkHttp) : aucun type Android, aucune brique, le graphe
des modules (ADR-011) est inchangé. Le serveur expose les outils par
un port minimal (`OutilsServeur`), l'hôte y met ses `OutilAgent` du
lot O.

### 2. Le port transport s'étend ADDITIVEMENT

Le client MCP passe par le MÊME port `TransportHttp` que les
fournisseurs — « un seul transport derrière une interface » rend
toute la couche testable hors ligne. Trois méthodes y sont ajoutées
(défauts qui refusent honnêtement, implémentées par OkHttp et le
faux de test) : `postOuvert` (le corps se lit ligne à ligne — le
Streamable HTTP décide au vu des EN-TÊTES si le corps est un JSON
complet ou un flux d'événements et doit s'arrêter à SA réponse), 
`getSse` (le flux vivant du repli SSE historique), `supprimer` (le
DELETE de clôture de session). La règle du lot N tient : hors 2xx,
l'erreur `EchecHttp` voyage avec son corps, jamais jetée.

### 3. Le client — le sous-ensemble assumé

Poignée de main `initialize` (protocole 2025-06-18 proposé, celui du
serveur fixé, aucune capacité déclarée — un serveur bien élevé ne
demande rien, un {@code ping} reçoit sa réponse vide, le reste reçoit
method-not-found), `tools/list` paginé mais BORNÉ (vingt pages — un
curseur qui boucle ne peut pas suspendre le client), `tools/call`
aplati : blocs texte joints, images supportées listées, ressources
texte ou notées binaires, liens et audio DÉCRITS plutôt que perdus,
contenu structuré seulement sans texte, plafond 40 000 caractères
avec coupure qui conseille, `isError` honnête. Les délais : 30 s de
poignée, 180 s d'appel. Les demandes du serveur répondent hors fil ;
la déconnexion complète tous les appels en attente avec la cause.

### 4. LA règle de sécurité — retry seulement PROUVÉ non exécuté

Le gestionnaire retente un appel UNIQUEMENT quand le serveur a prouvé
qu'il n'a pas exécuté l'outil : session expirée (404 sur une requête
porteuse de session), identifiants refusés avant examen (401/403). 
Dans ces deux cas : une reconnexion (ou un refresh de signature) puis
l'appel repart. Toute autre rupture EN COURS d'appel répond
honnêtement « la connexion a cassé pendant l'appel : il a pu
s'exécuter ou non » — un outil mutant n'est JAMAIS lancé deux fois.
Un échec JSON-RPC du serveur ou un délai ne se retentent pas non
plus. Les états publiés (DESACTIVE/CONNEXION/CONNECTE/AUTH_REQUISE/
ECHEC/NON_SUPPORTE) et la version d'outils incrémentée permettent à
l'UI d'afficher sans déduire.

### 5. Les transports et la configuration

Streamable HTTP : chaque message est un POST sur l'URL unique, la
réponse sur le POST (JSON ou flux), l'identifiant de session écho en
en-tête, `MCP-Protocol-Version` après négociation, DELETE best-effort
daemon ; PAS de flux GET permanent (l'agent n'a besoin que des
réponses à ses propres requêtes). Le repli SSE historique : un POST
refusé 4xx par le Streamable retente une fois le vieux transport
(GET flux + `endpoint` + POST), l'échec ORIGINAL rapporté si le
repli échoue aussi. La configuration lit le format `.mcp.json`
Claude Code/Cursor (racine `mcpServers` ou `servers`, `type`
http/sse/stdio et inférence URL/commande, `headers`, `disabled`) ;
les entrées sans sens sautées ; l'IDENTITÉ est l'URL ou la
commande+arguments — les signatures stockées et l'approbation se
lient à elle : pointer ailleurs est un AUTRE serveur. stdio est
lu mais jamais lancé ici (NON_SUPPORTE honnête sur Android).

### 6. OAuth 2.1 complet sur le port transport

RFC 9728 (métadonnées de ressource protégée, du défi du 401 puis les
emplacements bien connus) → RFC 8414/OpenID (points de terminaison) →
RFC 7591 (enregistrement dynamique comme client PUBLIC pour l'URI de
redirection de CETTE signature) → PKCE S256 OBLIGATOIRE (un serveur
sans S256 est refusé avec le conseil du jeton en en-tête) → RFC 8707
(paramètre `resource`, l'URL canonique du serveur comme audience) →
échange et refresh (rotation du refresh tolérée). Les secrets partent
en corps form-encodé, JAMAIS dans une URL. La signature se stocke
chiffrée côté hôte et se rafraîchit avant l'échec (périssable à une
minute de son expiration).

### 7. La normalisation des schémas tiers AVANT envoi

`$ref` inlinés bornés en profondeur (12 — un type récursif finit en
objet simple), tableaux de types réduits au premier non-null, enums
de CHAÎNES seulement, `anyOf` consolidé (une option dépliée, les
nulls écartés), `items` par défaut pour les tableaux, liste blanche
de clés gardées, niveau supérieur FORCÉ en objet avec les propriétés
du serveur. Un mauvais schéma ferait échouer la requête entière du
fournisseur — la réduction est un prérequis, pas un raffinement.

### 8. Le serveur local — bouclage, Host/Origin, lecture d'octets

`ServerSocket` lié à 127.0.0.1 UNIQUEMENT (non configurable par
intention) ; la vérification LOCALE couvre le DNS rebinding : l'en-tête
`Host` doit être local et un `Origin` PRÉSENT doit être local (absent
= client natif accepté, présent et étranger = 403). Corps plafonné à
16 Mio, socket à 30 s, Connection: close, un fil par connexion. La
lecture d'en-tête est OCTET PAR OCTET en ISO-8859-1 et le corps prend
EXACTEMENT `Content-Length` octets — la leçon consignée d'un emoji
qui bloquait la lecture jusqu'au délai de socket. Sessions créées à
l'initialize (réponse porteuse de `Mcp-Session-Id`), mode
requête-réponse, GET 405 (les clients SDK replient), DELETE ferme la
session. Les annotations du protocole se déduisent du modèle : les
outils `mutant` annoncent `readOnlyHint: false` — le client distant
peut confirmer, à défaut sûr il confirmera tout.

### 9. Les jobs longs découplés

Au-delà du seuil (90 s), le travail CONTINUE sur un pool borné et le
client reçoit immédiatement « toujours en cours — appelez
`task_status`, NE RELANCEZ PAS » : un premier build qui dépassait le
délai d'un client mourait avec son résultat, un client impatient
relançait le même travail. `task_status` est une pseudo-outil
(read-only) consultable sans limite, elle rend le résultat ou l'erreur
du job quand il finit.

## Conséquences

- Le graphe des modules est INCHANGÉ (les tests de tools-test le
  revérifient) ; le catalogue des dépendances est inchangé.
- Les tests rejouent des transports faux ET prouvent le chemin RÉEL :
  un `ClientMcp` se connecte à un `ServeurMcpLocal` réellement lié
  sur un port éphémère de la JVM (initialize, list, call, erreur
  honnête, task_status au seuil raccourci, Host étranger refusé,
  corps à Content-Length exact avec caractère multi-octets).
- L'hôte câble : lecture du `.mcp.json` du projet, la section
  Connexions des Paramètres, le store chiffré des signatures —
  l'approbation des outils distants passe par le gate du lot O
  (mutants), la carte d'outils du chat du lot P les affiche comme
  tout autre outil.
- Le relais hébergé et le FTP restent hors v1 (D-7) ; le stdio reste
  une entrée de fichier jamais lancée ici.
