package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 *
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */

public class KThread {
	/**
	 * Get the current thread.
	 *
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		// 返回当前KThread
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		// start
		// waitJoinQueue = ThreadedKernel.scheduler.newThreadQueue(true);
		// end
		boolean status = Machine.interrupt().disable();
		if (currentThread != null) {
			tcb = new TCB();
		} else {
			//currentThread是空的时候，实例化readyQueue
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();// 第一个线程是主线程，指向第一个TCB
			name = "main";
			restoreState();// 对主线程置运行状态

			createIdleThread();
		}
		waitQueue.acquire(this);
		Machine.interrupt().restore(status);

	}

	/**
	 * Allocate a new KThread.
	 *
	 * @param target
	 *            the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 *
	 * @param target
	 *            the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 *
	 * @param name
	 *            the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 *
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 *
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * 线程开始执行命令
	 */
	public void fork() {
		// 执行KThread
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: " + target);
		boolean intStatus = Machine.interrupt().disable();
		// 关中断，在线程将要执行的准备阶段不能被打断
		//start如果不是主线程，则开始执行的时候会被interrupt
		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();// 并未真正开始执行，只是将线程移动到ready队列

		Machine.interrupt().restore(intStatus);// 回到机器原来的状态。
	}

	private void runThread() {
		begin();
		target.run();// 执行target
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();// 开中断
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 *
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 */
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();// 关中断

		Machine.autoGrader().finishingCurrentThread();// 将TCB变成将要结束的TCB

		Lib.assertTrue(toBeDestroyed == null);
		// 将当前的线程变成将要结束的线程，下一个线程运行时消除它
		toBeDestroyed = currentThread;
		// 当前线程状态置为完成
		currentThread.status = statusFinished;
		// start
		KThread thread = currentThread().waitQueue.nextThread();
		if (thread != null) {
			thread.ready();
		}
		// end
		
		sleep();
		// 将线程置为完成状态，读取下一个就绪线程
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 *
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 *
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void yield() {
		
		// 运行线程放弃CPU，将当前线程放入就绪队列，读取就绪队列下一个线程
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();
		
		// 正在执行的线程放入就绪队列执行就绪队列的下一个线程
		currentThread.ready();
		// 运行下一个线程
		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 *
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		// 如果线程执行完，则是从finish来，否则线程锁死，读取下一个线程
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;
		runNextThread();
	}
	
	

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	public void ready() {
		// 将线程移动到ready队列
		Lib.debug(dbgThread, "Ready thread: " + toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);
		// 将线程移入队列，idle线程不用放入等待队列
		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 */
	public void join() {
		// 线程B中有A.join()语句，则B等带A执行完才能执行
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		
		//判断是不是当前线程调用了join
		Lib.assertTrue(this != currentThread);
		// start
//		Lib.assertTrue(join_counter == 0);
		join_counter++;
		//关中断
		boolean status = Machine.interrupt().disable();
		//如果调用join()的对象的status不为完成状态
		if(!hasAcquired) {
			waitQueue.acquire(this);
			hasAcquired = true;
		}
		if (this.status != statusFinished) {
			//将KThread下的current对象放入waitQueue
			waitQueue.waitForAccess(KThread.currentThread());
			//将当前线程睡眠
			currentThread.sleep();
		}
		//如果是Finish状态则直接返回
		//开中断
		Machine.interrupt().restore(status);
		// end
	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 *
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		// 创建idle线程
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					yield();
			}
			// idle线程一直执行的操作时yield（放弃CPU）
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {// 执行下一个线程
		
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null) {
			
			nextThread = idleThread;// 如果线程队列为空则执行idle线程
		}
		
		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 *
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 *
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 *
	 * @param finishing
	 *            <tt>true</tt> if the current thread is finished, and should be
	 *            destroyed by the new thread.
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();// 当前java线程放弃CPU

		currentThread.saveState();// 保存状态

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString() + " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		// 恢复状态，执行此线程，如果有要结束的线程就结束它
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
		
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i + " times");
				currentThread.yield();
			}
			
		}
		private int which;
	}
	
	
	
	public static class joinTest implements Runnable{
		private KThread thread1;
		public joinTest(KThread thread1){
			this.thread1=thread1;
		}
		public void run() {
			System.out.println("I will call join(),and wait for thread1 execute over!");
			thread1.join();
			currentThread.yield();
			System.out.println("As you can see ,after thread1 loops 5 times,I procceed ");
			System.out.println("successful");
		}
	}

	/**
	 * Tests whether this module is working.
	 */

	public static void test_join() {
	
		//本题我简单的创建了两个进程A,B，首先执行B，在执行B的过程中对A执行join方法，
		//因此B被挂起，A开始循环执行，等到A执行完毕，B才会返回执行并结束。
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		boolean ints = Machine.interrupt().disable();
		System.out.println("-----Now we begin to test join()!-----");
		//fork只是将它们放到就绪队列并未开始执行
		final KThread thread1 = new KThread(new PingTest(1));
//		thread1.setName("forked thread").fork();
		new KThread(new Runnable(){
			public void run(){
				System.out.println("*** 线程2运行开始");
				thread1.join();
				System.out.println("*** 线程2运行结束");
			}
		}).fork();
		thread1.setName("forked thread").fork();
		Machine.interrupt().restore(ints);
	}
	
	public static void test_condition2(){
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		Lock lock=new Lock();
		Condition2 cdt=new Condition2(lock);
		KThread thread_A = new KThread(new Runnable(){
			public void run(){
				lock.acquire();
				System.out.println("-----Now we begin to test condition2()!-----");
				System.out.println("线程_A 即刻开始 sleep");
				cdt.sleep();
				//KThread.currentThread.yield();
				System.out.println("线程_A is waked up");
//				cdt.wake();
				lock.release();
				System.out.println("线程_A execute successful!");
			}
		});
		
		KThread thread_B = new KThread(new Runnable(){
			public void run(){
				lock.acquire();
				System.out.println("线程_B 即刻开始 sleep");
				cdt.sleep();
				//KThread.currentThread.yield();
				System.out.println("线程_B is waked up");
				lock.release();
				System.out.println("线程_B execute successful!");
			}
		});
		KThread thread_MM=new KThread(new Runnable(){
			public void run(){
				lock.acquire();
				System.out.println("Thread_Wake:我将唤醒所有线程");
				cdt.wakeAll();
				lock.release();
				//KThread.currentThread.yield();
				System.out.println("thread_Wake execute successful!");
//				System.out.println("successful!");
			}
		});
		thread_A.fork();
		thread_B.fork();
		thread_MM.fork();
//		thread_A.join();
	
	}

	public static void test_Alarm(){
		KThread alarmThread_1=new KThread(new Runnable(){
			int wait=10;
			public void run(){
				System.out.println("-----Now we begin to test Alarm()-----");
				System.out.println("alarmThread_1进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
				ThreadedKernel.alarm.waitUntil(wait);
				System.out.println("alarmThread_1执行结束后的系统时间:"+Machine.timer().getTime());
			}
		}).setName("alarmThread_1");
		
		KThread alarmThread_2=new KThread(new Runnable(){
			int wait=150;
			public void run(){
				System.out.println("alarmThread_2进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
				//ThreadedKernel.alarm.waitUntil(wait);
				//System.out.println("alarmThread_2执行结束后的系统时间:"+Machine.timer().getTime());
//				System.out.println("successful");
			}
		}).setName("alarmThread_2");
		alarmThread_1.fork();
		alarmThread_2.fork();
		System.out.println("successful");
//		new KThread(new Runnable(){
//			int wait=480;
//			public void run(){
//				System.out.println("-----Now we begin to test Alarm()-----");
//				System.out.println("alarmThread_1进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
//				ThreadedKernel.alarm.waitUntil(wait);
//			}
//		}).fork();
//		
//		new KThread(new Runnable(){
//			int wait=550;
//			public void run(){
//				System.out.println("alarmThread_2进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
//				ThreadedKernel.alarm.waitUntil(wait);
//				System.out.println("successful");
//			}
//		}).fork();
	}
	
	public static void test_communicator(){
//		Lib.debug(dbgThread, message);
		Communicator communicator = new Communicator();
		KThread s1=new KThread(new Runnable(){
			public void run(){
				System.out.println("-----Now we begin to test Communicator()-----");
				communicator.speak(666);
					System.out.println("Speak1 说 666!");
			}
		});
		KThread s2=new KThread(new Runnable(){
			public void run(){
				communicator.speak(4843);
					System.out.println("Speak2  说 4843!");
			}
		});
		
		KThread l1=new KThread(new Runnable(){
			public void run(){
				int hear=communicator.listen();
				System.out.println("listen1  听 "+hear);
			}
		});
		
		KThread l2=new KThread(new Runnable(){
			public void run(){
				int hear=communicator.listen();
				System.out.println("listen2  听 "+hear);
				System.out.println("communicator() 实现成功!");
			}
		});
		l1.fork();
		s1.fork();
	
		l2.fork();
		s2.fork();
	}
	
	public static void test_Priority(){//使用join（）方法来对编写的程序进行测试。
/*
首先给三个线程分别设置了优先级为2，4，6；如果不执行join()方法，必定会按照优先级高低顺序执行（线程3先执行，然后执行线程2，最后执行线程1）；
但在测试程序中当优先级为6的线程（线程3）执行到第3次时，调用了a.join()方法；如果没有发生优先级捐赠的话，线程2应该先执行，因为它的优先级是4，
但由于发生了优先级捐赠，线程3将优先级捐赠给了线程1，
所以线程1的有效优先级现在是6，故结果中出现了线程1先执行，然后线程3继续执行，最后线程2才执行。
*/
		 boolean status = Machine.interrupt().disable();//关中断，setPriority()函数中要求关中断
		    final KThread a = new KThread(new PingTest(1)).setName("thread1");
		    new PriorityScheduler().setPriority(a,2);
		    System.out.println("thread1的优先级为："+new PriorityScheduler().getThreadState(a).priority);
		    KThread b = new KThread(new PingTest(2)).setName("thread2");
		    new PriorityScheduler().setPriority(b,4);
		    System.out.println("thread2的优先级为："+new PriorityScheduler().getThreadState(b).priority);
		    KThread c = new KThread(new Runnable(){
		        public void run(){
		            for (int i=0; i<5; i++) {
		                if(i==2) 
		                    a.join();
		                System.out.println("*** thread 3 looped "
		                           + i + " times");
		                KThread.currentThread().yield();
		            }
		        }
		    }).setName("thread3");
		    new PriorityScheduler().setPriority(c,6);
		    System.out.println("thread3的优先级为："+new PriorityScheduler().getThreadState(c).priority);
		    KThread d = new KThread(new Runnable(){
		        public void run(){
		            for (int i=0; i<5; i++) {
		                if(i==2) 
		                    c.join();
		                System.out.println("*** thread 4 looped "
		                           + i + " times");
		                KThread.currentThread().yield();
		            }
		        }
		    }).setName("thread4");
		    new PriorityScheduler().setPriority(d,7);
		    a.fork();
		    b.fork();
		    c.fork();
		    d.fork();
		    Machine.interrupt().restore(status);
		/*
		System.out.println("-----Now begin the test_Priority()-----");
		KThread thread1=new KThread(new Runnable(){
			public void run(){
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread1");
					
				}
			}
		});
		
		KThread thread2=new KThread(new Runnable(){
			public void run(){
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread2");
					
				}
			}
		});
		
		KThread thread3=new KThread(new Runnable(){
			public void run(){
				thread1.join();
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread3");
				}
			}
		});
		boolean status = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1,1);
		ThreadedKernel.scheduler.setPriority(thread2,4);
		ThreadedKernel.scheduler.setPriority(thread3,7);
		thread1.setName("thread-1");
		thread2.setName("thread-2");
		thread3.setName("thread-3");
		
		Machine.interrupt().restore(status);
		thread1.fork();
		thread2.fork();
		thread3.fork();
*/
		

	}
	
	public static void test_Boat(){
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("-----Boat test begin-----");
		
		new KThread(new Runnable(){
			public void run(){
				Boat.selfTest();
			}
		}).fork();
		System.out.println("Successful");
	}
	
	public static void test_Lottery(){
		System.out.println("-----Now begin the test_LotteryPriority()-----");
		KThread thread1=new KThread(new Runnable(){
			public void run(){
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread1");
					
				}
			}
		});
		
		KThread thread2=new KThread(new Runnable(){
			public void run(){
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread2");
					
				}
			}
		});
		
		KThread thread3=new KThread(new Runnable(){
			public void run(){
				thread1.join();
				for(int i=0;i<3;i++){
					KThread.currentThread.yield();
					System.out.println("thread3");
				}
			}
		});
		boolean status = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(thread1,1);
		ThreadedKernel.scheduler.setPriority(thread2,2);
		ThreadedKernel.scheduler.setPriority(thread3,6);
		thread1.setName("thread-1");
		thread2.setName("thread-2");
		thread3.setName("thread-3");
		
		Machine.interrupt().restore(status);
		thread1.fork();
		thread2.fork();
		thread3.fork();
	}
	
	public static void selfTest() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");

//		new KThread(new PingTest(1)).setName("forked thread").fork();
//		new PingTest(0).run();
//		test_join();
// 		test_condition2();
//		test_Alarm();
//		test_communicator();
//		test_Priority();
//		test_Boat();
//		test_Lottery();
	}
	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 *
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;
	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
	private boolean hasAcquired = false;
	
	private static final int statusNew = 0;
	private static final int statusReady = 1;
	private static final int statusRunning = 2;
	private static final int statusBlocked = 3;
	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;
	private String name = "(unnamed thread)";
	private Runnable target;
	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;
	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;
	// start
	private int join_counter = 0;

	// end
	private static ThreadQueue readyQueue = null;
	private static KThread currentThread = null;
	private static KThread toBeDestroyed = null;
	private static KThread idleThread = null;
	
	
}
