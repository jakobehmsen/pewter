package pewter;

import javax.swing.*;

/**
 * Created by jakob on 02-10-15.
 */
public interface Language {
    void attachTo(Object content, JPanel panel);

    void dettachFrom(Object content, JPanel panel);

    Object newContent();

    Object evaluate(Object content);

    Object evaluateForUsage(Object content);
}
