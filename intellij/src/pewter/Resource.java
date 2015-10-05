package pewter;

import javax.swing.*;

/**
 * Created by jakob on 02-10-15.
 */
public interface Resource extends Language {
    void addListener(ResourceListener listener);

    void removeListener(ResourceListener listener);

    default Runnable bindListener(ResourceListener listener) {
        addListener(listener);
        return () -> removeListener(listener);
    }

    Language getLanguage();

    String getName();

    void setName(String name);

    String getPath();

    Object getContent();

    void setContent(Object content);

    default Object evaluate() {
        return getLanguage().evaluate(getContent());
    }

    default Object evaluateForUsage() {
        return getLanguage().evaluateForUsage(getContent());
    }

    default void attachTo(JPanel panel) {
        getLanguage().attachTo(getContent(), panel);
    }

    default void detachFrom(JPanel panel) {
        getLanguage().dettachFrom(getContent(), panel);
    }

    ResourceDriver getDriver();

    void setDriver(ResourceDriver driver);
}
