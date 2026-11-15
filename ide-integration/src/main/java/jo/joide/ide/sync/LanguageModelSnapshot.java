package jo.joide.ide.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jo.codeide.lsp.model.ModuleLsp;
import jo.codeide.lsp.model.ProjetLsp;

/**
 * Vue JoIDE d'un instantané du modèle de langage : empreinte, modules,
 * avertissements — aucun type de brique dans les méthodes publiques
 * (ADR-044) ; la vue brute reste accessible au montage des moteurs,
 * à l'intérieur du module uniquement.
 */
public final class LanguageModelSnapshot {

    private final ProjetLsp raw;
    private final List<String> problems;

    private LanguageModelSnapshot(ProjetLsp raw, List<String> problems) {
        this.raw = raw;
        this.problems = List.copyOf(problems);
    }

    /**
     * @param raw     instantané de la brique
     * @param problems problèmes de résolution transportés
     * @return la vue JoIDE
     */
    static LanguageModelSnapshot of(ProjetLsp raw, List<String> problems) {
        return new LanguageModelSnapshot(raw, problems);
    }

    /** @return l'empreinte de l'instantané (SHA-256, contrat brique) */
    public String fingerprint() {
        return raw.getEmpreinte();
    }

    /** @return le répertoire racine du projet */
    public String rootDirectory() {
        return raw.getCheminRacine();
    }

    /** @return les résumés des modules, triés par nom */
    public List<ModuleSummary> modules() {
        List<ModuleSummary> out = new ArrayList<>();
        for (ModuleLsp module : raw.getModules()) {
            out.add(new ModuleSummary(module.getNom(),
                    module.getType().name(), module.getIgnore(),
                    module.getRaisonIgnore(),
                    module.getAvertissements(), module.getEmpreinte()));
        }
        return out;
    }

    /**
     * @param nom chemin du module
     * @return le résumé du module, ou vide
     */
    public Optional<ModuleSummary> module(String nom) {
        return modules().stream()
                .filter(m -> m.name().equals(nom)).findFirst();
    }

    /** @return les problèmes de résolution transportés */
    public List<String> problems() {
        return problems;
    }

    /**
     * Vue brute pour le montage des moteurs (lot C) — le type de la
     * brique ne quitte jamais le module : cette méthode n'est pas
     * compilable hors {@code ide-integration}.
     *
     * @return l'instantané « ProjetLsp » tel quel
     */
    public ProjetLsp raw() {
        return raw;
    }

    /** Résumé JoIDE d'un module de l'instantané. */
    public static final class ModuleSummary {

        private final String name;
        private final String type;
        private final boolean ignored;
        private final String ignoreReason;
        private final List<String> warnings;
        private final String fingerprint;

        ModuleSummary(String name, String type, boolean ignored,
                String ignoreReason, List<String> warnings,
                String fingerprint) {
            this.name = name;
            this.type = type;
            this.ignored = ignored;
            this.ignoreReason = ignoreReason;
            this.warnings = List.copyOf(warnings);
            this.fingerprint = fingerprint;
        }

        /** @return le chemin du module (« :a:b ») */
        public String name() {
            return name;
        }

        /** @return « ANDROID » ou « JVM » */
        public String type() {
            return type;
        }

        /** @return vrai si le module est écarté (avec sa raison) */
        public boolean ignored() {
            return ignored;
        }

        /** @return la raison de l'écart, ou {@code null} */
        public String ignoreReason() {
            return ignoreReason;
        }

        /** @return les avertissements du module */
        public List<String> warnings() {
            return warnings;
        }

        /** @return l'empreinte du module (SHA-256, contrat brique) */
        public String fingerprint() {
            return fingerprint;
        }
    }
}
