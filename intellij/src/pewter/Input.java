package pewter;

public interface Input {
    char peekChar();
    void consume();
    void mark();
    void reset();
}
