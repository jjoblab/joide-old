# agent — noyau de l'agent IA (lot N, ADR-055)

Module pur JVM : le modèle neutre à signatures opaques, la boucle
(prompt coupé par volatilité, historique append-only, lecture
parallèle / mutation séquentielle, réparation d'historique, attentes
visibles, plafond d'itérations en passation, compaction à hystérésis
à décisions permanentes), la taxonomie d'erreurs actionnables, les
permissions (gate, trois modes), la persistance des sessions (double
moitié, écriture atomique) et deux fournisseurs : Anthropic
(Messages API) et OpenAI-compatible (Chat Completions, URL de base
libre).

- Aucune dépendance Android, aucune brique : le port
  `EspaceTravailAgent` et les outils v1 vivent côté ide-integration
  (lot O).
- Une seule dépendance tierce : OkHttp (D-5, ADR-056).
- JSON : parseur maison `core-deps/json` (ADR-028) + écriture
  compacte `JsonEcrire`.
- Les tests tournent hors ligne : trames SSE rejouées par un faux
  transport, modèle scripté jugeant l'état final, garde-fous
  « préfixe stable » et « budget de surface d'outils ».
