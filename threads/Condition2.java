package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
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
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = KThread.currentThread();
        waitQueue.waitForAccess(thread);
        conditionLock.release();
        KThread.sleep();
        Machine.interrupt().restore(intStatus);
        conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = waitQueue.nextThread();
        if (thread != null)
            thread.ready();
        Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean intStatus = Machine.interrupt().disable();
        KThread thread = waitQueue.nextThread();
        while (thread != null) {
            thread.ready();
            thread = waitQueue.nextThread();
        }
        Machine.interrupt().restore(intStatus);
    }

    /**
     * Class for Dining Philosopher.
     */
    private static class DiningPhilosopher implements Runnable {
        DiningPhilosopher(int n, int t, int id) {
            this.id = id;
            if (lock == null) {
                num = n;
                times = t;
                lock = new Lock();
                conds = new ArrayList<Condition2>();
                state = new ArrayList<String>();
                for (int i = 0; i < num; i ++) {
                    conds.add(new Condition2(lock));
                    state.add(new String("thinking"));
                }
            }
        }

        private void pickup(int i) {
            lock.acquire();
            state.set(i, "hungry");
            test(i);
            if (state.get(i) != "eating")
                conds.get(i).sleep();
            System.out.println("Philosopher " + i + " starts to eat.");
            lock.release();
        }

        private void putdown(int i) {
            lock.acquire();
            state.set(i, "thinking");
            System.out.println("Philosopher " + i + " resumes to think.");
            test((i + 1) % num);
            test((i + num - 1) % num);
            lock.release();
        }

        private void test(int i) {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            if (state.get((i + 1) % num) != "eating" && state.get((i + num - 1) % num) != "eating" && state.get(i) == "hungry") {
                state.set(i, "eating");
                conds.get(i).wake();
            }
        }

        public void run() {
            for (int i = 0; i < times; i ++) {
                ThreadedKernel.alarm.waitUntil(id + 1);
                pickup(id);
                ThreadedKernel.alarm.waitUntil(5);
                putdown(id);
            }
        }

        private static Lock lock = null;
        private static int num;
        private static int times;
        private static ArrayList<Condition2> conds = null;
        private static ArrayList<String> state = null; 
        private int id;
    }

    /**
     * Test if this module is working by solving the dining philosopher problem.
     */
    public static void selfTest() {
        int num = 7;
        int times = 3;
        System.out.println("Condition2 Test (Dining Philosopher):");
        ArrayList<KThread> ths = new ArrayList<KThread>();
        for (int i = 0; i < num; i ++) {
            KThread thread = new KThread(new DiningPhilosopher(num, times, i)).setName("dp_"+i);
            thread.fork();
            ths.add(thread);
        }
        for (int i = 0; i < num; i ++)
            ths.get(i).join();
        System.out.println("Condition2 Test (Dining Philosopher) Passed.");
    }

    private Lock conditionLock;
    private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
}
