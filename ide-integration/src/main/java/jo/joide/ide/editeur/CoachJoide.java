package jo.joide.ide.editeur;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jo.celeris.android.host.popups.CoachPopups;
import jo.celeris.android.widgets.BadgeGenre;
import jo.celeris.android.widgets.CompletionDocModel;
import jo.celeris.android.widgets.CompletionListView;
import jo.celeris.android.widgets.SignatureAideModel;
import jo.celeris.core.text.Document;

/**
 * Le coach de complétion de repli de JoIDE : les mots réservés des
 * langages servis (Java, Kotlin, XML) et les badges des mots du
 * document — la complétion SERVIE passe d'abord par la session de
 * langage (moteurs du lot C), le coach ne parle que quand le serveur
 * se tait ({@code PopupsEditeur} : « le serveur passe premier »).
 */
final class CoachJoide implements CoachPopups {

    private static final Set<String> RESERVES = new HashSet<>();

    static {
        RESERVES.addAll(Langages.MOTS_JAVA);
        RESERVES.addAll(Langages.MOTS_KOTLIN);
        RESERVES.addAll(Langages.MOTS_XML);
    }

    @Override
    public List<String> motsReserves() {
        return List.copyOf(RESERVES);
    }

    @Override
    public List<CompletionListView.Candidat> candidats(
            List<String> bruts) {
        List<CompletionListView.Candidat> out = new ArrayList<>();
        for (String brut : bruts) {
            out.add(new CompletionListView.Candidat(brut, null,
                    RESERVES.contains(brut)
                            ? BadgeGenre.Genre.MOT_CLE
                            : BadgeGenre.Genre.VARIABLE));
        }
        return out;
    }

    @Override
    public CompletionDocModel docDe(String symbole) {
        return null;
    }

    @Override
    public SignatureAideModel signatureDe(Document document,
            int caret) {
        return null;
    }
}
