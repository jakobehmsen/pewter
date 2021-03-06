package pewter;

import java.util.Stack;

public class CharSequenceInput implements Input {
    private CharSequence chars;
    private int index;
    private Stack<Integer> marks = new Stack<>();

    public CharSequenceInput(CharSequence chars) {
        this.chars = chars;
    }

    @Override
    public boolean atEnd() {
        return index == chars.length();
    }

    @Override
    public Object peek() {
        return peekChar();
    }

    @Override
    public char peekChar() {
        return !atEnd() ? chars.charAt(index) : (char)0;
    }

    @Override
    public void consume() {
        index++;
    }

    @Override
    public void mark() {
        marks.push(index);
    }

    @Override
    public void reset() {
        index = marks.pop();
    }
}
