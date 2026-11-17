package jo.joide.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jo.joide.platform.CancelToken;

/**
 * Le modèle scripté (ADR-É9, les évaluations) : « scripter le modèle
 * fixe la seule variable qu'un run hors ligne ne peut fournir — le
 * jugement du modèle — et laisse sous test ce que ce dépôt possède
 * réellement ». Le constructeur de requêtes RÉEL tourne, le
 * fournisseur répond ce que le scénario a posé ; on juge l'ÉTAT
 * FINAL, pas le transcript.
 */
final class FournisseurScripte implements FournisseurLlm {

    /** Les requêtes reçues, dans l'ordre (le garde-fou les compare). */
    final List<RequeteLlm> requetes = new ArrayList<>();

    private final List<MessageAgent> reponses;
    private final List<UsageLlm> usages;
    private final List<RuntimeException> exceptions;
    private int index;

    /** @param reponses les réponses scriptées, une par requête */
    FournisseurScripte(List<MessageAgent> reponses) {
        this.reponses = List.copyOf(reponses);
        this.usages = List.of();
        this.exceptions = List.of();
    }

    /** @param reponses les réponses @param usages les usages (un par requête) @param exceptions les exceptions à lever (null = réponse normale à cette position) */
    FournisseurScripte(List<MessageAgent> reponses,
                       List<UsageLlm> usages,
                       List<RuntimeException> exceptions) {
        this.reponses = List.copyOf(reponses);
        this.usages = List.copyOf(usages);
        // les null (position sans exception) sont légitimes ici
        this.exceptions = new ArrayList<>(exceptions);
    }

    @Override
    public String nom() {
        return "scripte";
    }

    @Override
    public MessageAgent envoyer(TransportHttp transport,
                                RequeteLlm requete, CancelToken jeton,
                                Consumer<EvenementFlux> ecouteur) {
        requetes.add(requete.sansNoteFinale());
        if (index < exceptions.size()
                && exceptions.get(index) != null) {
            index++;
            throw exceptions.get(index - 1);
        }
        MessageAgent reponse = index < reponses.size()
                ? reponses.get(index) : MessageAgent.assistant("");
        if (index < usages.size()) {
            if (ecouteur != null) {
                ecouteur.accept(EvenementFlux.usage(usages.get(index)));
            }
        }
        for (PartieContenu partie : reponse.parties) {
            if (ecouteur == null) {
                break;
            }
            if (partie.genre == PartieContenu.Genre.TEXTE) {
                ecouteur.accept(EvenementFlux.texte(partie.texte));
            }
        }
        index++;
        return reponse;
    }
}
