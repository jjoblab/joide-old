package jo.joide.agent.outils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Le plan de l'agent (outil {@code todo_write}) : la carte épinglée
 * de l'ADR-É9 — l'état pur que l'UI du lot P observera, jamais
 * déduit du transcript.
 */
public final class PlanAgent {

    /** Un élément du plan. */
    public static final class Element {
        /** Le texte de l'élément. */
        public final String texte;
        /** Vrai si l'élément est fait. */
        public final boolean fait;

        /** @param texte le texte @param fait l'état */
        public Element(String texte, boolean fait) {
            this.texte = texte;
            this.fait = fait;
        }
    }

    private final List<Element> elements = new ArrayList<>();
    private final List<Consumer<List<Element>>> auditeurs =
            new ArrayList<>();

    /** Remplace le plan entier (l'outil écrit le plan, il ne le
     *  modifie pas — le modèle porte la liste complète). */
    public void remplacer(List<Element> nouveaux) {
        elements.clear();
        elements.addAll(nouveaux);
        publier();
    }

    /** Le plan courant (copie défensive). */
    public List<Element> elements() {
        return List.copyOf(elements);
    }

    /** L'auditeur des changements (l'UI épingle la carte). */
    public void poserAuditeur(Consumer<List<Element>> auditeur) {
        auditeurs.add(auditeur);
    }

    private void publier() {
        for (Consumer<List<Element>> auditeur : auditeurs) {
            auditeur.accept(elements());
        }
    }
}
