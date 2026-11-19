# ADR-072 — Lot X : l'ouvrière Kotlin morte est NOMMÉE — le JIT
dompté, le tas respectueux de la RAM, la reprise sur une neuve

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « :core:compileKotlin : échouée … la VM ouvrière du compilateur
  Kotlin a échoué : l'ouvrière Kotlin est morte (sortie 134) — pack
  kotlin 2.3.0 (kotlin-compiler-2.3.0.dex.zip), binaire
  /apex/com.android.art/bin/dalvikvm64 », et reproduire la manière
  CodeAssist)
- **Date** : 2026-11-17

## Contexte

Le lot VII avait mis le compilateur Kotlin dans une VM FORKÉE
(ADR-069, la manière CodeAssist `ForkedKotlinCompiler`) ; les lots
VIII et IX ont rendu la chaîne différée et le démon NOMMÉS. Le
premier lot Kotlin du terrain arrive et meurt : **sortie 134** — un
SIGABRT — et la console ne dit RIEN de plus, parce que le stderr de
l'ouvrière était HÉRITÉ (logcat sur l'appareil, invisible à la
console du build) : le même mal que l'ADR-070/071 avaient guéri pour
le service — un silence où une cause devait être.

Deux familles de SIGABRT sont documentées chez CodeAssist, dont la
note d'architecture (`R8ForkSupport`, `ForkedToolVm`) et son issue
#1748 portent la signature exacte :

1. **Le JIT d'ART malcompile le compilateur lui-même** (#1748) :
   en inline à travers un cache d'appel polymorphe, le JIT produit du
   code qui saute dans le tas ou dans boot.art — crashes dans
   `HInliner::TryInlinePolymorphicCall`, visés sur
   `FirJavaFacadeKt.convertJavaMethodToFir`. Les rapports Play
   couvrent l'API 26-30 (29 et 30 d'abord, 32 bits d'abord), AUCUN
   sur API 31+ ;
2. **La réservation de tas** : ART réserve DEUX fois `-Xmx` d'espace
   d'adresses (l'espace de régions du GC copiant concurrent) ; quand
   la réservation échoue, `Runtime::Init` fait un CHECK FAILED —
   `main_mem_map_1.IsValid()` / `region_space_mem_map.IsValid()` —
   qui est un SIGABRT AU DÉMARRAGE de la VM. Un tas demandé trop
   grand pour la RAM ne rate pas poliment : il aborte.

Les deux tuent la VM ouvrière et se voyaient « sortie 134 », sans
distinguer l'un de l'autre ni proposer de suite.

## Décision

