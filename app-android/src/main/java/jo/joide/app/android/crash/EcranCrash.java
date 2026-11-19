package jo.joide.app.android.crash;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.file.Path;
import java.util.List;

import jo.joide.app.android.MainActivity;
import jo.joide.app.android.R;

/**
 * Lot C1 — L'ÉCRAN DE CRASH : l'activité qui dit CE QUI A PLANTÉ, la
 * demande explicite du terrain du 2026-11-19 (« créé une activity
 * crash que détecte tout le type de crash »). Elle montre le rapport
 * complet du plantage le plus récent — type NOMMÉ (exception non
 * interceptée et sa classe exacte, ou gel de l'interface), message,
 * pile complète, pile du fil principal, version, appareil — et
 * l'historique des plantages précédents.
 *
 * <p><b>Pourquoi 100 % programmatique</b> : l'écran d'urgence NE
 * DOIT dépendre d'aucune des couches qui peuvent être LA CAUSE du
 * plantage — ni le thème Graphite ({@code ?attr/jo_*}), ni les
 * layouts applicatifs, ni ViewBinding. Le manifeste la pose sous
 * {@code Theme.Material.NoActionBar} du CADRE ; les couleurs vivent
 * en constantes de code ici (exemption motivée : la franchise §0.4
 * interdit les littéraux dans les LAYOUTS, un écran qui doit
 * survivre à un thème cassé ne peut pas lire ses couleurs dans le
 * thème), les textes passent par les ressources ordinaires (le
 * paquet de chaînes ne peut pas planter l'inflation).</p>
 *
 * <p><b>Relancer</b> rouvre l'accueil (tâche neuve, processus
 * vivant — l'état mort se vide) ; <b>Effacer</b> vide les rapports ;
 * <b>Quitter</b> ferme la tâche. Chaque geste vit sous garde : un
 * écran de crash qui planterait à son tour est la faute ultime —
 * le repli est une TextView nue.</p>
 */
public final class EcranCrash extends Activity {

    // Les couleurs de l'urgence : sombre lisible, rouge d'alerte,
    // texte inversé — constantes, jamais le thème (voir la classe).
    private static final int FOND = 0xFF111318;
    private static final int CARTE = 0xFF1B1E26;
    private static final int TEXTE = 0xFFE8E6E1;
    private static final int TEXTE_ESTOMPE = 0xFF8B8F9A;
    private static final int ALERTE = 0xFFE5484D;
    private static final int BORD = 0xFF33363F;

