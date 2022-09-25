package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapCreate();

	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// frames = Inverted Page Table (IVT)
	// IVT maps ppns (in order) to the process they belong to
	// ppnToVPN maps ppns to the vpns of the process they belong to
	public static int clockAlgo(VMProcess[] frames) {
		while (frames[victim].pageTable[ppnToVPN[victim]].used == true) {
			frames[victim].pageTable[ppnToVPN[victim]].used = false; // unset used bit and move ahead
			victim = (victim + 1) % frames.length;
		}
		int toEvict = victim;
		victim = (victim + 1) % frames.length; // move to next so that the next
		// run of clock algo starts from the nexrt position in the clock cycle
		return toEvict;
	}
	
	/**
	 * create a swap file
	 */
	public static void swapCreate() {
		// initialize list of swap pages
		freeSwapPages = new LinkedList<Integer>();

		// allocate swap pages
		for (int i = 0; i < freeSwapPageCount; i++) {
			freeSwapPages.add(i);
		}

		// initialze swap file
		swapFile = ThreadedKernel.fileSystem.open(swapName, true);
	}

	/**
	 * write pages from memory -> swapFile (for page out)
	 * must scan through the swap file to check for gap to fill in
	 * @return swap page position
	 */
	public static int swapOut(int swapPPN) {
		// writing from memory to our swapFile
		byte[] memory = Machine.processor().getMemory();

		// spn = swap page number
		int spn = freeSwapPages.poll();

		// writes the data in memory to location in swap file
		swapFile.write(spn*pageSize, memory, swapPPN*pageSize, pageSize);

		return spn;
	}

	/**
	 * read from swap to memory
	 * when swapping in, we need to 0-fill the data that previously occupied the swap space
	 * this will create a gap that can then be re-used later
	 */
	public static void swapIn(int faultSwapPos, int memPos) {
		// writing from memory to our swapFile
		byte[] memory = Machine.processor().getMemory();

		// reads data file swap file into memory
		swapFile.read(faultSwapPos*pageSize, memory, memPos*pageSize, pageSize);

		// zero-filled byte array of size pageSize
		byte[] buf = new byte[pageSize];

		// location in swap file to be zero-filled
		int destPos = faultSwapPos*pageSize;

		// zero-fills the selected swap-space, now a gap is made to be filled later by swapOut()
		System.arraycopy(buf, 0, swapFile, destPos, pageSize);

		// adds swap page to the front of the list to be used later
		freeSwapPages.addFirst(faultSwapPos);
	}
	

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	// added for peoject 3

	// single global swap file across all processes
	public static OpenFile swapFile;

	// name of swap file
	private static String swapName = "swapSpace";

	// global free list 
	public static LinkedList<Integer> freeSwapPages;

	// arbitrary size, but it can grow arbitrarily
	public static int freeSwapPageCount = 100;

	// vicitm physical page to be evicted from memory
	protected static int victim = 0;

	// number of total physical pages in memory
	protected static final int NUMBER_OF_FRAMES = Machine.processor().getNumPhysPages();

	// Inverted Page Table; ppn to process object
	protected static VMProcess[] IVT = new VMProcess[NUMBER_OF_FRAMES];

	// ppn to vpn when swappping
	protected static int[] ppnToVPN = new int[NUMBER_OF_FRAMES];
	
	// ppn to swap file position
	protected static int[] ppnToSwapPos = new int[NUMBER_OF_FRAMES];

	// size of frame (physical page) in  physical memory
	private static final int pageSize = Processor.pageSize;

	// keep track of pinned pages
	protected static Map<Integer, Boolean> pinnedPages = new HashMap<>();
}
