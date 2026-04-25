package mutex;

import process.Process;
import process.PCB;

import java.util.LinkedList;
import java.util.Queue;

public class Mutex {

    private String resourceName;
    private boolean isAvailable;        // true = no one is using it
    private Process owner;              // The process currently holding the mutex
    private Queue<Process> blockedQueue; // Processes waiting for this resource

    public Mutex(String resourceName) {
        this.resourceName = resourceName;
        this.isAvailable = true;
        this.owner = null;
        this.blockedQueue = new LinkedList<>();
    }

    // Try to acquire the mutex
    // Returns true if acquired, false if process got blocked
    public boolean acquire(Process process) {
        if (owner != null && owner.getPcb().getProcessID() == process.getPcb().getProcessID()) {
            System.out.println("[MUTEX] Process " + process.getPcb().getProcessID()
                    + " already owns resource: " + resourceName);
            return true;
        }

        if (isAvailable) {
            // Resource is free, give it to this process
            isAvailable = false;
            owner = process;
            System.out.println("[MUTEX] Process " + process.getPcb().getProcessID() + " acquired resource: " + resourceName);
            return true;
        } else {
            // Resource is taken, block this process
            process.getPcb().setState(PCB.ProcessState.BLOCKED);
            blockedQueue.add(process);
            System.out.println("[MUTEX] Process " + process.getPcb().getProcessID() + " BLOCKED on resource: " + resourceName + " (held by P" + owner.getPcb().getProcessID() + ")");
            return false;
        }
    }

    // Release the mutex
    // Returns the next process that was waiting, or null if no one was waiting
    public Process release(Process process) {
        if (owner != null && owner.getPcb().getProcessID() == process.getPcb().getProcessID()) {
            System.out.println("[MUTEX] Process " + process.getPcb().getProcessID()
                    + " released resource: " + resourceName);

            if (!blockedQueue.isEmpty()) {
                // Give resource to next waiting process
                Process next = blockedQueue.poll();
                owner = next;
                next.getPcb().setState(PCB.ProcessState.READY);
                System.out.println("[MUTEX] Resource " + resourceName
                        + " given to Process " + next.getPcb().getProcessID());
                return next;
            } else {
                // No one waiting, free the resource
                isAvailable = true;
                owner = null;
            }
        } else {
            System.out.println("[MUTEX ERROR] Process " + process.getPcb().getProcessID()
                    + " tried to release " + resourceName + " but doesn't own it!");
        }
        return null;
    }

    public String getResourceName() { 
    	return resourceName; 
    	}
    public boolean isAvailable() { 
    	return isAvailable; 
    	}
    public Process getOwner() { 
    	return owner; 
    	}
    public Queue<Process> getBlockedQueue() { 
    	return blockedQueue; 
    	}

    public void printState() {
        System.out.println("[MUTEX " + resourceName + "] Available: " + isAvailable
                + (owner != null ? ", Owner: P" + owner.getPcb().getProcessID() : "")
                + ", Blocked: " + blockedQueue.size() + " process(es)");
    }
}