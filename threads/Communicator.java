package nachos.threads;

import nachos.machine.*;

import java.util.*;

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
        lock = new Lock();
        condListen = new Condition2(lock);
        condSpeak = new Condition2(lock);
        message = 0;
        countListen = 0;
        countSpeak = 0;
        ready = false;
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
        lock.acquire();
        countSpeak ++;
        if (countListen == 0 || ready) {
            condSpeak.sleep();
            Lib.assertTrue(ready);
        }
        else {
            Lib.assertTrue(countSpeak == 1);
            ready = true;
        }
        message = word;
        countSpeak --;
        condListen.wake();
        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        lock.acquire();
        countListen ++;
        int ret = 0;
        if (countSpeak > 0 && countListen == 1) {
            Lib.assertTrue(!ready);
            ready = true;
            condSpeak.wake();
        }
        condListen.sleep();
        Lib.assertTrue(ready);
        ret = message;
        countListen --;
        if (countSpeak > 0 && countListen > 0)
            condSpeak.wake();
        else
            ready = false;
        lock.release();
        return ret;
    }


    /**
     * Print current status of the communicator.
     */
    public void print() {
        boolean intStatus = Machine.interrupt().disable();
        System.out.println("Status: countListen = "+countListen+", countSpeak = "+countSpeak+", message = "+message+", ready = "+ready+".");
        Machine.interrupt().restore(intStatus);
    }

    /**
     * Lister class for testing.
     */
    private static class Listener implements Runnable {
        Listener(Communicator comm, int id) {
            this.comm = comm;
            this.id = id;
        }

        public void run() {
            System.out.println("ID " + id + " listener ready to listen.");
            //comm.print();
            int word = comm.listen();
            System.out.println("ID " + id + " listener gets word " + word + ".");
            //comm.print();
        }

        private Communicator comm;
        private int id;
    }
    
    /**
     * Speaker class for testing.
     */
    private static class Speaker implements Runnable {
        Speaker(Communicator comm, int id) {
            this.comm = comm;
            this.id = id;
        }

        public void run() {
            System.out.println("ID " + id + " speaker ready to speak.");
            //comm.print();
            comm.speak(id);
            System.out.println("ID " + id + " speaker speaks.");
            //comm.print();
        }

        private Communicator comm;
        private int id;
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
        System.out.println("Communicator Test:");
        int num = 10;
        int maxWait = 20;
        ArrayList<Integer> people = new ArrayList<Integer>();
        for (int i = 0; i < num; i ++) {
            people.add(0);
            people.add(1);
        }
        Collections.shuffle(people);
        Communicator comm = new Communicator();
        ArrayList<KThread> ths = new ArrayList<KThread>();
        KThread thread;
        Random rand = new Random(666666666666666l);
        for (int i = 0; i < num * 2; i ++) {
            if (people.get(i) == 0)
                thread = new KThread(new Listener(comm, i)).setName("listener"+i);
            else
                thread = new KThread(new Speaker(comm, i)).setName("speaker"+i);
            thread.fork();
            ths.add(thread);
            //ThreadedKernel.alarm.waitUntil(rand.nextInt(maxWait));
            //ThreadedKernel.alarm.waitUntil(maxWait);
        }
        for (int i = 0; i < num * 2; i ++)
            ths.get(i).join();
        System.out.println("Communicator Test Passed.");
    }


    private Lock lock;
    private Condition2 condListen;
    private Condition2 condSpeak;
    private int message;
    private int countListen;
    private int countSpeak;
    private boolean ready;
}
