// Convention des modules purs JoIDE : bibliothèque Java + convention de
// base (ADR-011). Les artefacts publiés viendront avec leur convention
// propre (publication = P8+) ; P0 n'ajoute rien d'autre.
plugins {
    `java-library`
    id("joide.java-conventions")
}
