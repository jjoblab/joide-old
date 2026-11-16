# ADR-057 — Lot N : la politique de coûts — cache par la conception, compaction à hystérésis, usage exposé

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Un agent renvoie l'historique à chaque étape : il est
structurellement plus cher qu'un chat. L'ADR-É9 traite ce coût comme
une contrainte d'architecture transverse, pas une optimisation. Le
lot N en pose les trois piliers : le cache est une propriété DU
FORMAT, la compaction élague avec hystérésis et décisions
permanentes, la comptabilité est exposée dès le premier jour.

## Décision

### 1. Le cache par la conception : découpe par volatilité et préfixe stable

- Le prompt est COUPÉ PAR VOLATILITÉ : l'ancrage stable (identité,
  règles, liste d'outils) part en préfixe système écrit une fois ;
  l'état volatile (mode de permission, contexte projet vif) part en
  note de FIN d'historique, rafraîchie à chaque itération sans
  toucher un octet en amont. Chez Anthropic, la note vit en second
  bloc système APRÈS le point de rupture `cache_control` (contenu
  amont identique, cache préservé) ; chez OpenAI-compatible, en
  dernier message système.
- L'historique est APPEND-ONLY : aucun message n'est réécrit, la
  seule réécriture sanctionnée est la compaction (point 2).
- Le garde-fou `BoucleAgentTest.lePrefixeEstStableAuFilDeLaBoucle`
  exige que la requête N+1 contienne la requête N octet pour octet
  en préfixe (empreinte de la partie stable) — « une dérive
  silencieuse ne se voit que sur la facture ; ce test échoue à sa
  place ».
- La surface d'outils a un BUDGET : 16 000 caractères de noms +
  descriptions + schémas (`SurfaceOutils`), mesuré par test au lot
  O. Le chargement différé des outils est écarté (ADR-É9 : un
  préfixe caché coûte ~0,1× par requête contre un aller-retour
  entier dès qu'un outil invisible manque).

### 2. La compaction à hystérésis, décisions permanentes

`CompacteurHistorique` : rien n'est élagué sous 120 000 caractères ;
au déclenchement, UNE passe tronque à 4 000 caractères les résultats
d'outils plus vieux que les quatre plus récents ; les textes
utilisateur et assistant ne sont JAMAIS touchés ; la troncature
S'ANNONCE dans le texte (`[tool output truncated … — permanent]`).
Les décisions sont PERMANENTES : les identifiants tronqués sont
consignés dans l'historique et la session — un résultat réélargi au
tour suivant casserait le préfixe caché. Le mode DÉLÉGUÉ (fournisseur
qui gère le contexte côté serveur, p. ex. Anthropic) désactive
l'élagueur local : deux élagueurs se battraient sur le même préfixe.

La compaction par RÉSUMÉ demandé au modèle (le second mécanisme de
CodeAssist) est différée : elle exige un protocole de validation du
résumé (un modèle qui répond en appelant un outil n'a pas résumé) et
un budget d'erreurs dédié — backlog, l'élagueur local couvre le lot
N.

### 3. La comptabilité exposée dès le premier jour

`UsageLlm` (entrées, sorties, lectures de cache) est cumulé PAR TOUR
sur toutes ses requêtes (`HistoriqueAgent.ajouterUsage`), exposé dans
`ResultatTour.usage` et l'événement de fin — le pied « cached » qui
régresse est le symptôme visible d'un préfixe qui a dérivé. Les
adaptateurs relèvent les compteurs des DEUX fournisseurs
(`cache_read_input_tokens` chez Anthropic, `prompt_tokens_details.
cached_tokens` chez OpenAI) — les estimations maison (caractères ÷
4) restent interdites tant que le fournisseur rend les compteurs,
ce qui est le cas des deux (écart n° 8 de l'ADR-É9).

### 4. La passation ne facture pas pour rien

Au plafond d'itérations, le dernier tour part SANS outils avec la
consigne de rapporter changé/vérifié/restant : l'utilisateur reçoit
un état exploitable au lieu d'une erreur qui a facturé la tâche
entière et n'a rien rendu. La passation est la seule requête à
surface d'outils différente (vide) : la variation du préfixe est
assumée et documentée ici.

## Conséquences

- Les seuils (120 000 / 60 000 / 4 000 / 4 récents / budget 16 000)
  sont des constantes publiques, éprouvées par les tests — un
  réglage futur les change en un seul endroit.
- Les bornes de résultats d'outils vivent CÔTÉ OUTIL (lot O) : un
  résultat déjà borné part une fois dans le cache, le réécrire après
  coup invaliderait les tours suivants.
- Le coût des images et le régulateur préventif RPM/TPM sont le lot
  Q (ADR-É10) ; le lot N expose la structure qui les accueillera.
