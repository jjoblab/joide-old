package fixture.violation;

/** Fixture volontairement fausse : sert a tester le controle. */
public class Bad {
    /** Field with a non-ascii identifier (violation of D3). */
    private int largeurRéelle;

    public void run(int fichier) {
        largeurRéelle = fichier;
        throw new IllegalArgumentException("fichier introuvable");
    }
}
