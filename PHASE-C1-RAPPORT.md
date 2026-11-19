# Rapport du lot C1 — Le garde-crash : l'activité qui détecte TOUT type de plantage

**Base :** commit `a0266bc` (lot A9, ADR-085 Accepted).
**Origine :** remontée du terrain du 2026-11-19 — « L'application crash
quand j'ouvre le tiroir agent, entre les clés API depuis settings, le
bottomsheet behavior n'affiche rien que ce soit pour build, journal…
La clé API n'est pas sauvegardée ou du moins la fonctionnalité AI ou
chat ne la charge, ainsi que le provider. **Créé une activity crash
que détecte tout le type crash.** »

## 1. Constats

- **CONFIRMÉ (audit statique, 8 faits lint `NewApi` — crashs RÉELS)** :
  le module `app-android` (minSdk 28, SANS core library desugaring)
  appelait des méthodes java.* au-dessus du niveau du produit :
  `Path.of(String)` (API 34) ×3 dans `ControleurFichiers`,
  `Stream.toList()` (API 34) dans `EtatProblemes`,
  `InputStream.readAllBytes()` (API 33) ×2 dans
  `ProprietesConstruction`, `transferTo` (API 33) dans
  `PreparationChaine`, `Files.readString` dans `SpecsBuild` (API 33+).
  Sur un appareil Android ≤ 13/12L : **l'atelier ne pouvait pas
  s'OUVRIR** (`ProprietesConstruction.poser` à `EcranAtelier.onCreate`),
  le **BUILD mourait** à la lecture du manifeste du projet
  (`SpecsBuild.identifiantPaquet`, dans le processus `:build` — la
  « feuille qui n'affiche rien pour build » de la remontée est un
  candidat direct), les actions dupliquer/renommer/supprimer et le ↓
  « problème suivant » plantaient (`Path.of`, `toList`).
- **CONFIRMÉ (le reste du terrain)** : AUCUN mécanisme de capture — un
  plantage tuait le processus sans trace sur l'appareil ; les trois
  symptômes restants (tiroir agent, saisie des clés, provider/clé non
  chargés) ne sont pas reproductibles au bac à sable sans émulateur
  (écart P8 n° 4) : le GARDE-CRASH les rendra nommés au prochain
  plantage sur l'appareil du propriétaire.
- **INFIRMÉ (audit ciblé du tiroir agent)** : le mécanisme d'adoption
  `TiroirPousse.onFinishInflate` + `TiroirAgentPousseBinding.bind` est
  SAIN (décodage du bytecode `ViewBindings.findChildViewById` :
  fallback `View.findViewById` — l'enfant adopté par le panneau reste
  trouvé) ; le thème Graphite est posé avant toute inflation
  (`ActiviteJoide`), le manifeste ne référence les `?attr/jo_*` nulle
  part.

## 2. Décision (la demande : une activité crash qui détecte tout type)

**`AppJoide`** (nouvelle `Application`, `android:name` au manifeste) —
pose le garde à la création du processus, AVANT toute activité, dans
TOUT processus du produit (principal ET `:build`).

**`GardienCrash`** — trois voies de détection :

1. **Exceptions non interceptées, SUR TOUT FIL** —
   `Thread.setDefaultUncaughtExceptionHandler` : classe exacte,
   message (cause première dépliée), pile complète, ET la pile du fil
   principal (le contexte où l'UI vivait quand un fil de fond a cassé).
   Garde de réentrance : un garde qui planterait à son tour délègue au
   gestionnaire système d'origine, jamais de boucle.
2. **Gel du fil d'interface (classe ANR)** — chien de garde : une
   impulsion au fil principal toutes les 2 s ; non courue en 8 s =
   gel avéré — la PILE du fil principal est capturée DEPUIS le chien
   (LE diagnostic : où le fil est bloqué — le symptôme « ne répond
   plus » ne suffit pas) ; débogueur branché ⇒ jamais de faux gel ;
   un gel se traite comme une fracture (rapport, écran, mort propre —
   l'état gelé ne sert à personne).
3. **Fractures des processus secondaires (`:build`)** — rapport écrit
   sur le disque SANS lancer l'écran (ne pas voler l'atelier vivant) ;
   `MainActivity.onResume` montre les rapports JAMAIS CONSULTÉS au
   retour dans l'application (marqueur `.vus` : la feuille de build
   muette de la remontée trouve son auteur nommément).

