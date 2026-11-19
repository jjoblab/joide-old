package jo.joide.app.android;

import android.app.Application;

import jo.joide.app.android.crash.GardienCrash;

/**
 * Lot C1 — L'APPLICATION JoIDE : la coque de démarrage du PROCESSUS,
 * déclarée au manifeste ({@code android:name}). Une seule mission :
 * poser le GARDE-CRASH AVANT TOUTE activité — les remontées du
 * terrain (2026-11-19 : plantage du tiroir agent, de la saisie des
 * clés, feuille de build muette) n'avaient AUCUNE trace récupérable,
 * le garde en écrit désormais une pour CHAQUE type de plantage.
 *
 * <p>Rien d'autre ne vit ici (le cycle de vie des écrans reste dans
 * les activités) : l'application est posée dans TOUT processus du
 * produit ({@code :build} compris — le démon qui meurt en silence
 * pendant un build écrit son rapport, l'écran de crash ne s'impose
 * pas sur l'atelier vivant).</p>
 */
public final class AppJoide extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        GardienCrash.installer(this);
    }
}
