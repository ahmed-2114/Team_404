package queues;

import process.Process;
import process.PCB;

import java.util.LinkedList;
import java.util.Queue;

public class Ready {

    private Queue<Process> queue;

    public Ready() {
        queue = new LinkedList<>();
    }

    public void add(Process process) {
        process.getPcb().setState(PCB.ProcessState.READY);
        queue.add(process);
    }

    public Process poll() {
        return queue.poll();
    }

    public Process peek() {
        return queue.peek();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public Queue<Process> getQueue() {
        return queue;
    }

    public void remove(Process process) {
        queue.remove(process);
    }

    public void print() {
        System.out.print("Ready Queue: [ ");
        for (Process p : queue) {
            System.out.print("P" + p.getPcb().getProcessID() + " ");
        }
        System.out.println("]");
    }
}