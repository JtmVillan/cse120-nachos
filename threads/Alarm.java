package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	PriorityQueue<ThreadInQueue> waitingQueue;

	public class ThreadInQueue{

		// Instance Vairable
		public KThread kthread;
		public long threadTime;
		
		// Constructor
		ThreadInQueue(long threadTime, KThread kthread) {
			this.threadTime = threadTime;
			this.kthread = kthread;
		}
		
		// Get KThread's time
		public long getTime() {
			return threadTime;
		}

		// Get KThread object
		public KThread getKThread() {
			return kthread;
		}
	} 

	public class ThreadComparator implements Comparator<ThreadInQueue>{

		public int compare(ThreadInQueue tiq1, ThreadInQueue tiq2) {
			if (tiq1.getTime() < tiq2.getTime()) {
				return -1;
			}
			else if (tiq1.getTime() > tiq2.getTime()) {
				return 1;
			}
			else {
				return 0;
				//return this.kthread.compareTo(tiq.getKThread());
			}
		}
	}

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {

		waitingQueue = new PriorityQueue<ThreadInQueue>(100, new ThreadComparator());
		// Compare time of KThread to other KThread
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});

	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		
		KThread.currentThread().yield();

		long currentTime = Machine.timer().getTime();
	 	long queueSize = waitingQueue.size();

		if (!waitingQueue.isEmpty()) {
			// Checks more than 1 thread
			for (int i = 0; i < queueSize; i++) {

				ThreadInQueue tiq = waitingQueue.peek();
				// Checks if threads need to wake up
				if (currentTime >= tiq.getTime()) {
					// Machine.interrupt().disable();
					tiq.getKThread().ready();
					waitingQueue.poll();
					// Machine.interrupt().enable();
				}
				else {
					// Machine.interrupt().enable();
					break;
				}
			}
		}
		// Machine.interrupt().enable();
		// KThread.currentThread().yield();

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {

		// if return when 0 or negative
		if(x <= 0){
			return;
		}

		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;

		ThreadInQueue tiq = new ThreadInQueue(wakeTime, KThread.currentThread());
		//while (wakeTime > Machine.timer().getTime()) {
			// TODO: check if exceeded max clock time
			waitingQueue.add(tiq);
			Machine.interrupt().disable();
			KThread.sleep();
			// Machine.interrupt().enable();
		//}
	}

    /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
		// Machine.interrupt().disable()

		Iterator<ThreadInQueue> queueItr = waitingQueue.iterator();
		while(queueItr.hasNext()) {
			
			ThreadInQueue tiq = queueItr.next();
			// Check if same thread
			if (tiq.getKThread() == thread) {
				Machine.interrupt().disable();
				waitingQueue.remove(tiq);
				
				// tiq.getKThread().ready();
				// thread.ready();

				Machine.interrupt().enable();
				return true;
			}
			
		}
		return false;
	}


	// Implement more test methods here ...
    public static void alarmTest1() {
		System.out.println("------------- alarmTest1 -------------");
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
		for (int d : durations) {
   			t0 = Machine.timer().getTime();
    		ThreadedKernel.alarm.waitUntil (d);
    		t1 = Machine.timer().getTime();
    		System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }
	public static void alarmTest2() {
		System.out.println("------------- alarmTest2 -------------");
		int durations[] = {0};
		long t0, t1;
		for (int d : durations) {
   			t0 = Machine.timer().getTime();
    		ThreadedKernel.alarm.waitUntil (d);
    		t1 = Machine.timer().getTime();
    		System.out.println ("alarmTest2: waited for " + (t1 - t0) + " ticks");
		}
	}
	public static void alarmTest3() {
		System.out.println("------------- alarmTest3 -------------");
		KThread list[] = new KThread[7];
		int diff = 0;
		int base_dur = 5000;

		for (KThread i : list) {
			i = new KThread( new Runnable () {
				public void run() {
					long t0, t1;
					t0 = Machine.timer().getTime();
					ThreadedKernel.alarm.waitUntil(100);
					System.out.println("enter");
					t1 = Machine.timer().getTime();
					System.out.println("alarmTest3: wake up after " + (t1 - t0) + " ticks");
				}
			});
			i.fork();

			diff ++;
		}
		// Extend main thread
		ThreadedKernel.alarm.waitUntil(1000000);
		System.out.println("----------------------------");
	}
    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
		// Invoke your other test methods here ...
		alarmTest1();
		alarmTest2(); 
		alarmTest3();
    }

}
