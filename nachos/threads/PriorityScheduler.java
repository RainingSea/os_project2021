package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler�����ȳ��� that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler�����ȼ����ȳ��� associates a priority with each thread. The next thread
 * to be dequeued���Ӷ������Ƴ��� is always a thread with priority no less than any other
 * waiting thread's priority���������ĵȴ���������ȼ�����. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially�������ϣ�, a priority scheduler gives access��ʹ��Ȩ�� in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential�������ԣ� to starve�������� a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially�����ֵģ� solve the priority inversion�����ȼ���ת�� problem; in
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
    	//�õ��̵߳����ȼ�
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
    	//�õ��̵߳���Ч���ȼ�
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
    	//�����̵߳����ȼ�
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(priority >= priorityMinimum &&priority <= priorityMaximum);
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
    	//���������̵߳����ȼ�
    	boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		int priority = getPriority(thread);
		//�ﵽ������ȼ������������ȼ�
		if (priority == priorityMaximum)
			return false;
		setPriority(thread, priority+1);
		Machine.interrupt().restore(intStatus);
		return true;
    }

    public boolean decreasePriority() {
    	//���������̵߳����ȼ�
    	boolean intStatus = Machine.interrupt().disable();
    	KThread thread = KThread.currentThread();
    	int priority = getPriority(thread);
    	//�ﵽ��С���ȼ����ټ�С���ȼ�
    	if (priority == priorityMinimum)
    		return false;
    	setPriority(thread, priority-1);
    	Machine.interrupt().restore(intStatus);
    	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1; //���̵߳�Ĭ�����ȼ�
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;//�̵߳�������ȼ�
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;//�߳�������ȼ�Ϊ7

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
    	//�õ��߳����ȼ���δ�����򴴽�δĬ�����ȼ�
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    //�����ȼ����е��̶߳���
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    //!��������������������������������������������������������������������������������������������������������������������������������������������������������������������������������������������
    protected class PriorityQueue extends ThreadQueue {
    	//���ȼ�����
    	PriorityQueue(boolean transferPriority) {
		//�Զ����ø����޲������췽��������һ���̶߳���
	    this.transferPriority = transferPriority;
	}
    	
	public void waitForAccess(KThread thread) {
		//����ȴ����е��߳�
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
	
	//�޸�
	public KThread nextThread() {
        Lib.assertTrue(Machine.interrupt().disabled());
        ThreadState x = pickNextThread();//��һ��ѡ����߳�
        if(x == null)//���Ϊnull,�򷵻�null
            return null;
        KThread thread = x.thread;
        getThreadState(thread).acquire(this);//���õ����̸߳�Ϊthis�̶߳��еĶ���ͷ
        return thread;//�����̷߳���
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
	    nextthread = (KThread)i.next();//ȡ����һ���߳�
	    //System.out.println(nextthread.getName());
	    KThread x = null;
	    while(i.hasNext()){//�Ƚ��̵߳���Ч���ȼ���ѡ�����ģ�������ȼ���ͬ����ѡ��ȴ�ʱ�����
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
	//���ζ��а󶨵�һ��KThread
    public ThreadState linkedthread=null;
    private int index;
    protected LinkedList<KThread> waitList = new LinkedList<KThread>();
    }
//��������������������������������������������������������������������������������������������������������������������������������������������������������������������������
    //��һ���̰߳�ThreadState
    /**
     * The scheduling state of a thread��һ���̵߳����ȼ�״̬��. This should include the thread's
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
	    //�ж��Ƿ���Ҫ�ݹ飬���ÿһ���ȴ����̵߳Ķ��о�Ϊ�գ���û���߳���Ҫ�ȴ����߳�ִ������ִ�е�ʱ�򣬸��̵߳����ȼ�Ϊ���������ȼ�
	    boolean need_iteration = false;
	    for(Iterator i = acquired.iterator();i.hasNext();) {
	    	if(!((PriorityQueue)i.next()).waitList.isEmpty()){
	    		need_iteration = true;
	    	}
	    }
	    if(need_iteration){
	        effectivePriority = priority;//�Ƚ��Լ������ȼ�������Ч���ȼ�
	        for(Iterator i = acquired.iterator();i.hasNext();){//�Ƚ�acquired�е����еȴ������е������̵߳����ȼ�
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
	    waitQueue.waitList.add(this.thread);//�������̼߳��뵽�ȴ�����
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
	    waitQueue.waitList.remove(this.thread);//�����������д��ڸ��̣߳�ɾ��
	}
	
	public void addQueueToAcquired(PriorityQueue waitQueue) {
		if(waitQueue.transferPriority){//����������ȼ���ת����ִ���������
	        acquired.add(waitQueue);//���ȴ����̵߳Ķ��м�����̵߳ĵȴ����м�����
	    }
	}
	
	/** The thread with which this object is associated. */	   
	protected KThread thread;	//ThreadState��ϵ���߳�
	/** The priority of the associated thread. */
	protected int priority;		//���̵߳����ȼ�
	/**���ȼ��ȴ�����*/
	protected PriorityQueue waitQueue;	//���ȵȴ����У��ȴ����̵߳Ķ���
	protected int effectivePriority = -2;//��Ч���ȼ���ʼ��Ϊ-2
//	protected final int invalidPriority = -1;//��Ч���ȼ�
	//һ�������������еȴ����̵߳��������ȶ��У�ÿ�����ȶ������еȴ��̣߳�,�����ȴ������ȴ�join�����Ķ��У���Щ������Ҫ��join����Ӧ�����м��ص����������
	protected HashSet<nachos.threads.PriorityScheduler.PriorityQueue> acquired = new HashSet<nachos.threads.PriorityScheduler.PriorityQueue>();
	
    }
	
}