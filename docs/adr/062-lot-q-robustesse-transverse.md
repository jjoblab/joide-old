# ADR-062 — Lot Q : la robustesse transverse — banc de clés, bascule, régulateur préventif, bandes d'usage, images

- **Statut** : Accepted (GO du propriétaire pour les lots Q et R,
  message du 2026-11-16)
- **Date** : 2026-11-16

## Contexte

Le lot Q (ADR-É10) porte la robustesse « à trois étages » analysée
en ADR-É9 : la taxonomie d'erreurs (lot N, révisée pour porter les
quotas énoncés), la bascule multi-clés et la régulation préventive.
Le lot Q ajoute aussi « la comptabilité d'usage complète (entrées/
sorties/cache, bandes d'alerte) » et « les images (réduction côté
client) ». La consultation clean-room n° 3 (note
`docs/clean-room/003-robustesse-mcp.md`, § 1 et 2) fixe les
comportements attendus ; l'implémentation est propre, en Java 17,
pur module `agent`.

## Décision

### 1. Le banc de clés (`BancCles`) — l'ordre de l'utilisateur, des bancs différenciés

Les clés d'un fournisseur vivent dans l'ordre de l'utilisateur
(principale d'abord, secours ensuite — blâmes et doublons écartés) :
c'est un failover, pas un round-robin, la clé principale revient en
première ligne dès son temps écoulé. Les bancs sont différenciés
par nature de limite : une limite à la minute s'assoit le délai
ÉNONCÉ par le fournisseur, sinon 60 s (jamais moins d'une
seconde) ; une limite du jour, un quota ou un hors-plan s'assoit
une heure (la borne journalière dépend du fournisseur) ; une clé
rejetée (auth, 401/403) s'assoit À VIE — elle ne guérit pas seule,
le banc se reconstruit quand l'utilisateur édite ses clés. Le
message agrégé honnête compte les clés par raison et conseille quoi
faire.

### 2. La bascule (`FournisseurBascule`) — jamais après la première sortie

Le décorateur implémente `FournisseurLlm` : une requête qu'une clé
refuse D'AVANT TOUTE SORTIE repart sur la clé suivante ; dès que le
flux a émis un octet, la requête n'est plus jamais renvoyée (le
texte déjà affiché ne se duplique pas). Seuls les genres qui
peuvent être DIFFÉRENTS d'une clé à l'autre font basculer
(débit, jour, quota, hors-plan, auth) — tout ce qui échouerait
pareil partout (contexte trop long, réseau, modèle inconnu) arrête
la requête sur son erreur propre. Quand toutes les clés sont au
banc : si la première qui revient n'attend qu'une limite à la
minute COURTE (≤ 120 s — le plafond que la boucle attend déjà
visiblement), l'erreur est une `LIMITE_DEBIT` porteuse du délai et
le tour continue ; sinon l'erreur agrégée s'arrête avec ses
conseils. Un banc d'une clé ou zéro clé est un pass-through simple
(l'erreur du fournisseur part telle quelle).

### 3. Le régulateur préventif (`RegulateurDebit`) — attendre avant, apprendre des erreurs

Un pacer par couple clé + modèle (la portée que le fournisseur
compte) attend AVANT la requête au lieu de récolter un 429 :
fenêtre glissante de 60 s sur requêtes ET jetons d'entrée estimés
(≈ 4 caractères/jeton, image ≈ 1 500), `delaiPour` calcule sans
enregistrer, `enregistrer` pose la requête réellement envoyée. Les
limites viennent du plus spécifique au plus général : configurées
par l'utilisateur, puis ÉNONCÉES par le fournisseur dans une erreur
de quota (les `QuotaFournisseur` portés par `ErreurLlm`, révision
N/O), puis — sans énoncé — le plafond OBSERVÉ au moment du
déclenchement (jetons ×9/10, requêtes en vol − 1). Sans limite
connue, le régulateur n'attend jamais. L'attente préventive est
courte, découpée (annulation traversable) et silencieuse — le
précédent de la boucle : l'attente de jitter des retentes. Les
limites apprises survivent aux tours (le régulateur vit hors du
tour, détenu par la bascule ; les tests le prouvent).

### 4. Les bandes d'usage (`BandesUsage`) — prévenir, jamais inventer

L'occupation des fenêtres du régulateur (requêtes, jetons, requête
suivante comprise) se lit en trois bandes : NORMALE (< 70 %),
ATTENTION (70–90 %), CRITIQUE (≥ 90 %). Sans plafond connu,
l'occupation est 0 et la bande est NORMALE — une alerte sans
référence mesurée serait une invention. L'UI du lot P peut lire ces
bandes côté hôte sans toucher la boucle.

### 5. Les images — une partie neutre, la réduction chez l'hôte

Le modèle neutre gagne le genre `IMAGE` (`PartieContenu.image`,
type de média + base64) : une image produite par un outil se range
juste après son résultat et voyage chez les trois fournisseurs
(Anthropic : bloc `image` base64 ; OpenAI : message utilisateur en
`data:` URI ; Gemini : `inline_data`) — avant, les parties images
étaient avalées muettement. La réduction se fait CÔTÉ CLIENT,
AVANT l'insertion dans l'historique (une partie insérée est à sa
taille finale, l'historique ne se réécrit jamais) : le ré-encodage
réel appartient à la plateforme de l'hôte (`Reducteur`, BitmapFactory
côté Android) ; le module fournit `ReductionImages` (budget d'octets
décodés, défaut 1,5 Mio) et le comportement honnête : sous budget,
l'image passe telle quelle ; hors budget sans réducteur ou sur
échec, elle est remplacée par une note qui dit ce qui s'est passé —
jamais une omission muette, jamais une partie image cassée.

### 6. La révision N/O qui porte le lot

Deux révisions additives des lots N/O ont été jugées nécessaires
(consultation clean-room n° 3) : `ErreurLlm` porte le
`QuotaFournisseur` énoncé (métrique, fenêtre, limite — le champ est
transient), et le classificateur 429 détecte le PLAFOND ZÉRO (le
cas d'école de l'ADR-É9 : le modèle hors plan de la clé →
`QUOTA_EPUISE`, la bascule changera de clé). Le raisonnement
Anthropique est désormais TAGUÉ (`anthropic:`) et ne repart que
chez lui : une signature d'un autre fournisseur serait refusée par
l'API — le même garde que celui d'OpenAI du lot N. Les fabriques
`appelSigne` et `image` complètent le modèle sans rien casser
(les 100 tests du lot N restent verts).

## Conséquences

- Le module `agent` ne dépend toujours que de core-platform,
  core-deps et OkHttp : banc, bascule, régulateur et bandes sont
  purs, à horloge injectée — les critères « tests à horloge fausse
  (failover, pacer) » sont couverts.
- L'hôte (app-android, câblage de l'AgentService) construit un
  `BancCles` avec les clés de l'utilisateur (le champ clé accepte
  plusieurs clés séparées), un `FournisseurBascule` qui fabrique le
  fournisseur par clé — Anthropic, OpenAI-compatible ou Gemini —
  et garde les instances par session (les limites apprises
  survivent à la conversation).
- Les images ne sont PAS encore produites par les outils v1 : le
  genre, la sérialisation et la réduction sont en place, les
  producteurs (capture d'aperçu, rendu) viendront au backlog sans
  toucher le noyau.
- Le `web_fetch` borné du lot O (plafond 200 000 caractères,
  enveloppe non fiable) est inchangé : le critère du lot est déjà
  servi.
