package pewter;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Input {
    boolean atEnd();
    Object peek();
    default char peekChar() {
        return (Character)peek();
    }
    void consume();
    void mark();
    void reset();
    default void copyTo(Output output) {
        output.append(peekChar());
    }
    default Stream<Object> toStream() {
        Iterable<Object> iterator = () -> new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return !atEnd();
            }

            @Override
            public Object next() {
                Object next = peekChar();
                consume();
                return next;
            }
        };

        return StreamSupport.stream(iterator.spliterator(), false);
    }
}
