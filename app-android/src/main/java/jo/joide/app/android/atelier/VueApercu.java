package jo.joide.app.android.atelier;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import jo.joide.app.android.R;
import jo.joide.app.android.atelier.EtatApercu.Banniere;
import jo.joide.app.android.databinding.ItemBanniereApercuBinding;
import jo.joide.app.android.databinding.VueApercuBinding;
import jo.joide.app.android.ui.Graphite;
import jo.joide.app.android.ui.GraphiteVue;
import jo.joide.app.android.ui.PortePopups;

/**
 * Le volet d'aperçu de mise en page (lot L, ADR-É7 ; lot A5, ADR-085 —
 * la vue quitte le code) : barre de volet (« Aperçu — nom du fichier »,
 * rafraîchir sans délai, plein écran/côte à côte, fermer), contrôles
 * RÉELS du {@code ConfigurationApercu} (densité segmentée, mode nuit,
 * dimensions cibles), surface de rendu sur damier — le résultat
 * LayoutLib tel quel, jamais retouché —, stats MESURÉES de l'écouteur
 * et bannières de franchise. La vue ne décide rien :
 * {@link EtatApercu} arrive posé.
 *
 * <p>La hiérarchie vit dans {@code vue_apercu.xml} (un
 * {@code <merge>} : cette classe EST le conteneur vertical, un niveau
 * aplati — constat I3) ; le PERCHOIR du rendu est unique et gonflé une
 * fois ({@link #conteneurRendu()} le rend : le code d'hier en créait
 * UN PAR RENDU — l'accumulation disparaît) ; les bannières (liste très
 * courte et bornée, ≤ 6) se gonflent par binding à chaque maj, motif A4
 * des contentions bornées. Les couleurs des bannières se calculent au
 * bind (décision 8 : la gravité décide).</p>
 */
public final class VueApercu extends LinearLayout {

    /** L'auditeur des actions du volet. */
    public interface AuditeurApercu {
        /** Rafraîchir sans délai (l'action manuelle explicite). */
        void surRafraichir();

        /** Basculer plein écran / côte à côte. */
        void surPleinEcran();

        /** Fermer le volet. */
        void surFermer();

        /** La densité change (1, 2 ou 3). */
        void surDensite(int densite);

        /** Le mode nuit change. */
        void surNuit(boolean nuit);

        /** Les dimensions cibles changent (dp). */
        void surDimensions(int largeurDp, int hauteurDp);
    }

    /** La coque gonflée du XML (vue_apercu.xml, un <merge>). */
    private final VueApercuBinding binding;

    private final Graphite.Palette palette;
    private AuditeurApercu auditeur;

    /** Le porte-popups de l'atelier (le menu des dimensions s'y
     *  ANCRE — lot A7, registre partagé avec l'écran). */
    private PortePopups popups;

    /** Vrai pendant la pose de l'état (les écouteurs se taisent). */
    private boolean poseEnCours;

    public VueApercu(Context contexte) {
        this(contexte, null);
    }

