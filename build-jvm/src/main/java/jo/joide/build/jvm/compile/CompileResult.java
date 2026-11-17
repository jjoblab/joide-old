package jo.joide.build.jvm.compile;

import java.util.List;

import jo.joide.platform.Problem;

/**
 * Résultat d'une compilation : réussite, diagnostics structurés et
 * classes effectivement écrites (chemins relatifs au répertoire de
 * sortie, séparateurs « / », suffixe « .class »).
 */
public final class CompileResult {

    /** Vrai si aucune erreur de compilation. */
    public final boolean success;
    /** Diagnostics structurés (erreurs et avertissements). */
    public final List<Problem> diagnostics;
    /** Classes écrites par ce lot, chemins relatifs triés. */
    public final List<String> writtenClasses;

    /**
     * @param success        réussite du lot
     * @param diagnostics    diagnostics structurés
     * @param writtenClasses classes écrites
     */
    public CompileResult(boolean success, List<Problem> diagnostics,
                         List<String> writtenClasses) {
        this.success = success;
        this.diagnostics = List.copyOf(diagnostics);
        this.writtenClasses = List.copyOf(writtenClasses);
    }

    /** @return vrai si des erreurs bloquantes sont présentes */
    public boolean hasErrors() {
        return !success;
    }
}
