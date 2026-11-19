# ADR-074 — Lot XII : la remontée de rung — l'OOM mid-run nommé et
gravissable

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 : le
  lot XI sur le terrain, toujours « sortie 134 puis 134 » — mais le
  diagnostic du lot X parle enfin : « Android 35, inlining JIT actif ;
  stderr : vide », tas 512 Mo aux deux tentatives)
- **Date** : 2026-11-17

## Contexte

Le message du lot X/XI donne enfin les pièces du puzzle :

1. **Android 35** — hors des tables de crash connues : le JIT
   malcompileur (#1748, API ≤ 30) ne s'applique pas (l'inlining est
   correctement ACTIF, le drapeau de CodeAssist ne part que ≤ 30) ;
   le dispatch d'interface fastutil cassé ne concerne que 12/12L ;
   le pack ART-patché du lot XI couvre les sept breakages confirmés.
2. **Tas 512 Mo aux DEUX tentatives** — la première tentative a
   RÉPONDU (l'ouvrière démarrait bien), et la reprise du lot X
   RESTAIT au même rung : « l'ouvrière démarrait bien, le lot est le
   suspect ». Or sur un appareil ~4 Go, la garde de DÉMARRAGE
   (rung × 2 ≤ RAM/2) rejette 1024 de justesse (2048 > ~1974) :
   l'échelle plafonne à 768/512 — un gros module (`:core`) n'y tient
   PAS, et l'OOM d'ART au milieu d'un compile ABORTE sans message
   (sortie 134, stderr vide : le « Fatal signal » ne va qu'au
   logcat).
3. La descente du lot X était pensée pour les morts SANS réponse
   (échec de réservation au démarrage) — elle est contre-productive
   pour une mort APRÈS réponse : le tas essayé était trop PETIT.

## Décision

- **La remontée de rung** (la logique `rungTentative` inverse son
  cas « après réponse ») : une morte APRÈS réponse gravit l'échelle
  de REMONTÉE — l'union de l'échelle de démarrage, du tas demandé et
  d'un rung de secours 1536 Mo, à la garde ASSOULIE
  (rung × 2 ≤ 80 % de la RAM, triée ascendante). La garde de
  démarrage reste inchangée (la réservation virtuelle y est testée
  SANS engagement : un rung trop grand aborte net) ; la remontée ne
  part que d'un rung ÉPROUVÉ — la VM démarre déjà au rung essayé,
  l'engagement physique suit l'usage réel, et l'autre option est un
  build qui échoue. Sur 3 Go : 1024 gravissable ; sur 4 Go : 1536 ;
  au sommet sans guérir, dernier essai au même tas.
- **Trois tentatives par lot** (au lieu de deux) : 512 → 1024 →
  1536 tient dans le budget de reprises, sans dépasser le plafond
  d'abandon de session (trois échecs COMPLETS de lots).
- **L'échec final nomme les TAS ESSAYÉS** (« tas essayés : 512 puis
  1024 puis 1536 ») et, quand la remontée a plafonné SANS guérir
  (dernière morte après réponse au sommet), porte le soupçon d'OOM
  avec la RAM et la garde : « le lot dépasse peut-être le tas le
  plus grand que cet appareil soutient ».
- NOT : changer la fraction de la garde de DÉMARRAGE (0.5) — le
  comportement CodeAssist (startWorker et sa LADDER) reste la source ;
  la remontée est la couche au-DESSUS, pas un relâchement du filtre.
  NOT : découper le lot de compilation (`:core` en sous-lots) — la
  bonne direction si un module dépasse 1536 sur un appareil à 3 Go,
  mais une autre chaîne (les demandes de compilation), pas le
  pilote.

## Conséquences

- Le cas du terrain (Android 35, ~4 Go, :core) tente maintenant
  512 puis 1024 puis 1536 — le module a une chance de tenir dans
  1,5 Gio ; s'il dépasse encore, l'échec dit EXPLICITEMENT où est le
  plafond et quel rung a manqué.
- La prochaine console nommera l'un des trois états : succès au
  rung supérieur ; échec avec les tas essayés + soupçon d'OOM ;
  échec avec une cause stderr réelle (le drain sans course du lot X
  la capture désormais).
- Les essais desktop prouvent la remontée SANS dépendre de la RAM du
  bac à sable : la fausse VM journalise son `-Xmx` à chaque
  lancement, le test compare aux échelles calculées ; l'essai des
  trois morts-sans-réponse reste nommé (TROIS tentatives, descente).
- L'essai de bureau qui rejoue le terrain (RAM ~4 Go du bac à
  sable : échelle [768, 512, 384]) fait maintenant MONTER la reprise
  768 → 1024 après une morte après réponse — la preuve du cas
  Android 35.
