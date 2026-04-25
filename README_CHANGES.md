README: Changes between commits 7f14568 -> 203d16b
=================================================

Goal
----
Explain what changed between commit `7f145689a9e8fc1972db0c53b2810be1cd38c9bd` and `203d16b185755ec1c98fa0dfad167a0fe23c561c`, why the changes fixed the runtime errors you reported (swap/mutex/file I/O/GUI mismatch), and how the fixes work — in plain, compact language.

High-level summary
------------------
- Fixed swapping: processes saved to disk are now reliably restored to memory before the scheduler runs them.
- Fixed mutex behavior: a process will not block on a mutex it already owns, and unblock logic no longer accidentally skips an instruction.
- Improved variable/file handling: instructions that reference variables or filenames are resolved correctly; malformed instructions are handled gracefully.
- Memory layout made explicit: PCB, variable area, and instructions have fixed offsets and helper methods to access them; PCB state now syncs to memory automatically.

What changed (file-by-file, in plain language)
----------------------------------------------
- `src/main/Main.java`
  - Schedulers now receive the `DiskManager` and the full `allProcesses` list. Before attempting to run a process the scheduler now ensures the process is resident in memory.
  - Uses `Memory` constants instead of magic numbers when allocating the process block and instruction start.
  - Initializes the variable area in memory when a process is created so variables always have reserved slots.
  - Stops the main loop cleanly and logs when all processes finish.

- `src/memory/Memory.java`
  - Introduced constants and helpers: `PCB_WORDS`, `VARIABLE_WORDS`, `getVariableStart`, `getVariableEnd`, `getInstructionStart`.
  - Added `syncPCB(PCB)` to write PCB fields back into memory whenever they change. This keeps the in-memory representation and the PCB object consistent.

- `src/memory/DiskManager.java`
  - Added `ensureResident(process, memory, allProcesses)`: when a process is on disk, this method allocates space (swapping out other processes if needed) and reloads the process into memory before it runs.
  - Finding a swap victim now avoids selecting the process that we are trying to restore and avoids swapping running/finished processes.
  - Loading code now adjusts the process's lower/upper bounds and program counter to match where the block landed in memory.

- `src/interpreter/Interpreter.java`
  - Added `hasOperandCount(...)` checks to detect malformed instructions early and avoid crashing or advancing to the wrong instruction.
  - Added `resolveValue(...)` so any token that can be a variable is resolved to its stored value; if not a variable, it is treated as a literal.
  - `writeFile`/`readFile`/`print`/`printFromTo` now use `resolveValue` so variable references and filename variables work consistently.
  - `writeFile` changed to open files without the append flag (clean file writes per run) and report when a write happens.

- `src/mutex/Mutex.java` and `src/mutex/MutexManager.java`
  - `Mutex.acquire` returns immediately true when the requesting process already owns the mutex. This prevents a process from blocking on itself after an unblock.
  - `MutexManager` no longer force-increments the program counter for unblocked processes. Previously, the unblock logic incremented the PC and caused the process to skip instructions or to re-block later; now the process resumes correctly at its original next instruction.

- `src/process/PCB.java`
  - Added a lightweight `syncCallback` so when PCB fields (like program counter or bounds) change, they can be written back into memory (`Memory.syncPCB`). This ensures the in-memory PCB area stays in sync with the in-process PCB object.

- `src/scheduler/*` (RR, HRRN, MLFQ)
  - Schedulers call `diskManager.ensureResident(...)` right before dispatching a process. If restore fails, they skip dispatch and log an error. This prevents running a process whose memory is still on disk.

Why these fixes address the errors you reported
---------------------------------------------
- Symptom: GUI would sometimes behave differently from headless runs; processes would run but their memory/state would be wrong.
  - Root cause: a process saved on disk could be selected by the scheduler while its memory block wasn't restored yet. The code used to assume the process's memory was available.
  - Fix: `ensureResident` + scheduler checks force the system to restore the process to RAM before executing it. If RAM is full, the disk manager chooses a victim (not the one we're restoring) and swaps it out. This guarantees the CPU executes a process whose memory contents and program counter match the PCB object.

- Symptom: Processes would sometimes block on a mutex they already held, or resume and skip instructions.
  - Root cause: (1) A process calling `semWait` could be told to block even if it already owned the lock. (2) When a waiting process was unblocked, the manager incremented the process's PC immediately, causing it to skip the next intended instruction.
  - Fix: mutex acquisition first checks if the process already owns the mutex and returns success (no block). Unblock logic no longer increments the PC; instead the PCB `setState`/`setProgramCounter` flow is centralized and synced to memory via the PCB sync callback. That preserves correct control flow and avoids accidental instruction skipping.

- Symptom: File reads/writes and variable prints showed wrong or literal values (e.g., printing variable names instead of their values), and malformed instructions could break the run.
  - Root cause: The interpreter sometimes treated variable names as literals or failed to handle invalid instruction formats; file handling used append mode inconsistenly.
  - Fix: `resolveValue` and `hasOperandCount` ensure tokens are interpreted as variable values when appropriate, and malformed instructions are logged and skipped safely. Write logic uses a fresh write rather than append so output files are deterministic per run (this avoids leftover content from previous runs). The interpreter also uses the variable area indices defined in `Memory` so reads/writes are consistent.

Notes and trade-offs
--------------------
- The disk manager swaps processes to make room; this keeps the simulator faithful to memory constraints but means long-running restores can delay a scheduling decision. The code logs failures so you can see when a restore couldn't complete.
- `writeFile` now overwrites the file (not append). If you prefer appending logs instead of replacing, switch the `FileWriter` back to append mode. Overwriting was chosen to make test outputs reproducible across runs.
- PCB changes use a small sync callback pattern (simple and explicit) rather than a full observer pattern; this keeps the implementation minimal while ensuring memory and PCB stay in sync.

Where to look in the code (quick links)
--------------------------------------
- Process restore logic: `src/memory/DiskManager.java` → `ensureResident`, `loadIntoProcess`
- Scheduler checks: `src/scheduler/RRScheduler.java`, `src/scheduler/HRRNScheduler.java`, `src/scheduler/MLFQScheduler.java` (look for calls to `ensureResident`)
- Mutex fixes: `src/mutex/Mutex.java`, `src/mutex/MutexManager.java`
- Interpreter robustness: `src/interpreter/Interpreter.java` (`resolveValue`, `hasOperandCount`, changed file read/write behavior)
- Memory layout & sync: `src/memory/Memory.java` (`PCB_WORDS`, `VARIABLE_WORDS`, `syncPCB`) and `src/process/PCB.java` (sync callback)

If you want next
----------------
- I can: (A) add a short reproducible test case that demonstrates the old bug and the new correct behavior; (B) switch file writes back to append mode if you prefer; (C) polish the logging format so terminal output is less garbled.

End of summary.
