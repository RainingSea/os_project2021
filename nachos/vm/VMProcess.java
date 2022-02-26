package nachos.vm;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */

    public VMProcess() {
        super();
        allocatedPages = new LinkedList<Integer>();
        lazyLoadPages = new HashMap<Integer, CoffSectionAddress>();
        tlbBackUp = new TranslationEntry[Machine.processor().getTLBSize()];
        for (int i = 0; i < tlbBackUp.length; i++) {
            tlbBackUp[i] = new TranslationEntry(0, 0, false, false, false, false);
        }
        pagelength = Machine.processor().getNumPhysPages();
    }


    protected void swapOut(int pid, int vpn) {

        // 主存memory满了 这个牺牲者太可怜了 必须被放到交换区 （本来人家只需要让出TLB 还能在memory待着）
        // 把牺牲的TLB内容实打实的写入swap区中

        TranslationEntry entry = InvertedPageTable.getEntry(pid, vpn);
        if (entry == null) {
            return;
        }
        if (!entry.valid) {
            return;
        }

//        InvertedPageTable.print();
        //如果在tlb中有，将其设为无效
//        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
        for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
            TranslationEntry tlbEntry = InvertedPageTable.PhysicalPageCopy[i].getTranslationEntry();
            //遍历tlb  置换出对应的页
            if (tlbEntry.vpn == entry.vpn && tlbEntry.ppn == entry.ppn && tlbEntry.valid) {
                ///如果在tlb中有，将其设为无效
                tlbEntry.valid = false;
                //将反向页表中旧的页换掉
                InvertedPageTable.updateEntry2(pid, tlbEntry);
                //读取反向页表中对应的新页
                entry = InvertedPageTable.getEntry(pid, vpn);
                //写入tlb
                if (i < Processor.tlbSize)
                    Machine.processor().writeTLBEntry(i, tlbEntry);
                break;
            }
        }

        byte[] memory = Machine.processor().getMemory();
        int bufferOffset = Processor.makeAddress(entry.ppn, 0);
        SwapperController.getInstance(SwapFileName).writeToSwapFile(pid, vpn, memory, bufferOffset);

    }

    // 使用场合：取得一个空闲物理页，然后将发生页错误的虚拟页装到对应的物理页中
    // 被换入的页可能来自于磁盘 也可能来自于交换区
    //
    protected void swapIn(int ppn, int vpn) {
        TranslationEntry entry = InvertedPageTable.getEntry(processID, vpn);
        if (entry == null) {
            return;
        }
        if (entry.valid) {
            return;
        }
        boolean dirty, used;

        if (lazyLoadPages.containsKey(vpn)) {
            //第一次使用时载入内存（demand paging 的关键）
            // 读的coff
//            System.out.println("懒加载 " + vpn + " 到 " + ppn);
            lazyLoad(vpn, ppn);
        } else {
            // 读的是swap交互区
            //如果不是首次加载此coff  则将此物理页 从交换文件 复制到主存中
//            System.out.println("交换 " + vpn);
            byte[] memory = Machine.processor().getMemory();
            byte[] page = SwapperController.getInstance(SwapFileName).readFromSwapFile(processID, vpn);
            //src：源数组 srcPos：源数组要复制的起始位置 dest：目标数组  destPos：目标数组复制的起始位置 length：复制的长度
            System.arraycopy(page, 0, memory, ppn * pageSize, pageSize);
        }
        dirty = true;
        used = true;
        TranslationEntry newEntry = new TranslationEntry(vpn, ppn, true, false, used, dirty);
        //更改反向页表中此页的状态
        InvertedPageTable.setEntry(processID, newEntry);
    }

    protected int swap(int vpn) {
        TranslationEntry entry = InvertedPageTable.getEntry(processID, vpn);
        if (entry.valid)
            return entry.ppn;
        int ppn = getFreePage();
//        System.out.println(processID+"想访问"+vpn+" 将装载到"+ppn);
        swapIn(ppn, vpn);
        return ppn;
    }

    //这一步会将物理页真正加载到物理内存上，并从懒加载数组中去掉
    protected void lazyLoad(int vpn, int ppn) {
        CoffSectionAddress coffSectionAddress = lazyLoadPages.remove(vpn);
        if (coffSectionAddress == null) {
            return;
        }
        // 让VPN所在的Section去执行对应的装页方法 真正放到内存中
        CoffSection section = coff.getSection(coffSectionAddress.getSectionNumber());
        section.loadPage(coffSectionAddress.getPageOffset(), ppn);
    }


    protected int getFreePage() {
        //获取一个物理页
        int ppn = VMKernel.getFreePage();
//        System.out.println("ppn " + ppn);
        //
        if (ppn == -1) {
            //如果没有物理页了  需要选择一页牺牲掉
            TranslationEntryWithPid victim = InvertedPageTable.getVictimPage();

            ppn = victim.getTranslationEntry().ppn;

            swapOut(victim.getPid(), victim.getTranslationEntry().vpn);
        }
        return ppn;
    }

    //重写readVirtualMemory方法
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
            if (pageNum < 0 || pageNum >= numPages)
                return 0;
            //计算页偏移量
            int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
            //计算剩余页的容量
            int leftByte = pageSize - pageOffset;
            //计算下一次传送的数量:剩余页容量和需要转移的字节数中较小者
            int amount = Math.min(leftByte, length - transferredbyte);
            //计算物理内存的地址

            boolean intStatus = Machine.interrupt().disable();
            pageLock.acquire();
            TranslationEntry entry = InvertedPageTable.getEntry(processID, pageNum);
            if (entry == null) {
                return -1;
            }
            //如果对应的页不在内存中（valid为false） 需要取一个物理页 分配物理页 （将页装入内存中） 然后装入tlb
            if (!entry.valid) {
                int ppn = getFreePage();
                swapIn(ppn, pageNum);
                entry = InvertedPageTable.getEntry(processID, pageNum);
            }
            //否则直接牺牲TLB中的页 然后 置换
            int victim = getTLBVictim();
            replaceTLBEntry(victim, entry);

            pageLock.release();

            Machine.interrupt().restore(intStatus);
            int realAddress = entry.ppn * pageSize + pageOffset;
            //将物理内存的东西传输到虚拟内存
            System.arraycopy(memory, realAddress, data, offset + transferredbyte, amount);
            //修改传输成功的字节数
            transferredbyte = transferredbyte + amount;
        } while (transferredbyte < length);
        return transferredbyte;

        //return super.readVirtualMemory(vaddr, data, offset, length);
    }

    // 进程初始化会执行此方法 读入内存中
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
            if (pageNum < 0 || pageNum >= numPages)
                return 0;
            //此函数返回给定地址的页偏移量
            int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
            //页剩余的字节数
            int leftByte = pageSize - pageOffset;
            //设置本次转移的数量
            int amount = Math.min(leftByte, length - transferredbyte);

            boolean intStatus = Machine.interrupt().disable();

            pageLock.acquire();
            //由于进程只能看到TLB 所以需要将需要的页换入
            //swap方法是安全方法 如果pagenum已经在内存中则不会
            swap(pageNum);

            TranslationEntry entry = InvertedPageTable.getEntry(processID, pageNum);
            entry.dirty = true;
            entry.used = true;
            InvertedPageTable.setEntry(processID, entry);

            pageLock.release();
            Machine.interrupt().restore(intStatus);

            int realAddress = entry.ppn * pageSize + pageOffset;
            //从虚拟内存写入到物理内存(判断是否只读)
            if (!entry.readOnly) {
                System.arraycopy(data, offset + transferredbyte, memory, realAddress, amount);
            }
            //改变写成功的字节数
            transferredbyte = transferredbyte + amount;
        } while (transferredbyte < length);
        return transferredbyte;

        //return super.writeVirtualMemory(vaddr, data, offset, length);
    }

    protected TranslationEntry AllocatePageTable(int vpn) {
        return InvertedPageTable.getEntry(processID, vpn);
    }


    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    /**
     * 为了解决这个问题，您必须保证至少一个进程在上下文切换之前保证有2个TLB翻译以及内存中的2个对应页
     * 。这样，至少有一个进程能够在双误指令上取得进展。通过在上下文切换中保存和恢复TLB的状态，可以减少Live锁的效果，
     * 但是同样的问题可能发生在很少的物理内存页上。在这种情况下，在加载指令页和数据页之前，2个进程可能会以类似的方式被卡住。
     */
    public void saveState() {
        super.saveState();
//        Processor.printTLB();
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            //保存此进程当前的TLB信息
            tlbBackUp[i] = Machine.processor().readTLBEntry(i);
            //保存此时TLB的状态到 反向页表中
            if (tlbBackUp[i].valid) {
                InvertedPageTable.updateEntry(processID, tlbBackUp[i]);
            }
        }
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        //super.restoreState();

        for (int i = 0; i < tlbBackUp.length; i++) {
            //如果此进程之前的TLB中有页在内存中  则
            if (tlbBackUp[i].valid) {
                //还原TLB信息
                Machine.processor().writeTLBEntry(i, tlbBackUp[i]);
                TranslationEntry entry = InvertedPageTable.getEntry(processID, tlbBackUp[i].vpn);
                if (entry != null && entry.valid) {
                    Machine.processor().writeTLBEntry(i, entry);
                } else {
                    Machine.processor().writeTLBEntry(i, new TranslationEntry(0, 0, false, false, false, false));
                }
            } else {
                Machine.processor().writeTLBEntry(i, new TranslationEntry(0, 0, false, false, false, false));
            }
        }
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {
        //return super.loadSections();
        // load sections
        //加载coff的  section
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            for (int i = 0; i < section.getLength(); i++) {
                int virtualPageNum = section.getFirstVPN() + i;
//                System.out.println(virtualPageNum);
                //将coffsection的加载变为懒加载
                CoffSectionAddress coffSectionAddress = new CoffSectionAddress(s, i);
                lazyLoadPages.put(virtualPageNum, coffSectionAddress);
            }
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        // 进程释放之前会打印一次内存使用情况
//        InvertedPageTable.print();
//        Processor.printTLB();
        releaseResource();
        super.unloadSections();
        System.out.println(processID + " 进程退出");
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
            case Processor.exceptionTLBMiss:
//                System.out.println("进程 " + processID + " TLB失败次数 " + (++fault_count));
//                 导致TLB缺失的虚拟地址是通过调用processor.readRegister(processor.regBadVAddr) regBadVAddr=37获得的
                int address = processor.readRegister(Processor.regBadVAddr);
//                System.out.println("address "+address);
                pageLock.acquire();

                // 处理TLB缺页
                // 如果缺页不在内存 换入
                // 如果缺页在内存，但是不在TLB中
                // 反正最后都得换入到TLB中
                boolean isSuccessful = handleTLBFault(address);

//                InvertedPageTable.print();
                if (!isSuccessful) {
                    UThread.finish();
                }
                pageLock.release();
                break;
            case Processor.exceptionPageFault:
                Processor.privilege.stats.numPageFaults++;
                break;

            default:

                super.handleException(cause);

                break;
        }
    }

    //TLB错误  说明没有在TLB中找到对应的 虚拟页 vaddr是进程虚拟地址
    protected boolean handleTLBFault(int vaddr) {

        // 虚拟页数

        int vpn = Processor.pageFromAddress(vaddr);
        System.out.println("发生TLB miss ");
        //获取到 页错误发生的  反向页表中对应的TranslationEntry

//        for (int i = 0; i < Processor.tlbSize; i++) {
//            int TLB_vpn = Processor.translations[i].vpn;
//            System.out.println("TLB vpn: " + TLB_vpn);
//        }

        TranslationEntry entry = InvertedPageTable.getEntry(processID, vpn);
        if (entry == null) {
            return false;
        }

        //如果对应的页不在内存中（valid为false） 需要取一个物理页 分配物理页 （将页装入内存中） 然后装入tlb
        if (!entry.valid) {
//            System.out.println("不在memory中");
            // 手动抛出page fault 异常
            handleException(Processor.exceptionPageFault);
            int ppn = getFreePage();
//            System.out.println(processID+"将"+vpn+" 将装载到"+ppn);
            swapIn(ppn, vpn);
            //更新后的entry
            entry = InvertedPageTable.getEntry(processID, vpn);
        }

        //否则直接牺牲TLB中的页 然后置换
        int tlb_victim = getTLBVictim();
//        System.out.println("TLB替换: " + vpn + " 到 " + tlb_victim);
        replaceTLBEntry(tlb_victim, entry);

//        System.out.println("进程 " + processID + " 装入TLB 虚拟页为 " + entry.vpn + "  物理帧为 " + entry.ppn);
//        Processor.printTLB();

        return true;
    }

    // 在index处写入新的物理页
    protected void replaceTLBEntry(int index, TranslationEntry newEntry) {

        TranslationEntry oldEntry = Machine.processor().readTLBEntry(index);

        // 如果旧的TLB仍在内存中 在反制页表中更新 注意TLB被换出后并不会进交换区 仅仅是置为null而已
        // 首次加载整个sh时不会有valid
        if (oldEntry.valid) {
            InvertedPageTable.updateEntry(processID, oldEntry);
        }

        // CPU替换掉旧的TLB块 在原来的TLB写入新的位置 processor来操作
        Machine.processor().writeTLBEntry(index, newEntry);
    }

    //选择一个TLB中的页牺牲掉
    protected int getTLBVictim() {
        //如果
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            //如果此页现在已经不在主存中  则可以将它直接置换掉
            if (!Machine.processor().readTLBEntry(i).valid) {
                return i;
            }
        }
        //否则 随机置换一个
        int victim = Lib.random(Machine.processor().getTLBSize());
