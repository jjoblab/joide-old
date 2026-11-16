# ADR-É11 — L'écran de l'agent : tiroir chat, cartes d'outils, permissions sur diff

- **Statut** : Accepted (design — implémentation au lot P)
- **Date** : 2026-11-16
- **Maquette** : `design/agent.html`

## Contexte

Le lot P (ADR-É10) donne sa face à l'agent des lots N et O : le
noyau (`BoucleAgent`, `EvenementAgent`, `Permissions`,
`ErreursLlm`) et la surface d'outils (`OutilsV1`, checkpoints,
`ask_user`) existent comme modules purs testés JVM — rien ne les
montre. L'ADR-É9 a lu l'UI de CodeAssist : cartes d'outils
pliables, diff déplié avant d'approuver, questions paginées, usage
visible. Cette ADR fixe le design de l'écran agent sur la méthode
de la série (ADR-É1 à É8) : la maquette fait autorité sur la
structure, les jetons sont ceux d'ADR-É2 (Graphite), l'UI reste
mince (états purs JVM, vues du cadre, pas de Compose).

## Décision

### 1. Le tiroir agent dans l'atelier

L'agent vit DANS l'atelier, pas dans un écran séparé : le travail
de l'agent et le projet qu'il édite sont le même lieu.

- **Téléphone** : tiroir poussé depuis la droite, 360 dp au plus,
  au-dessus de l'éditeur ; le retour système le ferme (avant la
  confirmation de fermeture du projet, ordre ADR-É5 conservé).
- **Grand écran (≥ 720 dp)** : panneau amarré à droite, 360 dp.
  L'aperçu de mise en page (ADR-É7) et l'agent partagent le bord
  droit : **un seul des deux est amarré à la fois** — le bouton
  d'aperçu et le bouton agent se remplacent, jamais deux volets qui
  se disputent la largeur. Sur téléphone l'aperçu passe plein
  écran, le tiroir agent le recouvre transitoirement.
