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
    public char peekChar() {
        return index < chars.length() ? chars.charAt(index) : (char)0;
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
