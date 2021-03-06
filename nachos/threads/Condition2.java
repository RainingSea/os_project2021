package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	//start
	waitqueue=ThreadedKernel.scheduler.newThreadQueue(false);
    //end
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());//检测当前线程是否获得锁
	//start
	boolean status=Machine.interrupt().disable();//关中断
	conditionLock.release();
	//释放锁
	waitqueue.waitForAccess(KThread.currentThread());
	
	KThread.currentThread().sleep();//返回就是又线程使用wake唤醒
	conditionLock.acquire();//重新获得锁才能执行
    
	Machine.interrupt().restore(status);//开中断
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//start
	boolean status = Machine.interrupt().disable();
	KThread thread = waitqueue.nextThread();
	if(!(thread==null))
		thread.ready();//只是将线程加入就绪队列但不释放锁
//	else if(thread==null)
//		System.out.println("111");
	Machine.interrupt().restore(status);
    //end
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//start
	boolean status = Machine.interrupt().disable();
	KThread thread = waitqueue.nextThread();
	while(thread!=null){
		thread.ready();
		thread=waitqueue.nextThread();
	}
	Machine.interrupt().restore(status);
    //end
    }

    private Lock conditionLock;
    //start
    private ThreadQueue waitqueue=null;
    //end
}
