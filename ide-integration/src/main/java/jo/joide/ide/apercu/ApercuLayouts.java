package jo.joide.ide.apercu;

import android.content.Context;
import android.view.View;
import java.util.Objects;
import jo.layoutlib.drawables.DrawableResolverImpl;
import jo.layoutlib.inflater.RenderService;
import jo.layoutlib.resources.DimensionConverter;
import jo.layoutlib.resources.ResourceResolverImpl;
import jo.layoutlib.themes.ThemeResolverImpl;

/**
 * L'aperçu de layouts derrière une façade JoIDE (lot E, ADR-044) : le
 * service de rendu de la brique LayoutLib rend un ARBRE DE VUES VIVANT
 * (mesuré et layouté, pas une image) sur le fil principal — la façade
 * l'expose à l'application sous des types du CADRE Android
 * exclusivement ({@link View}), les résolveurs de ressources, drawables
 * et thèmes du projet étant nourris par la configuration.
 *
 * <p>Cycle : {@link #configurer(ConfigurationApercu)} pose les
 * résolveurs (une SEULE instance de convertisseur de dimensions posée
 * sur le service ET le résolveur — le motif canonique de la brique),
 * {@link #rendre(String)} demande un rendu débouncé,
 * {@link #rendreSansDelai(String)} un rendu posté sans délai ; le
 * rappel arrive sur le fil principal. {@link #close()} libère le
 * service (idempotent, à appeler depuis {@code onDestroy()} de
 * l'activité).</p>
 *
 * <p>Honnêteté du rendu (contrat de la brique) : un XML en cours de
 * frappe garde le dernier rendu valide ({@code onXmlInvalide}), une
 * classe inconnue se dégrade en MockView pointillé SANS échec, les
 * ressources manquantes retombent aux ressources natives — seules les
 * erreurs sémantiques remontent à {@code onErreur}.</p>
 */
public final class ApercuLayouts implements AutoCloseable {

    /** L'écouteur des rendus — toutes les méthodes sur le fil principal. */
    public interface EcouteurRendu {

        /**
         * Un rendu a réussi.
         *
         * @param racine  la vue racine MESURÉE et LAYOUTÉE (vivante)
         * @param dureeMs la durée du rendu en millisecondes
         * @param vues    le nombre de vues de l'arbre
         * @param largeur la largeur mesurée en pixels
         * @param hauteur la hauteur mesurée en pixels
         */
        void onSucces(View racine, long dureeMs, int vues, int largeur,
                int hauteur);

        /**
         * Une erreur sémantique a fait échouer le rendu.
         *
         * @param message le message lisible
         * @param cause   la cause, ou null
         */
        void onErreur(String message, Throwable cause);

        /**
         * Le XML est en cours de frappe (invalide) : le dernier rendu
         * valide reste affiché.
         *
         * @param message le message de la brique
         */
        default void onXmlInvalide(String message) {
        }
    }

    private final RenderService service;
    private final float densite;
    private final float policeEchelle;
    private EcouteurRendu ecouteur;

    /**
     * @param contexte le contexte Android (l'activité hôte — la densité
     *                 s'y lit)
     */
    public ApercuLayouts(Context contexte) {
        this.service = new RenderService(contexte);
        this.service.setDebounceMs(DEBOUNCE_MS);
        android.util.DisplayMetrics metriques =
                contexte.getResources().getDisplayMetrics();
        this.densite = metriques.density;
        this.policeEchelle =
                contexte.getResources().getConfiguration().fontScale;
    }

    /**
     * Configure les résolveurs du projet et les dimensions cibles.
     *
     * @param configuration la configuration (dossier res/, thèmes,
     *                      styles, attributs, dimensions)
     */
    public void configurer(ConfigurationApercu configuration) {
        Objects.requireNonNull(configuration);
        service.setTargetDimensions(configuration.largeurCible(),
                configuration.hauteurCible());
        DimensionConverter convertisseur = new DimensionConverter(densite,
                policeEchelle, densite * 160f);
        if (configuration.dossierRes() != null) {
            ResourceResolverImpl ressources =
                    new ResourceResolverImpl(configuration.dossierRes());
            ressources.setDimensionConverter(convertisseur);
            ressources.setNightMode(configuration.modeNuit());
            service.setResourceResolver(ressources);
            service.setDrawableResolver(
                    new DrawableResolverImpl(ressources, convertisseur));
            ThemeResolverImpl themes = new ThemeResolverImpl(ressources,
                    convertisseur);
            if (configuration.themesJour() != null) {
                themes.registerThemesFile(configuration.themesJour());
            }
            if (configuration.themesNuit() != null) {
                themes.registerThemesFile(configuration.themesNuit(),
                        true);
            }
            if (configuration.styles() != null) {
                themes.registerStylesFile(configuration.styles());
            }
            themes.setNightMode(configuration.modeNuit());
            service.setThemeResolver(themes);
            if (configuration.attrs() != null) {
                service.getAttributeRegistry()
                        .registerAttrsFile(configuration.attrs());
            }
        }
        service.setThemeDuProjet(configuration.nomTheme());
    }

    /**
     * Pose l'écouteur des rendus (rappels sur le fil principal).
     *
     * @param ecouteur l'écouteur, ou null pour le retirer
     */
    public void poserEcouteur(EcouteurRendu ecouteur) {
        this.ecouteur = ecouteur;
        service.setRenderCallback(ecouteur == null ? null
                : new RenderService.RenderCallback() {
                    @Override
                    public void onRenderSuccess(View racine, long dureeMs,
                            int vues, int largeur, int hauteur) {
                        ecouteur.onSucces(racine, dureeMs, vues, largeur,
                                hauteur);
                    }

                    @Override
                    public void onRenderError(String message,
                            Throwable cause) {
                        ecouteur.onErreur(message, cause);
                    }

                    @Override
                    public void onXmlInvalid(String message) {
                        ecouteur.onXmlInvalide(message);
                    }
                });
    }

    /** Demande un rendu débouncé (le XML en cours de frappe). */
    public void rendre(String xml) {
        service.requestRender(xml);
    }

    /** Demande un rendu immédiat (posté sans délai). */
    public void rendreSansDelai(String xml) {
        service.requestImmediateRender(xml);
    }

    /** Annule les rendus en attente. */
    public void annuler() {
        service.cancelPending();
    }

    /** Vrai dès le premier rendu réussi. */
    public boolean aUnRenduValide() {
        return service.hasValidRender();
    }

    /** Libère le service (idempotent ; les rendus suivants sont ignorés). */
    @Override
    public void close() {
        service.release();
    }

    /** Débounce par défaut de la brique (ms). */
    private static final long DEBOUNCE_MS = 400L;
}
