# ADR-038 — Démon de build : protocole par cadres, admission mémoire, reprise après mort

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

Le moteur de build (P2) et ses pipelines (P3, P5) vivaient dans la
machine virtuelle de l'appelant. L'IDE Android (P8) exige un process
`:build` distinct : la mort du démon (OOM, kill -9) ne doit jamais
être celle de l'UI, et le contexte doit renaître du disque. Les
compilateurs (ecj, compilateur Kotlin) et le dexeur D8 doivent rester
chauds entre les builds — les re-payer à chaque build tuerait la
latence — tandis que la fusion dex et le rétrécissement R8
travaillent dans un fork à gros tas (ADR-036) dont la mémoire n'est
pas celle du démon.

## Décision

1. **Protocole par cadres sur les flux standard**. Chaque message est
   un texte JSON UTF-8 précédé de sa longueur en octets (entier sur
   quatre octets, petit boutien), plafonné à dix Mio — un flux corrompu
   est refusé, jamais interprété. Le démon lit l'entrée standard et
   écrit la sortie standard ; les erreurs standard portent les piles.
   Les événements de tâches partent en direct (démarrage et fin), pas
   dans un bilan posthume : un démon qui ne parle qu'à la fin n'est
   pas un démon. La description de build (`BuildSpec`) voyage en JSON
   dans le protocole ; les modules y sont des objets imbriqués, jamais
   du texte doublement encodé (défaut rencontré et corrigé pendant la
   phase : le démon lisait un nom nul).

2. **Compilateurs chauds par détention**. Le démon détient une
   fabrique du pipeline JVM par espace (clé : sorties, bibliothèques,
   niveau de source) et un triplet dexeur-scope/dexeur-fork/signataire
   par démon : reconstruire le même projet ne paie aucune
   initialisation. Le compte de fabriques et de dexeurs créés est
   publié dans chaque bilan — la preuve « compilateurs chauds » est un
   compteur constant, pas une croyance.

3. **Admission mémoire contre le tas du démon**. Le budget de la
   description (`heapMb`) traverse l'ordonnanceur du moteur
   (revendications par tâche, P2) et l'entrée du démon : un budget au
  -dessus du tas du démon est refusé explicitement — jamais d'OOM
   silencieuse à l'admission. Les revendications des tâches de fork
   (fusion/R8 : 1 024 Mo) sont portées au budget : la comptabilité est
   celle de la mémoire de l'appareil, pas seulement du démon — le tas
   du démon doit couvrir le budget qu'il admet, l'usage réel du démon
   reste en dessous pendant que le fork vit dehors.

4. **Reprise après mort : le store est la mémoire**. Le démon de
   bureau ouvre le store SQLite persistant à la racine des sorties.
   Les résultats de tâches sont persistés par lot pendant le build :
   un build tué en plein vol laisse durable le travail fini — la
   reprise ne compile que la queue. L'ensemble sale de la racine des
   sources est différé contre l'instantané persisté du store : le
   démon n'a pas d'observateur entre ses morts, le diff disque le
   remplace (les ensembles sales explicites d'un observateur branché
   au protocole priment quand ils existent — le câblage P8).
   Limite documentée : le diff est par signaux (taille, horodatage,
   clé de fichier) ; une réécriture de même taille dans le même
   grain d'horodatage est invisible jusqu'au câblage de
   l'observateur — la comparaison par condensé de contenu est
   reportée au backlog.

5. **Fils de travail démons**. La boucle du démon est son seul fil de
   vie : une erreur non rattrapée (les rattrapages du protocole
   attrapent `Exception`, jamais `Error`) tue la machine virtuelle
   avec elle. Des fils de travail non démons zombifiaient le démon
   après OOM (défaut rencontré et corrigé pendant la phase : processus
   vivant, plus personne pour lire).

6. **Injection de pannes armée par propriété**. La preuve « OOM forcé
   » exige une mort déterministe par saturation réelle du tas : la
   requête « crash » épuise le tas par allocations impossibles (sans
   spirale de ramasse-miettes : une pique plus grande que le tas
   échoue après un ramassage au plus) puis relance l'erreur hors de
   tout rattrapage. Elle n'est honorée que si la machine virtuelle
   fut lancée avec `-Djoide.daemon.faults=1` ; le démon de production
   répond `crash-refused` et survit — l'injection est un outil de
   preuve, jamais une porte dérobée.

## Conséquences

- Le client survit à toute mort du démon : `DaemonDiedException` est
  un événement ; le code de sortie est récoltable (`awaitExit`).
- La latence mesurée (tier L : ping médian 0 ms, build sans changement
  1,6 s, salutation 209 ms) est la propriété du démon, pas celle du
  moteur.
- Le banc `daemonBench` (source set `bench`) fait foi par tier : pic
  de tas sous plafond, zéro travail sans changement, compteur de
  compilateurs constant.
- Le démon de bureau (desktop) reste hors contrôle API 28 comme le
  pilote SQLite ; le client et le protocole (source set principal)
  y sont soumis — le client voyage dans l'application Android.
