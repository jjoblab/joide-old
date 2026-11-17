package jo.joide.store;

/**
 * Unité de travail exécutée à l'intérieur d'une transaction du store.
 *
 * @param <T> type du résultat du travail
 */
@FunctionalInterface
public interface StoreWork<T> {

    /**
     * @param store le store courant, en cours de transaction
     * @return le résultat du travail
     */
    T apply(BuildStore store);
}
