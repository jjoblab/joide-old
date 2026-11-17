# ADR-060 — Lot P : l'UI du chat et l'intégration applicative — le service modèle d'état, les deux blocages honnêtes, le diff réel

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot P (ADR-É10) ferme le groupe 4 : l'agent des lots N (noyau)
et O (outils, espace) reçoit sa face — le tiroir chat de l'atelier
(ADR-É11, écrite ce lot avec sa maquette `design/agent.html`), le
service de premier plan, les réglages IA chiffrés (D-6), le vrai
diff de permission et l'entrée « Demander à l'IA ». Cette ADR
consigne les décisions que l'ADR-É11 laissait ouvertes.

## Décision

### 1. Le service est le modèle d'état, le journal se rejoue

`AgentService` (processus principal, binder local — l'espace de
travail de l'atelier est un OBJET partagé, pas une frontière IPC)
tient le journal ordonné du tour : messages utilisateurs ET
événements `EvenementAgent` dans UNE liste d'entrées. L'écran garde
un `EtatAgentChat` pur qu'il reconstruit en REJOUANT le journal —
un retour d'écran retrouve l'état exact, même mi-outils. La session
persiste par `SessionsAgent` à chaque fin de tour (transcript,
historique à signatures, compactés PERMANENTS conservés — le
constructeur `HistoriqueAgent(messages, compactes)` les restaure) ;
une session illisible rouvre une conversation neuve, jamais un
crash au démarrage.

### 2. Les deux blocages résolus par futures, défaut sûr au bout

La permission (`Permissions.PortePermission`) et `ask_user` (par
le délégataire `EspaceTravailInjecteur`, dont chaque méthode
redélègue EXPLICITEMENT — une signature ajoutée au port casse la
compilation, le compilateur fait la revue) bloquent le fil de
l'outil sur un future que l'UI complète. Sans UI — écran fermé,
processus en arrière-plan — ou après 120 secondes : REFUSER la
permission, PASSER chaque question. Le bouton Arrêter (notification
et composeur) annule le `CancelToken` ET débrique les futures sur
le défaut sûr : un tour arrêté ne reste jamais pendu.

### 3. Le diff de permission est RÉEL (Myers), l'aperçu suit l'outil

`DiffMyers` (module agent) : Myers en avant avec trace (la version
canonique, prouvable), coupe commune préfixe/suffixe d'abord, budget
d'édition borné (800) et milieu borné (3 200 lignes) — au-delà le
REPLI HONNÊTE nommé (« fichier trop remanié pour un diff fin ») ;
sortie unifiée à deux lignes de contexte, bornée à 400 lignes /
20 000 caractères, amputation annoncée. `ExecuteurOutils` calcule
l'aperçu du VRAI contenu pour `write_file`/`create_file`/
`edit_file`/`write_memory` par un `LecteurContenu` injecté (lecture
overlay-preferred de l'espace) ; les opérations nommées (rename,
delete, add_dependency…) gardent le résumé d'arguments — jamais un
faux diff. Convention git : un pur changement de newline final
n'apparaît pas dans l'aperçu (l'écriture réelle reste exacte) —
documenté dans la Javadoc.

### 4. Le dialogue de permission couvre tout, fermer = refuser

`Dialogue.autorisation` : corps libre (le diff déplié en mono dans
sa vue défilante), Refuser / Appliquer, et TOUTE annulation (croix,
tap extérieur, retour) appelle l'auditeur d'annulation qui répond
REFUSER — le défaut sûr est dans le CADRE, pas dans la vigilance de
l'appelant.

### 5. Les clés : Keystore, cœur pur, repli honnête

`ClesChiffrees` (pur, testé JVM avec une clé forgée) : format
`joide.cles/1`, AES-GCM, IV aléatoire par écriture (deux poses du
même texte diffèrent — éprouvé), écriture atomique, un fichier par
fournisseur neutralisé. `KeystoreCles` (Android, mince) fabrique ou
retrouve la clé (alias `joide.cles`, AES-256 GCM) ; un Keystore
cassé retombe sur une clé en mémoire — les clés d'avant deviennent
illisibles mais l'application démarre. Les rangées Paramètres
affichent l'ÉTAT (« posée »/« absente »), jamais la clé ; effacer
est une action destructive confirmée.

### 6. La section IA de Paramètres (quatre rangées réelles)

Clé Anthropic, clé OpenAI-compatible, modèle par défaut (borné,
jamais vide), URL de base (http(s) ou vide, bornée) — chaque
récriture valide et chaque lecture retombe sur son défaut : la
tolérance totale d'ADR-054 s'étend à l'IA. Le sélecteur DU TIROIR
affiche le modèle courant (la puce ouvre les réglages en v1 — le
sélecteur in-situ va au backlog) ; le mode de permission se change
par la puce (Demander à chaque / Acceptation auto / Plan seul).

### 7. « Demander à l'IA » cite le fichier actif (v1)

L'entrée du menu ⋮ préremplit le composeur avec le fichier ACTIF
quoté (borné à 8 000 caractères, amputation annoncée). La
sélection PRÉCISE attend l'exposition d'une API de sélection par
l'éditeur celeris — écart consigné au backlog, l'ADR-É11 point 10
reste la cible.

### 8. Écarts consignés (honnêteté du lot)

- L'amarrage grand écran du tiroir agent (partage du bord droit
  avec l'aperçu, ADR-É11 point 1) : v1 pousse le tiroir aux DEUX
  tailles — backlog.
- Le sélecteur de modèle in-situ (puce → réglages en v1) — backlog.
- La liaison Keystore (fabriquer la clé dans le Keystore matériel)
  s'éprouve sur appareil : le format AES-GCM est testé JVM, le
  composant Android reste mince — la preuve appareil va au rapport.
- `run_task`/`add_dependency` restent au défaut dégradé honnête
  (« non disponible ici ») tant que l'atelier n'injecte pas
  l'exécuteur borné et le résolveur P4 au service — l'espace les
  accepte déjà (ADR-059), le câblage est un travail d'hôte.

## Conséquences

- Arête `app-android -> agent` implicite par l'existing
  `app-android -> ide-integration -> agent` (le service consomme le
  noyau directement : les dépendances restent descendantes,
  `agent` ne connaît ni Android ni l'app).
- `EspaceTravailInjecteur` entre au module agent : le délégué
  explicite des futures signatures du port.
- Glyphe `BULLE` au kit (bouton agent de la barre, point d'activité
  pendant le tour) ; `Dialogue.autorisation` au kit des dialogues.
- Tests : DiffMyers 10 (reconstruction aller-retour, fuzz 200
  essais, bornes, repli), ApercuPermission 5 (le gate reçoit le
  vrai diff), EtatAgentChat 13 (transcript par événements, 14
  verbes d'erreur, fermer = refuser, journal rejoué, plafond),
  MaquetteAgent 8 (capture contre la maquette), ClesChiffrees 9,
  Reglages +6 (défauts IA, tolérance, bornes).
