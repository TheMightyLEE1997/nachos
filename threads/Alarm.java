package nachos.threads;

import java.util.*;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	boolean intStatus = Machine.interrupt().disable();
    	while (!threadsToAlarm.isEmpty() &&
    			threadsToAlarm.peek().wakeTime <= Machine.timer().getTime()) {
    		threadsToAlarm.remove().thread.ready();
    	}
    	Machine.interrupt().restore(intStatus);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
    	boolean intStatus = Machine.interrupt().disable();
    	threadsToAlarm.add(new WaitingThread(Machine.timer().getTime() + x, KThread.currentThread()));
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
    }
    
    private static class WaitingThread implements Comparable<WaitingThread> {
    	long wakeTime;
    	KThread thread;
    	
    	WaitingThread(long wakeTime, KThread thread) {
    		this.wakeTime = wakeTime;
    		this.thread = thread;
    	}
    	
    	@Override
    	public int compareTo(WaitingThread o) {
    		if (wakeTime != o.wakeTime) {
    			return ((Long) wakeTime).compareTo(o.wakeTime);
    		}
    		return thread.compareTo(o.thread);
    	}
    }
    
    private static PriorityQueue<WaitingThread> threadsToAlarm = new PriorityQueue<WaitingThread>();
}
