# ADR-031 — Transport HTTP : HttpsURLConnection, TLS vérifié, jamais de clair

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

`docs/toolchain.md` promettait pour P4 un « résolveur maison, transport
HTTP vérifié TLS ». `java.net.http.HttpClient` (JDK 11) n'existe pas
au niveau 28 ; `java.net.HttpURLConnection` et
`javax.net.ssl.HttpsURLConnection` y figurent (vérifié dans
`api28-signatures`).

## Décision

`jo.joide.deps.repo.HttpTransport` sur `HttpsURLConnection` :

- **https seul** : une URL `http://` est refusée d'entrée, sans
  requête — le clair ne touche jamais le réseau ;
- **TLS et vérification d'hôte par défaut, jamais désactivés** : aucun
  `HostnameVerifier` ni `SSLContext` maison — un certificat
  inconnu échoue (preuve par test : un serveur local au certificat
  auto-signé est rejeté, `HttpTransportTest`) ;
- **redirections à la main** (301/302/303/307/308, maximum cinq,
  `Location` relative ou absolue), sans rétrogradation
  https vers http ;
- **plafonds** : connexion 15 s, lecture 30 s, réponse 512 Mio par
  défaut (réglages `Settings`) ;
- **404 = introuvable** (état normal pour un checksum non publié) ;
  tout autre statut est une erreur explicite ;
- annulation coopérative entre les lectures (jeton de la
  configuration) ; les échecs se rendent en `TransportResponse.error`
  (jamais d'exception vers le résolveur).

## Options

| Option | Verdict | Raison |
|---|---|---|
| `java.net.http.HttpClient` | écartée | absent du niveau 28 |
| OkHttp / Apache HttpClient | écartée | dépendance tierce, obligations NOTICE, surface inutile |
| `HttpsURLConnection` (retenue) | retenue | dans le niveau 28, TLS vérifié par défaut, zéro dépendance |

## Conséquences

- HTTP/2, pipelining et pools ne sont pas utilisés : les dépôts Maven
  servent du GET séquentiel ; le parallélisme est au résolveur
  (plafond de téléchargements), pas au transport.
- Un dépôt interne en `http://` doit se faire servir par un miroir
  `https` ou un `Repository.Local` — la chaîne d'approvisionnement ne
  négocie pas.
- Les tests réseau réels (miroir public) ne sont pas dans le harnais :
  la vérification TLS est prouvée localement par rejet du certificat
  auto-signé, la politique de redirection par prédicat pur.
