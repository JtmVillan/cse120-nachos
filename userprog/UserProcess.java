package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed. 
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

        // begin
        fileTable = new OpenFile[16];
        fileTable[0] = UserKernel.console.openForReading();
        fileTable[1] = UserKernel.console.openForWriting();
        // end
		UserKernel.PIDlock.acquire();
			processID = UserKernel.PID;
			UserKernel.PID++;
			UserKernel.numProcess++;
		UserKernel.PIDlock.release();

	//	this.childrenID = new HashSet<Integer>();
		this.childStat = new HashMap<>();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) {
			return false;
		}

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// // OG Implementation
		// Lib.assertTrue(offset >= 0 && length >= 0
		// 		&& offset + length <= data.length);

		// byte[] memory = Machine.processor().getMemory();

		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);

		// return amount;

       	// Our Implementation
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();

        if (vaddr < 0 || vaddr >= memory.length ) {
        	return 0;
        }
        
        // Implement translation from virtual pages to physical pages
        int firstVPN= Processor.pageFromAddress(vaddr);
		// System.out.println("firstVPN is " + firstVPN);

        int lastVPN= Processor.pageFromAddress(vaddr + length);
		// System.out.println("lastVPN is " + lastVPN);

		// int vaddrOffset = Machine.processor().offsetFromAddress(vaddr);

        int bytesTransferred = 0;
        int currVPN = 0;

        for(currVPN = firstVPN; currVPN <= lastVPN; currVPN++) {
			// Case: invalid page (TranslationEntry), once reached invalid, stop reading
			if (!pageTable[currVPN].valid) {
				break;
			}

			// System.out.println("currVPN is " + currVPN);
			// beginning of the current page's virutal address in the page in pageTable[i]
			int beginVaddr = Machine.processor().makeAddress(currVPN, 0);
			// System.out.println("beginVaddr is " + beginVaddr);

			// end of the current page's virutal address in the page in pageTable[i]
			int endVaddr = Machine.processor().makeAddress(currVPN, pageSize-1);
			// System.out.println("endVaddr is " + endVaddr);

			// User to calculate the bytes transferred in a single page
			int numBytesBegin = 0;
			int numBytesEnd = 0;

			// case: normal case where we are translating the entire page
			if (vaddr <= beginVaddr && endVaddr <= vaddr+length) {
				// System.out.println ("---normal case begin---");
				numBytesBegin = 0;
				// System.out.println("numBytesBegin is " + numBytesBegin);
				numBytesEnd = pageSize;
				// System.out.println("numBytesEnd is " + numBytesEnd);

				// System.out.println("---normal case end---");
			}

			// case: the virtual page is the first one to be transferred
			// the 2nd conditional (vaddr+length >= endVaddr) is where the amount to read goes beyond the current page
			else if (vaddr > beginVaddr && vaddr+length >= endVaddr) {
				// System.out.println("---boundary case 1 begin---");
				numBytesBegin = vaddr - beginVaddr;
				// System.out.println("numBytesBegin is " + numBytesBegin);

				numBytesEnd = pageSize;
				// System.out.println("numBytesEnd is " + numBytesEnd);

				// System.out.println("---boundary case 1 end---");
			}

			// case: the virtual page is the last one to be transferred
			// the 1st conditional (vaddr <= beginVaddr) is where we began reading before this page's iteration
			else if (vaddr <= beginVaddr && vaddr+length < endVaddr) {
				// System.out.println("---boundary case 2 begin---");

				numBytesBegin = 0;
				// System.out.println("numBytesBegin is " + numBytesBegin);

				numBytesEnd = (vaddr + length) - beginVaddr;
				// System.out.println("numBytesEnd is " + numBytesEnd);

				// System.out.println("---boundary case 2 end---");
			}

			// case: only need inner chunk of a virtual page 
			// i.e. (vaddr > beginVaddr && vaddr+length < endVaddr)
			else { 
				// System.out.println("---Special case begin---");
				numBytesBegin = vaddr - beginVaddr;
				// System.out.println("numBytesBegin is " + numBytesBegin);
				numBytesEnd = (vaddr + length) - beginVaddr;
				// System.out.println("numBytesEnd is " + numBytesEnd);
				// System.out.println("---Special case end---");
			}

			// map the virtual address to the physical address
			int physAddr = Machine.processor().makeAddress(pageTable[currVPN].ppn, numBytesBegin);
			// System.out.println("physAddr is " + physAddr);

			// the amount of bytes read on the page
			int bytesPerPage = numBytesEnd - numBytesBegin;
			// System.out.println("bytesPerPage " + bytesPerPage);

			System.arraycopy(memory, physAddr, data, offset+bytesTransferred, bytesPerPage);

			// update total number of bytes transferred
			bytesTransferred += bytesPerPage;

			// Need to set true every time a page is read or written
			pageTable[currVPN].used = true;
		}	
		// System.out.println("bytesTransferred is " + bytesTransferred);
		return bytesTransferred;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// Lib.assertTrue(offset >= 0 && length >= 0
		// 		&& offset + length <= data.length);

		// byte[] memory = Machine.processor().getMemory();

		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(data, offset, memory, vaddr, amount);

		// return amount;

		// Our implementation
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
        
        byte[] memory = Machine.processor().getMemory();

        if (vaddr < 0 || vaddr >= memory.length ) {
         return 0;
        }

        // Implement translation from virtual pages to physical pages
        int firstVPN= Processor.pageFromAddress(vaddr);
        int lastVPN= Processor.pageFromAddress(vaddr + length);
        int bytesTransferred = 0;
        int currVPN = 0;

        for(currVPN = firstVPN; currVPN <= lastVPN; currVPN++) {
			// Case: invalid page (TranslationEntry), once reached invalid, stop reading
			if (!pageTable[currVPN].valid) {
				break;
			}
			// we're going through the entire page, so the offeset should be the pageSize-1
			// Note: pageSize is constant for every page in pageTable[] - 0x400 bytes

			// beginning of the current page's virutal address in the page in pageTable[i]
			int beginVaddr = Machine.processor().makeAddress(currVPN, 0);

			// end of the current page's virutal address in the page in pageTable[i]
			int endVaddr = Machine.processor().makeAddress(currVPN, pageSize-1);

			// User to calculate the bytes transferred in a single page
			int numBytesBegin = 0;
			int numBytesEnd = 0;

			// case: normal case where we are translating the entire page
			if (vaddr <= beginVaddr && endVaddr <= vaddr+length) {
				numBytesBegin = 0;
				numBytesEnd = pageSize;
			}

			// case: the virtual page is the first one to be transferred
			// the 2nd conditional (vaddr+length >= endVaddr) is where the amount to read goes beyond the current page
			else if (vaddr > beginVaddr && vaddr+length >= endVaddr) {
				numBytesBegin = vaddr - beginVaddr;
				numBytesEnd = pageSize ;
			}

			// case: the virtual page is the last one to be transferred
			// the 1st conditional (vaddr <= beginVaddr) is where we began reading before this page's iteration
			else if (vaddr <= beginVaddr && vaddr+length < endVaddr) {
				numBytesBegin = 0;
				numBytesEnd = (vaddr + length) - beginVaddr;
			}

			// case: only need inner chunk of a virtual page 
			// i.e. (vaddr > beginVaddr && vaddr+length < endVaddr)
			else { 
				numBytesBegin = vaddr - beginVaddr;
				numBytesEnd = (vaddr + length) - beginVaddr;
			}

			// map the virtual address to the physical address
			int physAddr = Machine.processor().makeAddress(pageTable[currVPN].ppn, numBytesBegin);

			// the amount of bytes read on the page
			int bytesPerPage = numBytesEnd - numBytesBegin;

			System.arraycopy(data, offset+bytesTransferred, memory, physAddr, bytesPerPage);


			// update total number of bytes transferred
			bytesTransferred += bytesPerPage;

			// Need to set true every time a page is read or written
			pageTable[currVPN].used = true;
			pageTable[currVPN].dirty = true;
			// System.out.println("succ wrote a line");
        }
        // System.out.println("succ writeVM");
        return bytesTransferred;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		System.out.println("executable is "+ executable);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			System.out.println("open failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			System.out.println("coff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				System.out.println("fragemnted executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			System.out.println("arguments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {

		// // OG Implementation
		// if (numPages > Machine.processor().getNumPhysPages()) {
		// 	coff.close();
		// 	Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// 	return false;
		// }

		// // load sections
		// for (int s = 0; s < coff.getNumSections(); s++) {
		// 	CoffSection section = coff.getSection(s);

		// 	Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		// 			+ " section (" + section.getLength() + " pages)");

		// 	for (int i = 0; i < section.getLength(); i++) {
		// 		int vpn = section.getFirstVPN() + i;

		// 		// for now, just assume virtual addresses=physical addresses
		// 		section.loadPage(i, vpn);
		// 	}
		// }

		// return true;


		if (numPages > Machine.processor().getNumPhysPages()) {
         coff.close();
         Lib.debug(dbgProcess, "\tinsufficient physical memory");
         return false;
        }

        // begin: added for Task 2, 2nd bullet - Jared

        UserKernel.lock.acquire();

        // allocate physical pages in memory from availPhysPage into pageTable (virtual page table)
        pageTable = new TranslationEntry[numPages];

        for (int vpn = 0; vpn < numPages; vpn++) {
         // removes page in physcial page table and places into virtual page table
         int availPhysPage = UserKernel.availPhysPages.poll();
         pageTable[vpn] = new TranslationEntry(vpn, availPhysPage, true, false, false, false);
        }
        UserKernel.lock.release();
        // end: 

        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
         CoffSection section = coff.getSection(s);

         Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                 + " section (" + section.getLength() + " pages)");

         for (int i = 0; i < section.getLength(); i++) {
             int vpn = section.getFirstVPN() + i;

             // for now, just assume virtual addresses=physical addresses -> fixed?
             section.loadPage(i, pageTable[vpn].ppn);
         }
        }

        return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
        UserKernel.lock.acquire();
        
        // deallocatep physical pages
        for (int vpn = 0; vpn < numPages; vpn++) {
         UserKernel.availPhysPages.add(pageTable[vpn].ppn);
        }

        UserKernel.lock.release();

        // need to close the files in the fd table
        for (int i = 0; i < 16; i++) {
         if (fileTable[i] != null) {
             fileTable[i].close();
         }
        }    
        coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	// for Task 3
	// UserProcess
	// children: a data structure that stores child PID
	// status: a data structure that stores the exit state of each children processes
	// parent: a variable to remember the parent

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		for (int i = 0; i < fileTable.length; i++) {
			if (fileTable[i] != null) {
				fileTable[i].close();
			}
		}

		this.unloadSections();

		coff.close();

		if (this.parent != null) {
			this.parent.childStat.put((Integer) this.processID, status);
			// this.parent.thread.finish();
		}

		if (UserKernel.numProcess > 1) {
			UserKernel.PIDlock.acquire();
				UserKernel.numProcess--;
				this.thread.finish();
			UserKernel.PIDlock.release();
		}
		else {
			Kernel.kernel.terminate();
		}
	
		// System.out.println("made it into handleExit()!");

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process
		// System.out.println("before terminate call in handleExit()!");
		// System.out.println("finished handleExit()!");
		return 0;
	}

	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int vaddr, int argc, int argvAddr) {

		// check vaName
        if (vaddr < 0 && vaddr >= Machine.processor().getMemory().length) {
            Lib.debug(dbgProcess, "Invalid virtual address!");
            return -1;
        }

		// check number of arguments being passed on
        if (argc < 0) {
            Lib.debug(dbgProcess, "Invalid number of arguments being passed on!");
            return -1;
        }

		String fileName = readVirtualMemoryString(vaddr, 256);

		// check file from vaddr
        if (fileName == null) {
            Lib.debug(dbgProcess, "Invalid file name from virtual address!");
            return -1;
        }

		// TODO: Need to check that fileName string must include the ".coff" extension
		if (!fileName.endsWith(".coff")){
			Lib.debug(dbgProcess, "Invalid file type!");
            return -1;
		}

		// Read the file name
		// Read the address of arguments (hint: create a local byte[] and use readVirtualMemory)
			byte[] argvArray = new byte[argc * 4];
			readVirtualMemory(argvAddr , argvArray);
	
		String[] arguments = new String[argc];

		for (int i = 0; i < argc; i++) {

			// transfer the address to integer type
			int addr = Lib.bytesToInt(argvArray, i*4); 
			// if argument is invalid
			if (addr < 0) {
				return -1;
			}
			// read and store the arguments
			arguments[i] = readVirtualMemoryString(addr, 256);

			if(arguments[i] == null){
				return -1;
			}
		}

		// Create the new child process and set the parent of the child process to be this process

		// Use lock accordingly (always think about what are shared resources)
		UserProcess newChild = newUserProcess();
		newChild.parent = this;
		
		// execute the child process
		if (newChild.execute(fileName, arguments)) {
	
			// get the child PID
			// put the child process into the data structure
			UserKernel.PIDlock.acquire();
				Integer childID = newChild.processID;
				this.childMap.put(childID, newChild);
			UserKernel.PIDlock.release();
			return childID;
		}
		// UserKernel.PIDlock.acquire();
		// UserKernel.numProcess--;
		// UserKernel.PIDlock.release();
		// return child PID
		return -1;
	}

	 /**
	  * Handle the join() system call
	  */
	private int handleJoin(int childPID, int vaddr) {

		//System.out.println("Made it into handleJoin()!");

		Integer intChild = new Integer(childPID);

		// Check if the children data structure contains child PID.
		if(!this.childMap.containsKey(intChild)){
			return -1;
		}

		childMap.get(intChild).thread.join();

		if (!childStat.containsKey(intChild)) {
			return 0;
		}

		// Disown the child process after join.
		// Remove the child PID from the data structure children.
		this.childMap.remove(intChild);

		Integer state = childStat.get(intChild);
		byte[] buff = Lib.bytesFromInt(state);
		writeVirtualMemory(vaddr, buff);
		return 1;
		
	}

    /**
	 * Handle the exit() system call.
	 */
    private int handleCreate(int vaName) {

        // check vaName
        if (vaName < 0 && vaName >= Machine.processor().getMemory().length) {
            Lib.debug(dbgProcess, "Invalid virtual address!");
            return -1;
        }

        // TA said to do this in the DI
        String fileName = readVirtualMemoryString(vaName, 256);

        // check if file is null
        if (fileName == null) {
            Lib.debug(dbgProcess, "Invalid Filename!");
        }

        // start at i = 2 since first 2 indices are stdin and stdout
        // also, TA said array needs 26 elements in DI
        // checking for free fileDescripter
        int empty = -1;
        for (int i = 2; i < 16; i++) {
            if (fileTable[i] == null) {
                empty = i;
                break;
            }
        }

        // check for free descriptor 
        if (empty == -1) {
            Lib.debug(dbgProcess, "No free file descriptor available!");
            return -1;
        }

        // Referred to load() in UserProcess.java
        // 'true' to make a file if it doesn't exist already
        OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
        if (file == null) {
            Lib.debug(dbgProcess, "Couldn't open file!");
            return -1;
        }
        else {
            fileTable[empty] = file;
            return empty;
        }
    }

    /**
     * Handle the open() system call
     */
    private int handleOpen(int vaName) {
        // check vaName
        if (vaName < 0 && vaName >= Machine.processor().getMemory().length) {
            Lib.debug(dbgProcess, "Invalid virtual address!");
            return -1;
        }

        // TA said to do this in the DI
        String fileName = readVirtualMemoryString(vaName, 256);

        // check if file is null
        if (fileName == null) {
            Lib.debug(dbgProcess, "Invalid Filename!");
        }

        // start at i = 2 since first 2 indices are stdin and stdout
        // also, TA said array needs 26 elements in DI
        // checking for free fileDescripter
        int empty = -1;
        for (int i = 2; i < 16; i++) {
            if (fileTable[i] == null) {
                empty = i;
                break;
            }
        }

        // check for free descriptor 
        if (empty == -1) {
            Lib.debug(dbgProcess, "No free file descriptor available!");
            return -1;
        }

        // Referred to load() in UserProcess.java
        // 'true' to make a file if it doesn't exist already
        OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
        if (file == null) {
            Lib.debug(dbgProcess, "Couldn't open file!");
            return -1;
        }
        else {
            fileTable[empty] = file;
            return empty;
        }
    }

	/**
	 * Handle the read() system call
	 */
	private int handleRead(int fileDescripter, int vaBuffer, int count) {
		// Error: if file space is out of bounds
		if (fileDescripter < 0 || fileDescripter > 15) {
			Lib.debug(dbgProcess, "Attempting to read a file that doesn't exist, file descriptor out of range");
			return -1;
		}
		
		// get file
		OpenFile useFile = fileTable[fileDescripter];

		// check if this file is null
		if (useFile == null) {
			Lib.debug(dbgProcess, "Attempting to read a NULL fule");
			return -1;
		}

		// Error: invalid count used to make buffer
		if (count < 0 || count >= Machine.processor().getMemory().length) {
			Lib.debug(dbgProcess, "writing with an invalid count");
			return -1;
		}

		// Error: invalid buffer address 
		if (vaBuffer < 0 || vaBuffer >= Machine.processor().getMemory().length) {
			Lib.debug(dbgProcess, "writing with an invalid buffer");
			return -1;
		}

		// new buffer we are writing to
		byte[] byteBuf = new byte[count];

		// read from file and put into buffer
		int bytesRead = useFile.read(byteBuf, 0, count);

		// Error: error while reading
		if (bytesRead == -1) {
			Lib.debug(dbgProcess, "Error while reading");
			return -1;
		}

		// Write from buffer to virtual address sace
		int bytesWritten = writeVirtualMemory(vaBuffer, byteBuf, 0 , bytesRead);

		return bytesWritten;
	}

	/**
	 * Handle the write() system call
	 */
	private int handleWrite(int fileDescripter, int vaBuffer, int count) {
		// Error: if file space is out of bounds
		if (fileDescripter < 0 || fileDescripter > 15) {
			Lib.debug(dbgProcess, "Attempting to read a file that doesn't exist, file descriptor out of range");
			return -1;
		}

		// get file
		OpenFile useFile = fileTable[fileDescripter];

		// check if this file is null
		if (useFile == null) {
			Lib.debug(dbgProcess, "Attempting to read a NULL file");
			return -1;
		}

		// Error: invalid count used to make buffer
		if (count < 0 || count >= Machine.processor().getMemory().length) {
			Lib.debug(dbgProcess, "writing with an invalid count");
			return -1;
		}

		// Error: invalid buffer address 
		if (vaBuffer < 0 || vaBuffer >= Machine.processor().getMemory().length) {
			Lib.debug(dbgProcess, "writing with an invalid buffer");
			return -1;
		}

		byte[] byteBuf = new byte[count];

		int bytesWritten = readVirtualMemory(vaBuffer, byteBuf, 0, count);

		int bytesRead = useFile.write(byteBuf, 0, bytesWritten);

		if (bytesRead == -1) {
			Lib.debug(dbgProcess, "Error while reading");
			return -1;
		}

		return bytesRead;
	}

	/**
	 * Handle the close() system call
	 */
	private int handleClose(int fileDescripter) {
		// Error: if file space is out of bounds
		if (fileDescripter < 0 || fileDescripter > 15) {
			Lib.debug(dbgProcess, "Attempting to read a file that doesn't exist, file descriptor out of range");
			return -1;
		}

		fileTable[fileDescripter].close();
		fileTable[fileDescripter] = null;

		return 0;
	}

	/**
	 * Handle the unlink() system call
	 */
	private int handleUnlink(int name) {
		String fileName = readVirtualMemoryString(name, 256);

		if (ThreadedKernel.fileSystem.remove(fileName)) {
			return 0;
		}

		return -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);


		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	public TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
    protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

    // added filetable
    protected OpenFile[] fileTable;

	protected int processID;

	private Map<Integer, Integer> childStat;

	protected UserProcess parent;
	protected int status;

	protected static Map<Integer, UserProcess> childMap = new HashMap<>();

}