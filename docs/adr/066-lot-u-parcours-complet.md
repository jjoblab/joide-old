# ADR-066 — Lot U : le parcours complet (OAuth d'interface, OpenRouter, le build embarqué)

- **Statut** : Accepted (mandat du propriétaire du 2026-11-17 :
  « Oui pour le flux OAuth d'interface pour compléter le parcours
  MCP. Ajoute aussi openrouter dans le providers. Le emptyview
  quand aucun onglets n'est ouvert, le tiroir pour l'arborescence
  des fichiers du projet. J'ai appuyé sur build, ça dit que d8 et
  autre n'est pas embarque »)
- **Date** : 2026-11-17

## Contexte

Le lot T avait livré les popups ancrés et la finition CodeAssist ;
le parcours MCP s'arrêtait au 401 : l'écran Connexions disait
AUTH_REQUISE sans jamais offrir la signature. Les fournisseurs
s'arrêtaient à trois — OpenRouter, la passerelle multi-modèles,
manquait. Sur téléphone, l'arborescence vivait dans un tiroir
SANS point d'entrée visible, et l'état vide du puits dépendait de
deux écritures dispersées. Et le build refusait tout module
Android avec « D8/R8 non embarqués » — une décision de taille du
P8 que le propriétaire vient de lever. La note clean-room n° 6
consigne ce que CodeAssist a montré (aapt2 en jniLibs parce
qu'ART n'exécute que depuis nativeLibraryDir ; D8 en processus
parce qu'aucune JVM fille ne vit sur l'appareil) ; l'implémentation
JoIDE reste clean-room.

## Décision

### 1. OpenRouter — la passerelle rejoint la rangée (U/1)

`FournisseurIa` s'allonge EN FIN — l'ordinal est le numéraire des
favoris et des récents du sélecteur, une insertion au milieu
déplacerait les choix de l'utilisateur. La passerelle parle
OpenAI-compatible et porte son URL de base propre
(`https://openrouter.ai/api/v1`) dans la fabrique du service ;
sa clé a sa rangée dans Paramètres, sa puce dans le sélecteur
(les puces DÉFILENT horizontalement, quatre fournisseurs
dépassent la largeur posée), son catalogue de suggestions.

### 2. Le tiroir et l'état vide — visibles (U/2)

Le dossier de la barre supérieure pousse l'arbre sur téléphone :
le tiroir existait depuis le lot J, SON point d'entrée manquait.
L'état vide du puits EST l'état sans onglet actif :
`majPuits()` est l'unique source de vérité — l'ouverture, la
fermeture du dernier onglet, le chargement et l'erreur passent
tous par elle ; jamais un puits muet derrière un éditeur absent.

### 3. Le flux OAuth d'interface — le parcours MCP se complète (U/3)

L'écran Connexions propose « Se connecter par le navigateur »
sur AUTH_REQUISE (et ECHEC) : `FluxOAuthMcp` retient UNE
tentative par serveur — découverte, enregistrement dynamique du
client (RFC 7591), défi PKCE S256, état anti-CSRF — et rend
l'URL de navigateur. Le rappel revient à `joide://mcp/oauth`,
l'écran de rappel exporté (singleTask, hors récents) porte code
et état au service, le code s'échange contre des jetons posés
sous l'identité du serveur, la connexion repart en fond. Un
rappel sans tentative attendue est un REFUS explicite ; l'écran
raconte honnêtement « signé » ou le motif — jamais un silence.

### 4. D8 et aapt2 embarqués — l'APK Android se construit sur l'appareil (U/4)

La route `SpecsBuild.constituer()` ouvre le pipeline ANDROID dès
qu'un module Android vit dans le workspace (frères JVM suivis,
compilés par la même fabrique) : manifeste par facette, ressources
android-res, actifs, AAR extraits (classes et libs — leurs
ressources restent un écart honnête), variante debug à identifiant
lu du manifeste. L'outillage vit dans l'APK : aapt2 préconstruit
en jniLibs par ABI (ReVanced/aapt2, segments LOAD alignés 16 Ko,
magie ELF vérifiée à la pose, jamais au dépouilleur), D8/R8 +
apksig + bundletool dexés dans l'app — le déxeur en processus
chargé par ServiceLoader puis réflexion, le fork à gros tas
réservé aux bureaux qui ont une JVM fille. android.jar et r8 se
téléchargent épinglés (URL, taille, SHA-256) dans le cache privé
de l'app (`joide.toolchain.dir` posé par l'atelier) ; aapt2 se
trouve par `joide.native.dir`. « D8/R8 non embarqués » ne se dit
plus : le mandat est exécuté.

## Conséquences

- L'APK grossit : la chaîne embarquée coûte des dizaines de
  méga-octets — le prix assumé du « build qui marche sur
  l'appareil », réversible par le mandat.
- La première construction Android télécharge la plateforme
  (~60 Mo) : la console l'annonce, la deuxième est à chaud.
- Les ressources des AAR tiers ne se lient pas encore : écart
  explicite dans la console, pas un échec silencieux.
- La v1 des signatures MCP vit en mémoire de session (lot R) ;
  leur persistance chiffrée reste au backlog.