**`EcranCrash`** (l'activité demandée) — UI **100 % programmatique**
sous thème du CADRE : l'écran d'urgence ne DOIT dépendre d'aucune
couche qui peut être LA cause du plantage (thème Graphite
`?attr/jo_*`, layouts applicatifs, ViewBinding) ; couleurs en
constantes de code (l'exemption à la franchise §0.4 est motivée :
elle vise les LAYOUTS, l'urgence ne lit RIEN dans le thème), textes
par ressources ordinaires. Contenu : type NOMMÉ (genre + classe
exacte ou « gel ANR »), message, moment, fil, PILE complète
monospace sélectionnable, historique des rapports (plus ancien / plus
récent), actions Relancer (tâche neuve vers l'accueil) / Effacer /
Quitter (tâche fermée). Repli nu (TextView) si même son montage
échoue. Lancée en tâche neuve par le garde, PUIS mort propre du
processus après 600 ms (AMS encaisse la tâche, le système relance
l'écran dans un processus frais — la manière CustomActivityOnCrash,
jamais un zombie).

**`RapportCrash`** — le cœur PUR, testé JVM (6 tests) : format
`joide.crash/1` (en-tête reconnaissable, bloc identité, piles,
métadonnées version/Android/appareil), CLASSIFICATION du genre
(`FRACTURE` avec classe exacte vs `GEL_INTERFACE`), cause première
des enveloppes, nom de fichier classé par moment (la purge
lexicographique fonctionne), écriture atomique (`AtomicFiles`),
plafond 20 rapports (les plus anciens s'effacent).

## 3. Changements

**Ajoutés** : `AppJoide.java` ; `crash/GardienCrash.java` ;
`crash/EcranCrash.java` ; `crash/RapportCrash.java` ;
`crash/RapportCrashTest.java` (6 tests JVM) ; chaînes `crash_*`
(values/ EN + values-fr/ FR, paritaires) ; manifeste —
`android:name=".AppJoide"` et l'activité `.crash.EcranCrash`
(non exportée, `Theme.Material.NoActionBar` du cadre, hors recents,
`allowTaskReparenting=false`) ; `MainActivity.onResume` — le filet
des rapports jamais vus.

**Corrigés (la dette NewApi, 8 erreurs lint = crashs réels
`NoSuchMethodError` sur appareils ≤ Android 13/12L)** : `Paths.get`
pour `Path.of` (×3 `ControleurFichiers`) ; `collect(toList())` pour
`Stream.toList` (`EtatProblemes`) ; lecture bornée à la main pour
`readAllBytes` (×2 `ProprietesConstruction`, `lireTout`) ; copie à la
main pour `transferTo` (`PreparationChaine`) ; `readAllBytes`+UTF-8
pour `Files.readString` (`SpecsBuild`).

**Filet** : `ArchitectureVuesTest.ZONES_EXCUSSEES_VUES` — UNE entrée
motivée `crash/EcranCrash.java` (l'unique vue du produit qui DOIT se
passer du Graphite ; consignée dans le filet).

## 4. Mesures (base A9 → C1)

| Mesure | A9 | C1 |
|---|---|---|
| Tests | 370 | **376** (6 nouveaux `RapportCrashTest` ; 0 échec) |
| Lint erreurs | 11 (8 NewApi + 3 DiffUtilEquals) | **3** (DiffUtilEquals seuls — faux positifs records) |
| APK debug | 15 848 929 o | **15 868 489 o** (+19 560 o : le garde) |
| Manifeste | 6 activités | 7 (+ `EcranCrash`) + `android:name` |
| Écrans instanciant des vues | 0 | 0 (l'exception vit hors `ecrans/`, motivée) |
| Chaînes FR/EN | 391 = 391 | **406 = 406** (+15 `crash_*`) |

## 5. Validation

- `:app-android:testDebug` — **376 tests, 0 échec, 0 erreur** (50
  fichiers de résultats ; `ArchitectureVuesTest` 9 tests verts,
  l'exception `crash/EcranCrash.java` motivée).
- `:app-android:assembleDebug :app-android:verifierOmbreApk` — verts
  (APK 15 868 489 o ; `aapt2 dump` vérifie `AppJoide` + `EcranCrash`
  au manifeste ; mapping R8 : `GardienCrash`/`RapportCrash` conservés
  sous noms courts — appelés directs, aucune réflexion).
- `:app-android:lintDebug` — **3 erreurs** (les `DiffUtilEquals` faux
  positifs connus sur records) contre 11 : les 8 `NewApi` éteintes.
- **Aucun appareil/émulateur au bac à sable** (écart P8 n° 4) : le
  comportement sur l'appareil du propriétaire se valide AU PROCHAIN
  PLANTAGE — c'est le but du garde : le rapport complet (type, classe,
  pile, contexte) dira nommément la cause des symptômes du 2026-11-19.

## 6. Risques résiduels et suite

- Le lancement de l'écran juste avant la mort du processus repose sur
  le mécanisme AMS éprouvé (CustomActivityOnCrash) ; s'il est refusé
  (app en arrière-plan), LE FILET de `MainActivity.onResume` montre le
  rapport au retour — jamais un silence.
- Le seuil du gel (8 s) est conservateur (l'ANR système vole à ~5 s
  d'entrée) ; un travail légitime du fil principal > 8 s est possible
  mais improbable dans cette application (imports, session et build
  sur fils de fond).
- Suite recommandée : reproduire les trois symptômes sur l'appareil
  avec le garde posé, LIRE le rapport (type + pile), corriger la
  cause racine au lot C2 ; la clé/provider IA non chargés
  (`brancherEspaceAgent` → `Reglages.lire` → `lireCle`) se déboguent
  avec la pile du plantage s'il y en a un, sinon par le journal de
  diagnostic des paramètres.
