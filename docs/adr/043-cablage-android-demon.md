# ADR-043 — Câblage Android du démon : service de premier plan, socket local, UI survivante

- **Statut** : Accepted
- **Date** : 2026-11-16

## Contexte

§6.7 : l'IDE Android exige un process pour le build (ADR-038) ; l'UI
survit à la mort du démon, le build suivant le relance. P6 a livré le
protocole (cadres JSON sur flux, `Frames`), le client (`DaemonClient`,
factory `spawn` pour machine virtuelle fille) et l'exécuteur équivalent
en processus (`BuildRunner`). ADR-038 : les mécanismes au protocole
priment quand ils existent — le câblage P8 les branche sur Android.

## Décision

1. **Service de premier plan `:build` en process séparé**
   (`android:process=":build"`), notification persistante, comme le
   demande §6.7.
2. **Transport : socket TCP sur localhost** (`java.net`, aucun AIDL) :
   le process `:build` héberge un serveur qui sert les cadres du
   protocole P6 au-dessus de `BuildRunner` ; l'UI se connecte par la
   nouvelle factory `DaemonClient.over(InputStream, OutputStream)` et
   consomme les mêmes événements que le client de bureau. Aucun
   mécanisme Android spécifique ne remplace le protocole — il le
   transporte.
3. **La mort du démon est un événement de flux** : EOF sur la socket →
   `DaemonDiedException` côté UI → « Build interrompu : mémoire », l'UI
   survit, le build suivant relance le service (§6.7).
4. **Bascule in-process explicite, jamais silencieuse** (§6.7) : le
   mode séparé est le défaut ; l'in-process reste disponible pour les
   tests et très petits appareils, annoncé à l'utilisateur.

## Options

| Option | Verdict | Raison |
|---|---|---|
| AIDL/Binder dédié | écartée | double le protocole P6 pour le seul transport ; ADR-038 prime le protocole |
| stdio vers machine virtuelle fille (`spawn`) | écartée | pas de binaire `java` lanceur sur Android ; le service EST l'hôte |
| Socket localhost + `DaemonClient.over` (retenue) | retenue | réutilise le protocole et le runner à l'identique, testable sur JVM |

## Conséquences

- `build-daemon` gagne une factory additive (`over`) et un hôte de
  serveur socket réutilisable côté bureau ; aucune rupture pour P6.
- Les budgets mémoire du build et des services de langage vivent dans
  des process distincts (§6.8 règle 4 : jamais de blocage mutuel).