    public VueApercu(Context contexte, AttributeSet attributs) {
        super(contexte, attributs);
        setOrientation(VERTICAL);
        palette = GraphiteVue.palette(contexte);
        setBackgroundColor(palette.surface);

        binding = VueApercuBinding.inflate(
                LayoutInflater.from(contexte), this);

        // le perchoir clippe le rendu aux coins arrondis du cadre :
        // l'attribut XML clipToOutline n'existe qu'à partir de l'API 31
        // (minSdk 28), l'appel en code (API 21) est la voie du lot L
        binding.perchoirApercu.setClipToOutline(true);

        // la barre de volet : rafraîchir, plein écran, fermer
        binding.rafraichirApercu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surRafraichir();
            }
        });
        binding.pleinEcranApercu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surPleinEcran();
            }
        });
        binding.fermerApercu.setOnClickListener(vue -> {
            if (auditeur != null) {
                auditeur.surFermer();
            }
        });

        // la densité segmentée (1×, 2×, 3×) : l'état pose la sélection,
        // le choix utilisateur prévient l'auditeur
        binding.segmentDensite.addOnButtonCheckedListener(
                (groupe, identifiant, coche) -> {
                    if (poseEnCours || !coche || auditeur == null) {
                        return;
                    }
                    if (identifiant == R.id.densite_1) {
                        auditeur.surDensite(1);
                    } else if (identifiant == R.id.densite_2) {
                        auditeur.surDensite(2);
                    } else if (identifiant == R.id.densite_3) {
                        auditeur.surDensite(3);
                    }
                });

        // le mode nuit (puce à bascule) et les dimensions (menu)
        binding.puceNuit.setOnCheckedChangeListener((bouton, coche) -> {
            if (!poseEnCours && auditeur != null) {
                auditeur.surNuit(coche);
            }
        });
        binding.puceDimensions.setOnClickListener(vue -> choisirDimensions());
    }

    /** L'auditeur des actions. */
    public void setAuditeur(AuditeurApercu ecouteur) {
        this.auditeur = ecouteur;
    }

    /** Pose le porte-popups de l'atelier (lot A7 : le menu des
     *  dimensions s'y ANCRE — la vue ne porte jamais la fenêtre). */
    public void poserPopups(PortePopups porte) {
        popups = porte;
    }

    /**
     * Traduit l'état : fichier lié, contrôles, stats mesurées,
     * bannières de franchise. Le rendu lui-même est posé par l'écran
     * ({@link #conteneurRendu()}) — jamais retouché ici.
     */
    public void maj(EtatApercu etat, String nomFichier) {
        Context contexte = getContext();
        binding.fichierApercu.setText(
                nomFichier == null ? "" : " · " + nomFichier);

        poseEnCours = true;
        binding.segmentDensite.check(densiteId(etat.densite()));
        binding.puceNuit.setChecked(etat.nuit());
        poseEnCours = false;

        binding.puceDimensions.setText(
                etat.largeurDp() + " × " + etat.hauteurDp());
        if (etat.stats() == null) {
            binding.statsApercu.setText(contexte.getString(
                    R.string.apercu_stats_attente));
        } else {
            binding.statsApercu.setText(contexte.getString(
                    R.string.apercu_stats,
                    etat.stats().vues(), etat.stats().dureeMs(),
                    etat.stats().largeur(), etat.stats().hauteur(),
                    etat.densite()));
        }
        majBannieres(contexte, etat.bannieres(),
                etat.nomTheme() == null);
    }

    /** Les bannières de franchise : le thème de repli se DIT (lot E). */
    private void majBannieres(Context contexte, List<Banniere> nouvelles,
                              boolean themeRepli) {
        binding.bannieresApercu.removeAllViews();
        LayoutInflater gonfleur = LayoutInflater.from(contexte);
        if (themeRepli) {
            // le thème n'est jamais deviné (lot E) : le repli se dit
            ajouterBanniere(gonfleur, palette.accent, contexte.getString(
                    R.string.apercu_theme_hote));
        }
        for (Banniere banniere : nouvelles) {
            int couleur = switch (banniere.gravite()) {
                case ERREUR -> palette.erreur;
                case ALERTE -> palette.alerte;
                case INFO -> palette.accent;
            };
            ajouterBanniere(gonfleur, couleur,
                    texteBanniere(contexte, banniere));
        }
    }

    /** Gonfle une bannière (item_banniere_apercu) et la teinte. */
    private void ajouterBanniere(LayoutInflater gonfleur, int couleur,
                                 String texte) {
        ItemBanniereApercuBinding banniere =
                ItemBanniereApercuBinding.inflate(gonfleur,
                        binding.bannieresApercu, false);
        banniere.banniereApercu.setText(texte);
        banniere.banniereApercu.setTextColor(couleur);
        // le fond mélangé (10 %) au liseré 30 % suit la gravité —
        // couleur CALCULÉE au bind (décision 8), jamais une littérale
        GradientDrawable fond = new GradientDrawable();
        fond.setColor(Graphite.melange(couleur, 0.10f, palette.surface));
        fond.setCornerRadius(getResources().getDimensionPixelSize(
                R.dimen.jo_rayon_banniere));
        fond.setStroke(getResources().getDimensionPixelSize(
                R.dimen.jo_ligne),
                Graphite.melange(couleur, 0.30f, palette.surface));
        banniere.banniereApercu.setBackground(fond);
        binding.bannieresApercu.addView(banniere.getRoot());
    }

    private String texteBanniere(Context contexte, Banniere banniere) {
        return switch (banniere.genre()) {
            case FRAPPE -> contexte.getString(
                    R.string.apercu_banniere_frappe,
                    banniere.detail() == null ? "?"
                            : banniere.detail());
            case RESSOURCES -> contexte.getString(
                    R.string.apercu_banniere_ressources,
                    banniere.detail());
            case MOCKVIEW -> contexte.getString(
                    R.string.apercu_banniere_mockview,
                    banniere.detail());
            case ERREUR -> contexte.getString(
                    R.string.apercu_banniere_erreur,
                    banniere.detail());
            case CONFIG -> contexte.getString(
                    R.string.apercu_banniere_config,
                    banniere.detail());
        };
    }

    /** L'identifiant du bouton de densité (1, 2 ou 3). */
    private static int densiteId(int densite) {
        return switch (densite) {
            case 1 -> R.id.densite_1;
            case 3 -> R.id.densite_3;
            default -> R.id.densite_2;
        };
    }

    /** Les dimensions cibles (lot L) : le MENU ANCRÉ à la puce des
     *  dimensions (lot A7 — le tap FERME le menu PUIS agit). */
    private void choisirDimensions() {
        Context contexte = getContext();
        List<PortePopups.EntreeMenu> entrees = new ArrayList<>();
        int[][] dimensions = {{360, 640}, {412, 915}, {600, 1024}};
        int[] libelles = {
                R.string.apercu_dims_telephone,
                R.string.apercu_dims_grand,
                R.string.apercu_dims_tablette,
        };
        for (int i = 0; i < dimensions.length; i++) {
            final int largeur = dimensions[i][0];
            final int hauteur = dimensions[i][1];
            entrees.add(new PortePopups.EntreeMenu(
                    contexte.getString(libelles[i]),
                    () -> {
                        if (auditeur != null) {
                            auditeur.surDimensions(largeur, hauteur);
                        }
                    }));
        }
        popups.menu(binding.puceDimensions,
                contexte.getString(R.string.apercu_dims_titre), 220,
                entrees);
    }

    /**
     * Le conteneur du rendu : l'écran y pose la vue LayoutLib telle
     * quelle — jamais retouchée, jamais embellie. UNIQUE et gonflé une
     * fois (le code d'hier en créait un par rendu).
     */
    public FrameLayout conteneurRendu() {
        return binding.perchoirApercu;
    }
}
