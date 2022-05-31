The OCMOS architecture provides a W65C02S CPU, along with an MMU (for memory protection and management) and some custom IO controllers (for accessing OpenComputers hardware other than memory).

Memory Mapping
==============

The OCMOS MMU maps all memory in banks, 4K each. Physical bank 0 contains the (tiny) built-in memory, the registers to control the MMU / perform IO, and a lot of reserved space. Physical banks 1 and up contain additional memory from memory modules, if any are installed.

When you perform a memory access to `$xyyy`, `$x` is the logical bank number, and `$yyy` is the address within the bank. To determine which physical bank to read from, the MMU will read the mapping registers, indexed by `$x`:

- If the system is in supervisor mode, it reads the low byte of the bank number from `$20x` and the high byte from `$21x`. The highest three bits of `$21x` are ignored.
- If the system is in user mode, it reads the low byte of the bank number from `$22x` and the high byte from `$23x`. The highest three bits are the protection mask. (See "Protection Mask")

So, say you're in supervisor mode and you perform a read from `$4E95`. This is in *logical* memory bank `$4`, so the MMU will read `$204` and `$214` to determine which *physical* memory bank to read from. If `$204` contains `$56` and `$214` contains `$04`, then it will read from *physical* bank number `$0456`. One might write out the full *physical* address as `$0456:E95`.

There are two exceptions to the above logic, both of which **only apply in supervisor mode**:

- If bit 0 of the MMU flags register is set, and the access is a read from logical bank `$F`, then this is an EEPROM read. If the access is a write, it's mapped normally regardless of bit 0's flag. This allows a BIOS to write boot code "underneath" the currently-running BIOS without fiddling with memory mapping. It also makes having a BIOS interrupt handler a little simpler.
- If the access is to logical bank `$0`, then it is *always* an access to physical bank 0. This is to prevent you from accidentally cutting off your own access to the registers, and rendering the computer useless.

As referred to above, physical bank 0 is the built-in memory and registers. Any additional memory modules installed in the computer will provide banks 1 and up, mapped straightforwardly.

Examples:

1. A system with a single 64K memory module installed will have 16 extra banks of memory, numbered 1 through 16.
2. A system with a 16K memory module and a 4K memory module installed will have 5 extra banks of memory, numbered 1 through 5.
3. A system with a 64K memory module, a 16K memory module, and a 4K memory module will have 21 extra banks of memory, numbered 1 through 21.

Since supervisor accesses to `$0xxx` always access physical bank 0, that means that the `$200`/`$210` registers are available for another purpose. These are used to communicate how many banks there are. When read, they return the number of extra memory banks that are installed. Since the first extra memory bank is bank 1, this also gives you the largest valid memory bank number.

Memory bank numbers are 13 bits wide, allowing up to 8191 extra banks to exist. This allows you to install just a bit less than 32MiB of physical memory in your system. That may not sound like much, but it's a truly mind-boggling amount of memory from the perspective of an 8-bit system.

Bank 0
======