    /** Les rapports, du plus récent au plus ancien. */
    private List<Path> rapports = List.of();
    /** L'index du rapport affiché (0 = le plus récent). */
    private int index;
    private TextView champType;
    private TextView champMessage;
    private TextView champPile;
    private TextView champHistorique;
    private LinearLayout racine;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            monter();
        } catch (Throwable tout) {
            // le repli nu : l'écran de crash ne meurt JAMAIS d'un
            // échec de montage
            repliNu();
        }
    }

    /** Monte l'écran complet : titre, type, pile, actions. */
    private void monter() {
        getWindow().setBackgroundDrawable(new ColorDrawable(FOND));

        ScrollView defilement = new ScrollView(this);
        defilement.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        racine = new LinearLayout(this);
        racine.setOrientation(LinearLayout.VERTICAL);
        int marge = dp(16);
        racine.setPadding(marge, dp(24), marge, dp(16));
        defilement.addView(racine, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titre = new TextView(this);
        titre.setText(R.string.crash_titre);
        titre.setTextSize(22f);
        titre.setTypeface(Typeface.DEFAULT_BOLD);
        titre.setTextColor(ALERTE);
        racine.addView(titre);

        TextView sousTitre = new TextView(this);
        sousTitre.setText(R.string.crash_sous_titre);
        sousTitre.setTextSize(14f);
        sousTitre.setTextColor(TEXTE_ESTOMPE);
        margeHaut(sousTitre, 6);
        racine.addView(sousTitre);

        racine.addView(carte(getString(R.string.crash_type),
                champType = valeur("")));
        racine.addView(carte(getString(R.string.crash_message),
                champMessage = valeur("")));

        TextView tetePile = etiquette(getString(R.string.crash_pile));
        margeHaut(tetePile, 10);
        racine.addView(tetePile);
        champPile = valeur("",
                Typeface.MONOSPACE);
        champPile.setTextColor(TEXTE_ESTOMPE);
        champPile.setTextSize(11f);
        champPile.setTextIsSelectable(true);
        // le ScrollView racine porte le défilement du rapport ENTIER
        // (aucun plafond de lignes : la pile complète se lit, la
        // sélection aussi) ; l'écran ne devient pas un puits
        LinearLayout blocPile = new LinearLayout(this);
        blocPile.setOrientation(LinearLayout.VERTICAL);
        blocPile.setBackground(new ColorDrawable(CARTE));
        int pad = dp(12);
        blocPile.setPadding(pad, pad, pad, pad);
        blocPile.addView(champPile);
        margeHaut(blocPile, 4);
        racine.addView(blocPile);

        champHistorique = new TextView(this);
        champHistorique.setTextSize(13f);
        champHistorique.setTextColor(TEXTE_ESTOMPE);
        margeHaut(champHistorique, 10);
        racine.addView(champHistorique);

        LinearLayout boutons = new LinearLayout(this);
        boutons.setOrientation(LinearLayout.HORIZONTAL);
        boutons.setGravity(Gravity.CENTER_VERTICAL);
        margeHaut(boutons, 14);
        boutons.addView(bouton(getString(R.string.crash_relancer),
                this::relancer, ALERTE));
        boutons.addView(bouton(getString(R.string.crash_precedent),
                this::rapportPrecedent, BORD));
        boutons.addView(bouton(getString(R.string.crash_suivant),
                this::rapportSuivant, BORD));
        boutons.addView(bouton(getString(R.string.crash_effacer),
                this::effacer, BORD));
        boutons.addView(bouton(getString(R.string.crash_quitter),
                this::quitter, BORD));
        racine.addView(boutons);

        setContentView(defilement);
        recharger();
    }

    /** Le repli nu (un échec de montage ne peut pas tuer l'écran). */
    private void repliNu() {
        TextView nue = new TextView(this);
        nue.setText(R.string.crash_titre);
        nue.setTextColor(0xFFE5484D);
        nue.setTextSize(18f);
        getWindow().setBackgroundDrawable(new ColorDrawable(FOND));
        setContentView(nue);
    }

    /** Relit les rapports et affiche le plus récent. */
    private void recharger() {
        rapports = GardienCrash.rapports();
        index = 0;
        afficher();
    }

    /** Affiche le rapport courant (ou l'absence honnête) — la
     *  consultation marque les rapports VUS (l'accueil ne les
     *  remontrera pas). */
    private void afficher() {
        GardienCrash.marquerVus();
        if (rapports.isEmpty()) {
            champType.setText(getString(R.string.crash_aucun));
            champMessage.setText("");
            champPile.setText("");
            champHistorique.setText("");
            return;
        }
        if (index < 0 || index >= rapports.size()) {
            index = Math.max(0, Math.min(index, rapports.size() - 1));
        }
        String texte = GardienCrash.lire(rapports.get(index));
        StringBuilder identite = new StringBuilder();
        for (String clef : new String[]{"genre: ", "classe: ",
                "fil: ", "moment: "}) {
            String ligne = libelle(clef, texte);
            if (!ligne.isEmpty()) {
                if (identite.length() > 0) {
                    identite.append('\n');
                }
                identite.append(ligne);
            }
        }
        champType.setText(identite.toString());
        champMessage.setText(libelle("message: ", texte));
        champPile.setText(texte);
        champHistorique.setText(
                getString(R.string.crash_rapports_precedents,
                        rapports.size() - 1));
    }

    /** La valeur de la ligne « clef: valeur » d'un rapport (vide sinon). */
    private static String libelle(String clef, String rapport) {
        int debut = rapport.indexOf('\n');
        String reste = debut < 0 ? "" : rapport.substring(debut + 1);
        for (String ligne : reste.split("\n")) {
            if (ligne.startsWith(clef)) {
                return ligne.substring(clef.length()).trim();
            }
        }
        return "";
    }

    private void rapportPrecedent() {
        if (index < rapports.size() - 1) {
            index++;
            afficher();
        }
    }

    private void rapportSuivant() {
        if (index > 0) {
            index--;
            afficher();
        }
    }

    private void effacer() {
        GardienCrash.effacer();
        recharger();
    }

    private void relancer() {
        try {
            Intent intention = new Intent(this, MainActivity.class);
            intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intention);
            finish();
        } catch (RuntimeException e) {
            // la relance échouée se dit par le départ : l'utilisateur
            // repasse par le lanceur système
            quitter();
        }
    }

    private void quitter() {
        finishAndRemoveTask();
    }

    // ---- les petites fabriques (l'écran ne gonfle RIEN du cadre) --

    /** Une étiquette de section (13 sp, estompée, capitales). */
    private TextView etiquette(String texte) {
        TextView vue = new TextView(this);
        vue.setText(texte);
        vue.setTextSize(13f);
        vue.setTextColor(TEXTE_ESTOMPE);
        vue.setAllCaps(true);
        return vue;
    }

    /** Une valeur (15 sp, texte plein). */
    private TextView valeur(String texte) {
        return valeur(texte, null);
    }

    /** Une valeur (15 sp, texte plein, police au choix). */
    private TextView valeur(String texte, Typeface police) {
        TextView vue = new TextView(this);
        vue.setText(texte);
        vue.setTextSize(15f);
        vue.setTextColor(TEXTE);
        if (police != null) {
            vue.setTypeface(police);
        }
        return vue;
    }

    /** La carte d'un bloc identité : l'étiquette AU-DESSUS de la
     *  valeur, fond sombre, marge haute. */
    private View carte(String libelle, TextView valeur) {
        LinearLayout bloc = new LinearLayout(this);
        bloc.setOrientation(LinearLayout.VERTICAL);
        bloc.setBackground(new ColorDrawable(CARTE));
        int pad = dp(12);
        bloc.setPadding(pad, pad, pad, pad);
        bloc.addView(etiquette(libelle));
        bloc.addView(valeur);
        margeHaut(bloc, 10);
        return bloc;
    }

    /** Un bouton plat de l'urgence. */
    private Button bouton(String texte, Runnable action, int teinte) {
        Button bouton = new Button(this);
        bouton.setText(texte);
        bouton.setTextColor(TEXTE);
        bouton.setTextSize(13f);
        bouton.setAllCaps(false);
        bouton.setBackground(new ColorDrawable(teinte));
        bouton.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams placements = new LinearLayout
                .LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        placements.setMargins(dp(3), 0, dp(3), 0);
        bouton.setLayoutParams(placements);
        bouton.setOnClickListener(vue -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                // une action morte ne doit jamais tuer l'écran
                // d'urgence
            }
        });
        return bouton;
    }

    /** La marge haute d'une vue (dp). */
    private void margeHaut(View vue, int valeur) {
        LinearLayout.LayoutParams placements =
                (LinearLayout.LayoutParams) vue.getLayoutParams();
        if (placements == null) {
            placements = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        placements.topMargin = dp(valeur);
        vue.setLayoutParams(placements);
    }

    /** Conversion dp → pixels. */
    private int dp(float valeur) {
        return Math.round(valeur * getResources().getDisplayMetrics()
                .density);
    }
}
