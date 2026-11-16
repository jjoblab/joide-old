# ADR-059 — Lot O : la surface d'outils v1 et le confinement par construction

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot O (ADR-É10) livre les outils de l'agent et l'espace de
travail : le port `EspaceTravailAgent` implémenté sur les briques P8,
le sous-ensemble assumé des 42 outils de CodeAssist, les checkpoints
au premier toucher, `ask_user`, et le confinement — la leçon du
risque n° 2 de l'ADR-É9 (aucun confinement de chemin chez CodeAssist :
le mode auto-accept pouvait sortir du projet).

## Décision

### 1. Le port : un seul point de contact, défauts dégradés

`EspaceTravailAgent` (module agent, pur) est le SEUL contact entre
l'agent et le projet : lectures overlay-preferred (le texte de
l'éditeur ouvert prime sur le disque), recherche, diagnostics,
écritures, exécution bornée, mémoire, questions. Les méthodes hors le
socle fichier ont un DÉFAUT DÉGRADÉ qui lève `EspaceNonSupporte` :
l'exécuteur d'outils le rend HONNÊTEMENT au modèle (« non disponible
ici ») — jamais une liste vide qui se lirait « rien trouvé » (leçon
7). L'implémentation `EspaceTravailIde` (ide-integration) sert ce que
les briques servent VRAIMENT : diagnostics et définitions par
`MoteursLangage` (moteur Java réel), fichiers par `java.nio` sous la
racine, aperçu par le modèle déclaratif ; références, renommage et
formatage restent au défaut dégradé (les vides honnêtes du lot D
jusqu'à ce que les moteurs les servent) — l'atelier du lot P y
branchera l'exécution (BuildRunner/client du démon) et la résolution
P4 via les ports injectés, les tests d'intégration prouvent déjà les
DEUX sur les vraies briques.

### 2. Le confinement par construction

Tout chemin d'outil passe par `CheminsConfines` AVANT d'atteindre le
port : relatif, sans `..`, sans racine (`/`, `~`, lettre de lecteur),
sans séparateur retour, 300 caractères au plus, segments de contrôle
refusés — un chemin refusé ne touche JAMAIS le disque (éprouvé par
test : le répertoire du faux projet reste vide). L'implémentation
REVÉRIFIE (resolve + normalize + startsWith de la racine) : la défense
en profondeur, parce que le port sera implémenté par d'autres hôtes.

### 3. La surface v1 : vingt-cinq outils, bornés côté outil

Le sous-ensemble assumé : lecture 6 (`read_file` à fenêtre de 400
lignes/50 000 caractères, `list_dir`, `search_text` plafonné à 50
occurrences avec annonce, `find_symbol`, `get_diagnostics`,
`project_overview`), écriture 6 (`create_file`, `write_file`,
`edit_file` à occurrence EXACTE unique, `rename_path`, `delete_path`,
`add_dependency`), sémantique 4 (`go_to_definition`, `find_references`,
`rename_symbol`, `format_file`), exécution 3 (`run_program` 120 s,
`run_task` 300 s, `run_tests` 600 s — la QUEUE de la sortie conservée,
l'amputation annoncée), plan 1 (`todo_write` → `PlanAgent` observable,
la carte épinglée), mémoire 2 (`read_memory`/`write_memory` sur
`AGENTS.md`), web 2 (`web_fetch`, `http_request`), questions 1
(`ask_user`, 1 à 4 questions de 2 à 4 options). Neuf sont mutants —
le marqueur porte la permission, le séquencement et les checkpoints.
Les bornes vivent CÔTÉ OUTIL (leçon 10) : un résultat déjà borné part
une fois dans le cache, le réécrire après coup invaliderait les tours
suivants. La surface ENTIÈRE part à chaque requête, sous le budget de
16 000 caractères — le garde-fou du test échoue à la place de la
facture (ADR-057).

### 4. Les checkpoints au premier toucher, rapport honnête

`EspaceCheckpoint` enveloppe l'espace : chaque écriture instantané le
fichier AU PREMIER TOUCHER du tour (plafond 200 fichiers — un projet
entier ne se duplique pas en mémoire) ; l'annulation restaure du plus
récent au plus vieux (un fichier créé disparaît, un modifié revient)
et AVOUE ce qu'elle ne peut pas restaurer — la dépendance ajoutée
(« resolver cache and lockfile are not reverted ») : le rapport dit,
il ne cache pas.

### 5. Le web enveloppé, les questions par l'hôte

Le contenu web revient TOUJOURS enveloppé
`<contenu-non-fiable source="…">` (plafond 200 000 caractères,
délai 20 s, http(s) seulement) — le paragraphe anti-injection du
prompt système et l'enveloppe se renforcent. `ask_user` passe par le
port (l'hôte affiche les cartes paginées au lot P) ; le modèle reçoit
la consigne de ne jamais terminer une réponse par une question en
texte.

## Conséquences

- Arête `ide-integration -> agent` (l'implémentation du port vit
  au-dessus des briques) et arête de TEST
  `ide-integration -> core-store` (le store journal de bureau,
  configuration `jvmApi` — le VRAI moteur de build tourne dans les
  tests de l'espace de travail, comme les tests du démon) ; le graphe
  et son test les portent.
- Tests : sur faux espace (25 outils, confinement, checkpoints,
  budget de surface, enveloppe web) dans le module agent ; sur VRAIES
  briques dans ide-integration — moteur Java réel pour les
  diagnostics et la définition, BuildRunner réel pour la compilation
  bornée, résolveur P4 réel sur un dépôt local (lockfile écrit,
  `joide-module.toml` réécrit, seconde demande sans effet).
- Ce qui reste au backlog : `find_symbol` sur l'index CIDX, les
  références/renommage/formatage dès que les moteurs les servent, la
  compaction par résumé (ADR-057).
