package nachos.threads;

import java.util.*;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat
{
    static BoatGrader bg;
    
    public static void selfTest()
    {
	    BoatGrader b = new BoatGrader();
	
	    System.out.println("\n ***Testing Boats with only 2 children***");
	    begin(0, 2, b);

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
        begin(3, 3, b);

        System.out.println("\n ***Testing Boats with only 3 children***");
	    begin(0, 3, b);

        System.out.println("\n ***Testing Boats with 2 children, 30 adults***");
        begin(30, 2, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	    // Store the externally generated autograder in a class
	    // variable to be accessible by children.
	    bg = b;

        // Instantiate global variables here

        lock = new Lock();

        islandOahu = new Island();
        adultRowCond = new Condition(lock);
        seeTwoChildrenCond = new Condition(lock);
        childGrabBoatCond = new Condition(lock);
        childRowCond = new Condition(lock);
        childRideCond = new Condition(lock);

        islandMolokai = new Island();
        childBackCond = new Condition(lock);

        exitCond = new Condition(lock);

        islandOahu.nBoatSeats = 2;
        
        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

        for (int i = 0; i < adults; i++) {
            new KThread(new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            }).fork();
        }
        for (int i = 0; i < children; i++) {
            new KThread(new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            }).fork();
        }

        lock.acquire();
        if (!islandMolokai.exiting) {
            exitCond.sleep();
        }
        Lib.assertTrue(adults == islandMolokai.getNAdults());
        Lib.assertTrue(children == islandMolokai.getNChildren());
        lock.release();
    }

    static void AdultItinerary()
    {
        bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE. 

        Lock currentLock = lock;
        currentLock.acquire();

        Adult me = new Adult();
        me.currentIsland = islandOahu;
        islandOahu.addIdividual(me);

        me.itinerary();

        currentLock.release();
    }

    static void ChildItinerary()
    {
        bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE. 

        Lock currentLock = lock;
        currentLock.acquire();

        Child me = new Child();
        me.currentIsland = islandOahu;
        islandOahu.addIdividual(me);

        me.itinerary();

        currentLock.release();
    }

    static void SampleItinerary()
    {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }

    static class Island {
        private int nAdults = 0;
        private int nChildren = 0;
        private Set<Idividual> people = new HashSet<>();
        public int nBoatSeats = 0;
        public boolean ridingPassager = false;
        public boolean exiting = false;

        public int getNAdults() {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            return nAdults;
        }
        public int getNChildren() {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            return nChildren;
        }
        public void addIdividual(Idividual i) {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            if (i instanceof Adult) {
                nAdults++;
            } else {
                Lib.assertTrue(i instanceof Child);
                nChildren++;
            }
            people.add(i);
        }
        public void removeIdividual(Idividual i) {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            if (i instanceof Adult) {
                nAdults--;
            } else {
                Lib.assertTrue(i instanceof Child);
                nChildren--;
            }
            boolean success = people.remove(i);
            Lib.assertTrue(success);
        }
    }

    static abstract class Idividual {
        public Island currentIsland;

        protected void move() {
            currentIsland.removeIdividual(this);
            currentIsland = currentIsland == islandOahu ? islandMolokai : islandOahu;
            currentIsland.addIdividual(this);
        }

        public abstract void row();
        public abstract void ride();
        public abstract void itinerary();

        protected boolean checkAdultRowCond() {
            Lib.assertTrue(currentIsland == islandOahu);
            if (currentIsland.ridingPassager || currentIsland.nBoatSeats == 0) {
                return false;
            }
            return currentIsland.getNChildren() == 1;
        }
        protected boolean checkChildGrabBoatCond() {
            Lib.assertTrue(currentIsland == islandOahu);
            if (currentIsland.ridingPassager || currentIsland.nBoatSeats == 0) {
                return false;
            }
            return currentIsland.getNChildren() >= 2;
        }
    }

    static class Adult extends Idividual {
        public void row() {
            if (currentIsland == islandOahu) {
                bg.AdultRowToMolokai();
            } else {
                Lib.assertTrue(currentIsland == islandMolokai);
                bg.AdultRowToOahu();
            }
            currentIsland.nBoatSeats = 0;
            move();
            currentIsland.nBoatSeats = 2;
        }
        public void ride() {
            Lib.assertNotReached();
        }

        public void itinerary() {
            while (true) {
                while (!checkAdultRowCond()) {
                    adultRowCond.sleep();
                }
                
                row();

                if (currentIsland.getNChildren() == 0) {
                    row();
                    seeTwoChildrenCond.sleep();
                } else {
                    childBackCond.wake();
                    return;
                }
            }
        }
    }

    static class Child extends Idividual {
        public void row() {
            if (currentIsland == islandOahu) {
                bg.ChildRowToMolokai();
            } else {
                Lib.assertTrue(currentIsland == islandMolokai);
                bg.ChildRowToOahu();
            }
            currentIsland.nBoatSeats = 0;
            move();
            currentIsland.nBoatSeats = 2;
        }
        public void ride() {
            if (currentIsland == islandOahu) {
                bg.ChildRideToMolokai();
            } else {
                Lib.assertTrue(currentIsland == islandMolokai);
                bg.ChildRideToOahu();
            }
            move();
        }

        public void itinerary() {
            while (true) {
                if (currentIsland.getNChildren() >= 2) {
                    seeTwoChildrenCond.wakeAll();
                }
                if (checkAdultRowCond()) {
                    adultRowCond.wake();
                }

                while (!checkChildGrabBoatCond()) {
                    childGrabBoatCond.sleep();
                }
    
                if (currentIsland.nBoatSeats == 2) { // 2 seats, row
                    currentIsland.nBoatSeats--;
                    childGrabBoatCond.wake();
                    childRowCond.sleep();
                    currentIsland.ridingPassager = true;
                    row();
                    childRideCond.wake();

                    childBackCond.sleep();
                    if (currentIsland.exiting) {
                        return;
                    }
                    row();
                } else { // 1 seat, ride
                    currentIsland.nBoatSeats--;
                    
                    boolean isLast = currentIsland.getNChildren() == 2 && currentIsland.getNAdults() == 0;
                    
                    if (isLast) {
                    	lock.release();
                        ThreadedKernel.alarm.waitUntil(WAIT_TIME);
                        lock.acquire();
                        isLast = currentIsland.getNChildren() == 2 && currentIsland.getNAdults() == 0;
                    }
                    
                    childRowCond.wake();
                    childRideCond.sleep();
                    currentIsland.ridingPassager = false;
                    ride();

                    if (isLast) {
                        currentIsland.exiting = true;
                        exitCond.wake();
                        childBackCond.wakeAll();
                        return;
                    }
                    row();
                }
            }
        }
    }
    
    static final long WAIT_TIME = 5000;
    
    static Lock lock;

    // Island Oahu
    static Island islandOahu;
    static Condition adultRowCond;
    static Condition seeTwoChildrenCond;
    static Condition childGrabBoatCond;
    static Condition childRowCond;
    static Condition childRideCond;

    // Island Molokai
    static Island islandMolokai;
    static Condition childBackCond;

    // one-way communication to the main thread
    static Condition exitCond;
}
