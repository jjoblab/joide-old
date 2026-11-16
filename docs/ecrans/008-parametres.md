# ADR-É8 — Écran de paramètres

- **Statut** : Accepted (design — implémentation en lots futurs)
- **Date** : 2026-11-16
- **Maquette** : `design/parametres.html`

## Contexte

Aucun écran de réglages en P8 : la case « en processus » traîne dans
l'en-tête de console, le thème est imposé clair par le cadre. Un IDE
sérieux expose ses réglages réels — et seulement eux (ADR-É1 : ce
que l'appareil ne fait pas, l'UI ne le propose pas).

## Décision

Écran plein écran, atteint depuis l'accueil (⋮) et l'atelier (⋮).
Recherche par champ en tête (filtre les rangées), sections en
listes. Chaque réglage existe déjà comme comportement ou est un
backlog nommé — jamais une rangée morte qui ne fait rien.

### Sections

1. **Apparence** — thème (segment : Sombre / Système / Clair ; sombre
   par défaut, Graphite ADR-É2 ; clair livré quand sa palette existe,
   pas avant) ; accent (nuancier restreint, un seul jeton réellement
   appliqué) ; taille de police UI.
2. **Éditeur** — police de code (nuancier mono installées) ; taille
   (stepper 10–20) ; numéros de ligne (switch) ; retour à la ligne
   (switch) ; thème de syntaxe (Graphite, unique v2).
3. **Build** — moteur par défaut (segment : « Service :build » /
   « En processus » — l'ancienne case à cocher, promue réglage) ;
   fils par build (stepper) ; la légende porte la franchise du
   service : « la mort du service interrompt le build, jamais
   l'IDE ; le prochain build le relance » (lot F).
4. **Stockage** — mesures réelles par rangée : projets importés
   (Mo), cache CIDX (Mo) avec bouton « Vider » (confirmation :
   l'index se reconstruit à la prochaine sync, honnête),
   dépendances m2 (Mo) avec « Vider » (confirmation : les
   dépendances seront re-téléchargées). Jamais de mesure inventée :
   chaque valeur vient du disque.
5. **À propos** — version (nom + SHA court du build), licences et
   NOTICE (écran texte), journal de diagnostic (facile : le fichier
   existe déjà côté service), lien dépôt.

### Franchise

- Chaque action destructive confirme avec sa conséquence réelle en
  une phrase (« Vider le cache CIDX ? L'index se reconstruit à la
  prochaine synchronisation. »).
- Les sections vides ne s'affichent pas : un réglage absent du
  build courant (p. ex. clair non livré) ne laisse pas de rangée
  désactivée — la section compte ce qui existe.
- La recherche ne trouve que ce qui existe ; aucun résultat est un
  état affiché, pas un blanc.

## Conséquences

- Les préférences vivent dans les préférences privées de
  l'application, lues par des classes pures (l'UI reste mince) ;
  les réglages build sont consommés par le lanceur de build, les
  réglages éditeur par la vue celeris à l'ouverture suivante.
- Un réglage nouveau = une rangée + un consommateur ; jamais une
  rangée orpheline.
