# build-daemon

Process :build (service de premier plan), IPC, compilateurs chauds,
admission mémoire, reprise après kill.

| | |
|---|---|
| Paquet | `jo.joide.build.daemon` |
| Nature | JVM pure (source set principal sous contrôle API 28 — le client de l'IDE Android l'embarquera ; point d'entrée de bureau et banc de mesure dans les source sets `desktop` et `bench`, hors contrôle API 28) |
| Dépendances JoIDE | build-android, core-store |
| Phase de remplissage | P6 |

## Invariants

Le contexte se reconstruit depuis le disque ; la mort du daemon n'est
jamais celle de l'UI. Un build tué en plein vol ne corrompt rien :
seule la queue recompile. Les compilateurs et dexeurs restent chauds
entre les builds d'un même espace.

## Contenu

- `protocol` : cadres de longueur (JSON UTF-8 précédé de sa taille,
  plafond de cadre), écriture JSON compacte, description de build
  (`BuildSpec` : espaces, modules JVM ou Android, variantes).
- `client` : `DaemonClient` — lance la machine virtuelle fille,
  échange les cadres, expose les événements en file bloquante ; la
  mort du démon (OOM, kill) est un événement (`DaemonDiedException`),
  jamais celle de l'appelant.
- `runner` : `BuildRunner` — exécuteur équivalent en processus :
  construit le graphe (pipeline JVM ou Android selon la description),
  le donne au moteur complet, publie les tâches en direct ; les
  fabriques et le dexeur en processus sont créés une fois par démon
  (compilateurs chauds) ; l'ensemble sale de la racine des sources
  est différé contre l'instantané persisté du store — le démon meurt
  et renaît, le diff disque remplace l'observateur ; les fils de
  travail sont démons : une erreur non rattrapée (OOM) tue la machine
  virtuelle, pas un zombie.
- source set `desktop` : `DaemonMain` — point d'entrée de bureau :
  lit les cadres sur l'entrée standard, ouvre le store SQLite
  persistant à la racine des sorties (la reprise après kill s'y
  appuie), écrit les événements sur la sortie standard ; les erreurs
  standard portent les piles (un OOM y est visible sans corrompre le
  protocole) ; l'injection de pannes (preuve OOM forcé) y vit,
  armée par `-Djoide.daemon.faults=1` — le démon de production
  refuse toujours.
- source set `bench` : `DaemonBench` — preuves de mémoire et de
  latence par tier (`./gradlew :build-daemon:daemonBench -Ptier=L`),
  générateur déterministe aux gabarits de tools-bench.

## Preuves (rapport P6)

Kill -9 en plein build et au repos, OOM forcé : le client survit, un
nouveau démon reprend depuis le disque (seule la queue recompile ;
zéro re-travail au second passage). Tier L (200 modules, 30 000
fichiers) : pic de tas 95 Mo sous plafond de 700 Mo, 19 s à froid,
1,6 s sans changement. Pipeline Android complet par IPC : fusion et
rétrécissement R8 dans le fork à gros tas d'un gigaoctet pendant que
le démon reste sous 700 Mo, APK signé v1+v2 vérifié, AAB validé.
