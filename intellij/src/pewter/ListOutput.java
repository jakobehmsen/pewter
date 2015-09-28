package pewter;

import java.util.List;
import java.util.Stack;

public class ListOutput implements Output {
    private List<Object> list;
    private Stack<Integer> marks = new Stack<>();

    public ListOutput(List<Object> list) {
        this.list = list;
    }

    public List<Object> getList() {
        return list;
    }

    @Override
    public void append(Object obj) {
        list.add(obj);
    }

    @Override
    public void mark() {
        marks.push(list.size());
    }

    @Override
    public void reset() {
        list.subList(marks.pop(), list.size()).clear();
    }

    @Override
    public Input toInput() {
        return new ListInput(list);
    }
}
