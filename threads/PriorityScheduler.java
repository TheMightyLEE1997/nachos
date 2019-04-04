package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
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
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	    currentTimeStamp = 0;
	    holder = null;
	    stablePQ = new TreeSet<ThreadState>(new comp());
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    //print();
	    // Reset the effective priority and holdingQueues of current holder.
        if (transferPriority) {
            holder.holdingQueues.remove(this);
            int eff = holder.getPriority();
            int peek = 0;
            Iterator<PriorityQueue> iter = holder.holdingQueues.iterator();
            while (iter.hasNext()) {
                ThreadState next = iter.next().pickNextThread();
                if (next != null)
                    peek = next.getEffectivePriority();
                if (peek > eff)
                    eff = peek;
            }
            holder.setEffectivePriority(eff);
        }
        ThreadState ret = stablePQ.pollFirst();
        holder = ret;
        if (ret == null) {
            return null;
        }
        ret.waitingQueue = null;
        if (transferPriority)
            ret.holdingQueues.add(this);
	    return ret.thread;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    if (stablePQ.isEmpty())
	        return null;
        ThreadState ret = stablePQ.first();
	    return ret;
	}
	
	/**
	 * Update the effective priority of a thread.
	 */
	protected void updateEffectivePriority(ThreadState ts, int priority) {
	    //print();
	    Lib.assertTrue(stablePQ.remove(ts));
	    ts.effectivePriority = priority;
	    stablePQ.add(ts);
	    if (transferPriority && holder.getEffectivePriority() < priority)
	        holder.setEffectivePriority(priority);

    }

	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    System.out.println("Current priority queue: ");
	    Iterator<ThreadState> iter = stablePQ.iterator();
	    while (iter.hasNext()) {
	        ThreadState ts = iter.next();
            System.out.println(ts+" Priority: "+ts.getPriority()+", Effective Priority: "+ts.getEffectivePriority()+", Time Stamp: "+ts.timeStamp+".");
        }
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	protected long currentTimeStamp;
	protected ThreadState holder;
	protected TreeSet<ThreadState> stablePQ;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
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
	    this.priority = priorityDefault;
	    this.effectivePriority = priorityDefault;
	    this.waitingQueue = null;
	    this.timeStamp = 0;
	    this.holdingQueues = new LinkedList<PriorityQueue>();
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    return effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
	        return;
        else if (priority > this.effectivePriority) {
	        this.priority = priority;
	        setEffectivePriority(priority);
        }
        else {
	        this.priority = priority;
            int eff = priority;
            int peek = 0;
            Iterator<PriorityQueue> iter = holdingQueues.iterator();
            while (iter.hasNext()) {
                ThreadState next = iter.next().pickNextThread();
                if (next != null)
                    peek = next.getEffectivePriority();
                if (peek > eff)
                    eff = peek;
            }
            setEffectivePriority(eff);
        }
	}

	/**
	 * Set the effective priority of the associated thread to the specified value.
	 *
	 * @param	priority    the new effective priority.
	 */
	public void setEffectivePriority(int priority) {
	    if (this.effectivePriority == priority)
	        return;
	    if (this.waitingQueue == null)
	        this.effectivePriority = priority;
        else {
	        this.waitingQueue.updateEffectivePriority(this, priority);
        }
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
	    Lib.assertTrue(Machine.interrupt().disabled());
	    this.waitingQueue = waitQueue;
	    waitQueue.currentTimeStamp ++;
	    timeStamp = waitQueue.currentTimeStamp;
	    waitQueue.stablePQ.add(this);
	    if (waitQueue.transferPriority)
	        waitQueue.holder.setEffectivePriority(waitQueue.stablePQ.first().getEffectivePriority());
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
	    Lib.assertTrue(waitQueue.holder == null);
	    Lib.assertTrue(waitQueue.stablePQ.size() == 0);
	    waitQueue.holder = this;
	    if (waitQueue.transferPriority)
	        this.holdingQueues.add(waitQueue);
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	/** The effective priority of the associated thread. */
	protected int effectivePriority;
	/** The queue that the associated thread is waiting on. */
	protected PriorityQueue waitingQueue;
	/** A time stamp so that we can maintain FIFO for same effective priority. */
	protected long timeStamp;
	/** The list of queues that is waiting for the associated thread. */
	protected LinkedList<PriorityQueue> holdingQueues;
    }



    /**
     * A comparator class that compares two ThreadStates based on both effective priority and timestamp.
     */
	protected class comp implements Comparator<ThreadState> {
	    public int compare(ThreadState ts1, ThreadState ts2) {
	        Lib.assertTrue(ts1.waitingQueue == ts2.waitingQueue);
	        if (ts1.getEffectivePriority() < ts2.getEffectivePriority())
	            return 1;
            else if (ts1.getEffectivePriority() == ts2.getEffectivePriority() && ts1.timeStamp > ts2.timeStamp)
                return 1;
            else if (ts1.getEffectivePriority() == ts2.getEffectivePriority() && ts1.timeStamp == ts2.timeStamp)
                return 0;
            return -1;
        }
    }




    /**
     * Test if this module is working.
     */
    public static void selfTest() {
        //********* Test 1!
        System.out.println("Priority Scheduling Test 1 (testing if higher priority threads are scheduled first):");
        int num = 7;
        int times = 5;
        ArrayList<KThread> ths = new ArrayList<KThread>();
        KThread thread;
        for (int i = 0; i < num; i ++) {
            thread = new KThread(new ThreadWithPriority(i, times, true)).setName("id"+i);
            System.out.println("Forking Thread ID "+i+".");
            boolean intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(thread, i);
            Machine.interrupt().restore(intStatus);
            thread.fork();
            ths.add(thread);
        }
        for (int i = 0; i < num; i ++)
            ths.get(i).join();
        System.out.println("Priority Scheduling Test 1 Passed.\n");

        //********* Test 2!
        /** This test has a background in Game of Thrones.
         * Jon, Sansa, Arya and Bran are four Stark children. All of them want to get hold of a "Dragonglass" or a "Valyrian steel" to kill white walkers. 
         * Initially Jon has priority 4, Sansa 3, Arya 2, Bran 1 (according to their age). 
         * Initiallly Jon and Sansa ask for Valyrian steel, Bran asks for Dragonglass, and Arya asks for both. 
         * Arya gets Dragonglass, and Jon gets Valyrian steel.
         * Now suddenly Bran becomes the new Greenseer, and he gets priority 5. (This is set by the thread of Arya.) 
         * After a while Jon releases the Valyrian steel.
         * Because we have priority donation, Arya now has larger effective priority than Sansa, so she gets the Valyrian steel.
         */
        System.out.println("Priority Scheduling Test 2 (testing if priorities are donated at locks, simple test with 2 locks):");
        // 1. Define the locks and fork the threads.
        Lock Dragonglass = new Lock();
        Lock Valyriansteel = new Lock();
        KThread Jon = new KThread(new JonSansaClass(Valyriansteel, "Jon")).setName("Jon");
        KThread Sansa = new KThread(new JonSansaClass(Valyriansteel, "Sansa")).setName("Sansa");
        KThread Bran = new KThread(new BranClass(Dragonglass)).setName("Bran");
        KThread Arya = new KThread(new AryaClass(Dragonglass, Valyriansteel, Bran)).setName("Arya");
        boolean intStatus = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(Jon, 6); 
        ThreadedKernel.scheduler.setPriority(Sansa, 6); 
        ThreadedKernel.scheduler.setPriority(Arya, 6); 
        ThreadedKernel.scheduler.setPriority(Bran, 1); 
        Machine.interrupt().restore(intStatus);
        Jon.fork();
        Sansa.fork();
        Arya.fork();
        Bran.fork();
        Jon.join();
        Sansa.join();
        Arya.join();
        Bran.join();
        System.out.println("Priority Scheduling Test 2 Passed.\n");
   
        //********* Test 3!
        /**
         * A join graph with dependence as follows:
         * (id0, 1) <--- (id1, 1) <--- (id2, 1) <--- (id3, 4)
         *     ^             ^
         *     |             |
         * (id4, 3)      (id5, 2)
         * Threads should finish in the same order as their ids.
         */
        System.out.println("Priority Scheduling Test 3 (testing if priorities are donated at joins with transitivity, simple test with 6 threads, threads should finish in the same order as their ids):)");
        KThread id0 = new KThread(new ThreadJoin(0, null)).setName("0");
        KThread id1 = new KThread(new ThreadJoin(1, id0)).setName("1");
        KThread id2 = new KThread(new ThreadJoin(2, id1)).setName("2");
        KThread id3 = new KThread(new ThreadJoin(3, id2)).setName("3");
        KThread id4 = new KThread(new ThreadJoin(4, id0)).setName("4");
        KThread id5 = new KThread(new ThreadJoin(5, id1)).setName("5");
        intStatus = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(id0, 1); 
        ThreadedKernel.scheduler.setPriority(id1, 1); 
        ThreadedKernel.scheduler.setPriority(id2, 1); 
        ThreadedKernel.scheduler.setPriority(id3, 4); 
        ThreadedKernel.scheduler.setPriority(id4, 3); 
        ThreadedKernel.scheduler.setPriority(id5, 2); 
        Machine.interrupt().restore(intStatus);
        id5.fork();
        id4.fork();
        id3.fork();
        id2.fork();
        id1.fork();
        id0.fork();
        id5.join();
        System.out.println("Priority Scheduling Test 3 Passed.\n");

   }


    /** 
     * Runnable for threads with different priorities.
     * Used for Test 1.
     */
    private static class ThreadWithPriority implements Runnable {
        ThreadWithPriority(int id, int times, boolean useRand) {
            this.id = id;
            this.times = times;
            this.useRand = useRand;
        }

        public void run() {
            Random rand = new Random();
            for (int i = 0; i < times; i ++) {
                System.out.println("Next turn: Thread ID "+id+" with priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+".");
                if (useRand) {
                    if (rand.nextInt(1000) > 600) {
                        priority = rand.nextInt(priorityMaximum - priorityMinimum) + priorityMinimum;
                        boolean intStatus = Machine.interrupt().disable();
                        ThreadedKernel.scheduler.setPriority(priority);
                        System.out.println("Priority of thread ID "+id+" is set to "+priority);
                        Machine.interrupt().restore(intStatus);
                    }
                }
                KThread.yield();
            }
            System.out.println("Thread ID "+id+" with priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" finished running.");
        }

        private int id;
        private int priority;
        private int times;
        private boolean useRand;
    }

    /** 
     * Runnable for four Stark children.
     * Used for Test 2.
     */
    private static class JonSansaClass implements Runnable {
        JonSansaClass(Lock lock, String name) {
            this.Valyriansteel = lock;
            this.name = name;
        }

        public void run() {
            boolean intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(KThread.currentThread(), ((name == "Jon") ? 4 : 3));
            Machine.interrupt().restore(intStatus);
            System.out.println(name+" has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
            System.out.println(name+" acquires the Valyrian steel......");
            Valyriansteel.acquire();
            System.out.println(name+" gets the Valyrian steel!");
            for (int i = 1; i < 4; i ++) {
                KThread.yield();
            }
            Valyriansteel.release();
            System.out.println(name+" releases the Valyrian steel!");
        }

        private Lock Valyriansteel;
        private String name;
    } 

    private static class AryaClass implements Runnable {
        AryaClass(Lock lock1, Lock lock2, KThread thread) {
            this.Dragonglass = lock1;
            this.Valyriansteel = lock2;
            this.Bran = thread;
        }

        public void run() {
            boolean intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(KThread.currentThread(), 2);
            Machine.interrupt().restore(intStatus);
            System.out.println("Arya has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
            System.out.println("Arya acquires the Dragonglass......");
            Dragonglass.acquire();
            System.out.println("Arya gets the Dragonglass!");
            intStatus = Machine.interrupt().disable();
            ThreadedKernel.scheduler.setPriority(Bran, 5);
            Machine.interrupt().restore(intStatus);
            System.out.println("Bran becomes the Greenseer and get priority 5!");
            KThread.yield();
            System.out.println("***Arya has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
            System.out.println("Arya acquires the Valyrian steel......");
            Valyriansteel.acquire();
            System.out.println("Arya gets the Valyrian steel!");
            for (int i = 1; i < 4; i ++) {
                KThread.yield();
            }
            Valyriansteel.release();
            System.out.println("Arya releases the Valyrian steel!");
            Dragonglass.release();
            System.out.println("Arya releases the Dragonglass!");
            System.out.println("Arya has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
        }

        private Lock Valyriansteel;
        private Lock Dragonglass;
        private KThread Bran;
    } 

    private static class BranClass implements Runnable {
        BranClass(Lock lock) {
            this.Dragonglass = lock;
        }

        public void run() {
            System.out.println("Bran has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
            System.out.println("Bran acquires the Dragonglass......");
            Dragonglass.acquire();
            System.out.println("Bran gets the Dragonglass!");
            for (int i = 1; i < 4; i ++) {
                KThread.yield();
            }
            Dragonglass.release();
            System.out.println("Bran releases the Dragonglass!");
        }

        private Lock Dragonglass;
    } 


    /** 
     * Runnable for a thread that joins another thread.
     * Used for Test 3.
     */
    private static class ThreadJoin implements Runnable {
        ThreadJoin(int id, KThread toJoin) {
            this.id = id;
            this.toJoin = toJoin;
        }

        public void run() {
            if (toJoin == null) {
                System.out.println("Thread ID "+id+" starts.");
                ThreadedKernel.alarm.waitUntil(500);
            }
            else {
                System.out.println("Thread ID "+id+" joins Thread ID "+toJoin.getName()+".");
                toJoin.join();
            }
            System.out.println("Thread ID "+id+" has priority "+((ThreadState)KThread.currentThread().schedulingState).getPriority()+" and effective priority "+((ThreadState)KThread.currentThread().schedulingState).getEffectivePriority()+".");
            System.out.println("Thread ID "+id+" finishes.");
        }

        private int id;
        private KThread toJoin;
    }

}