- **Entrée** : glyphe de bulle dans la barre supérieure (à côté de
  la pilule d'index) ; quand un tour est en cours, le glyphe porte
  un point d'activité — l'état est visible SANS ouvrir le tiroir.

### 2. Le transcript : l'UI ne déduit rien

Le service de premier plan (point 8) est le modèle d'état : il
émet des `EvenementAgent`, l'écran les rend. Le transcript visible
se construit à partir de ces événements — jamais déduit du texte,
jamais reconstruit de l'historique. Chaque genre a sa carte :

| Événement | Rendu |
|---|---|
| message utilisateur | bloc aligné à droite, fond surface, texte intégral |
| `TEXTE_DELTA` | le texte de la réponse se compose en direct (Markdown interdit — texte brut, liens cliquables au cadre) |
| `RAISONNEMENT_DELTA` | section « Raisonnement » visible PENDANT le flux, repliée à la fin du message (résumé « n lignes »), rouverte au tap |
| `OUTIL_DEBUT` | carte d'outil : nom mono + cible lisible, état en cours (point accent qui pulse) |
| `OUTIL_FIN` | la carte se complète : résultat en mono, plié par défaut (« 14 lignes »), dépliable ; résultat d'erreur teinté erreur |
| `OUTIL_REFUSE` | carte refusée : motif du refus (« refusé par l'utilisateur », « mode plan seul ») |
| `FICHIERS_CHANGES` | ligne tonale sous la carte : les fichiers réellement changés |
| `ATTENTE` | ligne visible « Limite de débit — reprise dans 12 s », décompte vivant, l'arrêt reste possible |
| `COMPACTION` | ligne tonale « Historique compacté : 128 400 → 61 200 caractères » — jamais silencieuse (ADR-057) |
| `FIN_TOUR` | ligne d'usage (point 6) + la raison ; PLAFOND affiche « passation » — le dernier message EST l'état |
| `ERREUR` | carte d'erreur actionnable (point 7) |

Les cartes d'outils sont PLIÉES par défaut : le transcript se lit
comme une conversation, les outils sont le détail qu'on ouvre. Un
outil qui tourne reste en tête du défilement tant qu'il n'est pas
fini.

### 3. Les permissions : le diff déplié, fermer = refuser

Le dialogue de permission est le cœur de la confiance (ADR-É9,
risque n° 3 : autorisations sur nom de fichier — à ne pas
reproduire) :

- **DEMANDE_CHAQUE** (le défaut) : chaque outil mutant bloque sur
  le gate. Le dialogue couvre TOUT l'écran : nom de l'outil, cible
  (chemin relatif), et le **diff déplié** — le vrai contenu avant /
  après, pas un résumé d'arguments. Boutons « Appliquer » et
  « Refuser ». **Fermer le dialogue = refuser** (défaut sûr,
  `Permissions.PortePermission`).
- **ACCEPTATION_AUTO** : pas de dialogue ; la carte d'outil porte
  « appliqué » et le diff reste consultable dans la carte.
- **PLAN_SEULEMENT** : pas de dialogue ; le refus motivé part au
  modèle (il décrit au lieu d'écrire), la carte le dit.

Le mode se change par la puce d'en-tête du tiroir (persisté par
projet — un projet de test n'a pas à subir DEMANDE_CHAQUE, un
projet réel ne doit pas subir ACCEPTATION_AUTO par défaut).

### 4. Le diff Myers borné

L'argument `diffApercu` du gate devient un **vrai diff** :
`DiffMyers` (pur, module agent) calcule un diff unifié entre le
contenu courant du fichier (lu par l'espace, overlay-preferred) et
le contenu après l'outil — `write_file`/`create_file` contre le
texte posé, `edit_file` contre le texte remplacé. Les outils sans
contenu textualisable (`rename_path`, `delete_path`,
`add_dependency`) montrent l'opération nommée (de → vers), pas un
faux diff. **Borné** : 400 lignes et 20 000 caractères au plus,
l'amputation s'annonce (« … 214 lignes de plus ») — le même contrat
que la console (ADR-É6) et les outils (ADR-059) ; un diff infini
est un outil de refus de permission.

### 5. Les questions : des cartes, jamais la fin d'un message

`ask_user` (1 à 4 questions, 2 à 4 options) rend une carte par
question : options en puces tapables, « Autre » révèle un champ
libre, « Passer » saute la question. Répondre à la dernière
complète l'outil — le tour repart (le fil de l'outil attendait).
Les réponses rendues au modèle : une par ligne
« question : réponse ». Après réponse, la carte montre les choix
(estompés, avec « Autre : … » le cas échéant). La consigne du
système (lot N) interdit au modèle de terminer par une question en
texte — une question arrive TOUJOURS par la carte.

### 6. L'usage par tour, affiché

Chaque `FIN_TOUR` (et chaque `ERREUR` — ce qui est facturé est
facturé) pose sa ligne : « 14 120 in · 903 out · 11 240 cached ·
6 requêtes ». Entrées, sorties ET lectures de cache (ADR-É9 : le
pied « cached » qui régresse est le symptôme visible d'un préfixe
qui a dérivé). La ligne est tonale (texte2), mono 12, jamais un
dialogue.

### 7. Les erreurs actionnables : chaque genre sait quoi faire

La taxonomie de quatorze genres (ADR-055) devient quatorze cartes
qui disent l'action, pas le code HTTP :

- `CLE_MANQUANTE` → « Clé absente — la poser dans Paramètres » +
  bouton **Ouvrir les réglages IA** ;
- `CLE_INVALIDE` / `CLE_SANS_DROIT` → « vérifier la clé / changer
  de modèle » + bouton réglages ;
- `MODELE_INCONNU` → « souvent l'URL de base, pas le modèle » +
  bouton **changer de modèle** ;
- `LIMITE_DEBIT` → « reprise dans 12 s » (l'attente est déjà
  visible, la carte la reflète) ;
- `QUOTA_EPUISE` / `LIMITE_JOUR` → « attendre / changer de clé » ;
- `CONTEXTE_TROP_LONG` → « la conversation sera compactée au
  prochain tour » ;
- les retentables (réseau, 5xx, délai) → « nouvelle tentative… »
  (la boucle retente déjà, la carte le montre).

Jamais une carte qui ne propose rien : chaque genre a son verbe.

### 8. Le service de premier plan : le tour survit à l'écran

Un tour d'agent dure des minutes et change d'écran sous les doigts
de l'utilisateur. Le tour vit dans **AgentService** (service de
premier plan, pattern ADR-043 du démon) : exécuteur nommé du
`core-platform`, notification silencieuse « JoIDE — l'agent
travaille » avec un bouton **Arrêter** (le `CancelToken` du tour).
L'UI s'abonne et se réabonne : le service garde le journal
d'événements du tour en cours, un écran qui revient rejoue les
événements et retrouve l'état exact (même mi-outils). Arrêter
proprement mi-outils : l'annulation coupe la boucle, la réparation
d'historique (lot N) apparie les appels interrompus, le tour finit
`ANNULE`, la conversation reprend au message suivant — éprouvé par
test.

### 9. Les réglages IA (Paramètres, clés chiffrées)

La page Paramètres (ADR-É8) héberge la section « IA » — des
réglages réels seulement :

- **Clé Anthropic** et **Clé OpenAI-compatible** : rangées à état
  (« absente » / « posée » — la clé ne s'affiche JAMAIS en clair) ;
  le dialogue pour poser/remplacer/effacer écrit dans le stockage
  CHIFFRÉ (ADR-058 : clé de chiffrement dans Android Keystore,
  fichier AES-GCM par `AtomicFiles`, jamais de clés de secours en
  clair — `PortCles` du lot N, implémenté ici).
- **Modèle par défaut** (texte, avec suggestions par fournisseur)
  et **URL de base** OpenAI-compatible (texte) — BYOK pur (D-2) ;
  l'offre gratuite est hors périmètre (D-3).
- Le sélecteur DU TIROIR (puce d'en-tête) choisit fournisseur +
  modèle pour la conversation courante ; sans clé posée, l'envoi
  produit la carte `CLE_MANQUANTE` actionnable — jamais un champ
  vide qui échoue en silence.

### 10. « Demander à l'IA » sur la sélection

Une sélection active dans l'éditeur propose « Demander à l'IA »
(action de sélection celeris) : le tiroir s'ouvre, le composeur
est prérempli avec la sélection quotée (fichier + lignes +
contenu dans un bloc code), l'utilisateur complète et envoie. La
sélection est un CONTEXTE, jamais une commande : le texte arrive
comme message utilisateur, les règles anti-injection du préfixe
système (lot N) s'appliquent à lui comme au reste.

## États et franchise

- Premier envoi sans clé : carte `CLE_MANQUANTE` + bouton réglages
  — l'erreur EST le parcours.
- Résultat d'outil web : le contenu enveloppé
  `<contenu-non-fiable source=…>` (ADR-059) s'affiche en mono
  ESTOMPÉ dans la carte pliée — jamais stylé comme la parole de
  l'assistant.
- Tour en arrière-plan : le point d'activité du glyphe suffit ;
  la notification porte Arrêter ; rouvrir le tiroir retrouve l'état
  exact.
- Compaction : ligne tonale AVANT la requête suivante, jamais
  après coup.
- Aucun streaming sans envoi : le composeur est désactivé pendant
  le tour (l'arrêt est l'action disponible), pas de file d'attente
  muette.

## Conséquences

- `EtatAgentChat` (pur, JVM) consomme les `EvenementAgent` et
  construit le modèle de transcript (cartes, états, usage, erreurs
  actionnables, questions) ; les vues minces le rendent. Tests :
  construction du transcript par événements, gate (fermer =
  refuser), cartes de questions (format des réponses), les
  quatorze genres → phrases d'action, somme d'usage, état après
  `ANNULE` mi-outils.
- `DiffMyers` (pur, module agent) borné et testé ; l'exécuteur
  d'outils l'utilise pour `diffApercu` (décision d'implémentation
  consignée au lot P).
- `AgentService` : service de premier plan, notification Arrêter,
  journal d'événements rejouable — la logique d'état est pure, le
  service est mince.
- `KeystoreCles` (app-android) : l'implémentation Android de
  `PortCles` — le format AES-GCM est testé JVM avec une clé
  forgée, la liaison Keystore est un composant Android mince
  (l'appareil la prouvera).
- Chaînes en parité `values`/`values-fr`.
- La maquette montre les trois vues : conversation en cours
  (cartes d'outils + question), dialogue de permission sur diff
  déplié, fin de tour avec usage et carte d'erreur actionnable.
