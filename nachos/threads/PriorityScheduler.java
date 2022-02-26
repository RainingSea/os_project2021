package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler（调度程序） that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler（优先级调度程序） associates a priority with each thread. The next thread
 * to be dequeued（从队列中移除） is always a thread with priority no less than any other
 * waiting thread's priority（比其他的等待程序的优先级都大）. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially（本质上）, a priority scheduler gives access（使用权） in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential（可能性） to starve（饿死） a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially（部分的） solve the priority inversion（优先级反转） problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
    	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
    	//得到线程的优先级
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
    	//得到线程的有效优先级
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
    	//设置线程的优先级
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(priority >= priorityMinimum &&priority <= priorityMaximum);
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
    	//增加运行线程的优先级
    	boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		int priority = getPriority(thread);
		//达到最大优先级后不再增加优先级
		if (priority == priorityMaximum)
			return false;
		setPriority(thread, priority+1);
		Machine.interrupt().restore(intStatus);
		return true;
    }

    public boolean decreasePriority() {
    	//降低运行线程的优先级
    	boolean intStatus = Machine.interrupt().disable();
    	KThread thread = KThread.currentThread();
    	int priority = getPriority(thread);
    	//达到最小优先级后不再减小优先级
    	if (priority == priorityMinimum)
    		return false;
    	setPriority(thread, priority-1);
    	Machine.interrupt().restore(intStatus);
    	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1; //新线程的默认优先级
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;//线程的最低优先级
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;//线程最高优先级为7

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
    	//得到线程优先级，未创建则创建未默认优先级
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    //用优先级排列的线程队列
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    //!·····························································································！
    protected class PriorityQueue extends ThreadQueue {
    	//优先级队列
    	PriorityQueue(boolean transferPriority) {
		//自动调用父类无参数构造方法，创建一个线程队列
	    this.transferPriority = transferPriority;
	}
    	
	public void waitForAccess(KThread thread) {
		//传入等待队列的线程
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        getThreadState(thread).acquire(this);
	}
	
	public void addQueueToAcquired(KThread thread) {
		getThreadState(thread).addQueueToAcquired(this);
	}
	
	//修改
	public KThread nextThread() {
        Lib.assertTrue(Machine.interrupt().disabled());
        ThreadState x = pickNextThread();//下一个选择的线程
        if(x == null)//如果为null,则返回null
            return null;
        KThread thread = x.thread;
        getThreadState(thread).acquire(this);//将得到的线程改为this线程队列的队列头
        return thread;//将该线程返回
    }

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    java.util.Iterator i = waitList.iterator();
	    KThread nextthread;
	    if(i.hasNext()){
	    nextthread = (KThread)i.next();//取出下一个线程
	    //System.out.println(nextthread.getName());
	    KThread x = null;
	    while(i.hasNext()){//比较线程的有效优先级，选出最大的，如果优先级相同，则选择等待时间最长的
	        x = (KThread)i.next();
	        //System.out.println(x.getName());
	        int a = getThreadState(nextthread).getEffectivePriority();
	        int b = getThreadState(x).getEffectivePriority();
	        if(a<b){
	                nextthread = x;     
	        }
	    }
	    return getThreadState(nextthread);
	    }else 
	    return null;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	//将次队列绑定到一个KThread
    public ThreadState linkedthread=null;
    private int index;
    protected LinkedList<KThread> waitList = new LinkedList<KThread>();
    }
//！····················································································
    //给一个线程绑定ThreadState
    /**
     * The scheduling state of a thread（一个线程的优先级状态）. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     * 
     * @see	nachos.threads.KThread#schedulingState
     */
    
    
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    setPriority(priorityDefault);
	    waitQueue=new PriorityQueue(true);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}
	
//	public int geteffectivepriority(){
//		return effectivepriority;
//	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    //判断是否需要递归，如果每一个等待该线程的队列均为空，即没有线程需要等待该线程执行完再执行的时候，该线程的优先级为本身的优先级
	    boolean need_iteration = false;
	    for(Iterator i = acquired.iterator();i.hasNext();) {
	    	if(!((PriorityQueue)i.next()).waitList.isEmpty()){
	    		need_iteration = true;
	    	}
	    }
	    if(need_iteration){
	        effectivePriority = priority;//先将自己的优先级赋给有效优先级
	        for(Iterator i = acquired.iterator();i.hasNext();){//比较acquired中的所有等待队列中的所有线程的优先级
	            for(Iterator j = ((PriorityQueue)i.next()).waitList.iterator();j.hasNext();){
	                ThreadState ts = getThreadState((KThread)j.next());
	                int ttmpPriority = ts.getEffectivePriority();
	                if(ttmpPriority>effectivePriority){
	                    effectivePriority = ttmpPriority;
	                }
	            }
	        }
	        return effectivePriority;
	    }else{ 
	    	return priority;
	    }
	}
	
	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    this.priority = priority;
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    waitQueue.waitList.add(this.thread);//将调用线程加入到等待队列
	}


	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    waitQueue.waitList.remove(this.thread);//如果这个队列中存在该线程，删除
	}
	
	public void addQueueToAcquired(PriorityQueue waitQueue) {
		if(waitQueue.transferPriority){//如果存在优先级翻转，则执行下面操作
	        acquired.add(waitQueue);//将等待该线程的队列加入该线程的等待队列集合中
	    }
	}
	
	/** The thread with which this object is associated. */	   
	protected KThread thread;	//ThreadState联系的线程
	/** The priority of the associated thread. */
	protected int priority;		//此线程的优先级
	/**优先级等待队列*/
	protected PriorityQueue waitQueue;	//优先等待队列，等待该线程的队列
	protected int effectivePriority = -2;//有效优先级初始化为-2
//	protected final int invalidPriority = -1;//无效优先级
	//一个容器，里面有等待该线程的所有优先队列（每个优先队列里有等待线程）,包括等待锁，等待join方法的队列，这些队列需要在join等相应方法中加载到这个容器中
	protected HashSet<nachos.threads.PriorityScheduler.PriorityQueue> acquired = new HashSet<nachos.threads.PriorityScheduler.PriorityQueue>();
	
    }
	
}
