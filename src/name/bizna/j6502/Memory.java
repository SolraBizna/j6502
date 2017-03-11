package name.bizna.j6502;

/**
 * Implementors of this interface present a 16-bit address, 8-bit data memory space.
 */
public interface Memory {
	/**
	 * Read a byte with SYNC=low, VPB=high (anything but interrupt vectors and instruction opcodes)
	 * @param addr The address to read
	 * @return The data that was read
	 */
	public byte readByte(short addr);
	/**
	 * Read a byte with SYNC=low, VPB=low (reading an interrupt vector)
	 * @param addr The address to read
	 * @return The data that was read
	 */
	public byte readVectorByte(short addr);
	/**
	 * Read a byte with SYNC=high, VPB=high (reading an instruction opcode, which may or may not be about to execute)
	 * @param addr The address to read
	 * @return The data that was read
	 */
	public byte readOpcodeByte(short addr);
	/**
	 * Write a byte. SYNC is always low and VPB is always high on writes.
	 * @param addr The address to write to
	 * @param value The data to write
	 */
	public void writeByte(short addr, byte value);
}
