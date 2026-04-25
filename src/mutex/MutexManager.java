
package mutex;

import process.Process;

import queues.Blocked;
import queues.Ready;
import scheduler.MLFQScheduler;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MutexManager {

    private Map<String, Mutex> mutexes;
    private Blocked blockedQueue;
    private Ready readyQueue; // ✅ now properly used
    private MLFQScheduler mlfqScheduler = null;
    private Consumer<String> logger;

    // ✅ FIX: add readyQueue parameter
    public MutexManager(Blocked blockedQueue, Ready readyQueue) {
        this.blockedQueue = blockedQueue;
        this.readyQueue = readyQueue;
        this.logger = message -> {};

        mutexes = new HashMap<>();

        mutexes.put("userInput", new Mutex("userInput"));
        mutexes.put("userOutput", new Mutex("userOutput"));
        mutexes.put("file", new Mutex("file"));
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger != null ? logger : message -> {};
        for (Mutex mutex : mutexes.values()) {
            mutex.setLogger(this.logger);
        }
    }

    public boolean semWait(String resourceName, Process process) {
        Mutex mutex = mutexes.get(resourceName);

        if (mutex == null) {
            emit("[MUTEX ERROR] Unknown resource: " + resourceName);
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
            emit("[MUTEX ERROR] Unknown resource: " + resourceName);
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
        emit("\n--- MUTEX STATES ---");
        for (Mutex mutex : mutexes.values()) {
            mutex.printState();
        }
        emit("--------------------");
    }

    private void emit(String message) {
        System.out.println(message);
        logger.accept(message);
    }
}

