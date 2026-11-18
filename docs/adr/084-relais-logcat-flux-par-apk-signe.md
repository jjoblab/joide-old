# ADR-084 — Le relais de journal du Logcat : le flux passe PAR l'APK construit et signé par JoIDE

Date : 2026-11-18 — chantier « Refonte de l'atelier (v2) », suite du
lot L3 (la section Logcat porte ses états honnêtes depuis le
2026-11-18, sans mécanisme de collecte).

## Contexte

La section Logcat de la feuille basse (§3.6 du prompt v2) montre la
sortie de l'APPLICATION DE L'UTILISATEUR — jamais celle de l'IDE. Le
terrain interdit toutes les voies habituelles :

- `READ_LOGS` n'est plus octroyable à une application tierce depuis
  Android 4.1 ; les voies « shell logcat » n'existent pas dans notre
  bac à sable (ADB est hors de portée d'une app) ;
- lire les fichiers `/dev/log/*` exige le même privilège ;
- injecter une classe DANS l'APK au build (transform D8) est fragile
  face à R8 et à la règle d'immutabilité des artefacts publiés.

La maquette dit « JoIDE ajoute un petit relais de journal à l'APK » :
le flux doit venir DE l'application de l'utilisateur, à sa demande.

## Décision

1. **Le relais est une brique explicite que l'utilisateur ajoute** :
   `jo.joide:relais-logcat` (une classe + une déclaration de
   provider). Rien n'est injecté à l'insu : la vie privée est un
   choix de l'utilisateur, pas un effet du build.
2. **Le transport est un ContentProvider de l'IDE** (autorité
   `joide.logcat`), **exporté, gardé par une permission SIGNATURE**
   `joide.permission.RELAIS_LOGCAT`. L'IDE construit ET signe l'APK de
   l'utilisateur (doctrine des APK signés, ADR-047) : l'application
   construite porte la même signature que JoIDE et se voit accorder la
   permission sans configuration. La frontière d'UID est franchie par
   la signature, jamais par une permission dangereuse.
3. **Le protocole : `call()` par lots** — `getContentResolver().call(
   "joide.logcat", "ecrire", null, bundle)` avec les lignes en
   `ArrayList<String>` (`Niveau|Tag|Message`), bornées au plafond
   annoncé (400, comme `EtatLogcat`). Pas de socket, pas d'AIDL, rien
   à réécrire au build ; un lot refusé (fichier fermé, IDE absent) est
   simplement perdu — le logcat n'est pas un journal légal.
4. **Côté IDE, tout arrive via un tampon unique** (`FluxLogcat`,
   thread-safe) que le provider alimente depuis le fil Binder et que
   l'atelier lit sur le fil UI — la même source que le test JVM.
5. **La brique utilisateur est le lot suivant** : jusqu'à son
   existence, la section garde ses états honnêtes (« Application non
   lancée ») — la franchise interdit de faire croire à un flux vivant.

## Conséquences

- Aucune permission dangereuse, aucun privilège système, aucune
  fragilité de transform de build.
- L'utilisateur voit exactement ce qu'il partage : ce que son code
  journalise via la brique.
- Le plafond 400 et le lot par `call()` bornent la mémoire des deux
  côtés ; les lignes hors fenêtre d'histogramme restent dans le
  transcript (déjà le comportement d'`EtatLogcat`).
- Si l'utilisateur construit son APK hors JoIDE (sans la signature
  JoIDE), la permission signature n'est pas accordée et le relais est
  muet — dégradation visible dans la section (état vide), jamais un
  crash.
