package nachos.vm;


import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;

import java.util.HashMap;

//全局的反向页表  保存在真正内存位置的页的虚拟地址以及拥有该页的进程的信息
public class InvertedPageTable {
    //全局的反向页表，是一个哈希map，里面有每个进程对应的虚拟页号，但是不一定已经加载到物理内存
    private static HashMap<VirtualPageFlag, TranslationEntry> GlobalInvertedPageTable = new HashMap<VirtualPageFlag, TranslationEntry>();

    //全局的物理页副本，是一个数组。主要有两个作用，第一个是用来记录哪些物理页已经加载了，哪些还是没有加载，没有加载的为null。
    //里面记录哪些物理页近期被使用，其应用主要在于页置换的时钟算法中
    public static TranslationEntryWithPid[] PhysicalPageCopy = new TranslationEntryWithPid[Machine.processor().getNumPhysPages()];
    private static SecondChanceReplacement secondChanceReplacement = new SecondChanceReplacement();

    private InvertedPageTable() {
    }

    //向反向页表中插入一页
    public static boolean insertEntry(int pid, TranslationEntry entry) {
        VirtualPageFlag virtualPageFlag = new VirtualPageFlag(pid, entry.vpn);
        if (GlobalInvertedPageTable.containsKey(virtualPageFlag)) {
            return false;
        }
        GlobalInvertedPageTable.put(virtualPageFlag, entry);
        //如果则虚拟页在物理页中，而tlb可以直接由处理器使用。
        if (entry.valid) {
            PhysicalPageCopy[entry.ppn] = new TranslationEntryWithPid(entry, pid);
        }
        return true;
    }

    //将某进程的某虚拟页 从反向页表中删除
    public static TranslationEntry deleteEntry(int pid, int vpn) {
        VirtualPageFlag virtualPageFlag = new VirtualPageFlag(pid, vpn);
        TranslationEntry entry = GlobalInvertedPageTable.get(virtualPageFlag);
        if (entry == null) {
            return null;
        }
        //如果则虚拟页在内存中，则将其清空
        if (entry.valid) {
            PhysicalPageCopy[entry.ppn] = null;
        }
        return entry;
    }

    //更新反向页表中某进程的某个虚拟地址指向的物理地址项
    //更新成和newEntry一样的结果
    public static void setEntry(int pid, TranslationEntry newEntry) {
        VirtualPageFlag virtualPageFlag = new VirtualPageFlag(pid, newEntry.vpn);
        //如果此TranslationEntry本身就不在反向页表中
        if (!GlobalInvertedPageTable.containsKey(virtualPageFlag)) {
            return;
        }

        //从反向页表中取出旧TranslationEntry
        TranslationEntry oldEntry = GlobalInvertedPageTable.get(virtualPageFlag);
        if (oldEntry.valid) {
            if (PhysicalPageCopy[oldEntry.ppn] == null) {
                return;
            }
            PhysicalPageCopy[oldEntry.ppn] = null;
        }
        if (newEntry.valid) {
            if (PhysicalPageCopy[newEntry.ppn] != null) {
                return;
            }
            PhysicalPageCopy[newEntry.ppn] = new TranslationEntryWithPid(newEntry, pid);
        }

        GlobalInvertedPageTable.put(virtualPageFlag, newEntry);
    }

    //更新反向页表中某进程的某个虚拟地址指向的物理地址项
    //更新成原来的entry和需要更新的entry的混合结果，这个混合结果混合了标志位used和dirty，其他的都跟新的entry保持一致

    public static void updateEntry(int pID, TranslationEntry entry) {

        VirtualPageFlag key = new VirtualPageFlag(pID, entry.vpn);
        if (!GlobalInvertedPageTable.containsKey(key)) {
            return;
        }

        TranslationEntry oldEntry = GlobalInvertedPageTable.get(key);
        // entry 为主体
        TranslationEntry newEntry = mix(entry, oldEntry);


        if (oldEntry.valid) {
            if (PhysicalPageCopy[oldEntry.ppn] == null) {
                return;
            }
            // 因为让出了memory空间 所以在副本中相应的置为null
            PhysicalPageCopy[oldEntry.ppn] = null;
        }
        // 新的TLB要占据老的TLB所拥有的贮存空间 当然 必须是从disk来的
        if (newEntry.valid) {
            // 接上 如果只是从主存中重新调回来 不用操作 因为新的TLB本来就在主存中
            if (PhysicalPageCopy[newEntry.ppn] != null) {
                return;
            }
            // 否则 说明新的TLB是从disk里或swap中来的 需要记录一个位置
            PhysicalPageCopy[newEntry.ppn] = new TranslationEntryWithPid(newEntry, pID);
        }
        GlobalInvertedPageTable.put(key, newEntry);
    }

    public static void updateEntry2(int pID, TranslationEntry entry) {
//        System.out.println("你不是个jb");
        VirtualPageFlag key = new VirtualPageFlag(pID, entry.vpn);
        if (!GlobalInvertedPageTable.containsKey(key)) {
            return;
        }

        PhysicalPageCopy[entry.ppn] = null;
//        System.out.println("你就是个jb "+entry.valid);
        GlobalInvertedPageTable.put(key, entry);
    }


    private static TranslationEntry mix(TranslationEntry entry1, TranslationEntry entry2) {
        TranslationEntry mixture = entry1;
        if (entry1.dirty || entry2.dirty) {
            mixture.dirty = true;
        }
        if (entry1.used || entry2.used) {
            mixture.used = true;
        }
        return mixture;
    }


    //获取 某进程 某虚拟页下对应的TranslationEntry
    public static TranslationEntry getEntry(int pid, int vpn) {
        VirtualPageFlag virtualPageFlag = new VirtualPageFlag(pid, vpn);
        TranslationEntry entry = null;
        if (GlobalInvertedPageTable.containsKey(virtualPageFlag)) {
            entry = GlobalInvertedPageTable.get(virtualPageFlag);
        }
        return entry;
    }

    // 选取被置换的页
    public static TranslationEntryWithPid getVictimPage() {
        TranslationEntryWithPid entry = null;
//        int i = secondChanceReplacement.findSwappedPage_Second();
        int i = secondChanceReplacement.findSwappedPage_FIFO();
//        System.out.println(i);
        entry = PhysicalPageCopy[i];
        return entry;
    }

    public static void print() {
        for (int i = 0; i < PhysicalPageCopy.length; i++) {
            if (PhysicalPageCopy[i] != null)
                System.out.println("进程 " + PhysicalPageCopy[i].getPid() + " 拥有虚拟页 " + PhysicalPageCopy[i].getTranslationEntry().vpn + " 在物理页 " + PhysicalPageCopy[i].getTranslationEntry().ppn);
        }
    }
}
