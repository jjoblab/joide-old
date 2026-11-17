# ADR-058 — Lot N : le stockage chiffré des clés d'API — un port, l'implémentation Android Keystore au lot P

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

La décision D-6 du plan ADR-É10 (GO du propriétaire) retient le
chiffrement DÈS la v1 : « clé dans Android Keystore, fichier AES-GCM
via `AtomicFiles`, sans AndroidX ; jamais de clés de secours en
clair ». L'ADR-É9 constatait le contraire chez CodeAssist (risque
n° 1 : `prefs.properties` en texte, clés de secours une par ligne,
chiffrement repoussé) — à ne pas reproduire.

## Décision

### 1. Le lot N ne touche aucune clé

Le noyau agent ne connaît pas les clés : les adaptateurs reçoivent
la clé en CONSTRUCTEUR (une chaîne en mémoire le temps du tour),
jamais lue d'un fichier par le noyau, jamais écrite. La règle «
aucune clé dans le dépôt » vaut le code ET les tests : les clés des
tests sont des constantes visiblement factices (`cle-test`), aucun
réseau n'est appelé. Une clé absente produit `CLE_MANQUANTE` — un
message qui dit où la poser (les réglages), jamais un échec muet.

### 2. Le port du stockage chiffré

L'interface `PortCles` (lot P, côté application) : `poser(fournisseur,
cle)` / `lire(fournisseur)` / `effacer(fournisseur)`. Le format
persisté (`joide.cles/1`, écrit par `AtomicFiles`) : un fichier par
fournisseur, `AES-GCM` avec IV aléatoire par écriture, la clé de
chiffrement vit dans **Android Keystore** (alias dédié JoIDE,
`KeyGenParameterSpec`, `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`,
`GcmBlockSize`) — l'implémentation est un composant Android (le
Keystore n'existe pas sur la JVM de bureau), donc elle vit au lot P
dans app-android et ses tests instrumentés ; le lot N pose le port
et cette ADR, le contrat est verrouillé avant l'UI.

### 3. Les règles du défaut sûr

- **Jamais de clé de secours en clair** : l'échec de lecture du
  Keystore est un échec NOMMÉ (l'utilisateur repose la clé), pas un
  repli silencieux sur du texte.
- **La clé ne quitte jamais l'appareil** : pas de sauvegarde
  (`allowBackup` exclura le répertoire des clés quand le lot P y
  viendra), pas de journal (ADR-056 : les erreurs du transport
  n'échoient jamais la clé), pas d'export.
- **La suppression est honnête** : effacer une clé efface le
  fichier ET l'entrée — le réglage affichera « non configurée », pas
  un champ vide qui laisserait croire une clé présente.
- **Un test de bout en bout** chiffrera/déchiffrera une clé factice
  au lot P : la garantie est mécanique, pas déclarative.

### 4. Pourquoi un port et pas une implémentation au lot N

Le module agent est pur JVM (ADR-055) : `android.keystore` y est
interdit par construction (le scan API 28 rejette tout `android/`).
Le port laisse le noyau testable sans SDK et l'implémentation
vérifiable SUR appareil — exactement la frontière que les lots
suivants consomment.

## Conséquences

- Le lot P branchera : l'écran des réglages IA (ADR-É10) pose les
  clés par le port ; l'entrée « Demander à l'IA » refusera
  proprement tant qu'aucune clé n'est posée (`CLE_MANQUANTE` dit
  quoi faire — l'erreur actionnable de la taxonomie).
- La même interface servira les clés des fournisseurs futurs
  (Gemini au lot Q) : un fichier par fournisseur, aucune clé
  partagée.
