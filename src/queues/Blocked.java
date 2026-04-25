package queues;

import process.Process;
import process.PCB;

import java.util.LinkedList;
import java.util.List;

public class Blocked {

    private List<Process> queue;

    public Blocked() {
        queue = new LinkedList<>();
    }

    public void add(Process process) {
        process.getPcb().setState(PCB.ProcessState.BLOCKED);
        if (!queue.contains(process)) {
            queue.add(process);
        }
    }

    public void remove(Process process) {
        queue.remove(process);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public List<Process> getQueue() {
        return queue;
    }

    public void print() {
        System.out.print("Blocked Queue: [ ");
        for (Process p : queue) {
            System.out.print("P" + p.getPcb().getProcessID() + " ");
        }
        System.out.println("]");
    }
}