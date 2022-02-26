package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.InvertedPageTable;

import java.util.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        console = new SynchConsole(Machine.console());

        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() {
                exceptionHandler();
            }
        });

        //initialize list of free pages and page lock
        allocateMemoryLock = new Lock();
        memoryLinkedList = new LinkedList<Integer>();
        processIDSem = new Semaphore(1);


        //add them to the list of free pages
        for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
            memoryLinkedList.add(i);
        }
    }

    /**
     * Test the console device.
     */
    public void selfTest() {


//        super.selfTest();

//        System.out.println("Testing the console device. Typed characters");
//        System.out.println("will be echoed until q is typed.");
//
//        char c;
//
//        do {
//            c = (char) console.readByte(true);
//            console.writeByte(c);
//            char d = '\n';
//            console.writeByte(d);
//        }
//        while (c != 'q');

        //System.out.println("Test Finished");
    }

    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;

        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);

    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see    nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();
        UserProcess process = UserProcess.newUserProcess();
        String shellProgram = Machine.getShellProgramName();
//        System.out.println(shellProgram);
        Lib.assertTrue(process.execute(shellProgram, new String[]{}));

        KThread.currentThread().finish();

    }

    /**
     * Allocate pages when a process needs memory. Ensure no overlapping in memory.
     */
    public List<Integer> malloc(int numPages) {

        //make sure number of pages needed is a valid amount.
        if (numPages <= 0 || numPages > memoryLinkedList.size()) {
            //pageLock.release();
            return null;
        }

        List<Integer> toRet = new ArrayList<Integer>();

        for (int i = 0; i < numPages; i++) {
//      for (int i=0;i<1024;i++){
            toRet.add(memoryLinkedList.remove());
        }

        return toRet;
    }

    //给进程获取物理页
    public static int getFreePage() {
        int pageNumber = -1;

        allocateMemoryLock.acquire();
        if (!memoryLinkedList.isEmpty()) {
            pageNumber = memoryLinkedList.removeFirst();
        }
        allocateMemoryLock.release();

        return pageNumber;
    }


    /**
     * Free pages when a process's resources are released.
     */
    public void free(List<Integer> allocatedPages) {
        //free allocated pages and add them to freePages
        for (Integer page : allocatedPages) {
            memoryLinkedList.add(page);
        }
    }

    //释放物理内存
    public static void addFreePage(int pageNumber) {
        allocateMemoryLock.acquire();
        memoryLinkedList.addFirst(pageNumber);
        allocateMemoryLock.release();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    /**
     * Globally accessible reference to the synchronized console.
     */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    //List of available pages
    //private LinkedList<Integer> freePages;

    //Lock for synchronization while allocating/freeing pages
    //private Lock pageLock;

    //空闲链表
    public static LinkedList<Integer> memoryLinkedList;
    public static Lock allocateMemoryLock;
    public static Semaphore processIDSem;
}
