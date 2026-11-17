# ADR-025 — ABI des membres inline : condensé du fichier de classe et recompilation complète du consommateur

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

§6.4 : « fonctions inline Kotlin (le corps fait partie de l'ABI) ». Le test
différentiel P3 (200 séquences à graine fixe) a échoué sur l'édition du corps
d'une fonction `inline` : le module aval conservait l'ancien corps copié (la
recette de concaténation dans le fichier de classe de l'appelant), alors que
les manifestes ABI de part et d'autre étaient égaux — l'édition était invisible.

Mesure fondatrice (consignée dans `InlineBodyAbiTest`, exécutable) : compiler
deux fois une façade de fichier dont seule l'expression du corps change
(`"<" + value + ">"` contre `"[" + value + "]"`). Résultat sur les métadonnées
2.1.0 (kotlin-metadata-jvm 2.3.0, lecture structurelle ET condensé brut) :
`d1` de 51 caractères identique, condensé brut identique — **les métadonnées
Kotlin ne portent pas les corps**. La conception initiale (« corps des membres
inline par hachage des métadonnées ») reposait donc sur une hypothèse fausse.

Deuxième constat, structurel : l'appel d'une fonction inline ne laisse **aucune
référence** dans le pool de l'appelant (le corps est copié) — la propagation
par index de références (`ImpactAnalysis`) ne peut pas retrouver les appelants
d'un changement de corps.

## Décision

1. **L'ABI d'une classe qui déclare au moins un membre inline inclut le
   condensé 128 bits du fichier de classe entier** (`ClassModel.fileDigest`,
   calculé à la lecture) : toute variation du fichier — corps inline, mais
   aussi constantes du pool — devient un changement d'ABI. Sur-invalidation
   bornée aux seules classes à membres inline, jamais de sous-invalidation.
2. **La valeur de manifeste porte un drapeau inline** (`hachage + " I"`, lu
   par `ClassAbi.isInlineFlagged`) : un changement d'ABI d'une classe à
   membres inline impose la **recompilation complète de la tâche consommatrice**
   — en amont (diff de manifestes de classpath, `ClasspathDiff`) comme en aval
   (lots produits par le module lui-même, `BatchOutcome`). Un appelant inline
   ne porte pas de référence exploitable : seule la recompilation complète est
   sûre. L'escalade par paquet essayée d'abord était insuffisante (un appelant
   qui ne référence AUCUNE classe du paquet échappait) : abandonnée.
3. Un diff de classpath **non vérifiable** (manifeste illisible, jamais
   consommé par cet état, manifeste consommé introuvable) vaut repli complet —
   jamais « supposer inchangé ».
4. Genres de métadonnées lus sans faits exploitables (façade multi-fichiers,
   classe synthétique) : le condensé brut rejoint l'ABI **sans** drapeau
   inline (leurs appelants portent des références réelles). La liste des
   sous-classes scellées rejoint les faits de classe (exhaustivité des `when`
   aval).
5. `recordBatch` : une classe lue non produite par le lot mais appartenant à
   un autre source du module voit son ABI rafraîchie **sans** entrer dans le
   pseudo-source `__orphan__` — sinon ses références (sa propre classe,
   présente dans son pool) tiraient `__orphan__` dans un lot sans fichier
   sous-jacent (défaut « source vanished », demasqué à la séquence 99).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Hachage des métadonnées seules | écartée | mesuré : les métadonnées 2.1.0 ne portent pas les corps — aveugle par construction |
| Corps des méthodes inline par désassemblage ciblé | écartée | les constantes du corps vivent dans le pool, pas dans le code : un mini-désassembleur (longueurs variables, switch) est un risque de sous-invalidation pour un gain nul |
| Escalade par paquet | écartée | mesuré à la séquence 99 : l'appelant purement inline n'y référence rien — insuffisante |
| Condensé du fichier + repli complet du consommateur | retenue | correcte par construction, coût borné aux classes à membres inline (rares et rarement éditées) |

## Conséquences

- Toute édition d'un fichier générant une classe à membres inline recompile
  entièrement les modules consommateurs de cette classe : c'est le prix de la
  correction, assumé (§4.2 : sur-invalider).
- L'invariant d'or est couvert par le test différentiel (séquence kind 4,
  corps inline) et par `InlineBodyAbiTest` (échoue avant le correctif).
- `docs/backlog.md` : cas `sealed` et `@JvmInline` à couvrir par des
  éditions dédiées du test différentiel (la liste scellée est déjà dans l'ABI,
  voir décision 4).
