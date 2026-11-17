# ADR-030 — Cache de dépendances partagé, adressé par contenu

- **Statut** : Accepted
- **Date** : 2026-11-15

## Contexte

ADR-007 exige un cache disque **partagé entre projets** pour les
dépendances. Deux adressages étaient possibles : par coordonnée
(`g/a/v/fichier`, à la `~/.m2/repository`) ou par empreinte de
contenu (le style déjà retenu pour le cache de sorties de P2,
ADR-024).

## Décision

Adressage **par empreinte SHA-256** (`jo.joide.deps.cache.DependencyCache`) :

```
<racine>/files/xx/<sha256>   artefacts et métadonnées, immuables
<racine>/aars/xx/<sha256>/   AAR extraits (marqueur .joide-extracted)
<racine>/tmp/                zone d'attente des écritures
```

- un artefact téléchargé est haché **pendant** la vérification, puis
  entre au cache par déplacement atomique (`AtomicFiles`) : deux
  processus concurrents qui écrivent le même contenu tombent d'accord
  sur le même fichier, le perdant du déplacement abandonne sa copie ;
- la lecture **revérifie** : un fichier stocké sous une clé doit
  hacher vers cette clé ; une divergence (corruption, écriture
  interrompue) retire le fichier et le lit absent — jamais un
  contenu faux servi ;
- un fichier GMM portant son `sha256` incorporé dispense de
  téléchargement : la présence au cache se teste par empreinte, sans
  réseau ;
- l'extraction AAR vit sous la même clé que l'AAR ; le marqueur
  `.joide-extracted` contient l'empreinte (idempotence).

## Options

| Option | Verdict | Raison |
|---|---|---|
| Adressage par coordonnée (arborescence m2) | écartée | un même artefact depuis deux dépôts occupe deux places ; la vérification exige un état annexe ; l'immutabilité n'est pas structurelle |
| Adressage par contenu (retenue) | retenue | déduplication inter-projets et inter-dépôts, immuabilité par construction, la clé est la vérification ; cohérent avec ADR-024 (cache de sorties) |

## Conséquences

- Le coût de la relecture chaude (lockfile) est un hachage par
  artefact : accepté, la correction passe avant (même choix qu'en
  P3 pour les manifestes ABI).
- Les métadonnées (POM, GMM) ne sont pas recherchables par coordonnée
  au cache : sans lockfile, une résolution complète les refait lire —
  c'est le lockfile (ADR-033) qui supprime la relecture, pas le cache.
- L'éviction (quota) n'est pas livrée en P4 : backlog, avec le reste
  de la gestion de l'espace.