Toutes les couches sont côté PILOTE (`KotlinForkedDriver`) : le
protocole ligne et l'ouvrière du pack restent EXACTS — le pack
épinglé (`ccc17bcd…`, l'ADR-069) reste valable, AUCUNE
régénération, AUCUN décalage de version entre un APK neuf et un pack
en cache.

### 1. Le stderr CAPTURÉ (X/1)

Le `redirectError(INHERIT)` part : un fil séparé draine le stderr de
l'ouvrière — une queue non lue bloque la VM quand le tampon du tuyau
se remplit — et le porte deux fois : le JOURNAL COMPLET dans
`ouvriere-kotlin.err` du répertoire d'outillage, et une QUEUE de
40 lignes gardée en mémoire qui accompagne tout échec (« stderr :
… | Abort message: … »). Le « Abort message: … » d'ART atteint enfin
la console du build : la prochaine morte se diagnostique sans adb.

### 2. Le JIT dompté (X/2)

Sur SDK ≤ 30 (Android 11 et plus ancien — le périmètre exact du
#1748), la VM ouvrière démarre avec `-Xcompiler-option
--inline-max-code-units=0` : le JIT lit les options de compilateur,
un budget d'inlining nul éteint SON inliner, les méthodes compilent
quand même. Le SDK se lit par RÉFLEXION sur `android.os.Build$VERSION`
(le module n'a PAS le SDK à la compilation, le processus de l'app
l'a au runtime), en repli `/system/build.prop`. SDK inconnu avec un
binaire dalvikvm : le drapeau part QUAND MÊME (prudence — il ne
coûte que la vitesse du JIT). Sur le bureau (`joide.kotlinVm` =
java), JAMAIS : un drapeau inconnu tuerait la VM au démarrage.
Avec les drapeaux de l'environnement que CodeAssist passe aussi :
`-Djava.io.tmpdir` pointe `kotlinc-tmp` du répertoire d'outillage
(la VM en ligne de commande a un tmpdir par défaut qui N'EXISTE PAS
sous le bac à sable d'une app, et le compilateur y crée des fichiers
temporaires) et `-Dkotlin.colors.enabled=false` (l'initialiseur de
`PlainTextMessageRenderer` lit jansi, absent du pack — un
`NoClassDefFoundError` là tuerait la VM au lieu d'échouer un lot).

### 3. L'échelle de tas respecte la RAM (X/3)

L'échelle [demandé (`joide.kotlinWorkerHeap`, défaut 1 Gio —
l'ADR-069), 768, 512, 384 Mo] est FILTRÉE : un rung dont la
RÉSERVATION (deux fois le rung) dépasse la moitié de la RAM de
l'appareil (`/proc/meminfo`) ne part jamais — « stopper un petit
téléphone à un rung atteignable au lieu de monter vers un abort
certain », la `ForkedToolVm.affordableHeaps` de CodeAssist. RAM
inconnue : l'échelle reste entière, l'appareil reste l'autorité.
Une morte SANS réponse (un abort de démarrage) re-fourque au rung
INFÉRIEUR ; le rung du dernier worker ayant RÉPONDU se MÉMORISE
(`rungEprouve`) : les re-forks suivants partent de là plutôt que de
remonter à un tas déjà abortif.

### 4. La reprise neuve, l'échec NOMMÉ, le silence borné (X/4)

Par lot : DEUX tentatives — la première réutilise l'ouvrière vivante,
la reprise fourque une NEUVE (le plus souvent une ouvrière morte
entre deux lots : l'OS la récupère quand il veut, et CodeAssist ne
compte pas ce cas comme un échec quand la reprise réussit). Une
morte qui survit au retry échoue le lot NOMMÉ : les deux codes de
sortie, la queue de stderr, le tas essayé — et sur SDK ≤ 30
l'explication du #1748 avec l'instruction de réessayer. Trois échecs
COMPLETS abandonnent le fork pour la session (les lots suivants
échouent vite, sans re-payer les forcs) ; le crash du JIT ne compte
PAS (abandonner ne le guérit pas — le prochain lot réessaie une
neuve, la manière CodeAssist). Une ouvrière VIVANTE mais muette est
bornée par `joide.kotlinWorkerTimeoutSec` (défaut 3600 s) : le lot
échoue nommé au lieu de pendre le build. La lecture du protocole
passe par un fil séparé et une file bornée : une morte est vue À
L'EOF (jamais pendue sur un canal mort), un silence à l'échéance.

### Les preuves (bureau, sans ART)

Les morts du terrain se JOUENT : une fausse VM (`fausseVm`) meurt à
son premier lancement (`exit 134`, stderr « Abort message: … ») et
sert le VRAI ouvrier aux suivants — `uneOuvriereMorteEstSurviveeParUneNeuve`
prouve la reprise transparente ; `fausseVmMorte` meurt À CHAQUE
lancement — `deuxMortsNommentLEchecAvecLeStderr` prouve le message
nommé (les deux sorties, la queue de stderr, le journal sur disque) ;
`uneOuvriereMuetteEstBorneeParLeDelai` prouve le silence borné ;
`lesTasAuDelaDeLaMoitieDeLaRamSontRejetes` et `leTasSeParseEnUnites`
ancrent l'échelle ; `leDrapeauJitNePartPasSurLeBureau` ancre le
drapeau ART-only. Les trois essais bout-en-bout du lot VII restent
verts (protocole inchangé), ainsi que les suites de build-jvm,
build-android (acceptation complète du pipeline) et build-daemon.

## Conséquences

- La prochaine morte d'ouvrière porte SA CAUSE dans la console du
  build (code de sortie + queue de stderr + journal complet) : le
  « sortie 134 » du terrain du 2026-11-17 devient un diagnostic, et
  les deux familles connues de SIGABRT ont chacune leur parade.
- Sur un appareil Android ≤ 11, la VM du compilateur démarre avec
  l'inlining JIT désactivé : la cause n° 1 des morts du terrain
  (CodeAssist #1748, jour du 2026-11-07) ne peut plus produire un
  SIGABRT par inline mal compilé.
- Sur un appareil aux RAM modestes, la VM ne demande plus un tas
  dont la simple réservation aborterait : elle démarre au rung que
  la moitié de la RAM soutient, et descend si le rung aborte.
- Une morte transitoire (l'OS qui récupère l'ouvrière) ne coûte plus
  un build : la reprise sur ouvrière neuve est transparente.
- La surcharge de tas (`joide.kotlinWorkerHeap`) et le délai
  (`joide.kotlinWorkerTimeoutSec`) rejoignent `joide.kotlinVm` et
  `joide.kotlinWorkerCp` comme boutons d'essai sans recompilation.
- Suites au vert après le lot : build-android (dont l'acceptation
  bout-en-bout), build-jvm, build-daemon ; le pack épinglé est
  INTACT (protocole inchangé, aucune empreinte bougée).
- Suitent pour une prochaine régénération de pack (pas bloquant,
  protocole à figer d'abord) : côté ouvrière, rediriger System.out
  vers stderr avec une poignée privée sur le vrai stdout (une ligne
  égarée du compilateur déchronise le protocole — la manière
  `KotlincWorkerMain` de CodeAssist), transformer toute exception en
  réponse ERR (une ouvrière ne meurt pas d'un lot qui lève), et une
  sortie après repos (rendre le tas à l'appareil quand une session
  s'endort).
