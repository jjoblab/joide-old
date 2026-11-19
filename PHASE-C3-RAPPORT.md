# PHASE C3 — l'IV refusé, la feuille en fragments (2026-11-19)

## Le terrain

Deux remontées, un build d'écart :

| | 09:55:35 (C1/C2 traités) | 10:23:01 (ce lot) |
|---|---|---|
| build | `0.1.0 (68f72b9)` | `0.1.0 (ef88743)` |
| symptôme | tiroir agent mort à l'ouverture | « restart » au bouton Sauver de la clé d'API |
| pile | `ViewStub.inflate → getLayoutDimension` | `MaterialButton.performClick → EcranParametres.U2 → ClesChiffrees` |
| exception | `UnsupportedOperationException` (layout_width) | `IllegalStateException: AES-GCM unavailable: Caller-provided IV not permitted` |

La demande : « Pourquoi il y a restart quand j'avais sur save après
l'ajout de l'API key. Le bottomsheet n'affiche toujours pas aucun vue,
utiliser des fragments que ce soit les logcat, journal, etc. »

## Le diagnostic

**Le restart EST le plantage.** Le garde-crash (lot C1) écrit son
rapport `joide.crash/1` puis le processus meurt : Android relance
l'activité — c'est le « restart », la clé n'est jamais sauvée.

**La cause est l'asymétrie AOSP de l'IV GCM.** La clé de
chiffrement des clés d'API vit dans l'Android Keystore
(`KeystoreCles`, `KeyGenParameterSpec` : randomisation exigée par
défaut). Pour une telle clé :

- en CHIFFREMENT, un IV fourni par l'appelant est REFUSÉ
  (« Caller-provided IV not permitted ») ;
- en DÉCHIFFREMENT, il est REQUIS (il faut relire celui du fichier).

`ClesChiffrees.poser` fabriquait son IV (SecureRandom) et le passait
en `GCMParameterSpec` à `init(ENCRYPT_MODE, cle)` : refus
systématique. Le message « AES-GCM unavailable: » (le préfixe
maison) + « Caller-provided IV not permitted » (le refus Keystore)
le nommait dans le rapport.

**Et la feuille ?** Le posement d'hier restait structurellement
fragile : la console arrivait par ViewStub à la racine (z-plan bas)
puis se DÉPLAÇAIT dans la feuille ; les trois listes vivaient dans
le layout de la feuille et s'en DÉTACHAIENT au premier passage de
la console ; surtout, le `0dp`+poids du transcript de la console
était mesuré dans un `wrap_content` sous ScrollView : ZÉRO pixel.
Le terrain demande des fragments — la structure qui tue la classe
des bugs.

## Le lot

### Le coffre (ClesChiffrees)

- `poser` : `init(ENCRYPT_MODE, cle)` SANS spécification — le
  fournisseur génère l'IV aléatoire, `getIV()` le relit, il
  s'écrit dans le fichier comme avant. Le format `joide.cles/1`
  (en-tête, IV 12 octets, chiffré) ne change PAS ; la
  randomisation par écriture demeure.
- `dechiffrer` inchangé : IV du fichier en `GCMParameterSpec` —
  permis (requis, même) en déchiffrement.
- Le `SecureRandom` injecté disparaît (l'aléa vit chez le
  fournisseur) ; les tests suivent (2 constructions, le garde « deux
  poses du même texte diffèrent » reste armé).

### La feuille en fragments

- `FragmentSection` (`ui/atelier`) : UN fragment, QUATRE sections —
  la section vit en argument (constructeur sans argument : la
  restauration du FragmentManager la relit), l'étiquette
  `feuille_*` la retrouve. `onCreateView` gonfle le layout de la
  section par SON binding (`VueConsoleZoneBinding`,
  `FeuilleSectionProblemesBinding`, `FeuilleSectionJournalBinding`,
  `FeuilleSectionLogcatBinding`).
- `atelier_feuille.xml` : le ScrollView meurt — le corps porte la
  barre d'outils puis le `FragmentContainerView`
  `contenu_feuille` (plein parent). Les trois listes quittent le
  layout de la feuille (layouts de section dédiés,
  `feuille_section_*.xml`).
- Les DEUX racines (`activity_atelier.xml`, `layout-w720dp`)
  perdent leur `stub_console` : le montage paresseux devient une
  transaction.
- `VueAtelier.monterSectionFeuille` : ajoute-montre-cache par
  transaction (`commitNow` : la vue existe au retour),
  paresseux, idempotent, l'étiquette retrouve l'instance après
  restauration. `assurerConsoleMontee` : la console se monte EN
  RÉSERVE au premier événement de build (cachée si une autre
  section est visible).
- `surVueSection` / `surSectionDetruite` : câblage des auditeurs et
  RE-POSE de l'état au montage (jamais une section muette) ;
  le registre des listes montées alimente les contrôleurs d'hier
  (`feuille().listeJournal()` etc., nuls tant que non montés — les
  contrôleurs vérifient déjà).
- Le TAP du rail logcat démarre le flux (il ne démarrait qu'au
  glissement d'en-tête).
- Ré-inflation de géométrie : les fragments se retirent
  explicitement avant `setContentView`, la racine neuve les remonte
  depuis les états (`reposerEtatSurNouvelleRacine`).
- `FeuilleBasse` : le registre des listes remplace les `contenus[]`
  ; `surOuverture` devient `surSection` ; plus AUCUNE vue posée ou
  déplacée dans `majCorps`.
- `EcranAtelier` implémente `FragmentSection.Hote` (la voie unique
  du câblage — la coque délègue à `VueAtelier`).

## Discipline tenue (filet A0)

- `androidx.fragment` est dans la liste blanche de l'ADR-085 (la
  famille des dialogues demeure interdite, inutilisée).
- Aucune vue instanciée en code dans les zones ni les écrans.
- Chaque layout a son consommateur `*Binding`.
- Aucune couleur ni texte littéral dans les layouts.
- Le manifeste ne change pas (l'atelier reste seul à déclarer la
  géométrie ; les fragments suivent l'activité).

## Ce qui reste au terrain à vérifier

1. Sauver une clé d'API dans les Paramètres : plus de restart, la
   rangée l'annonce comme posée ; quitter et revenir : la clé
   survit (le format n'a pas changé — une clé posée avant le lot
   C3 ne l'a JAMAIS été, le poser plantait toujours).
2. La feuille : les quatre sections montent avec leur contenu (le
   transcript de la console a sa hauteur, le journal et le logcat
   défilent, le logcat démarre au TAP du rail).
3. Le tiroir agent (C2) reste vivant ; le garde-crash ne dit plus
   rien.
