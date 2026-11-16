# agent — noyau de l'agent IA (lots N et Q, ADR-055/061/062)

Module pur JVM : le modèle neutre à signatures opaques, la boucle
(prompt coupé par volatilité, historique append-only, lecture
parallèle / mutation séquentielle, réparation d'historique, attentes
visibles, plafond d'itérations en passation, compaction à hystérésis
à décisions permanentes), la taxonomie d'erreurs actionnables (les
quotas énoncés voyagent avec l'erreur), les
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

La robustesse transverse (lot Q, ADR-062) : le banc de clés à bancs
différenciés, la bascule qui ne re-émet jamais après la première
sortie, le régulateur préventif RPM/TPM qui apprend des erreurs, les
bandes d'usage et les images réduites côté client.

Le Model Context Protocol (lot R, ADR-063) : le client MCP (config
`.mcp.json` Claude Code/Cursor, Streamable HTTP + repli SSE, OAuth
2.1 complet, schémas tiers normalisés, outils `mcp__<serveur>__<outil>`
mutants sauf readOnlyHint — un appel ne se retente que PROUVÉ non
exécuté) et le serveur local (bouclage vérifié Host/Origin, corps
plafonné, lecture d'octets exacte, jobs longs découplés en
task_status), dans le paquet `jo.joide.agent.mcp`.
