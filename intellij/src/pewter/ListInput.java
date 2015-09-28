package pewter;

import java.util.List;
import java.util.Stack;

public class ListInput implements Input {
    private List<Object> list;
    private int index;
    private Stack<Integer> marks = new Stack<>();

    public ListInput(List<Object> list) {
        this.list = list;
    }

    @Override
    public boolean atEnd() {
        return index == list.size();
    }

    @Override
    public Object peek() {
        return !atEnd() ? list.get(index) : null;
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
