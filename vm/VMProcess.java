package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	// Note: swap4/swap5 do not use rVM/wVM
	/**
	 * TODO: Modify read/writeVM for page pinning using VMKernel.pinnedPages hashMap
	 */
	/**
	 * Update to handle invalid pages and page faults
	 * directly access physical memory to read data between user and kernel
	 * now need to check to see if the virtual page is valid
     * if valid, can use the physical page same as before
	 * if invalid, then will need to fault the page in as with any other page fault
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// get vpn of vaddr
		int vpn = Machine.processor().pageFromAddress(vaddr);

		// get translation entry of vpn
		TranslationEntry tEntry = pageTable[vpn];

		// check if the entry is null
		if (tEntry == null) {
			Lib.debug(dbgVM, "Failed finding entry in pageTable!");
			return -1;
		}

		// check if entry is valid
		if (tEntry.valid == false) {
			// fault the page
			handlePageFault(vaddr);
		}
		// if page is valid, can use the physical page as before
		return super.readVirtualMemory(vaddr, data, offset, length);
	}

	/**
	 * Update to handle invalid pages and page faults
	 * directly access physical memory to write data between user and kernel
	 * now need to check to see if the virtual page is valid
     * if valid, can use the physical page same as before
	 * if invalid, then will need to fault the page in as with any other page fault
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// get vpn of vaddr
		int vpn = Machine.processor().pageFromAddress(vaddr);

		// get translation entry of vpn
		TranslationEntry tEntry = pageTable[vpn];

		// check if the entry is null
		if (tEntry == null) {
			Lib.debug(dbgVM, "Failed finding entry in pageTable!");
			return -1;
		}

		// check if entry is valid
		if (tEntry.valid == false) {
			// fault the page
			handlePageFault(vaddr);
		}
		// if page is valid, can use the physical page as before
		return super.writeVirtualMemory(vaddr, data, offset, length);
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		// return an error if there are not enough free pages to 
		// hold the process' new address space
		if (numPages > Machine.processor().getNumPhysPages()) {
         	coff.close();
         	Lib.debug(dbgProcess, "\tinsufficient physical memory");
         	return false;
        }

		// initialize pageTable for this process
        pageTable = new TranslationEntry[numPages];

		// initialize all of the TEs as invalid (i.e. set valid bit to false)
        for (int i = 0; i < pageTable.length; i++) {
			// mark all TranslationEntries as invalid on initialization
        	pageTable[i] = new TranslationEntry(i, i, false, false, false, false);			
		}

        return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
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
		// Handle page fault exceptions
		case Processor.exceptionPageFault:
			Lib.debug(dbgProcess, "Pagefault in handleException!");
			
			int faultAddr = processor.readRegister(Processor.regBadVAddr);
			handlePageFault(faultAddr);
			break;

		default:
			super.handleException(cause);
			break;
		}
	}

	public void handlePageFault(int faultVaddr) {
		// get faulting VPN from faulting address
		int faultVPN = Processor.pageFromAddress(faultVaddr);

		// get faulting TranslationEntry from faulting VPN
		TranslationEntry faultEntry = pageTable[faultVPN];
		
		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return;
			}
			numPages += section.getLength();
		}

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;

		// and finally reserve 1 page for arguments
		numPages++;

		// iterate through the coff sections 
		for (int s = 0; s < coff.getNumSections(); s++) {
        	CoffSection section = coff.getSection(s);

			// iterate through the section
        	for (int i = 0; i < section.getLength(); i++) {
            	int vpn = section.getFirstVPN() + i;

				int physPage = 0;

				// faulting address is in a swap file
				if (faultEntry.dirty == true) {
					// have available phys pages
					if (UserKernel.availPhysPages.size() != 0) {
						// to allocate a physical page for the virtual page
						physPage = UserKernel.availPhysPages.poll();
						// load the appropriate swap page
						VMKernel.swapIn(VMKernel.ppnToSwapPos[faultEntry.ppn], physPage);
						faultEntry.ppn = physPage;
						faultEntry.used = true;
						// update IVT whenever we allocate a physical page
						VMKernel.IVT[faultEntry.ppn] = this;
						// update ppnToVPN whenever we allocate a physical page
						VMKernel.ppnToVPN[faultEntry.ppn] = vpn;
						
					}
					// no free memory, need to evict a page, i.e. physical memory is full					
					else {
						// select a victim page to evict from memory
						int victimPPN = VMKernel.clockAlgo(VMKernel.IVT);
						// VMKernel.IVT[victimPPN] -> Process of victimPPN
						// ppnToVPN -> vpn of the ppn
						// VMKernel.IVT[victimPPN].pageTable[ppnToVPN[victimPPN]] -> translationEntry of the victimPPN
						if (VMKernel.IVT[victimPPN].pageTable[VMKernel.ppnToVPN[victimPPN]].dirty == true) {
							// kernel must save the page contents in the swap file on disk
							// set victimPPN's swap page position
							int faultSwapPos = VMKernel.ppnToSwapPos[faultEntry.ppn];
							VMKernel.ppnToSwapPos[victimPPN] = VMKernel.swapOut(victimPPN);
							VMKernel.swapIn(faultSwapPos, victimPPN);
							faultEntry.ppn = victimPPN;
							faultEntry.used = true;
						}
						VMKernel.IVT[victimPPN].pageTable[VMKernel.ppnToVPN[victimPPN]].valid = false;
					}
					// once paged in the faulted page, mark the TranslationEntry as valid
					faultEntry.valid = true;
				}
				// faulting address is in the COFF file
				else {
					// check if faulting address is in stack section
					if ((numPages - 1 - stackPages < vpn) && (vpn < numPages - 1)) {
						if (vpn == faultVPN) {
							// 'memory' is a reference to main memory array
							byte[] memory = Machine.processor().getMemory();
							byte[] buf = new byte[pageSize];
							int destPos = faultEntry.ppn*pageSize;
							// zero-fill if not code/data (i.e. stack)
							System.arraycopy(buf, 0, memory, destPos, pageSize);
							// allocate a physical page for the virtual page
							physPage = UserKernel.availPhysPages.poll();
							faultEntry.ppn = physPage;
							faultEntry.used = true;
						}
					}
					
					// check if faulting address is code/data segment
					if (vpn < numPages - 1 - stackPages) {
						if (vpn == faultVPN) {
							// have available phys pages
							if (UserKernel.availPhysPages.size() != 0) {
								physPage = UserKernel.availPhysPages.poll();
								// load the appropriate code/data page
								section.loadPage(i, physPage);
								faultEntry.ppn = physPage;
								faultEntry.used = true;
								// update IVT whenever we allocate a physical page
								VMKernel.IVT[faultEntry.ppn] = this;
								// update ppnToVPN whenever we allocate a physical page
								VMKernel.ppnToVPN[faultEntry.ppn] = vpn;
							}
							// no free memory, need to evict a page, i.e. physical memory is full					
							else {
								// select a victim page to evict from memory
								int victimPPN = VMKernel.clockAlgo(VMKernel.IVT);
								// VMKernel.IVT[victimPPN] -> Process of victimPPN
								// ppnToVPN -> vpn of the ppn
								// VMKernel.IVT[victimPPN].pageTable[ppnToVPN[victimPPN]] -> translationEntry of the victimPPN
								if (VMKernel.IVT[victimPPN].pageTable[VMKernel.ppnToVPN[victimPPN]].dirty == true) {
									// kernel must save the page contents in the swap file on disk
									VMKernel.ppnToSwapPos[victimPPN] = VMKernel.swapOut(victimPPN);
								}
								// the page is clean (i.e. page not dirty), page can be used immdiately
								VMKernel.IVT[victimPPN].pageTable[VMKernel.ppnToVPN[victimPPN]].valid = false;
								section.loadPage(i, victimPPN);
								faultEntry.ppn = victimPPN;
								faultEntry.used = true;
								// update IVT whenever we allocate a physical page
								VMKernel.IVT[faultEntry.ppn] = this;
								// update ppnToVPN whenever we allocate a physical page
								VMKernel.ppnToVPN[faultEntry.ppn] = vpn;
							}
						}
						// once paged in the faulted page, mark the TranslationEntry as valid
						faultEntry.valid = true;
					}
				}
			}
        }
		// // print entire page table at end of pageFault
		// for (int i = 0; i < pageTable.length; i++) {
		// 	// issue: ppn and vpn are still the same, some being properly updated
		// 	System.out.println("pageTable[" + i + "]: " + "vpn = " + pageTable[i].vpn + "; ppn = " + pageTable[i].ppn + 
		// 		"; valid = " + pageTable[i].valid + "; readOnly = " + pageTable[i].readOnly 
		// 			+ "; used = " + pageTable[i].used+ "; dirty = " + pageTable[i].dirty);
		// }
		// // print entire IVT
		// for (int i = 0; i < VMKernel.IVT.length; i++) {
		// 	System.out.println("IVT[" + i + "] = " + VMKernel.IVT[i]);
		// }

		// for (int i = 0; i < VMKernel.ppnToVPN.length; i++) {
		// 	System.out.println("ppnToVPN[" + i + "] = " + VMKernel.ppnToVPN[i]);
		// }

		// for (int i = 0; i < VMKernel.ppnToSwapPos.length; i++) {
		// 	System.out.println("ppnToSwapPos[" + i + "] = " + VMKernel.ppnToSwapPos[i]);
		// }

		
	}
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	// General notes:
	// TranslationEntry Bits:
	// 	Valid Bit:
	// 		- valid bit set to true: virtual page is resident in memory
	// 		- valid bit set to false: virtual page is NOT resident in memory
	// 		- when a process references an address marked invalid, CPU raises a pageFaultException
	// 	Use Bit:
	// 		- used bit = 'reference bit'
	// 		- *if virtual page is refereneced by a process, set reference bit to tell kernel that the page is active
	// 		- once set, the reference bit remains set until kernel clears it
	// 		- therefore, only need to set used bit, and NEVER need to set to false afterwards
	// 	Dirty Bit:
	// 		- set dirty bit whenever process executes a write to a virutal page
	// 		- *if kernel evicts page from memory, first "clean" page by writing content to disk
	// 		- once set, dirty bit remains set until the kernel clears it
	// 		- therefore, only need to set dirty bit, and NEVER need to set to false afterwards
	// Swap File:
	// 	- use StubFileSystem (via ThreadedKernel.fileSystem)
	// 	- units of the swap file are pages
	// 	- *if gaps are made in the swap space, fill them
	// 	- assume swap file can grow arbitrarily
	// 	- *should not be any read/write errors, assert is there are any
	// Global Memory Accounting
	// 	-  *need data structure to keep track of which pages are pinned
	// 		- *needed to prevent the eviction of "sensitive" pages
	// 	- use Inverted Page Table to keep track of which process owns which pages
	// 		- needed to manage eviction of pages
	// Page Pinning
	// 	- *need to "pin" the physical page while using it 
	// 		(when using a phys page for system calls (i.e. readVM/writeVM) or I/O(reading from COFF or swap))
	// 	- need data structure to track which pages are pinned

	
}
