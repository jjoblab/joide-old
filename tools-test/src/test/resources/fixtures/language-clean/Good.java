package fixture.clean;

/** Fixture conforme : le controle doit passer. */
public class Good {
    /** Runs the fixture without any violation. */
    public void run(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }
}