- `$000`: MMU Flags
- `$001,$002,$003,$004`: Forbidden operation information is written here (if you're not making use of User Mode, you can use these as normal)
- `$001-$1FF`: built-in memory
- `$200`: (read-only) highest valid memory bank number, low
- `$201-$20F`: Supervisor memory banks, low
- `$210`: (read-only) highest valid memory bank number, high
- `$211-$21F`: Supervisor memory banks, high; top 3 bits are reserved and should be 0
- `$220-$22F`: User memory banks, low
- `$230-$23F`: User memory banks, high; top 3 bits are protection mask
- `$240`: Signal input (UIF)
- `$241`: Watchdog ticks
- `$242`: Component/Value IO (UIF)
- `$243`: (on read) Battery amount, 255=full, 0=empty; (on write) dispose indicated Value
- `$244`: Simple crash port (on write, crashes with "Crash! $xx" as the message, where `$xx` is the written value)
- `$245`: Complex crash port
- `$246`: Component list port
- `$247`: Debug device port (not related to debug card)
- `$248-$24F`: Time
- `$25x`: UUID of this computer (read only)
- `$260-$27F`: Disk drives
- `$28x`: UUID of /tmp filesystem (read only)
- `$2A0-$2F5`: Redstone IO
- `$2F6-$2FE`: Reserved for future expansion
- `$2FF`: Serial terminal module
- `$300-$FFF`: Reserved for some kind of standard bus

MMU Flags (`$000`)
------------------

- Bit 0 (`$01`): ROM responds to `$Fxxx` Supervisor reads (writes are as normal)
- Bit 1 (`$02`): User mode is active
- Bit 2 (`$04`): forbid `WAI` in User mode
- Bit 3 (`$08`): forbid `STP` in User mode
- Bit 4 (`$10`): forbid `SEI` in User mode
- Bit 5 (`$20`): forbidden operation sets to 1, set to 0 yourself afterward (don't forget to still check watchdog)
- Bit 6 (`$40`): we were in User mode when the last IRQ/BRK occurred
- Bit 7 (`$80`): we were in User mode when the last NMI occurred

Writing the User bit requires some special care. The change does not actually take place until and unless two opcode fetches occur, without the CPU processing an IRQ, NMI, or BRK in the meantime. If an interrupt occurs before the change, the PC that gets pushed on the stack is modified to be the address of the instruction that wrote the User bit. Thus, when the handler returns, the User mode transition will be retried automatically.

            SMB1 $00
            JMP targetAddressInUserCode

`RTS`/`RTI` could also be used, but if you're really "returning" to user mode, then you have to copy the appropriate stack elements from the user mode stack.

There is a special case; when the instruction after the one that changed the flag is indirect `JMP` (specifically `$6C`, and not also _indexed_ indirect `JMP`, which is `$7C`), the change takes place after the second operand byte is fetched. This would let you do:

            SMB1 $00
            JMP (userspaceAddressOfEntryPoint)

Of course, if you're executing a bank where the user and supervisor mappings are the same, all of this becomes much safer and simpler.

Protection Mask (`$23x`)
------------------------

- Bit 5 (`$20`): Forbid opcode fetches (effectively also true if bit 7 is set)
- Bit 6 (`$40`): Forbid writes
- Bit 7 (`$80`): Forbid reads

Mapping bank 0 into userspace with a protection mask that allows writing is self-defeating. User code is, then, free to stomp all over your carefully-designed protections. Better not let it happen at all.

Forbidden User Operations
-------------------------

When the CPU is in User mode, and attempts a forbidden operation, an NMI occurs. In such case, MMU flag bit 5 is set, and the PC pushed on the stack is modified to point to the offending instruction.

It is always possible to restart the offending instruction, allowing virtualization. This is a LOT of work, though.

For some operations, you may have to restore the Carry bit and accumulator before restarting the operation. Recovering the Carry bit and previous accumulator state is always possible, but will depend on exactly which instruction was executing.

If the access that failed was a write, you shouldn't restart the instruction. You should, instead, emulate the write using the information from `$02-$04`, update the PC on the stack to advance past the instruction, _then_ return.

Information that gets written when a forbidden operation occurs:

- `$001`: Operation type that failed
- `$002`: Low byte of the address of the access that failed
- `$003`: High byte of the address of the access that failed
- `$004`: If it was a write, the byte that was being written. Otherwise, this location is not updated.

Operation types:

- `$00`: Opcode read (fetching the first byte of an instruction)
- `$01`: Data read (all other reads)
- `$02`: Data write
- `$03`: Privileged instruction (SEI/WAI/STP)

Watchdog (`$241`)
-----------------

On reset, the watchdog timer's counter is set to 0, which disables it. When any other value is in the counter, it ticks upward at an inconsistent pace, no faster than Minecraft world ticks. If the counter reaches 255, an NMI occurs, after which the watchdog is inactive again until reinitialized.

This can be used to help prevent some forms of system hang from rendering the system unusable, but bear in mind that if there's not a useful NMI handler mapped, this won't help. Real watchdog timers used in 6502 systems usually reset the system instead of sending an NMI. I think this one sends an NMI so that it can be used to do preemptive scheduling?

Crash Ports (`$244`,`$245`)
---------------------------

If you encounter an unrecoverable error:

- Write `$00` to `$245`. The error message will be "Crash!"
- Write any byte `$xx` to `$244`. The error messsage will be "Crash! $xx"
- Write up to 2048 bytes of UTF-8 text to `$245`, followed by `$00`. The error message will be the text you wrote.

This will cause OpenComputers to shut down the computer, and store the error message for later retrieval by an Analyzer. (It may also display the error on one or more of the attached screens, I don't recall.)

The first two options aren't very user friendly, but they're compact, and most 8-bit computers had similarly unfriendly error messages.

UIF Ports (`$240`,`$242`)
-------------------------

UIF ports are serial buses that transmit data using packed, **big**-endian [OETF #2: Universal Interchange Format](https://oc.cil.li/index.php?/topic/1170-oetf-2-universal-interchange-format/) as the message format.

Signals always start with a `UIFTAG_STRING` giving the name of the signal. Output on the Component/Value IO port must always start with a `UIFTAG_UUID` or Value handle giving the target of the IO, then a `UIFTAG_STRING` giving the name of the requested command. Input on the Component/Value IO port will always start with a status byte, and then be followed by the reply in UIF format.

Value handles are represented as interchange tags that start with `$80`. Each active Value has a unique second byte. When you have finished with a Value, you should dispose it by writing the second byte of its tag to `$243`.

A given UIF bus is in one of three states at a given time: Input, Output, or Idle. A UIF bus in an unknown state can be forced into the Idle state by reading once (to abort any in-progress transmission), writing any byte, then reading until V becomes set.

Status bytes for command replies:

- `$00`: Command acknowledged. Reply follows.
- `$01`: An unknown error occurred. `UIFTAG_STRING` with human-readable error message follows.
- `$02`: A component/value acknowledged the address, but did not recognize the command. No data follows.
- `$7E`: (emulator-specific) The message was not valid UIF. (Don't forget the `UIFTAG_END` at the end)
- `$7F`: (emulator-specific) The message was too long.
- `$FF`: (also sets overflow) No component/value acknowledged that address. No data follows.

### Input

A Component or Value is transmitting to us, or a Signal is being received.

**Read**: Returns the next byte of the transmission. If the most recently read byte was the last byte of the transmission, returns `$FF`, sets the V flag, and enters the Idle state. (Depending on the port, there may be another transmission immediately afterward.)   
**Write**: Aborts the transmission. The bus enters the Idle state. (The written byte is lost.)

### Output

We are transmitting to a Component or Value.

**Read**: The transmission is considered to be concluded. Waits until a reply is received (including an error reply), and then the bus enters the Input state and returns the first byte of the reply.  
**Write**: The next byte to transmit.

### Idle

**Read**: Returns `$FF` and sets the V flag.  
**Write**: If this is a bidirectional port, the bus enters Output mode and the written byte becomes the first byte of a transmitted message.

Component List Port (`$246`)
----------------------------

First, write `$FF` to the port to latch the list and reset the cursor.

Next, repeatedly read to get the name of the first component. When the name is over, `$00` is read. The next 16 reads will read the UUID of the component in question. After that, reads will return `$FF` and V will be set.

At any point in the above process (for instance, if you've already decided this isn't a component you're interested in), write `$00` to the port to move the cursor to the next component.

When there are no more components, all reads will return `$FF` and set V. There's nothing left to do then until the next time `$FF` is written to the port.

Components are not listed in any particular order.

Debug Device (`$247`)
---------------------

The Debug Device (which is *not* related to the Debug Card) provides a very low-level way to debug bootloaders and custom BIOSes. It must be enabled in the configuration. It's a bad idea to use this routinely; enable it only in extreme circumstances.

Write bytes to this port to output UTF-8 to the Minecraft game log. On read, returns either `$00` (the port is disabled and writes are ignored) or `$FF` (the port is enabled) depending on the configuration.

Writing `$00` (NUL), `$0A` (CR), or `$0D` (LF) terminate a line. Blank lines are not allowed, and lines longer than 256 characters will be cut off.

If your output doesn't appear, one of three things is probably true:

- Debug device is disabled
- You're not looking in the right log (there are a lot)
- You forgot to output a `$00`, `$0A`, or `$0D`

Time (`$248-$24F`)
-----------------

Writing any byte will attempt to sleep for the written number of Minecraft world ticks. The sleep will often end prematurely, in particular if a signal arrives, or if the scheduler gets cranky. The sleep may even end late. For precise timing, there is no substitute for checking the Time value after the sleep ends.

On read:

- `$248`: Tick (BCD), 0-19
- `$249`: Second (BCD), 0-59
- `$24A`: Minute (BCD), 0-19 (sun rises at 5, sets at 15)
- `$24B`: Day, low byte
- `$24C`: Day, high byte
- `$24D`: Moon phase, as it will appear the night after the most recent sunrise (`$00` = full, `$01` = waning gibbous, ... `$07` = waxing gibbous)
- `$24E`: Monotonic uptime in ticks, low byte
- `$24F`: Monotonic uptime in ticks, high byte

`$248-$24D` don't make much sense in other dimensions. `$24E-$24F` start at 0 on power-on, and should be used for any precise timing.

Disk Drives (`$260-$27F`)
-------------------------

There are four Disk Drive slots, each 8 bytes long. At reset, no drive is mapped; the BIOS may map one or more drives. (see BIOS)

- `$0,$1`: (RW) First and second byte of the UUID of the "drive" component this slot accesses. If there is no drive with a matching UUID, the IOs will go nowhere!
- `$2`: (R) The sector size of this drive, in units of 256 bytes. Zero means no drive here.
- `$3`: Read/write port. Sets V if a read/write overflows, or if a read/write is attempted on a drive that doesn't exist [anymore], or if a read/write is performed on an out-of-range sector.
- `$4,$5`: (R) The number of sectors on this drive. All zeroes means no drive here.
- `$6,$7`: (RW) Sector number of current read/write. Be aware that sector numbers start at 1.

Reading aborts a write, and writing aborts a read. A write does not finish until the moment you write the last byte of a sector.

Sector size and number of sectors are configurable on OpenComputers' end. By default, sectors are 512 bytes, and media have the following sizes:

- Floppy: 1024 sectors
- Tier 1 hard drive: 2048 sectors
- Tier 2 hard drive: 4096 sectors
- Tier 3 hard drive: 8192 sectors

Redstone IO (`$280-$2F5`)
-------------------------

The redstone component with the lowest UUID is mapped here.

- `$280`: (RW) Wake threshold
- `$281-$296`: (R) Comparator input on each side
- `$287-$28C`: (R) Redstone input value on each side
- `$28D-$292`: (RW) Redstone output value on each side
- `$293-$2F3`: The last two items repeated 16 times, once for each possible bundled output color.
- `$2F4`: (R) Wireless input
- `$2F5`: (RW) Wireless output

(To get or set the wireless frequency, you must use the UIF bus.)

Serial Terminal (`$2FF`)
------------------------

At power-on, the serial terminal is disabled. Reading from the port will return `$00` if the terminal is currently working, and `$FF` if the terminal is disabled _or_ not present. When it is disabled, writing `$00` to the port attempts to initialize the terminal. (Whether it was successful can be determined by reading the port afterward). Writing anything other than `$00` to the port while the terminal is disabled is undefined, except that writing any value from `$F8` to `$FF` will _always_ do nothing.

Commands:

- `$00`: Reinitialize terminal
- `$03`: Move cursor one column forward, if there is room (reverse of `$08`)
- `$04`: Clear screen, and move cursor to upper-left cell
- `$05`: Request terminal_size signal
- `$06`: Cycle to next supported size, clear the screen, and request terminal_size signal
- `$07`: Bell
- `$08`: Move cursor one column backward, if there is room
- `$09`: Move cursor to next eighthmost column, erasing cells as it goes, and linebreak if necessary
- `$0A`: Move cursor to leftmost column
- `$0B`: Clear from the cursor to the rightmost column
- `$0C`: Move cursor to next line, possibly scrolling
- `$0D`: Move cursor to leftmost column on next line, possibly scrolling (linebreak)
- `$0E`: Disable automatic linebreak at end of line.
- `$0F`: Enable automatic linebreak at end of line. (default)
- `$13`: Shut down the terminal. Data will remain on the screen unless you did `$04` first.
- `$20-$F7`: Output the given byte. (The terminal speaks UTF-8, but doesn't understand character composition or invisible characters.)
- `$FE`: Display an "insertion point" at the cursor location. The next output to the terminal, including `$FF`, will undo this.
- `$FF`: Do nothing

The response to `$05` will be a `terminal_size` signal, with a `UIFTAG_BYTE_ARRAY` that is at least 4 bytes long. The bytes are: current width, current height, maximum width, maximum height. Software that manages the terminal will generally handle the interrupt and store the bytes somewhere easy to access.

BIOS
====

All of this applies only if the standard OCMOS BIOS is in use. If you provide your own EEPROM, you can rely on everything above (though some things you'll have to initialize yourself) and nothing below.

Booting
-------

General-purpose BIOS bootloaders shall be [OETF #1: Cross-Architecture Booting](https://oc.cil.li/index.php?/topic/1121-oetf-1-cross-architecture-booting-draft/) compliant. The standard BIOS implements the following boot sequence:

- For each attached `drive`:
  - Check sector 0 for a CAB boot sector. If it doesn't have one, check sector 1.
  - If a CAB boot sector is found, AND the boot sector contains binary records, AND one of the binary records specifies an AID of `OCMOS`, attempt to boot based on that record.
- If no boot attempt has yet been made, then for each attached `filesystem`:
  - If `OCMOS/boot` exists, attempt to boot it.
  - If `OCMOS` exists, attempt to boot it.
- If no boot attempt has yet been made, inform the user and break to a monitor.

What happens on attempting to boot depends on the first byte of the booted data.

- If the byte is `$53` (capital S), the data is interpreted as Motorola S-records.
- If it is any other value, the data is interpreted as raw binary data, linearly loaded starting at `$1000`.

If no entry point is specified in the S-records/HEX data, or if it is a binary image, the entry point is assumed to be `$1000`.

Loading data to the `$F000-$FFFF` region is supported, but in order to use that data you will have to enter User mode (`SMB1 $00` followed by `NOP`)---or unmap the ROM (`RMB0 $00`), in which case you must provide your own interrupt handler. Attempting to load data to `$0000-$001F` or `$0100-$0FFF` is an error. Attempting to load data into RAM where no RAM is installed is an error.

Post-Boot Environment
---------------------

Upon entry to your entry point...

- The terminal will be initialized, if possible. If reading `$02FF` returns `$FF`, then the BIOS' attempt to initialize the terminal failed.
- The terminal size will have been written to `$0B-$0E`, if the terminal is initialized.
- Any installed memory will be linearly mapped starting at `$1000`. (This is the power-on state of the MMU.)
- The UUID of the device that was booted from will be in `$20-$2F`. It is assumed that your code "knows" how it booted.
- If booted from an unmanaged drive, the first disk drive controller (`$260-$268`) will be set up with that drive. Other disk drives are in an undefined state.
- All user-facing BIOS control flags are 0.

Interrupts
----------

If an interrupt occurs in User mode, the BIOS will reestablish User mode before doing `RTI` (if it handled the interrupt itself) or jumping to user code (if it didn't).

Memory
------

`$05-$1F` is reserved for the use of the BIOS. Using `$01-$04` from your own code is an option if you're not using User mode at all. Some of the BIOS reserved area has designated uses for user code.

- `$07-$08`: NMI interrupt vector
- `$09-$0A`: IRQ/BRK interrupt vector
- `$0B-$0E`: Current width, height, and maximum width, height of terminal (filled in whenever a `terminal_size` signal is processed by the BIOS)
- `$0F`: BIOS Control Flags
- `$20-$2F`: On boot, the BIOS sets this to the address of the booted device (and thereafter doesn't care what happens to it).

BIOS Control Flags
------------------

- Bit 0 (`$01`): 0 = BIOS should handle signals, 1 = BIOS jumps to `($09)` (with Z clear) on IRQ
- Bit 1 (`$02`): 0 = Break to monitor on BRK, 1 = BIOS jumps to `($09)` (with Z set) on BRK
- Bit 2 (`$04`): 0 = Break to monitor on NMI, 1 = BIOS jumps to `($07)` on NMI
- Bit 3 (`$08`): 0 = `getb` blocks until a character is available, and presents a blinking cursor as it does. 1 = `getb` returns immediately if no character is available.
- Bit 4 (`$10`): 0 = BIOS will automatically reinitialize the terminal whenever a `gpu` or `screen` is added or removed. Every time it does so, it will simulate a control-L (`$0C`) keypress. Your code should then check if the terminal is still valid. 1 = BIOS ignores `gpu`/`screen` add/remove signals.
- Bit 5 (`$20`): 0 = BIOS echoes inputted printable characters to the screen. (Note: Backspace is not printable!) 1 = BIOS never echoes characters. `echo` still works as normal.
- Bit 6 (`$40`): 0 = The user may break to the monitor by typing control-backslash. 1 = Control-backslash is handled just like any other keypress.
- Bit 7 (`$80`): 0 = The user may change the size of the terminal by typing control-T. Every time this occurs, the BIOS simulates a control-L (`$0C`) keypress. 1 = Control-T is handled just like any other keypress.

Entry Points
------------

Unless otherwise specified, BIOS routines do not clobber the A, X, or Y registers.

- `($F000)`: `getb`
- `($F002)`: `echo`
- `($F004)`: `discard`

### `getb`

Get <strike>character</strike> byte of text input. If a byte is available, returns it in A and clears V. If it isn't, clobbers A and sets V. (The latter case will only happen if bit 3 of `$0F` is set; otherwise, `getb` waits until a byte is available.)

`getb` will clear the interrupt disable flag. Interrupt handling is necessary to receive input.

### `echo`

Echoes the byte in A, exactly as it would be echoed on entry by the BIOS (with BIOS flag 5 disabled).

Please, as with `getb`, bear in mind that bytes with the most significant bit set are part of a multi-byte character!

### `discard`

Discards any buffered input.

`discard` will clear the interrupt disable flag.
