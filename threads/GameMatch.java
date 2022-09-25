package nachos.threads;

import nachos.machine.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    /* Store number of players */
    public int numRequired;

    public int begCounter = 0;
    public int interCounter = 0;
    public int expCounter = 0;
    public static int gameHistory = 0;

    /* Store the condition, lock, and game history */
    private Condition begQueue = null;
    private Condition interQueue = null;
    private Condition expQueue = null;
    private Lock begLock = null;
    private Lock interLock = null;
    private Lock expLock = null;
    private Lock gameHistLock = null;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        this.begLock = new Lock();
        this.interLock = new Lock();
        this.expLock = new Lock();
        this.gameHistLock = new Lock();

        this.begQueue = new Condition(begLock);
        this.interQueue = new Condition(interLock);
        this.expQueue = new Condition(expLock);

        this.numRequired = numPlayersInMatch;
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {

        // Check if conditions are about to be met
        if (ability == abilityBeginner && begCounter == (numRequired - 1)) {
            begCounter = 0;

            gameHistLock.acquire();
            gameHistory++;
            gameHistLock.release();

            begLock.acquire();
            begQueue.wakeAll();
            begLock.release();
            
            return gameHistory;
        }
        if (ability == abilityIntermediate && interCounter == (numRequired - 1)) {
            interCounter = 0;

            gameHistLock.acquire();
            gameHistory++;
            gameHistLock.release();

            interLock.acquire();
            interQueue.wakeAll();
            interLock.release();

            return gameHistory;
        }
        if (ability == abilityExpert && expCounter == (numRequired - 1)) {
            expCounter = 0;

            gameHistLock.acquire();
            gameHistory++;
            gameHistLock.release();

            expLock.acquire();
            expQueue.wakeAll();
            expLock.release();

            return gameHistory;
        }
        else if (ability == abilityBeginner) {
            begCounter++;
            begLock.acquire();
            begQueue.sleep();
            begLock.release();

            return gameHistory;
        }
        else if (ability == abilityIntermediate) {
            interCounter++;
            interLock.acquire();
            interQueue.sleep();
            interLock.release();

            return gameHistory;
        }
        else if (ability == abilityExpert) {
            expCounter++;
            expLock.acquire();
            expQueue.sleep();
            expLock.release();

            return gameHistory;
        }

	    return -1;

    }

    // Place GameMatch test code inside of the GameMatch class.
    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);
        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg1.setName("B1");
        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg2.setName("B2");
        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
        });
        int1.setName("I1");
        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }
        });
        exp1.setName("E1");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
    }
    public static void matchTest3 () {
        final GameMatch match = new GameMatch(2);
        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg1.setName("B1");
        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg2.setName("B2");
        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
        });
        int1.setName("I1");
        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                for (int i = 0; i < 10; i++) {
                      KThread.currentThread().yield();
                }

                System.out.println ("exp1 matched");
                Lib.assertTrue(r == 2, "expected match number of 2");
            }
        });
        exp1.setName("E1");
        KThread exp2 = new KThread( new Runnable () {
            public void run() {
                for (int i = 0; i < 10; i++) {
                  KThread.currentThread().yield();
                 }
                int r = match.play(GameMatch.abilityExpert);
                System.out.println("exp2 matched");
                Lib.assertTrue(r == 2, "expected match number of 2");
            }
        });
        exp2.setName("E2");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
        exp2.fork();
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 40; i++) {
            KThread.currentThread().yield();
        }
    }
        
    public static void selfTest() {
        // matchTest4();
        matchTest3();
    }
}