//        System.out.println("进程 "+processID + " 选择随机TLB进行牺牲 " + victim);
        return victim;
    }

    //给进程分配逻辑页  先设置页表条目  但不分配物理页
    protected boolean allocate(int vpn, int acquirePagesNum, boolean readOnly) {

        for (int i = 0; i < acquirePagesNum; ++i) {
            InvertedPageTable.insertEntry(processID, new TranslationEntry(vpn + i, 0, false, readOnly, false, false));
            SwapperController.getInstance(SwapFileName).insertUnallocatedPage(processID, vpn + i);
            allocatedPages.add(vpn + i);
        }
        numPages += acquirePagesNum;
        return true;
    }

    protected void releaseResource() {
        // 进程退出时释放全局的反制页表中的帧 如果有帧在物理页中 需要补上物理页
        for (int vpn : allocatedPages) {
            pageLock.acquire();
            TranslationEntry entry = InvertedPageTable.deleteEntry(processID, vpn);
            if (entry.valid)
                VMKernel.addFreePage(entry.ppn);
            SwapperController.getInstance(SwapFileName).deletePosition(processID, vpn);
            pageLock.release();
        }
    }

    protected boolean load(String name, String[] args) {
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

            //分配虚拟页，但是不加载到物理内存，并统计numPages
            if (!allocate(numPages, section.getLength(), section.isReadOnly())) {
                releaseResource();
                return false;
            }
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

        // 分配堆栈页；堆栈指针最初指向它的顶部
        if (!allocate(numPages, stackPages, false)) {
            releaseResource();
            return false;
        }
        initialSP = numPages * pageSize;

        // 最后保留1页作为参数
        if (!allocate(numPages, 1, false)) {
            releaseResource();
            return false;
        }

        if (!loadSections())
            return false;

        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    public boolean execute(String name, String[] args) {
        if (!load(name, args)) {
            return false;
        }


        UThread t = (UThread) new UThread(this).setName(name);
        pidThreadMap.put(this.processID, t);
        t.fork();

        return true;
    }

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';

    //处理器只能看到TLB,这个东西是让进程在上下文切换时将tlb的状态存下来，然后restore时回调
    protected TranslationEntry[] tlbBackUp;

    protected static Lock pageLock = new Lock();

    //实现coffsection的懒加载,是一个对应关系。一个虚拟页号对应一个coff文件的段号，可以通过段号查询文件真正的内容的位置
    protected HashMap<Integer, CoffSectionAddress> lazyLoadPages;//还没有加载的
    private static String SwapFileName = "Proj3SwapFile";
    private int fault_count = 0;
}
