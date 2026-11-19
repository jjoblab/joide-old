package jo.joide.agent.outils;

import java.util.ArrayList;
import java.util.List;

import jo.joide.agent.DescOutil;
import jo.joide.agent.OutilAgent;
import jo.joide.agent.SurfaceOutils;
import jo.joide.agent.TransportHttp;
import jo.joide.agent.espace.EspaceTravailAgent;

/**
 * La surface d'outils v1 (lot O, ADR-059) : le sous-ensemble ASSUMÉ
 * des 42 outils du terrain — lecture 6, écriture 6, sémantique 4,
 * build/tests 3, plan 1, mémoire 2, web 2, questions 1 : vingt-cinq
 * outils, chacun portant son marqueur {@code mutating} et ses bornes
 * de résultat CÔTÉ OUTIL. La surface entière part à chaque requête
 * (ADR-057 : le chargement différé perdrait), sous le budget de
 * 16 000 caractères — le garde-fou l'exige.
 */
public final class OutilsV1 {

    private OutilsV1() {
    }

    /**
     * Assemble la surface v1.
     *
     * @param espace    l'espace de travail (enveloppé de checkpoints
     *                  par l'appelant s'il veut l'annulation)
     * @param transport le transport web (D-5, partagé)
     * @param plan      le plan épinglé
     * @return les 25 outils, dans un ordre stable
     */
    public static List<OutilAgent> tous(EspaceTravailAgent espace,
                                        TransportHttp transport,
                                        PlanAgent plan) {
        List<OutilAgent> outils = new ArrayList<>();
        outils.add(OutilsLecture.lireFichier(espace));
        outils.add(OutilsLecture.listerDossier(espace));
        outils.add(OutilsLecture.chercherTexte(espace));
        outils.add(OutilsLecture.trouverSymbole(espace));
        outils.add(OutilsLecture.diagnostics(espace));
        outils.add(OutilsLecture.apercuProjet(espace));
        outils.add(OutilsEcriture.creerFichier(espace));
        outils.add(OutilsEcriture.ecrireFichier(espace));
        outils.add(OutilsEcriture.editerFichier(espace));
        outils.add(OutilsEcriture.renommerChemin(espace));
        outils.add(OutilsEcriture.supprimerChemin(espace));
        outils.add(OutilsEcriture.ajouterDependance(espace));
        outils.add(OutilsSemantiques.allerDefinition(espace));
        outils.add(OutilsSemantiques.chercherReferences(espace));
        outils.add(OutilsSemantiques.renommerSymbole(espace));
        outils.add(OutilsSemantiques.formaterFichier(espace));
        outils.add(OutilsBuild.executerProgramme(espace));
        outils.add(OutilsBuild.executerTache(espace));
        outils.add(OutilsBuild.executerTests(espace));
        outils.add(OutilsDivers.ecrirePlan(plan));
        outils.add(OutilsDivers.lireMemoire(espace));
        outils.add(OutilsDivers.ecrireMemoire(espace));
        outils.addAll(new OutilsWeb(transport).tous());
        outils.add(OutilsDivers.demanderUtilisateur(espace));
        return List.copyOf(outils);
    }

    /**
     * @return les descriptions de la surface v1 (pour le garde-fou
     *         de budget et le montage de l'exécuteur)
     */
    public static List<DescOutil> descriptions(EspaceTravailAgent espace,
                                               TransportHttp transport,
                                               PlanAgent plan) {
        List<DescOutil> descriptions = new ArrayList<>();
        for (OutilAgent outil : tous(espace, transport, plan)) {
            descriptions.add(outil.description());
        }
        return descriptions;
    }

    /**
     * Le garde-fou de budget : la surface v1 doit tenir sous les
     * 16 000 caractères (ADR-É9/057) — ce getter sert au test qui
     * échoue à la place de la facture.
     *
     * @return la mesure de la surface complète
     */
    public static int mesureSurface(EspaceTravailAgent espace,
                                    TransportHttp transport,
                                    PlanAgent plan) {
        return SurfaceOutils.mesure(
                descriptions(espace, transport, plan));
    }
}
