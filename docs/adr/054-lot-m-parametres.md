# ADR-054 — Lot M : les Paramètres — préférences privées, mesures au disque, journal propre

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

Le lot M (ADR-É10) livre l'écran de paramètres (ADR-É8) : réglages
réels seulement, valeurs mesurées au disque, actions destructrices à
conséquence nominée. Le design fait autorité sur la structure
(sections, recherche, segments/steppers/interrupteurs/nuancier) ;
cette ADR consigne les décisions que le design ne tranchait pas : où
vivent les préférences, comment le thème devient un réglage réel,
ce que mesure le disque, d'où vient le journal, et ce que l'écran
refuse d'afficher.

## Décision

### 1. Les préférences : schéma `joide.reglages/1`, classe pure, tolérance totale

Les réglages vivent dans les préférences privées de l'application
(`getSharedPreferences("joide.reglages")`, écritures `apply()` —
jamais un fil UI bloqué). La classe `Reglages` (pure, testée JVM)
porte la validation : une valeur absente, illisible ou hors bornes
retombe sur son DÉFAUT, jamais une exception — des préférences
posées par une version future ou corrompues ne doivent pas empêcher
l'application de démarrer. Les bornes sont des constantes publiques
(échelle UI 85–130 % au pas de 5, taille du code 10–20 sp au pas de
0,5, fils 1–8) : les steppers de l'écran et les tests lisent les
mêmes nombres. Le pont Android (`PreferencesAndroid`) est le seul à
connaître le cadre.

### 2. Le thème et l'accent sont des réglages réels, consommés au montage

`GraphiteVue.palette(Context)` lit désormais les réglages à chaque
appel : SOMBRE (le défaut, ADR-É2 « sombre d'abord ») et CLAIR sont
explicites, SYSTEME suit le mode nuit du cadre ; l'accent du
nuancier (les cinq rôles de la palette, `Graphite.ACCENTS`) remplace
le jeton par `Graphite.avecAccent` — un seul jeton réellement
appliqué (ADR-É8), le reste de la palette ne bouge pas. Un changement
de thème, d'accent ou d'échelle recrée l'écran (`recreate()`) : les
vues déjà posées ne se re-stylent jamais à moitié ; les autres
écrans suivent à leur propre recréation. L'échelle du texte UI
s'applique à la taille ET à l'interligne (le rapport du style reste).

### 3. Ce que l'écran ne montre pas

La « police de code » et le « thème de syntaxe » de l'ADR-É8 n'ont
ni alternative modifiable ni consommateur distinct sur l'appareil
(la seule police mono fiable du cadre est `monospace`, la seule
syntaxe est Graphite) : les rangées N'EXISTENT PAS — « un réglage =
une rangée + un consommateur », jamais une rangée morte. Écart
consigné au rapport ; les rangées naîtront avec leurs consommateurs.

### 4. Stockage : chaque valeur vient du disque, le vidage garde le répertoire

`MesuresStockage` (pure) mesure récursivement (plafond 100 000
entrées — l'appareil n'est pas un serveur de fichiers), formate en
mégaoctets (une décimale sous 100, entier au-delà — l'arrondi
n'est qu'affichage). Les trois mesures réelles : projets importés
(`filesDir/projets`), cache CIDX (`filesDir/cidx` — reconstruit à la
prochaine synchronisation), dépendances m2 (`filesDir/m2` —
re-téléchargées au prochain build qui les demande). Le vidage
supprime le CONTENU, jamais le répertoire : les moteurs le
recréent. Les deux vidages sont les seules actions destructrices,
chacune avec SA phrase de conséquence exacte (reprise de l'ADR-É8
dans les deux langues, éprouvée par le test de maquette).

### 5. Le journal : logcat du propre compte, borné et annoncé

Le journal de diagnostic exécute `logcat -d -t 1000` depuis le
processus de l'application : depuis Android 4.1, un processus sans
`READ_LOGS` ne voit que ses propres lignes — le service « :build »
partage l'UID, les builds y passent. `JournalDiagnostic` (pur) borne
aux 400 dernières lignes NON VIDES de 2 000 caractères (ellipse
annoncée) ; un journal vide est affiché vide, une commande qui
échoue est une erreur nommée — jamais un faux journal. L'exécution
de la commande vit dans l'écran (elle ne se teste pas en JVM).

### 6. Version, licences, dépôt

La rangée Version porte `versionName` et le SHA court du commit
injecté à la construction (`buildConfigField`, « dev » hors dépôt
git) — jamais de version inventée. LICENSE et NOTICE de la racine
sont empaquetés dans les assets par une tâche `copierLicences` câblée aux fusions d'assets (AGP 9 : le dossier généré est posé en
statique, la dépendance de tâche est explicite) — jamais de copie à
la main qui vieillirait mal. Le lien dépôt ouvre
`github.com/jjoblab/joide` (l'URL du dépôt réel) ; sans navigateur,
un dialogue dit l'URL au lieu d'échouer en silence.

### 7. Les consommateurs

Le moteur par défaut et les fils par build remplacent la case « en
processus » du menu ⋮ de l'atelier (elle déménage ici, ADR-É5) :
l'atelier relit le moteur à CHAQUE build, le `BuildRunner` en
processus prend les fils des réglages, et le service « :build » les
lit à son démarrage (sa légende le dit : en processus à chaque
build, service à son prochain démarrage — jamais une promesse
cachée). Les réglages d'éditeur (taille, numéros, retour à la ligne)
s'appliquent au MONTAGE de la session celeris (ADR-É8 : « à
l'ouverture suivante »), le thème de l'éditeur suit la palette posée.

## Conséquences

- `EtatParametres` (pur) porte la structure (cinq sections, quinze
  rangées, genres), le filtrage de recherche (insensible à la casse
  et aux accents, le nom de section compte, les sections vides
  disparaissent) et la liste des actions destructrices ; l'écran
  monte, traduit et déroule.
- Trois composants entrent au kit : `Stepper` (bornes posées, bouton
  hors borne désactivé), `Interrupteur` (piste 46×27, pouce animé
  180 ms), `Nuancier` (pastilles 26 dp, la choisie liserée) — la
  maquette parametres.html les spécifie, ADR-048 pour la méthode.
- Chaînes en parité `values`/`values-fr` (46 nouvelles clés,
  conséquences exactes de l'ADR-É8) ; les chaînes mortes du menu
  « en processus » sont retirées.
- Tests JVM : `ReglagesTest` (9), `EtatParametresTest` (10),
  `MesuresStockageTest` (7), `JournalDiagnosticTest` (6),
  `MaquetteParametresTest` (7) — la « capture » suit la méthode
  ADR-048 (le test lit la maquette ET l'ADR-É8, l'état doit
  reproduire l'un et les phrases exactes des conséquences).
