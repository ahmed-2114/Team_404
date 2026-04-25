
package mutex;

import process.Process;

import queues.Blocked;
import queues.Ready;
import scheduler.MLFQScheduler;
import java.util.HashMap;
import java.util.Map;

public class MutexManager {

    private Map<String, Mutex> mutexes;
    private Blocked blockedQueue;
    private Ready readyQueue; // ✅ now properly used
    private MLFQScheduler mlfqScheduler = null;

    // ✅ FIX: add readyQueue parameter
    public MutexManager(Blocked blockedQueue, Ready readyQueue) {
        this.blockedQueue = blockedQueue;
        this.readyQueue = readyQueue;

        mutexes = new HashMap<>();

        mutexes.put("userInput", new Mutex("userInput"));
        mutexes.put("userOutput", new Mutex("userOutput"));
        mutexes.put("file", new Mutex("file"));
    }

    public boolean semWait(String resourceName, Process process) {
        Mutex mutex = mutexes.get(resourceName);

        if (mutex == null) {
            System.out.println("[MUTEX ERROR] Unknown resource: " + resourceName);
            return true;
        }

        boolean acquired = mutex.acquire(process);

        if (!acquired) {
            blockedQueue.add(process);
        }

        return acquired;
    }

    public void semSignal(String resourceName, Process process) {
        Mutex mutex = mutexes.get(resourceName);

        if (mutex == null) {
            System.out.println("[MUTEX ERROR] Unknown resource: " + resourceName);
            return;
        }

        Process unblocked = mutex.release(process);

        

        if (unblocked != null) {
            blockedQueue.remove(unblocked);

            if (mlfqScheduler != null) {
                mlfqScheduler.addUnblockedProcess(unblocked);
            } else if (!readyQueue.getQueue().contains(unblocked)) {
                readyQueue.add(unblocked);
            }
        }
    }
    public void setMLFQScheduler(MLFQScheduler mlfq) {
        this.mlfqScheduler = mlfq;
    }

    public Mutex getMutex(String resourceName) {
        return mutexes.get(resourceName);
    }

    public void printAllMutexStates() {
        System.out.println("\n--- MUTEX STATES ---");
        for (Mutex mutex : mutexes.values()) {
            mutex.printState();
        }
        System.out.println("--------------------");
    }
}

