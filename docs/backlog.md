# Backlog (après les lots Q et R de la V2)

Entrées ouvertes à l'issue des lots Q (ADR-061/062) et R
(ADR-063) — la piste 2 du plan ADR-É10 est clôturée :

- [ ] Lot Q : les PRODUCTEURS d'images restent à câbler pour les
      OUTILS — le genre `IMAGE` du modèle neutre, la sérialisation
      des trois fournisseurs, la réduction côté client
      (`ReductionImages`) et le réducteur BitmapFactory sont en
      place, et le lot S a posé le PREMIER producteur (les
      attachements du chat, ADR-064) ; les outils v1 restent sans
      image (capture d'aperçu, rendu de vue).
- [ ] Lot Q : les BANDES d'usage — le lot S a posé l'écran Usages
      (usage cumulé de la conversation, état du banc de clés par
      raison, ADR-064), mais `BandesUsage` (NORMALE/ATTENTION/
      CRITIQUE) reste non affiché : le régulateur vit dans
      `FournisseurBascule` et n'est pas exposé au binder —
      l'exposer (la bande critique avant le 429) reste à faire.
- [ ] Lot Q : le sélecteur de niveau de réflexion Gemini est un
      réglage de construction — l'exposer dans Paramètres si le
      propriétaire le veut.
- [x] Lot R : la section Connexions MCP — CLOS par le lot S
      (ADR-064) : l'écran Connexions vit dans le menu ⋯ de l'agent
      (états publiés, reconnexion, compte d'outils), la lecture du
      `.mcp.json` se branche à l'ouverture de la conversation, les
      outils distants approuvés rejoignent le port du tour. Restent
      au backlog : le flux de signature OAuth UI (ouvrir l'URL,
      capter le rebouclage, `echanger`) et le store chiffré des
      jetons (v1 : mémoire, par session).
- [ ] Lot R : le serveur MCP local n'est pas démarré par l'hôte
      (le service l'exposera aux clients de bureau via `adb
      forward`, pattern de l'ADR-É9) — les outils, le transport et
      la sécurité sont prouvés en JVM.
- [x] Lot R : l'approbation des serveurs d'un `.mcp.json` issu du
      PROJET — CLOS par le lot T (ADR-065) : la décision se
      souvient PAR PROJET, liée à la signature du fichier (chemin +
      contenu passés au SHA-256) — le même `.mcp.json` retrouve ses
      serveurs, un fichier changé redemande le geste explicite. La
      clé persistée vit dans les préférences privées (une décision
      de l'utilisateur, pas un secret) ; le STORE CHIFFRÉ reste
      réservé aux jetons OAuth (item suivant, toujours ouvert).
- [ ] Lot S : la cible caméra de l'agent
      (`FournisseurCameraAgent`) ne survit pas à la mort du
      processus (champ statique) — une photo en vol au kill est
      perdue, jamais un crash.


# Backlog (après le lot P de la V2)

Entrées ouvertes à l'issue du lot P (ADR-É11/060) :

- [ ] Lot P : l'amarrage grand écran du tiroir agent (partage du
      bord droit avec l'aperçu, ADR-É11 point 1) — v1 pousse le
      tiroir aux deux tailles.
- [x] Lot P : le sélecteur de modèle IN-SITU — CLOS par le lot S
      (ADR-064) : la puce ouvre le dialogue fournisseur +
      modèle + URL sans quitter la conversation, les clés restent
      gérées dans les Paramètres.
- [ ] Lot P : « Demander à l'IA » sur la SÉLECTION de l'éditeur
      (v1 quote le fichier actif borné ; le lot S ajoute le
      FICHIER COURANT en pièce jointe `<file>`) — la sélection
      précise attend l'exposition d'une API de sélection par
      celeris (ADR-060 §7).
- [ ] Lot P : la preuve appareil de la liaison Keystore (fabriquer
      la clé dans le Keystore matériel) — le format AES-GCM est
      éprouvé JVM, le composant Android reste mince (ADR-060 §8).
- [ ] Lot P : brancher run_task et add_dependency au service de
      l'agent (l'exécuteur borné du build et le résolveur P4 avec
      lockfile existent déjà — ADR-059 —, c'est un travail d'hôte).
- [ ] Lot P : le mode de permission n'est pas persisté par projet
      (v1 : en mémoire par conversation, ADR-É11 point 3 vise par
      projet).


# Backlog (après les lots M, N et O de la V2)

Entrées ouvertes à l'issue des lots M (ADR-054), N (ADR-055 à 058)
et O (ADR-059) :

- [ ] Lot O : `find_symbol` passe par la recherche textuelle du port
      (`chercherTexte` sur les déclarations probables) — la vraie
      interrogation de l'index CIDX (lot A) attend que l'index
      expose une recherche par nom de symbole ; l'outil est déjà
      branché, seul le service change.
- [ ] Lot O : références, renommage de symbole et formatage restent
      au défaut dégradé honnête (EspaceNonSupporte — « non
      disponible ici ») tant que les moteurs code-lsp ne les servent
      pas (la couverture non servie du protocole, entrée P8/P9
      ci-dessous) ; l'implémentation EspaceTravailIde branche les
      moteurs dès qu'ils répondent.
- [ ] Lot N : la compaction par RÉSUMÉ (un modèle résume les tours
      anciens au lieu de tronquer les résultats d'outils) — l'ADR-057
      pose la troncature permanente comme v1 honnête ; le résumé
      demanderait un appel fournisseur dédié et son coût propre.
- [ ] Lot M : les rangées « police de code » et « thème de syntaxe »
      de l'ADR-É8 n'existent pas (aucun consommateur sur l'appareil
      — la seule police mono fiable du cadre est `monospace`, la
      seule syntaxe est Graphite) ; elles naîtront avec leurs
      consommateurs (polices embarquées, thèmes de coloration).
- [ ] Lot N : le port PortCles (ADR-058) attend son implémentation
      Android Keystore au lot P (composant Android : AES-GCM +
      AtomicFiles, jamais de clés de secours en clair).


# Backlog (après le groupe 2 de la V2 — lots J, K, L)

Entrées ouvertes à l'issue du groupe 2 (ADR-051/052/053) :

- [ ] Lot K : le pipeline ne transporte pas la LOCALISATION des
      problèmes (`Problem.location` est perdue dans
      `BuildRunner.outcomeOf` — seul `message()` part dans le
      protocole) ; le parseur de cible de la console est prêt, le
      bouton « Ouvrir le fichier à la ligne » ne s'active que pour
      une cible réelle — faire transporter la localisation par le
      démon/pipeline (hors périmètre du plan UI) l'activera.
- [ ] Lot L : les replis de ressources signalés par la BRIQUE
      LayoutLib elle-même (aujourd'hui détectés côté JoIDE sur les
      références directes du XML ; `@layout/` et les références
      transitives ne sont pas vérifiées).
- [ ] Lot L : stats de rendu au fil de l'eau pendant la frappe
      (aujourd'hui les bannières se rafraîchissent aux événements de
      l'écouteur).
- [ ] Lot J : recherche de fichiers de l'atelier (la loupe du rail
      des maquettes) — rien n'est câblé, aucun bouton mort.
- [ ] Lot J : l'état d'expansion du tiroir ne survit pas à la
      rotation (l'activité se recrée ; l'état pur est recréé) —
      sauvegarder dossiersOuverts dans le savedInstanceState si le
      besoin se fait sentir sur appareil.
- [ ] Lot K : compteur de problèmes distinct des erreurs de tâches
      dans la poignée du dock (« · 0 problème » de la maquette) —
      l'état honnête du lot K porte le compte d'erreurs du
      transcript.

# Backlog (après P8)

Entrées ouvertes à l'issue de P8 :

- [ ] P8/P9 : builds Android sur appareil — D8/R8/aapt2 ne sont pas
      embarqués dans l'APK (ADR-046, décision de taille reportée au
      propriétaire) ; les embarquer exigerait aussi les règles de
      conservation des ServiceLoader des moteurs (déjà identifiées
      au lot A pour le rétrécissement release).
- [ ] P8/P9 : accès direct SAF sans copie (provider de système de
      fichiers au-dessus de DocumentsContract) — l'import par copie
      duplique le stockage ; un VFS par fournisseur exigerait une
      mesure avant décision.
- [ ] P8/P9 : gestion du stockage des projets importés (purge,
      plusieurs projets) et lockfile joide.lock sur appareil (le
      résolveur y tourne sans lockfile — la reproductibilité des
      builds sur appareil s'en remet au cache).
- [ ] P8/P9 : couverture protocole non servie par les moteurs
      (symboles, plis, formatage, surlignages, plages de sélection,
      renommage, correctifs, indices, jetons sémantiques, références —
      vides honnêtes du lot D).

Entrées ouvertes à l’issue du groupe 1 de la V2 (lots G-I,
2026-11-16) :

- [ ] V2/lots H-J : exécution des mains et des tests sur l’appareil —
      la tâche `JvmRunTask` existe dans le pipeline (P3), le
      protocole du démon ne la sert pas ; il manque le bouton
      Exécuter de l’atelier et l’usage du journal `run.log` (la
      carte du modèle Console JVM ne promet que la construction,
      ADR-050, écart n° 1).
- [ ] V2/lot H+ : outil bureau qui constitue une spec Android
      (BuildSpec.Kind.ANDROID) depuis un workspace natif joide.toml —
      la chaîne P5 est prouvée sur specs forgées, aucun outil
      utilisateur ne la branche ; la carte du modèle Application
      Android le nomme (« câblage à venir », ADR-050, écart n° 3) ;
      l’échappatoire existant est l’export Gradle P7.
- [x] V2/lot M : le journal (logcat filtré) — clos par le lot M
      (ADR-054) : JournalDiagnostic vit DANS les Paramètres, borné et
      annoncé ; le menu de l’application porte Paramètres et À
      propos, aucune entrée morte.
- [ ] V2/lots H+ : importer un projet inconnu (ni joide.toml ni
      script Gradle) comme un départ Vide — le lot H refuse avec un
      dialogue nommé ; la création couvre déjà ce besoin par le
      modèle Vide.

Entrées ouvertes à l'issue de P7 :

- [ ] P8 : implémenter l'échappatoire « build Gradle déporté » conçue
      en ADR-042 — l'export existe (ADR-041), les interfaces
      arrivent avec l'adaptateur d'IDE.
- [ ] P7/P9 : résoudre les plateformes BOM à l'import —
      `implementation(platform(libs.compose.bom))` et les alias gérés
      par BOM (sans version au catalogue) sont rapportés puis ignorés
      (ADR-040) ; la résolution demanderait le résolveur P4 en
      lecture seule (réseau, lockfile) — décision du propriétaire.
- [ ] P7/P9 : greffons de traitement d'annotations (kapt, ksp) et
      tests instrumentés (androidTest*) : hors modèle, rapportés à
      l'import ; les représenter demanderait des portées et des
      ensembles dédiés — décision du propriétaire.
- [ ] P7/P9 : variables locales des scripts (`val composeBom =
      platform(...)`) : la notation n'est pas résolue statiquement,
      rapportée ; une évaluation partielle hors-ligne est pensable,
      à mesurer.
- [ ] P6/P7 (question du rapport P6 reprise) : quota d'espace du
      démon — l'import ne le porte pas non plus ; la décision
      (avant l'IDE ou après) appartient au propriétaire.

Entrées héritées fermées par P7 :

- [x] P6/P7 : fusion de manifestes des bibliothèques (backlog P5) —
      évaluée en P7 : la sémantique AGP (manifeste de l'application
      + bibliothèques fusionnés par Merger) est un travail du
      pipeline (build-android), pas de l'import ; l'import porte les
      manifestes par module (facette android) ; la fusion reste au
      backlog P5/P8.
- [x] P7 : authentification des dépôts privés (reporté P4) — les
      dépôts et leurs identifiants ne sont pas lus à l'import
      (rapportés comme contexte) ; le transport JoIDE (ADR-031)
      refuse d'envoyer des identifiants ; aucune exigence du prompt
      ne la demande — la question reste ouverte pour l'échappatoire
      déportée (ADR-042, P8).
# Backlog (P0)

Entrées ouvertes — aucune n'est un TODO orphelin dans le code (§10) ;
chaque entrée sera fermée ou reportée explicitement dans un rapport de
phase.

- [ ] P1 : valider le sous-ensemble TOML figé (ADR-014/R2) quand
      core-model lit les projets synthétiques.
- [ ] P3 (ouvert) : compiler les sources Kotlin générés — le SPI
      `SourceGenerator` ordonne la génération avant les compilations,
      la validation sur un générateur réel reste à faire (validité
      constatée à la main en P0, R3). Le spike API Build Tools est
      TRANCHÉ (ADR-027) : incrémental maison retenu, révisable à P9.
- [ ] P3 (ouvert) : éditions différentielles dédiées `sealed` et
      `@JvmInline` (la liste scellée est déjà dans l'ABI, ADR-025) ;
      `const` et `inline` sont couverts.
- [ ] P3/P5 : vérifier le désucrage D8 de records/sealed avant tout
      usage dans le code exécuté sur Android (ADR-009/013).
- [ ] P2 : D8/D9 — benchmarker le hash 128 bits (D9) contre SHA-256 et
      trancher par ADR.
- [ ] P2 : ADR store alternatif journal append-only (D8, interface
      BuildStore).
- [ ] P6/P8 : appliquer le conditionnel Android (ADR-015) à l'arrivée
      du premier module Android, avec JOIDE_SKIP_ANDROID.
- [x] P8 : réactiver la parité values/values-fr dans le script de
      langue (ADR-012) au premier module UI.
      FERMÉE par le lot G (ADR-048) : contrôle n° 5 du script
      (même ensemble de clés, fixtures et test dans tools-test),
      values/ en anglais par défaut, values-fr/ en français.
- [ ] Bench : surveiller le coût des tests TestKit ; si instabilité,
      bug à corriger, pas à relancer (§9 du prompt).
- [ ] Bench : valider la comparaison temporelle (regressionTest S/M) sur
      la machine de référence (D12) — le déterministe est déjà une porte
      exacte ; le temporel a produit un faux positif sur l'ordonnanceur
      partagé du développement (preuves au rapport P0).
- [ ] Bench : baselines L/XL à committer quand une machine de référence
      sera désignée (coût de génération documenté dans le rapport P0).


---

# Backlog (après P4)

Entrées ouvertes à l'issue de P4 :

- [ ] P5 : consommer `ResolvedArtifact.file` et `aarContents` dans le
      pipeline Android (dex des `classes.jar` + `libs/*.jar`) — la
      surface existe, le câblage arrive avec build-android.
- [ ] P4/P7 : authentification des dépôts privés (jetons, en-têtes) —
      le transport refuse le clair et n'envoie aucun identifiant ;
      aucune exigence du prompt ne la demande à P4.
- [ ] P4 : éviction du cache de dépendances (quota d'espace) — même
      travail que l'éviction du cache de sorties (ADR-024), à faire
      avec la gestion de l'espace du daemon (P6).
- [ ] P4 : classifieurs (`sources`, `javadoc`) résolus sur demande
      explicite — les entrées du lockfile existent (`g:a:v:c@ext`),
      aucune demande ne les produit aujourd'hui.
- [ ] P4 : unions de plages multiples (`[1,2),[3,4)`) — rejetées à
      l'analyse ; aucune occurrence dans les projets synthétiques.
- [ ] P4/P9 : benchmarker la relecture chaude (un hachage par artefact
      sur `verifyInCache`) sur un graphe de centaines de dépendances ;
      si le coût gêne, mémoïser par (taille, date) comme le fait le
      VFS (ADR-023 de celeris, même esprit que les signaux de P2).
- [ ] P4 : `HttpTransport` contre un miroir public réel (test hors
      harnais, à exécuter sur la machine de référence) — le harnais
      prouve la politique (clair, redirections, certificat rejeté),
      pas la latence du monde réel.


---

# Backlog (après P6)

Entrées ouvertes à l'issue de P6 :

- [ ] P8 : câbler l'observateur de l'arbre (core-vfs) au protocole du
      démon — les ensembles sales explicites priment déjà sur le diff
      disque (ADR-038) ; jusqu'au câblage, une réécriture de même
      taille dans le même grain d'horodatage est invisible au diff
      par signaux (détection à l'édition d'IDE réelle : la taille
      change) — la comparaison par condensé de contenu de l'ensemble
      de la racine est l'alternative, à mesurer avant adoption.
- [ ] P6/P7 : quota d'espace du démon — éviction du cache de dex et de
      l'outillage téléchargé (même travail que l'ADR-024). Évalué en
      P6 : le périmètre fermé de la phase (feuille de route) ne le
      portait pas ; les points d'ancrage existent désormais
      (ToolchainLocator.defaultCacheDir() unique, répertoire de cache
      de dex par espace) — la décision d'ajouter le quota au démon
      avant l'IDE (P8) ou à l'import (P7) appartient au propriétaire.


---

# Backlog (après P5)

Entrées ouvertes à l'issue de P5 :

- [ ] P6/P7 : fusion de manifestes des bibliothèques dans le
      manifeste de l'application (l'application est liée telle
      quelle en P5 ; les bibliothèques ne contribuent que leurs
      ressources) — décision à prendre avec l'import Gradle (P7)
      qui connaît la sémantique AGP.
- [x] P6 : éviction du cache de dex et de l'outillage téléchargé
      (quota d'espace du démon, même travail que l'ADR-024) — évalué
      en P6, repris dans le bloc P6 ci-dessus (question au
      propriétaire : avant l'IDE ou à l'import ?).
- [ ] P5/P8 : règles de conservation R8 lues dans le manifeste
      (points d'entrée des activités et services) ; aujourd'hui la
      règle porte le paquet de l'application entier.
- [ ] P5/P8 : signature V3 avec lignée de certificats et signature
      V4 (niveau fichier) ; rotation de clé.
- [ ] P5/P8 : ressources spécifiques à une variante (répertoires
      res par type de build) et identifiant de suffixe de debug —
      le lien est unique par module en P5.
- [ ] P5/P8 : bibliothèques natives des AAR dans l'APK et le bundle
      (jni/*.so extraits, alignés page) — le rédacteur les aligne
      déjà, le câblage des AAR reste à faire.
- [ ] P7 : authentification des dépôts privés (reporté P4).
