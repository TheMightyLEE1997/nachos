package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;

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
        messageQueue = new LinkedList<Integer>();
        countListen = 0;
        countSpeak = 0;
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
        if (countListen > 0) {
            Lib.assertTrue(countSpeak == 0);
            messageQueue.add((Integer)word);
            condListen.wake();
            countListen --;
        }
        else {
            countSpeak ++;
            condSpeak.sleep();
            messageQueue.add((Integer)word);
            condListen.wake();
        }
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
        int ret = 0;
        if (countSpeak > 0) {
            Lib.assertTrue(countListen == 0);
            condSpeak.wake();
            countSpeak --;
        }
        else {
            countListen ++;
        }
        condListen.sleep();
        ret = (int)messageQueue.removeFirst();
        lock.release();
        return ret;
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
            int word = comm.listen();
            System.out.println("ID " + id + " listener gets word " + word + ".");
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
            comm.speak(id);
            System.out.println("ID " + id + " speaker speaks.");
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
        //Random rand = new Random(666666666666666l);
        for (int i = 0; i < num * 2; i ++) {
            if (people.get(i) == 0)
                thread = new KThread(new Listener(comm, i)).setName("listener"+i);
            else
                thread = new KThread(new Speaker(comm, i)).setName("speaker"+i);
            thread.fork();
            ths.add(thread);
            //ThreadedKernel.alarm.waitUntil(rand.nextInt(maxWait));
            ThreadedKernel.alarm.waitUntil(maxWait);
        }
        for (int i = 0; i < num * 2; i ++)
            ths.get(i).join();
        System.out.println("Communicator Test Passed.");
    }


    private Lock lock;
    private Condition2 condListen;
    private Condition2 condSpeak;
    private LinkedList<Integer> messageQueue;
    private int countListen;
    private int countSpeak;
}
