# ADR-046 — Structure de l'application P8 : import par copie, store système, builds JVM sur appareil

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

ADR-043 câble le démon sur Android (service de premier plan `:build`,
socket locale, `DaemonClient.over`). Restait à décider comment l'UI
consomme un projet choisi par le sélecteur système (SAF), quel store
le démon ouvre sur l'appareil, et ce que « construire » veut dire sur
un appareil où D8/R8 ne sont pas embarqués. P1 avait prévu «
l'implémentation Android du même contrat [BuildStore] vivra avec le
démon » sans en fixer la forme.

## Décision

1. **Import par copie SAF → stockage privé** : les moteurs (code-lsp),
   l'aperçu (LayoutLib) et le pipeline travaillent sur de vrais
   `java.nio.file.Path` ; un arbre SAF n'en est pas un. L'application
   copie l'arbre choisi dans `filesDir/projets/<nom>` (réimport =
   remplacement), l'original n'est jamais modifié. L'accès direct SAF
   (provider de système de fichiers) est un backlog, pas un raccourci
   muet.
2. **`StoreAndroid` sur la base système** (`android.database.sqlite`),
   même contrat `BuildStore` et même schéma que le store SQLite de
   bureau (P1) : WAL, transactions, éviction LRU — la reprise après
   mort du démon relit le disque sur l'appareil comme au bureau. La
   politique d'éviction (pure) est testée sur la JVM ; la couche
   SQLite s'exécute sur l'appareil.
3. **Builds JVM seuls sur appareil en P8** : le pipeline Android
   exige D8/R8 (et la fusion aapt2), non embarqués dans l'APK — les
   embarquer doublerait la taille pour un gain que personne n'a
   demandé. Les modules Android d'un projet sont listés en écarts de
   la console, jamais construits en silence. Décision de périmètre
   rapportée au propriétaire (backlog P8).
4. **Projets Gradle en mode compatibilité** : l'import statique P7
   n'est pas dans l'application (le graphe ADR-011 ne lui donne pas
   accès à `import-gradle`) ; la bannière du lot A le dit. L'échappatoire
   reste l'export P7 côté bureau (ADR-041/042).
5. **Port du service publié par fichier** : le service `:build` écrit
   son port éphémère (rebouclage) dans `filesDir/demon-build.port`
   (écriture atomique) ; l'UI le lit après `startForegroundService`.
   Jamais de port fixe, jamais exposé au réseau.
6. **Une connexion à la fois sur l'hôte socket** : l'UI est un client
   unique ; le `BuildRunner` (compilateurs chauds) survit aux
   reconnexions. La bascule en processus (ADR-043 point 4) est une
   case de la console, annoncée dans la console — jamais silencieuse.

## Options

| Option | Verdict | Raison |
|---|---|---|
| Provider SAF direct (pas de copie) | écartée | aucun chemin réel pour les moteurs et le pipeline ; un pont par fournisseur exigerait un VFS parallèle non mesuré |
| Store journal déplacé vers core-store principal | écartée | change la structure P1 pour éviter une classe ; la base système est le contrat annoncé par P1 |
| Embarquer D8/R8 dans l'APK | écartée en P8 | taille doublée sans demande ; décision du propriétaire requise |
| Découverte du port par broadcast/binder | écartée | double le mécanisme de découverte ; un fichier atomique suffit et se teste |

## Conséquences

- L'import par copie duplique le stockage (projet original + copie) :
  documenté, l'utilisateur peut purger (backlog : écran de gestion).
- `StoreException` gagne un constructeur à un argument (additif, sans
  rupture pour le bureau).
- `LanguageModelSync` expose la dernière résolution (`derniereResolution`)
  — le build réutilise les jars déjà résolus par la synchronisation,
  jamais une seconde résolution.
- Les écarts de build (modules Android, dépendances non résolues, AAR)
  s'affichent dans la console AVANT le lancement, jamais perdus.
