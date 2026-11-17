# ADR-061 — Lot Q : le fournisseur Gemini — streamGenerateContent, cache de contexte serveur, budgets de réflexion

- **Statut** : Accepted (GO du propriétaire pour les lots Q et R,
  message du 2026-11-16)
- **Date** : 2026-11-16

## Contexte

Le lot Q (ADR-É10) ajoute le troisième fournisseur : Gemini, avec
« cache de contexte serveur, budgets de réflexion ». L'ADR-É9 a
consigné les particularités du wire-protocol ; la consultation
clean-room n° 3 (note `docs/clean-room/003-robustesse-mcp.md`, § 3)
en a donné le comportement détaillé observé dans le code de
CodeAssist — sans une ligne reprise. Le modèle neutre du lot N
(`RequeteLlm`, `MessageAgent`, `PartieContenu`) reste la seule
source de vérité : l'adaptateur traduit, il n'étend pas.

## Décision

### 1. Le wire-protocol, entier

`streamGenerateContent?alt=sse` sur
`https://generativelanguage.googleapis.com` (URL de base
surchargeable pour une passerelle), clé par en-tête
`x-goog-api-key` — JAMAIS dans l'URL (les clés ne traversent ni le
journal ni les requêtes capturées). Rôles `user`/`model` ; les
résultats d'outils partent en parts `functionResponse` (le nom de
corrélation avant `#`) ; les appels arrivent ENTIERS (jamais
diffusés) — le message final les porte, l'écouteur vit les deltas
de texte et de réflexion en direct. La `thoughtSignature` reçue par
part se range dans la partie appel (`PartieContenu.appelSigne`,
signature opaque) et revient sur la `functionCall` au tour suivant,
sans quoi l'outil est refusé (« missing a thought signature »).
Gemini n'émet pas d'identifiant d'appel : le nom de l'outil sert de
corrélation, dédoublonné par suffixe `#N`.

### 2. La coupure par volatilité respectée sans rôle système

Gemini n'a pas de rôle système en conversation : l'ancrage stable
part en `system_instruction` (préfixe cachable) et la note volatile
se plie en part `<system-reminder>` du DERNIER tour utilisateur —
neuf à chaque itération, donc le préfixe stable ne dérive pas. Le
garde-fou du test de préfixe reste vert pour ce fournisseur.

### 3. Le cache de contexte serveur, politique conservatrice

L'instruction système + les déclarations d'outils — le plus gros
envoyé à chaque itération — se posent côté fournisseur
(`POST /v1beta/cachedContents`, TTL 3 600 s) et se référencent par
`cachedContent` ; le système et les outils ne partent PLUS inline
quand le cache est référencé (l'API le rejette). L'état vit par
SESSION (`CacheContexteGemini`, horloge injectée) avec la politique
de l'analyse : rien au premier tour ; pas de création sous 2 048
jetons estimés ; repli inline sur tout échec ET échec mémorisé par
clé de charge utile (jamais une retente par tour) ; recréation une
minute AVANT l'expiration. Le caching ne peut jamais être une perte
nette. Gemini gérant le contexte côté serveur, `FournisseurGemini`
déclare `compactionDeleguee() = true` : l'élagage local se
désactive quand ce fournisseur est choisi (deux élagueurs se
battraient sur le même préfixe).

### 4. Les budgets de réflexion au dialecte de la génération

Gemini 3 prend un NIVEAU (`thinkingLevel`) et rejette un budget ; la
famille 2.5 prend un budget (`thinkingBudget`) et rejette un niveau
— envoyer le mauvais échoue la requête entière. Le niveau demandé à
la construction du fournisseur (null : défaut du modèle) se traduit
selon la génération du modèle (`gemini-3.x` → niveau,
`gemini-2.x` → budget 0/2 048/8 192/16 384) ; sur un Pro,
`minimal` n'existe pas chez Gemini 3 (devient `low`) et le minimum
128 force le budget chez 2.5. `maxOutputTokens` plafonne réflexion
ET réponse ENSEMBLE : la marge de réflexion s'ajoute au plafond
demandé (plafond absolu 65 536) — sinon un effort élevé dépense
tout le plafond en réflexion et rend un tour vide coupé à
MAX_TOKENS.

### 5. L'usage et les fins de flux

`usageMetadata` : `promptTokenCount` CONTIENT les lectures du cache
— l'adaptateur soustrait, le modèle neutre garde les entrées non
cachées (la comptabilité reste comparable entre fournisseurs). Pas
d'événement terminal : usage et fin s'émettent à la fin du flux ;
`finishReason` STOP/MAX_TOKENS/SAFETY-family se classent (les
garde-fous → `CONTENU_REFUSE` actionnable) ; un flux qui n'a rien
rendu est ILLISIBLE — jamais un tour vide muet que la boucle
lirait comme une fin.

### 6. Les schémas d'outils strippés

Gemini rejette `additionalProperties` à toute profondeur : le
strippage récursif se fait à la sérialisation (les structures
mutables équivalentes passent par l'écriture compacte). Les schémas
JoIDE et les schémas MCP normalisés du lot R en profitent sans
copie.

## Conséquences

- `FournisseurGemini` suit le contrat `FournisseurLlm` du lot N —
  la boucle n'apprend rien de nouveau.
- Les tests rejouent des trames SSE enregistrées ; le cache se
  prouve à horloge fausse (création au 2e tour, échec mémorisé,
  rafraîchissement près du TTL).
- Les listes de modèles restent côté hôte (le sélecteur du chat
  porte des chaînes) ; l'adaptateur ne devine jamais un modèle.
- Le niveau de réflexion est un réglage de construction — l'UI du
  lot P peut l'exposer plus tard sans toucher le noyau.
