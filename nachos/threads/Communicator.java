package nachos.threads;

import java.util.LinkedList;
import java.util.Queue;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	lock=new Lock();
    	queue=new LinkedList<Integer>();
    	speaker=new Condition2(lock);
    	listener=new Condition2(lock);
    	word=0;
    	speakerNum=0;
    	listenerNum=0;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
//    	boolean status = Machine.interrupt().disable();
    	//获得锁
    	lock.acquire();
    	if(listenerNum==0){
    		//如果听者为0,需要将speaker加入队列
    		speakerNum++;
    		//将要传输的int放入链表尾部
    		queue.offer(word);
    		speaker.sleep();
    		listener.wake();
    		speakerNum--;
    	}
    	else{
    		//如果听者不为0,直接唤醒听者
    		queue.offer(word);
    		listener.wake();
    	}
    	//释放锁
    	lock.release();
//    	Machine.interrupt().setStatus(status);
    	return;
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	//如果有人说话,则让说者说话，得到word，听者返回word，否则听者等待
//    	boolean status = Machine.interrupt().disable();
    	lock.acquire();
    	if(speakerNum!=0){
    		//如果说话者为0，则wake一个speaker说话
    		speaker.wake();
    		//将此听者sleep
    		listener.sleep();
    	}
    	else{
    		//如果有speaker,则将lister++
    		listenerNum++;
    		listener.sleep();
    		//被唤醒后 将听者减1
    		listenerNum--;
    	}
    	lock.release();
//    	Machine.interrupt().restore(status);
    	return queue.poll();
    }
    private Lock lock=null;//互斥锁
    private int speakerNum;
    private int listenerNum;
    private int word;
    private Condition2 speaker;
    private Condition2 listener;
    private Queue<Integer> queue;
}
