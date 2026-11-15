package jo.joide.app.android;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran principal : ouverture d'un projet (sélecteur système de
 * répertoire) et détection de sa nature. L'état vide est un état
 * fonctionnel complet : rien n'est ouvert, rien n'est affirmé.
 *
 * <p>L'UI reste mince : la détection vit dans {@link ProjectDetector}
 * (testable sur la JVM), la lecture des noms racine dans
 * {@link RootNames}.
 */
public final class MainActivity extends Activity {

    private static final int PICK_ROOT = 41;

    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setText(R.string.aucun_projet);
        status.setTextSize(18f);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status);

        Button open = new Button(this);
        open.setText(R.string.ouvrir_projet);
        open.setOnClickListener(v ->
                startActivityForResultRoot());
        root.addView(open);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(scroll);
        return outer;
    }

    private void startActivityForResultRoot() {
        android.content.Intent pick = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(pick, PICK_ROOT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    android.content.Intent data) {
        if (requestCode != PICK_ROOT || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri tree = data.getData();
        List<String> names = RootNames.list(this, tree);
        ProjectDetector.Detection d = ProjectDetector.detect(names);
        switch (d.kind()) {
            case NATIVE -> status.setText(R.string.projet_natif);
            case GRADLE -> status.setText(R.string.projet_gradle);
            case UNKNOWN -> status.setText(R.string.projet_inconnu);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
