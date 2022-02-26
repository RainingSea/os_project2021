package nachos.vm;
import nachos.machine.Machine;
import nachos.userprog.UserKernel;
import java.util.LinkedList;

/**
 * 二次机会算法
 */
public class SecondChanceReplacement {
    private int currentPhysicalPage;
    private int replacePhysicalPage;
    private static int count=0;

    public SecondChanceReplacement() {
        currentPhysicalPage = 0;
        replacePhysicalPage = 0;
    }

    public int findSwappedPage_Second() {
//        System.out.println("-----------选择受害者--------------");
        if (!UserKernel.memoryLinkedList.isEmpty()) {
            replacePhysicalPage = UserKernel.memoryLinkedList.removeFirst();
            return replacePhysicalPage;
        } else {
            System.out.println("------调用SecondChance-------" + (++count));
            System.out.println();
            while (InvertedPageTable.PhysicalPageCopy[currentPhysicalPage].getTranslationEntry().used) {
                InvertedPageTable.PhysicalPageCopy[currentPhysicalPage].getTranslationEntry().used = false;
                InvertedPageTable.updateEntry( InvertedPageTable.PhysicalPageCopy[currentPhysicalPage].getPid(), InvertedPageTable.PhysicalPageCopy[currentPhysicalPage].getTranslationEntry());
                currentPhysicalPage = ++currentPhysicalPage % Machine.processor().getNumPhysPages();
            }

            replacePhysicalPage = currentPhysicalPage;
            currentPhysicalPage++;
            currentPhysicalPage %= Machine.processor().getNumPhysPages();

            return replacePhysicalPage;
        }
    }


    public int findSwappedPage_FIFO() {
//        System.out.println("-----------选择受害者--------------");
        if (!UserKernel.memoryLinkedList.isEmpty()) {

            replacePhysicalPage = UserKernel.memoryLinkedList.removeFirst();
            return replacePhysicalPage;
        } else {
//            System.out.println("------调用FIFO-------" + (++count));

            replacePhysicalPage = currentPhysicalPage;
            currentPhysicalPage++;
            currentPhysicalPage %= Machine.processor().getNumPhysPages();
            return replacePhysicalPage;
        }
    }

}
