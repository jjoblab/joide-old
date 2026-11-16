# ADR-É9 — Analyse du code IA de CodeAssist : ce que JoIDE retient

- **Statut** : Accepted (analyse — implémentation en lots, voir 010)
- **Date** : 2026-11-16

## Contexte

L'ADR-É1 avait écarté la famille IA de CodeAssist d'un revers (« pas
d'agent dans JoIDE ») sur la seule vue des écrans. Le propriétaire
rouvre le sujet et change la méthode : « Étendre l'analyse pour les
fonctionnalités de AI (par contre pour l'IA tu dois regarder le code) ».
Cette analyse lit donc le code de l'agent, pas son apparence. La
planification qui en découle est mandatée dans le même souffle : « Tu
peux commencer à planifier les lots » (ADR-É10).

## Méthode

Sources du dépôt public `tyron12233/CodeAssist`, branche `main`
(pushé le 2026-11-11, commit `d155772` déjà analysé par le rapport
initial) : 67 fichiers téléchargés et lus, soit ≈ 18 700 lignes — la
documentation de conception (`docs/agentic-coding.md`, 423 lignes), le
contrat public (`agent-api` + son fichier de surface `.api`), le cœur
de l'implémentation (`agent-impl` : boucle, prompt système, outils,
checkpoints, tous les fournisseurs, transport, erreurs, failover,
pacer, compaction), l'intégralité du module MCP (serveur et client),
l'UI de l'agent, l'intégration IDE (`ide-core/agent`), le service
Android, et cinq fichiers de tests révélateurs du comportement.
L'analyse des wire-protocols s'appuie sur les quatre adaptateurs et
leurs décodeurs de flux, ligne par ligne. GPL-3.0 : lecture seule,
aucune ligne reprise (clean-room, ADR-008) — noms de classes cités
comme des faits, jamais comme du code à copier. Les points de
performance restent des constats de lecture, pas des mesures.

## Ce que c'est — résumé

L'agent de CodeAssist est un **assistant de codage conversationnel
embarqué** : un chat qui pilote un modèle LLM capable d'appeler des
outils (lire, chercher, éditer, exécuter) sur le projet ouvert, avec
permission de l'utilisateur à chaque écriture. Il tourne dans l'app
(bureau et Android), survit au changement d'écran grâce à un service
de premier plan, parle à cinq fournisseurs LLM plus toute passerelle
compatible OpenAI, et **s'expose lui-même comme serveur MCP** pour
que Claude Code ou Cursor éditent le projet de l'appareil depuis un
PC. Un système de points avec publicités récompensées offre un accès
gratuit sans clé. Le tout tient dans cinq modules Kotlin
(≈ 30 000 lignes avec les tests) et sept axes d'ingénierie peu
visibles mais décisifs : économie de tokens (prompt caching), erreurs
catégorisées, basculement multi-clés, régulation préventive,
checkpoints annulables, réparation d'historique, évaluations hors
ligne.

## Cartographie des modules

| Module | Lignes lues | Rôle |
|---|---|---|
| `services/agent-api` | ≈ 765 (+ 1 109 de surface `.api`) | Le contrat public : événements, permissions, outils, espace de travail, modèle LLM neutre — aucune dépendance moteur |
| `services/agent-impl` | ≈ 5 100 | La boucle, le prompt système, 34 outils, les checkpoints, six fournisseurs LLM, transport SSE, erreurs, failover, pacer, compaction |
| `services/agent-mcp` | ≈ 3 700 | CodeAssist **serveur** MCP (stdio, HTTP, relais hébergé) et **client** MCP (trois transports, OAuth 2.1) |
| `services/agent-ui` | ≈ 3 550 | Le chat Compose : tiroir, cartes d'outils, diffs, permissions, questions, sélecteur de modèles, usage |
| `app/ide-core/agent` | ≈ 4 150 | L'intégration IDE : service d'état, connexions MCP/skills, sessions, outils métier (modules, création de projet, tests) |
| `app/ide-android` | 195 | Le service de premier plan qui garde un tour en vie hors écran |
| tests lus | ≈ 830 | Évaluations sans LLM (modèle scripté), strictité des schémas, appels interrompus, failover, limites |

Le rapport initial (§2) notait « agent IA » dans le périmètre trop
large de CodeAssist (problème n° 13). La lecture du code nuance : la
partie IA est le module le mieux testé du dépôt et son architecture
est la plus disciplinée — couches pures, SPI public épinglé, zéro
accès moteur hors du port `AgentWorkspace`.

## Le contrat public (`agent-api`)

Un module sans dépendance moteur qui publie : `AgentEvent` (douze
événements : deltas de texte et de raisonnement, appels d'outils
début/fin/refusé, fichiers changés, tâches, attente, compaction,
fin de tour, erreur), le modèle de permission (`PermissionMode`
ASK_EACH / AUTO_ACCEPT / PLAN_ONLY, `AgentPermissionGate`,
`WriteRequest` avec diff de prévisualisation), le modèle d'outil
(`AgentTool` avec `mutating`, `preview`, `summarize`), le port
`AgentWorkspace` (≈ 34 méthodes, chacune avec un défaut dégradé — un
hôte n'implémente que ce qu'il sait faire), et le modèle LLM neutre
(`LlmRequest`, `LlmStreamEvent`, `ContentPart` fermé). Deux choix
méritent d'être retenus :

- **Les signatures opaques voyagent dans le modèle neutre** : chaque
  `ContentPart` porte un champ `signature` opaque que les
  fournisseurs remplissent (signature de raisonnement d'Anthropic,
  `thoughtSignature` de Gemini, raisonnement chiffré d'OpenAI tagué
  pour ne jamais être confondu avec celui d'un autre). Le modèle
  neutre ne connaît aucun dialecte, mais l'aller-retour
  conversation-fournisseur ne perd rien — condition nécessaire pour
  que le raisonnement survive entre les tours et que le prompt cache
  reste valide.
- **La surface est épinglée dans un fichier `.api`** vérifié par un
  test de compatibilité, avec des constructeurs dépréciés maintenus
  en bytecode pour les greffons compilés contre les versions
  précédentes. C'est la discipline que JoIDE applique déjà à ses
  propres modules (scan de pool, API 28) ; l'agent en fait bénéficier
  son SPI.

## La boucle d'agent (`AgentLoop`, 643 lignes)

La boucle enchaîne : requête → flux → exécution des appels d'outils →
résultats → recommence, jusqu'à l'arrêt du modèle ou du plafond
d'itérations (24 par défaut). Les mécanismes autour de ce squelette
sont les leçons :

1. **Le prompt est coupé par volatilité** : l'ancrage stable
   (identité, règles, liste d'outils, instructions du projet) part en
   préfixe système ; l'état volatile (mode de permission, contexte
   projet vif) part en message système **en fin d'historique**,
   rafraîchi à chaque itération sans toucher un octet en amont — et
   écrit une seule fois, jamais déplacé (une note qui bouge est une
   édition d'historique, qui invalide le cache du prompt et le
   raisonnement antérieur).
2. **Les outils de lecture s'exécutent en parallèle, les mutations
   en séquence** : un tour qui lit cinq fichiers paie une latence,
   pas cinq ; les écritures passent l'une après l'autre pour que les
   demandes de permission ne se court-circuitent pas et que les
   écritures restent ordonnées. L'ordre des résultats suit toujours
   l'ordre des appels du modèle.
3. **L'historique se répare tout seul** (`pairToolResults`) : un
   appel d'outil resté sans réponse (tour interrompu) reçoit un
   résultat « interrompu » inséré au bon endroit ; un résultat orphelin
   est retiré. Sans cela, la conversation sauvée est refusée par le
   fournisseur à chaque message suivant — le bug est documenté, le
   test le rejoue, et la réparation rattrape aussi les conversations
   cassées par d'anciennes versions.
4. **Les échecs transitaires se patientent visiblement** : une limite
   de débit attend son délai (jusqu'à six fois par tour, compte à
   rebours annulable émis en événement), un refus de crédit retente
   avec un plafond de sortie réduit à ce que le compte peut payer
   (jusqu'à deux fois), une réponse coupée en pleine écriture d'appel
   redemande un découpage en morceaux plus petits (une fois). Chaque
   pathologie a son remède nommé, rien n'échoue en silence.
5. **Le plafond d'itérations se termine en passation** : au lieu
   d'un message d'erreur qui facture la tâche entière et ne rend
   rien, un dernier tour **sans outils** demande au modèle de
   rapporter ce qu'il a changé, vérifié, et ce qui reste —
   l'utilisateur reçoit un état exploitable.
6. **La compaction automatique préserve le cache** : quand la
   conversation dépasse le seuil, un résumé est demandé **avec les
   mêmes outils, prompt et réglages** que l'étape qu'il remplace,
   pour que la conversation soit relue depuis le cache du prompt au
   lieu d'être réécrite ; le résumé est validé (un modèle qui répond
   en appelant un outil n'a pas résumé) et le tour continue depuis
   lui.

## L'économie des tokens — la vraie leçon d'ingénierie

Un agent renvoie l'historique à chaque étape : il est structurellement
plus cher qu'un chat. CodeAssist traite ce coût comme une contrainte
d'architecture transverse, pas comme une optimisation :

- **Compaction à hystérésis** (`HistoryCompactor`) : rien n'est
  élagué sous 120 000 caractères ; au déclenchement, une seule passe
  tronque à 4 000 caractères les résultats d'outils plus vieux que
  les quatre plus récents, jusqu'à 60 000 ; les décisions sont
  **permanentes** (un résultat réélargi au tour suivant casserait le
  préfixe caché). Les textes utilisateur et assistant ne sont jamais
  touchés. Pour les fournisseurs qui gèrent le contexte côté serveur,
  un mode « délégué » désactive l'élagueur local — deux élagueurs se
  battraient sur le même préfixe.
- **Caching fournisseur exploité partout** : points de rupture
  `cache_control` positionnés par coût décroissant (outils → système
  → conversation) chez Anthropic ; cache de contexte `cachedContents`
  côté Gemini, créé seulement à partir du deuxième tour et au-dessus
  de 2 048 tokens, avec échecs mémorisés ; clé de cache par
  conversation chez OpenAI (`prompt_cache_key`) et sessions
   collantes chez OpenRouter.
- **La comptabilité du cache est exposée** : l'usage par tour affiche
   entrées, sorties **et lectures de cache**, et le pied « cached »
   qui régresse est décrit comme le symptôme visible d'un préfixe qui
   a dérivé.
- **Un test veille sur le préfixe** : l'évaluation
  `promptPrefixIsStableAcrossAToolLoop` exige que la requête N+1
  contienne la requête N **octet pour octet en préfixe** (hors le
  marqueur de cache qui avance par conception) — « une dérive
  silencieuse ne se voit que sur la facture ; ce test échoue à sa
  place ».
- **La surface d'outils a un budget** : 16 000 caractères de
  noms + descriptions + schémas, mesuré à ≈ 11 000 pour 30 outils
  (≈ 3 000 tokens). Le commentaire argue que le chargement différé
  des outils **perdrait** (un préfixe caché coûte ~0,1× par requête
  contre un aller-retour entier dès qu'un outil invisible manque) —
  décision inverse de celle des skills (voir plus bas), cohérente
  parce que les outils changent rarement et coûtent peu, les skills
  peuvent être nombreux et coûteux.

JoIDE retiendra : mesurer l'usage (entrées/sorties/cache) dès le
premier jour, tester la stabilité du préfixe, garder les décisions
d'élagage permanentes, et faire du caching un critère de conception
des formats, pas un rattrapage.

## Les fournisseurs LLM

| Fournisseur | Wire-protocol | Particularités |
|---|---|---|
| Anthropic | Messages API, SSE | strict tool use plafonné à 20 outils aux schémas fermés ; gestion de contexte déléguée au serveur ; thinking entrelacé ; recherche web serveur (≤ 5 usages) |
| OpenAI | Responses API (endpoint officiel), sinon Chat Completions | raisonnement chiffré transporté en signature opaque ; URLs de base nettoyées des copier-coller ; `reasoning_effort` plafonné |
| Gemini | `streamGenerateContent` SSE | cache de contexte serveur ; budget de réflexion (Gemini 3 par niveau, 2.5 par budget, minimum 128 forcé sur Pro) ; les appels de fonctions arrivent entiers, non diffusés |
| OpenRouter | Chat Completions réutilisé | wrapper de 54 lignes : caching explicite + sessions collantes ; OAuth PKCE qui échange un code contre une **clé possédée par le compte de l'utilisateur** |
| CodeAssist Free | Messages API via passerelle | offre gratuite à points (publicités récompensées, crédit serveur), jeton de session à la place de la clé, repli entre proxies |
| Passerelle custom | OpenAI-compatible | URL de base libre (LiteLLM, auto-hébergé), en-tête d'authentuation omis si vide (serveurs locaux) |

Le choix de structuration : **un seul transport** derrière une
interface (`sse`, `get`, `post`), ce qui rend toute la couche testable
hors ligne en rejouant des trames SSE enregistrées. Le transport ne
retente **que les échecs d'avant-émission** (jamais un flux coupé
mi-course, pour ne pas dupliquer la sortie déjà affichée) et
**délègue les 429 à la boucle**, qui les attend visiblement. Un
certificat CA personnalisé est ajouté **par-dessus les ancres
système** — la chaîne reste validée, aucun trust-all.

## La robustesse à trois étages

1. **Taxonomie d'erreurs** (`LlmErrors`, 14 catégories,
   retryable vs fatal) avec le cas d'école : un 429 gratuit de Gemini
   emploie le vocabulaire de facturation d'un quota épuisé — le
   classificateur fait gagner le 429 (transitoire, attendu) sur le
   wording, raffine ensuite par les quotas nommés (plafond zéro =
   modèle hors plan, fenêtre journalière = limite du jour, sinon
   limite à la minute), et sait extraire « il ne reste de crédit que
   pour N tokens » pour retenter avec un plafond réduit. Chaque
   message d'erreur dit quoi faire ; un 404 sans mention de modèle
   guide vers l'URL de base au lieu de blâmer le modèle.
2. **Basculement multi-clés** (`KeyFailover`) : pool par
   fournisseur + modèle + ensemble de clés, ordre utilisateur
   respecté (failover, pas round-robin — la clé principale revient
   dès qu'elle guérit), bancs différenciés par nature (60 s une limite
   à la minute, 1 h une limite du jour, à vie une clé rejetée), et
   quand tout le monde est au banc, un message agrégé honnête qui
   compte les clés par raison.
3. **Régulation préventive** (`RequestPacer`) : attendre **avant**
   la requête au lieu de récolter le 429 — fenêtre glissante de
   60 s sur requêtes et tokens, limites configurables ou **apprises**
   des erreurs (une limite énoncée par le fournisseur devient la
   nouvelle borne), un pacer par clé + modèle.

## L'espace de travail et les outils

Le port `AgentWorkspace` (≈ 34 méthodes : lectures, écritures, édits
offset, renommages sémantiques, correctifs rapides, formatage,
exécution, tâches de build, mémoire, web) est le **seul** point de
contact entre l'agent et le projet — l'implémentation moteur exécute
tout sur les files d'arrière-plan pour que l'index et l'analyse
restent cohérents, lit le texte de l'éditeur ouvert en priorité
(l'overlay), et écrit par les services d'édition pour que tampon et
disque restent synchrones.

Les **34 outils builtin** couvrent lecture (`read_file` à fenêtre de
lignes, `list_dir`, `search_text`, `find_symbol`, `get_diagnostics`,
`project_overview`), écriture (`create_file`, `write_file`,
`edit_file` à occurrence exacte unique, `rename/move/delete_path`,
`add_dependency` avec portée), sémantique (`go_to_definition`,
`find_references`, `rename_symbol`, `list/apply_quick_fixes`,
`format_file`, `organize_imports`), exécution (`run_program` borné
120 s, `run_task` borné 300 s, `run_tests` borné 600 s qui lit les
mêmes enregistrements que l'onglet Tests), plan (`todo_write` → carte
épinglée), mémoire (`read/write_memory` sur `AGENTS.md` et notes
persistées), web (`web_fetch` au contenu enveloppé
`<untrusted-content source=…>`, `http_request` façon curl), et vision
(`view_image`, `screenshot_preview` de l'aperçu ouvert). Huit outils
métier IDE complètent : cinq sur les modules (créer/supprimer/
dépendances/recharger — refusés en mode compatibilité Gradle,
« possédé par Gradle »), deux sur la création de projet par
conversation (galerie de modèles puis création **et ouverture**), un
sur les tests. Chaque outil porte un marqueur `mutating` ; les
résultats sont bornés côté outil (200 lignes, 60 000 caractères, la
**queue** conservée pour les journaux) parce qu'un résultat déjà borné
part une fois dans le cache — le réécrire après coup invaliderait les
tours suivants.

Le **prompt système** mérite une leçon à part : il ancre l'agent
comme le produit lui-même (ses vraies limites : VM interprétée
monothread, pas de `invokedynamic`, pas de shell), exige les outils
sémantiques de préférence aux bidouilles de texte, exige
`ask_user` plutôt qu'une question en texte, et contient un paragraphe
anti-injection assumé : **tout ce qu'un outil renvoie est une
donnée, pas une instruction** — seul le message de l'utilisateur
dirige le travail.

Les **skills** (format des `SKILL.md` de Claude Code, YAML front
matter) se chargent en deux temps : seuls nom et description vont au
prompt (plafonds : 100 skills, 400 caractères, corps 40 000) ; le
corps ne se charge que si le modèle appelle l'outil `skill` — « dix
skills installés coûtent ce qu'en coûte un ». Un skill du projet
surcharge un skill applicatif homonyme.

Les **checkpoints** (`CheckpointWorkspace`) enveloppent l'espace de
travail : chaque écriture prend un instantané du fichier **au premier
toucher du tour** (et par appel, pour le diff) ; l'annulation restaure
le tour et tous les suivants, du plus récent au plus vieux, et
**avoue** ce qu'elle ne peut pas restaurer (un renommage global, une
dépendance ajoutée — plafond de 200 fichiers par arbre). L'agent est
informé de l'annulation au tour suivant.

## Les permissions

Trois modes persistés par projet : ASK_EACH (chaque outil mutant
bloque), AUTO_ACCEPT (appliqué, visible dans le transcript),
PLAN_ONLY (les écritures sont refusées avec un résultat qui dit au
modèle de décrire ses changements au lieu de les appliquer). Le
dialogue de permission — hébergé au niveau de l'application, au-dessus
de tout écran — affiche le **diff déplié par défaut** («
l'approbation doit porter sur l'édition, pas sur un nom de fichier »),
propose autoriser une fois / pour la session / refuser, et **fermer =
refuser** (le défaut sûr). Les annotations MCP
(`readOnlyHint`, `destructiveHint`, `openWorldHint`) sont dérivées du
modèle d'outil — sans elles, « lire un fichier » et « supprimer un
chemin » se ressemblent et un client confirme tout ou rien.

## MCP — les deux sens

**Serveur** (CodeAssist exposé) : trois hébergements, même câblage —
stdio (process séparé que Claude Desktop lance), HTTP Streamable sur
127.0.0.1:8765 (serveur maison sans conteneur, octet par octet en
ISO-8859-1 pour prendre exactement `Content-Length` **octets** — le
commentaire documente le bug d'un emoji dans un `edit_file` qui
bloquait la lecture), et un **relais hébergé** : un seul WebSocket
sortant vers une URL publique à secret de 256 bits (« détenir l'URL
est toute l'autorisation ») qui rejoue chaque requête HTTP contre le
serveur local. La sécurité du transport HTTP : bouclage non
configurable, vérification de l'en-tête `Host` et de `Origin`
(anti DNS-rebinding), corps plafonné à 16 Mio, socket à 30 s —
assumé : « le port ne doit pas être atteignable hors de l'appareil ».
Les **jobs longs découplés** (`task_status`) : au-delà de 90 s, le
travail continue et le client reçoit « toujours en cours — appelez
`task_status`, ne le relancez pas » (un premier build Android qui
dépassait le timeout du relais mourait avec son résultat). Un
**serveur FTP** (contrôle 8021, passif 8022 fixe pour le `adb
forward`) sert de boîte de dépôt d'assets binaires vers `assets/`,
avec confinement des chemins à la racine — le seul des trois serveurs
à le vérifier.

**Client** (CodeAssist consommateur) : écrit maison en coroutines
parce que les transports du SDK officiel reposent sur `java.net.http`,
absent d'Android. Trois transports (Streamable HTTP, SSE historique,
stdio bureau seulement — relancé via le shell de login pour que
`npx`/`uvx` résolvent depuis une app lancée du Dock). Configuration
par fichier `.mcp.json` **au format Claude Code/Cursor** (« une
config écrite pour eux marche ici inchangée »). OAuth 2.1 complet
conforme à la spécification MCP (métadonnées RFC 9728/8414,
enregistrement dynamique RFC 7591, PKCE S256 obligatoire, paramètre
`resource` RFC 8707). Les schémas tiers sont **normalisés avant
envoi** aux fournisseurs (`$ref` inlinés bornés, tableaux de types
réduits, enums de chaînes seulement — Gemini les exige) parce qu'un
mauvais schéma fait échouer la requête entière. Les outils distants
sont nommés `mcp__<serveur>__<outil>`, mutants sauf `readOnlyHint`
donc soumis au même gate. Et la règle de sécurité clé : **un appel
n'est retenté que si le serveur n'a preuve pas exécuté l'outil** —
un outil mutant n'est jamais lancé deux fois ; un échec ambigu
répond honnêtement « il a pu s'exécuter ou non ». Un serveur issu
d'un fichier de projet (un dépôt cloné n'est pas un choix de
l'utilisateur) reste non approuvé tant que l'utilisateur ne l'a pas
approuvé, et l'approbation est liée à l'**identité** du serveur (URL
ou commande) — pointer ailleurs exige une nouvelle approbation.

## L'UI de l'agent

Le même chat est hébergé trois fois (fenêtre d'outil bureau, tiroir,
onglet plein écran de l'accueil) — seul l'en-tête s'adapte. Le
modèle d'état est **le service** : l'UI collecte des `StateFlow` et
ne déduit rien. Les leçons d'UI :

1. **Le travail se replie** : tout ce qui précède le dernier appel
   d'outils devient un bloc pliable (« N outils · M fichiers
   modifiés ») ; chaque appel est une ligne à statut, dépliable en
   arguments et résultat ; le raisonnement est visible pendant qu'il
   coule, replié en une ligne ensuite.
2. **Un long silence ne doit jamais se lire comme un blocage** : la
   ligne d'état au-dessus de la saisie porte un libellé vivant (« en
   cours d'écriture », « exécute ⟨outil⟩ », « en attente ») **et un
   chronomètre du tour** qui tourne à la seconde.
3. **Le suivi du bas se pilote au layout mesuré**, pas aux changements
   de messages (un scroll émis depuis un changement de contenu part
   avant le layout et rate) ; le bouton d'envoi devient le bouton
   d'arrêt — pas de second bouton qui apparaît.
4. **L'erreur est actionnable en un geste** : sur la dernière erreur,
   un bouton « utiliser le modèle X » bascule et relance (le cas
   « modèle que le compte ne peut pas utiliser » sans faire deviner
   le sélecteur) ; chaque message utilisateur porte son annulation
   (« défait ce tour et les suivants »).
5. **Les images sont réduites côté client** avant envoi (bord long
   1 568 px, JPEG qualité 85, laissées telles quelles sous 1 Mo) —
   « les fournisseurs les redimensionnent pareil, l'original de
   plusieurs Mio ne coûte que du temps d'envoi ».
6. **Le diff est Myers borné** (2 000 étapes de recherche, repli
   honnête « tout le milieu remplacé » au-delà), hunks à 3 lignes de
   contexte, teintes opaques (rien ne compose avec alpha — le fond
   est l'éditeur, imprévisible).
7. **La consommation se lit sans ouvrir rien** : pied d'usage par tour
   (entrées / sorties / cache), bande d'alerte à 80 % de la limite
   (« comme Claude avant une limite ») qui « ne harcèle jamais
   en dessous du seuil », compteurs par jour/mois et remise à zéro
   dans N heures.
8. **Les questions à l'utilisateur sont des boutons** : l'outil
   `ask_user` (1 à 4 questions, 2 à 4 options, choix multiple),
   cartes paginées une question à la fois, « Autre » en texte libre
   toujours proposé par l'hôte, « passer » qui répond rien — et le
   modèle reçoit la consigne de ne jamais terminer une réponse par
   une question en texte.
9. **La saisie** : Entrée envoie, Maj+Entrée saute une ligne, cinq
   lignes maximum, complétion des commandes slash et des mentions
   `@fichier` au point d'insertion (une adresse électronique n'est
   pas une mention — la regex exige un début de mot).

## Android

Le service de premier plan (`AgentForegroundService`) ne détient
rien : un objet compagnon **observe** l'état (tour en cours, relais
actif) et démarre/met à jour/arrête le service — la notification
porte ce que fait l'agent (raison d'attente, outil en cours) et un
bouton **Stop** ; elle ne sonne qu'une fois (`setOnlyAlertOnce`) bien
que chaque pas d'outil la remette à jour. Le démarrage ne se tente
que premier plan (Android 12+ refuse depuis le fond) et l'échec est
documenté : « l'agent continue tant que l'app est devant ». Sans ce
service, un tour qui court des minutes meurt au changement d'écran
« sans rien dire ».

## L'offre gratuite

Le fournisseur « Free AI » facture un portefeuille de points par
étape (pas par token), rechargé par des publicités récompensées dont
le crédit est validé **côté serveur au callback signé** (l'UI
interroge le solde à une échelle de reprise après la pub, 1,5 à 10 s
selon le palier, et s'arrête dès qu'il bouge). « Configuré » veut
dire « connecté » : connexion GitHub/Google, jeton de session à la
place de la clé. Une bande sous la saisie passe en rouge sous 15
points (« environ un pas de plus d'un Haiku »). L'API est bridée par
le proxy (32 K tokens de sortie par étape, 16 K sur le repli) et le
client s'y adapte (plafonds de compaction et de tokens dédiés). Le
code du proxy dit sa limite : « Rien ici n'est une limite ; le client
est open source ». C'est un modèle d'affaires complet (comptes,
points, pubs) — pas seulement une fonctionnalité d'agent.

## Les tests et évaluations — comment tester sans LLM

Le principe énoncé en tête du fichier d'évaluation : « scripter le
modèle fixe la seule variable qu'un run hors ligne ne peut fournir —
le jugement du modèle — et laisse sous test ce que ce dépôt possède
réellement ». La boucle, les outils et le **constructeur de requêtes
réel** tournent ; seul le transport est un faux qui rejoue des tours
SSE enregistrés. Six critères, jugés sur **l'état final du fichier,
pas sur le transcript** : une édition réussie, le rétablissement
après un premier appel d'outils qui échoue, l'usage qui somme toutes
les requêtes du tour, la stabilité du préfixe (voir économie), le
budget de surface d'outils, et la strictité de chaque schéma (un
schéma ouvert désactive silencieusement le mode strict). Autour :
les adaptateurs testés contre des trames SSE enregistrées, le
failover testé à horloge fausse, les appels interrompus rejoués en
quatre scénarios dont « conversation sauvegardée cassée par une
ancienne version, réparée à la reprise ».

## Ce qui est bien fait — les leçons à retenir

1. Un **SPI pur** (agent-api) sans dépendance moteur, épinglé et
   testé contre sa propre surface.
2. Le **modèle neutre à signatures opaques** : la conversation ne
   connaît aucun dialecte fournisseur mais ne perd aucun état de
   raisonnement.
3. Le **prompt coupé par volatilité** et l'**historique
   append-only** : le cache est une contrainte de conception, testée
   par un test dédié qui échoue à la place de la facture.
4. La **réparation d'historique** (`pairToolResults`) : une
   conversation interrompue reste toujours valide pour le
   fournisseur.
5. La **taxonomie d'erreurs qui désambiguïse** (429 vs facturation)
   et propose une action par erreur, parfois une retente adaptée
   (plafond réduit).
6. **Failover à l'ordre utilisateur + pacer préventif qui apprend** :
   la robustesse à trois étages indépendants.
7. Le **port unique de l'espace de travail** avec défauts dégradés,
   exécution sur les files moteur, lectures overlay-preferred,
   écritures par les services d'édition.
8. Les **permissions sur le diff**, le défaut sûr, les annotations
   dérivées du modèle d'outil.
9. Le **parallélisme lecture / séquence mutation** des appels
   d'outils, l'ordre des résultats garanti.
10. Les **bornes côté outil** (jamais côté transcript) pour préserver
    le cache ; la queue des journaux conservée.
11. Les **jobs longs découplés** (`task_status`, « ne le relancez
    pas ») et le **retry seulement prouvé non exécuté** côté client
    MCP.
12. Le **contenu web enveloppé** `<untrusted-content>` et le
    paragraphe anti-injection du prompt système.
13. Les **checkpoints au premier toucher** avec un rapport d'annulation
    honnête sur ce qui ne se restaure pas.
14. Les **sessions à double moitié** (transcript vu + historique
    modèle) écrites entières et atomiquement après chaque tour ; un
    index inter-projets pour les reprendre sans ouvrir chaque projet.
15. Le **service de premier plan piloté par observation**, sans état,
    notification muette à bouton Stop.
16. Les **évaluations hors ligne à modèle scripté** jugeant l'état
    final, avec gardes-fous cache, budget et strictité.
17. Le **prompt système ancré dans le produit** (vraies limites de
    la plateforme, préférence aux outils sémantiques, `ask_user`
    plutôt qu'une question).

## Faiblesses et risques — à ne pas reproduire

| # | Constat | Détail | Leçon JoIDE |
|---|---|---|---|
| 1 | Clés API en clair | `prefs.properties` en texte, clés de secours une par ligne ; chiffrement repoussé, champ masqué en UI seulement | chiffrer dès la v1 (keystore Android / préférences chiffrées), jamais de clés de secours en clair |
| 2 | Aucun confinement de chemin | les espaces de travail acceptent les chemins absolus tels quels ; seul le serveur FTP vérifie la racine — le mode stdio avec auto-accept peut sortir du projet | confinement **par construction** dans l'espace de travail (racines nommées, ADR déjà dans nos habitudes) |
| 3 | Serveur MCP sans authentification | assumé et documenté, mais la sécurité tient au seul bouclage ; le relais met un secret de 256 bits **dans l'URL** (journaux, non révocable sans changer de lien) | authentification locale dédiée si serveur exposé ; secret hors de l'URL |
| 4 | Heuristiques sur le wording | regex sur les messages d'erreur des fournisseurs (« try again in Ns », « can only afford N ») — une reformulation casse la classification | s'appuyer aux codes et en-têtes d'abord, wording en repli |
| 5 | Listes de modèles codées en dur | identifiants « prospectifs » vieillissants, amortis par `listModels` | aucun identifiant de modèle en dur ; découverte dynamique |
| 6 | Backoff sans jitter | 1 s / 2 s / 4 s fixes — meute en multi-client ; `readTimeout = 0` (un serveur muet tient le flux indéfiniment) | jitter obligatoire, garde de lecture |
| 7 | Mono-fichier de 1 661 lignes | `ChatDrawer.kt` ; les feuilles référencées éparpillées | découpage par fichier dès la conception |
| 8 | Estimations grossières | caractères ÷ 4, image = 1 500 tokens ; compaction en caractères là où les compteurs serveur existent | mesurer en tokens dès que le fournisseur les rend |
| 9 | Sessions réécrites entières | à chaque tour, images en base64 dans le JSON — pas de rotation | rotation / plafond de fichier |
| 10 | GIF ré-encodé statique, diff sans surlignage intra-ligne | pertes visibles assumées | à la marge |
| 11 | Le gratuit par la publicité | modèle d'affaires complet (comptes, points, pubs récompensées) qui engage un backend — pas une fonctionnalité d'agent | décision propriétaire, hors périmètre produit par défaut |

## Verdict pour JoIDE

**Adopter le principe, pas le code.** L'agent de CodeAssist est la
partie la plus disciplinée de ce dépôt et la référence la plus
crédible d'un agent de codage embarqué sur appareil — mais il est en
Kotlin/coroutines sur un substrat Compose, GPL-3.0, et ses choix
d'implémentation (OkHttp, kotlinx-serialization, regex de wording,
clés en clair) ne sont pas ceux de JoIDE (Java 17, parseurs maison
ADR-028, `CancelToken`, minSdk 28, ADR-009). JoIDE retiendra :

- **l'architecture** : module pur à SPI épinglé, port d'espace de
  travail unique, modèle neutre à signatures opaques, boucle à
  parallélisme lecture/séquence mutation, réparation d'historique ;
- **l'économie** : prompt coupé par volatilité, compaction à
  hystérésis à décisions permanentes, comptabilité du cache exposée
  et testée, budget de surface d'outils ;
- **la robustesse** : taxonomie d'erreurs actionnables, failover,
  pacer apprenant, attentes visibles, passation au plafond
  d'itérations ;
- **la sécurité** : permissions sur diff à défaut sûr, contenu web
  enveloppé, prompt anti-injection, confinement à ajouter par
  construction, clés chiffrées dès la v1, retry seulement prouvé non
  exécuté ;
- **la méthode de test** : évaluations à modèle scripté jugeant
  l'état final, trames SSE rejouées, gardes-fous cache/budget.

À trancher par le propriétaire (repris dans ADR-É10) : le périmètre
fournisseurs de la v1, l'offre gratuite (modèle d'affaires, backend à
créer), MCP client et/ou serveur, le serveur FTP, et l'ordre des lots
face à la piste UI v2.
