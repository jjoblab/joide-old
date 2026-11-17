# ADR-056 — Lot N : le transport HTTP/SSE — OkHttp (décision D-5 du propriétaire), le SSE maison

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

La question D-5 du plan ADR-É10 proposait une implémentation maison
sur `HttpURLConnection` ; le propriétaire a tranché le 2026-11-16 :
« pour OkHttp je le préfère plutôt de créé une maison ». Cette ADR
consigne l'application de la décision : ce qu'OkHttp apporte, ce qui
reste maison, et les règles que l'ADR-É9 impose au transport.

## Décision

### 1. OkHttp 5.5.1, la seule dépendance tierce du module agent

`com.squareup.okhttp3:okhttp` (Apache-2.0, transitif kotlin-stdlib)
entre au catalogue au titre du transport du module agent — NOTICE et
`docs/third-party.md` mis à jour. OkHttp apporte ce que la maison
n'aurait pas dû réécrire : TLS moderne, suivi des redirections,
pool de connexions, retry des échecs de connexion d'avant-émission
(corps en mémoire, rejouable). Le transport du RÉSOLVEUR P4
(ADR-031) n'est PAS concerné : il reste l'implémentation maison de
`core-deps` — la décision D-5 ne touche que l'agent.

### 2. Un port unique, trois méthodes

`TransportHttp` expose `postSse`, `post`, `get`. « Un seul transport
derrière une interface » (ADR-É9) : toute la couche fournisseur est
testable hors ligne en rejouant des trames SSE enregistrées par un
faux — aucun adaptateur n'est testé contre le réseau. Les réponses
hors 2xx lèvent `EchecHttp` (statut + en-têtes + corps) : le corps
d'erreur voyage jusqu'à la taxonomie, jamais jeté.

### 3. Le SSE se PARSE maison

Le format « text/event-stream » se lit par `ParseurSse` (et son
lecteur incrémental dans `TransportOkHttp`) : trames terminées par
ligne vide, `event:`/`data:` assemblés, commentaires `:` de
battement de cœur ignorés — une trame de commentaires seuls reste
VIDE (piège éprouvé par test), la dernière trame sans ligne vide
part telle quelle et c'est l'ADAPTATEUR qui juge la complétude
(`message_stop` manquant chez Anthropic, `[DONE]` manquant chez
OpenAI : flux illisible, jamais un message tronqué muet). Cinquante
lignes éprouvées valent mieux qu'une dépendance de plus ; le
`okhttp-sse` officiel (flux par callbacks) aurait couplé la lecture
au fil d'OkHttp et interdit le rejoue en tests.

### 4. Les règles de l'ADR-É9 s'appliquent au transport

- **Garde de lecture** (écart n° 6 : `readTimeout = 0` chez
  CodeAssist — un serveur muet tient le flux indéfiniment) : 120 s
  entre deux lectures, aucun plafond de durée totale (un tour peut
  être long), 30 s de connexion et d'écriture.
- **Jamais de retente d'un flux coupé** : un flux qui meurt en
  pleine réponse remonte en `FLUX_INTERROMPU` — le tour s'arrête, le
  texte déjà affiché n'est jamais dupliqué. Les 429 ne se retentent
  pas au transport : la boucle les attend VISIBLEMENT.
- **L'annulation traverse le transport** : `jeton.onCancel` appelle
  `Call.cancel()` — le service de premier plan du lot P arrêtera un
  tour proprement.
- **Pas de trust-all** : un certificat personnalisé s'ajouterait
  PAR-DESSUS les ancêtes système, jamais à leur place (le transport
  du résolveur applique déjà cette règle, ADR-031).

### 5. Les clés ne traversent jamais le journal

L'en-tête d'authentification est posé par l'adaptateur au moment de
la requête ; le transport ne connaît pas la clé, les erreurs ne
l'échoient jamais (les messages d'erreur portent le statut et le
corps du fournisseur). Pour les serveurs locaux sans
authentification, l'en-tête est OMIS si la clé est vide — l'URL de
base libre, nettoyée des copier-coller (slash final,
`/chat/completions` déjà présent, blancs).

## Conséquences

- `FauxTransport` rejoue des trames aux deux adaptateurs : les tests
  des fournisseurs ne touchent pas le réseau.
- Les limites documentées : pas de certificat personnalisé au lot N
  (le résolveur en montre le besoin), pas de proxy — backlog si le
  propriétaire les exige.
