package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.InvertedPageTable;

import java.io.EOFException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	/*
    if(openfile[0]==null&&openfile[1]==null) {
    	openfile[0]=UserKernel.console.openForReading();
    	openfile[1]=UserKernel.console.openForWriting();
    }
    */
        this.openfile = new OpenFile[max_openfile_num];
        this.openfile[0] = UserKernel.console.openForReading();
        this.openfile[1] = UserKernel.console.openForWriting();

        this.processID = processesCreated++;
        this.childrenCreated = new HashSet<Integer>();

    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {

        if (!load(name, args)) {
            return false;
        }


        UThread t = (UThread) new UThread(this).setName(name);
        pidThreadMap.put(this.processID, t);
        t.fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        //从虚拟内存地址读出到data中
        //偏移量与长度都要为正数，偏移量+长度<=总的数据长度
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        //getMemory会返回主程序的数组
        byte[] memory = Machine.processor().getMemory();      //获得物理数组
        //计算剩下的页表字节个数
        if (length > (pageSize * numPages - vaddr))
            length = pageSize * numPages - vaddr;
        //计算能够传输的数据的大小，如果data数组中存不下length，则减小length（传输字节数）
        if (data.length - offset < length)
            length = data.length - offset;
        //转换成功的字节数
        int transferredbyte = 0;
        do {
            //计算页号
            int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
            //页号大于 页表的长度 或者 为负 是异常情况
            if (pageNum < 0 || pageNum >= pageTable.length)
                return 0;
            //计算页偏移量
            int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
            //计算剩余页的容量
            int leftByte = pageSize - pageOffset;
            //计算下一次传送的数量:剩余页容量和需要转移的字节数中较小者
            int amount = Math.min(leftByte, length - transferredbyte);
            //计算物理内存的地址
            int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
            //将物理内存的东西传输到虚拟内存
            System.arraycopy(memory, realAddress, data, offset + transferredbyte, amount);
            //修改传输成功的字节数
            transferredbyte = transferredbyte + amount;
        } while (transferredbyte < length);
        return transferredbyte;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        //物理内存
        byte[] memory = Machine.processor().getMemory();
        //写内存的长度如果超过页剩余量
        if (length > (pageSize * numPages - vaddr))
            length = pageSize * numPages - vaddr;
        //如果数组中要写的长度比给定的小，则给length减为数组剩余的长度
        if (data.length - offset < length)
            length = data.length - offset;
        int transferredbyte = 0;
        do {
            //此函数返回给定地址的页号
            int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
            if (pageNum < 0 || pageNum >= pageTable.length)
                return 0;
            //此函数返回给定地址的页偏移量
            int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
            //页剩余的字节数
            int leftByte = pageSize - pageOffset;
            //设置本次转移的数量
            int amount = Math.min(leftByte, length - transferredbyte);
            int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
            //从虚拟内存写入到物理内存(判断是否只读)
            if (!pageTable[pageNum].readOnly) {
                System.arraycopy(data, offset + transferredbyte, memory, realAddress, amount);
            }
            //改变写成功的字节数
            transferredbyte = transferredbyte + amount;
        } while (transferredbyte < length);
        return transferredbyte;
        // for now, just assume that virtual addresses equal physical addresses
//      if (vaddr < 0 || vaddr >= memory.length)
//         return 0;
        //
//      int amount = Math.min(length, memory.length - vaddr);
//      System.arraycopy(data, offset, memory, vaddr, amount);
        //
//      return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }
        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        UserKernel.allocateMemoryLock.acquire();//获取分配的内存的锁

        if (numPages > Machine.processor().getNumPhysPages()) {
            //判断能否装载
            coff.close();
            //缺少物理内存（如果程序的页数大于处理器的空闲物理页数就会失败）
            Lib.debug(dbgProcess, "\t insufficient（缺少物理地址） physical memory");
            UserKernel.allocateMemoryLock.release();
            return false;
        }
        allocatedPages = ((UserKernel) Kernel.kernel).malloc(numPages);
        if (allocatedPages == null) {
            UserKernel.allocateMemoryLock.release();
            return false;
        }
        pageTable = new TranslationEntry[numPages];
        pagelength = numPages;

        for (int i = 0; i < numPages; i++) {
            pageTable[i] = new TranslationEntry(i, allocatedPages.get(i), true, false, false, false);
        }
        UserKernel.allocateMemoryLock.release();
        // load sections（段），一个段是由很多页组成
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);     //coff获得Section
            Lib.debug(dbgProcess,
                    "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                pageTable[vpn].readOnly = section.isReadOnly();//标记为只读
                // for now, just assume virtual addresses=physical addresses
                //装入物理页
                section.loadPage(i, pageTable[vpn].ppn);
            }
        }
        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        UserKernel.allocateMemoryLock.acquire();
        ((UserKernel) Kernel.kernel).free(allocatedPages);
        coff.close();
        UserKernel.allocateMemoryLock.release();
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    private int handleCreate(int fileAddress) {

        final String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

        // Is the fileName valid?
        if (fileName == null || fileName.length() == 0) {
            return -1;
        }

        // Do we already have a file descriptor for a file with the same name?
        for (int i = 0; i < this.openfile.length; ++i) {
            if (this.openfile[i] != null && this.openfile[i].getName().equals(fileName)) {
                return i;
            }
        }

        // Find an empty slot in the file descriptor table
        final int fileDescriptor = this.findEmpty();

        // Are we out of file descriptors?

        final OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);

        // Was the file successfully created?
        if (file == null) {
            return -1;
        }

        // Add this openFile to openFiles
        this.openfile[fileDescriptor] = file;

        // return the new fileDescriptor
        return fileDescriptor;

    }

    private int handleOpen(int fileAddress) {
        final int fileDescriptor = findEmpty();

        final String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);
        if (fileName == null || fileName.length() == 0) {
            return -1;
        }

        final OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
        if (file == null) {

            return -1;
        }

        // Add this openFile to openFiles
        this.openfile[fileDescriptor] = file;


        // return the new fileDescriptor
        return fileDescriptor;

    }

    private int handleRead(int fileDescriptor, int bufferAddress, int length) {
        // check count is a valid arg
        if (length < 0) {
            return -1;
        }
        // Make sure FD is valid
        if (fileDescriptor < 0 ||
                fileDescriptor >= this.openfile.length ||
                openfile[fileDescriptor] == null) {
            return -1;
        }

        final OpenFile file = this.openfile[fileDescriptor];

        final byte[] tmp = new byte[length];
        final int numBytesRead = file.read(tmp, 0, length);
        final int numBytesWritten = writeVirtualMemory(bufferAddress, tmp, 0, numBytesRead);

        if (numBytesRead != numBytesWritten) {
            return -1;
        }

        return numBytesRead;
    }

    private int handleWrite(int fileDescriptor, int bufferAddress, int length) {
        if (length < 0) {
            return -1;
        }
        // Make sure FD is valid
        if (fileDescriptor < 0 ||
                fileDescriptor >= this.openfile.length ||
                openfile[fileDescriptor] == null) {
            return -1;
        }

        final OpenFile file = this.openfile[fileDescriptor];

        final byte[] tmp = new byte[length];
        final int numBytesToWrite = readVirtualMemory(bufferAddress, tmp);

        if (numBytesToWrite != length) {
            return -1;
        }


        // TODO(amidvidy): need to handle the case that file is actually an instance of SynchConsole.file()...
        return file.write(tmp, 0, numBytesToWrite);
    }

    private int handleClose(int fileDescriptor) {
        //check if file descriptor exists and then that
        if (fileDescriptor < 0 || fileDescriptor > 15) {
            return -1;
        }

        //set file to file referred to by file descriptor
        OpenFile file = openfile[fileDescriptor];

        //check that the file is still open
        if (file == null) {
            return -1;
        }

        //close the file
        openfile[fileDescriptor] = null;
        file.close();

        return SUCCESS;
    }

    private int handleUnlink(int fileAddress) {

        //get the file from memory
        String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

        //check that the file has a legitimate name and length
        if (fileName == null || fileName.length() <= 0) {
            return -1;
        }

        // Invalidate any file descriptors for this file for the current process
        for (int i = 0; i < openfile.length; i++) {
            if ((openfile[i] != null) && (openfile[i].getName().equals(fileName))) {
                openfile[i] = null;
                // If we change the behavior
                break;
            }
        }

        if (!ThreadedKernel.fileSystem.remove(fileName)) {
            return -1;
        }
        return SUCCESS;

    }

    private void handleExit(int status) {

        for (OpenFile file : openfile) {
            if (file != null)
                file.close();
        }

        this.unloadSections();

        if (pidThreadMap.size() == 1) {
            Kernel.kernel.terminate();
        }

        UserProcess dadProcess = null;
        if (this.fatherPID != -1) {
            dadProcess = pidThreadMap.get(this.fatherPID).process;
            dadProcess.childrenCreated.remove(this.processID);
        }

        for (Iterator i = this.childrenCreated.iterator(); i.hasNext(); ) {
            UserProcess childProcess = pidThreadMap.get((int) i.next()).process;
            childProcess.fatherPID = this.fatherPID;
            if (dadProcess != null)
                dadProcess.childrenCreated.add(childProcess.processID);
        }

        pidThreadMap.remove(this.processID);
        processStatus = status;
        exitSuccess = true;

        KThread.currentThread().finish();
    }

    /**
     * Execute the program stored in the specified file, with the specified
     * arguments, in a new child process. The child process has a new unique
     * process ID, and starts with stdin opened as file descriptor 0, and stdout
     * opened as file descriptor 1.
     *
     * @param pFile is a ptr to a null-terminated string that specifies the name
     *              of the file containing the executable. Note that this string must include
     *              the ".coff" extension.
     * @param argc  specifies the number of arguments to pass to the child
     *              process. This number must be non-negative.
     * @param argv  is an array of pointers to null-terminated strings that
     *              represent the arguments to pass to the child process. argv[0] points to
     *              the first argument, and argv[argc-1] points to the last argument.
     * @return the child process's process ID, which can be passed to join(). On
     * error, returns -1.
     */
    private int handleExec(int pFile, int argc, int pArgv) {

        if (inVaddressSpace(pFile) && argc >= 0) {
            String fileName = readVirtualMemoryString(pFile, MAX_FILENAME_LENGTH);
            byte[] argvBytes = new byte[argc * sizeOfInt];
            // read bytes of the argv array
            if (readVirtualMemory(pArgv, argvBytes, 0, argvBytes.length) == argvBytes.length) {
                // concatenate bytes into an array of addresses
                int[] argvAddrs = new int[argc];
                for (int i = 0; i < (argc * sizeOfInt); i += sizeOfInt) {
                    argvAddrs[i / sizeOfInt] = Lib.bytesToInt(argvBytes, i);
                }
                // read the strings from virtual memory
                String[] argvStrings = new String[argc];
                int remainingBytes = Processor.pageSize;
                for (int i = 0; i < argc; ++i) {
                    argvStrings[i] = readVirtualMemoryString(argvAddrs[i], Math.min(remainingBytes, 256));
                    if (argvStrings[i] == null || argvStrings[i].length() > remainingBytes) {
                        return ERROR; // arguments do not fit on one page
                    }
                    remainingBytes -= argvStrings[i].length();
                }

                UserProcess childProcess = UserProcess.newUserProcess();

                if (childProcess.execute(fileName, argvStrings)) { //tries to load and run program
                    childrenCreated.add(childProcess.processID);
                    childProcess.fatherPID = this.processID;
                    return childProcess.processID;
                }
            }
        }
        return ERROR;
    }

    /**
     * Suspends execution of the current process until the child process
     * specified by the processID argument has exited. handleJoin() returns
     * immediately if the child has already exited by the time of the call. When
     * the current process resumes, it disowns the child process, so that
     * handleJoin() cannot be used on that process again.
     *
     * @param processID the process ID of the child process, returned by exec().
     * @param status    points to an integer where the exit status of the child
     *                  process will be stored. This is the value the child passed to exit(). If
     *                  the child exited because of an unhandled exception, the value stored is
     *                  not defined.
     * @return returns 1, if the child exited normally, 0 if the child exited as
     * a result of an unhandled exception and -1 if processID does not refer to
     * a child process of the current process.
     */
    private int handleJoin(int processID, int pStatus) {

        if (!childrenCreated.contains(processID)) {
            return ERROR; // pID not a child of calling process
        }
        // calls join on thread running child process
        UThread uThread = pidThreadMap.get(processID);
        uThread.join();

        //set the status of the process retrieved
        int tmpStatus = uThread.process.processStatus;


        // removes this child's pID from set of children pIDs which will
        // prevent a future call to join on same child process
        //hildrenCreated
        //childrenCreated.remove(processID);

        byte[] statusBytes = new byte[sizeOfInt];
        Lib.bytesFromInt(statusBytes, 0, tmpStatus);

        int statusBytesWritten = writeVirtualMemory(pStatus, statusBytes);

        if (uThread.process.exitSuccess && statusBytesWritten == sizeOfInt) {
            return 1; // child exited normally
        }
        return 0; // child exited as a result of an unhandled exception
    }

    private int handleSleep(int time) {

        ThreadedKernel.alarm.waitUntil(time);
        return 0;
    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5;

    protected static final int syscallRead = 6;

    protected static final int syscallWrite = 7;

    protected static final int syscallClose = 8;

    private static final int syscallUnlink = 9;

    private static final int syscallSleep = 13;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:

                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:

                return handleClose(a0);
            case syscallUnlink:

                return handleUnlink(a0);
            case syscallExit:
                handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallSleep:
                return handleSleep(a0);


            default:
                System.out.println(syscall);
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();
        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);

                processor.advancePC();
                break;


            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                System.out.println("cause "+cause);
                System.out.println(Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    protected int initialPC, initialSP;
    protected int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    private static int max_openfile_num = 16;
    protected final OpenFile[] openfile;

    //作用：查询空位，若没有，返回-1
    protected int findEmpty() {
        int empty_index = 0;
        while (openfile[empty_index] != null) {
            empty_index++;
        }
        if (empty_index == max_openfile_num)
            return -1;
        return empty_index;
    }

    //作用：若文件已经打开，返回文件描述符的index，如果文件未打开，返回
    private int checkIsOpen(String fileName) {
        for (int i = 0; i < max_openfile_num; i++) {
            if (openfile[i] != null && openfile[i].getName().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean checkFileExist(String fileName) {
        OpenFile tempOpenFile = ThreadedKernel.fileSystem.open(fileName, false);
        if (tempOpenFile != null) {
            return true;
        }
        return false;
    }

    private boolean inPhysAddressSpace(int addr) {
        return (addr >= 0 || addr < Machine.processor().getMemory().length);
    }

    private boolean inVaddressSpace(int addr) {
        return (addr >= 0 && addr < numPages * pageSize);
    }

    //添加的
    //
    protected static Map<Integer, UThread> pidThreadMap = new HashMap<Integer, UThread>();
    //
    private int processStatus;
    //
    private boolean exitSuccess;
    //
    public static int processesCreated = 0;
    //
    private HashSet<Integer> childrenCreated;
    //
    protected final int processID;
    //
    private int fatherPID = -1;
    //保护内存互斥访问锁
    public static Lock allocateMemoryLock;
    //
    protected List<Integer> allocatedPages;
    //
    protected int pagelength = -1;

    private static final int ERROR = -1;
    private static final int SUCCESS = 0;
    private static final int MAX_FILENAME_LENGTH = 256;
    private static final int sizeOfInt = 4;

}
