package name.bizna.j6502;

public abstract class AbstractCore {
	public enum State {
		RECOVERING_FROM_RESET, RUNNING, AWAITING_INTERRUPT, STOPPED
	}
	public static final short IRQ_VECTOR = (short)0xFFFE;
	public static final short RESET_VECTOR = (short)0xFFFC;
	public static final short NMI_VECTOR = (short)0xFFFA;
	public static final byte P_C_BIT = 0x01;
	public static final byte P_Z_BIT = 0x02;
	public static final byte P_I_BIT = 0x04;
	public static final byte P_D_BIT = 0x08;
	public static final byte P_B_BIT = 0x10;
	public static final byte P_1_BIT = 0x20;
	public static final byte P_V_BIT = 0x40;
	public static final byte P_N_BIT = (byte)0x80;
	protected byte a, x, y, p, s;
	protected short pc;
	protected Memory memory;
	protected State state;
	private boolean irq = false, nmi = false, nmi_edge = false, so = false, so_edge = false;
	protected AbstractCore(Memory memory) {
		this.memory = memory;
	}
	abstract protected void executeOpcode(byte opcode);
	protected void simplePUpdateNZ(int result) {
		if((result & 0xFF) == 0) p |= P_Z_BIT;
		else p &= ~P_Z_BIT;
		if((result & 0x80) != 0) p |= P_N_BIT;
		else p &= ~P_N_BIT;
	}
	protected void simplePUpdateNZC(int result) {
		if((result & 0xFF) == 0) p |= P_Z_BIT;
		else p &= ~P_Z_BIT;
		if((result & 0x80) != 0) p |= P_N_BIT;
		else p &= ~P_N_BIT;
		if((result & 0x100) != 0) p |= P_C_BIT;
		else p &= ~P_C_BIT;
	}
	public byte pop() {
		return memory.readByte((short)(0x100 | (++s&0xFF)));
	}
	public void push(byte b) {
		memory.writeByte((short)(0x100 | (s--&0xFF)), b);
	}
	public void fakePush() {
		memory.readByte((short)(0x100 | (s--&0xFF)));
	}
	public byte readA() { return a; }
	public byte readX() { return x; }
	public byte readY() { return y; }
	public byte readP() { return p; }
	public byte readS() { return s; }
	public short readPC() { return pc; }
	public void writeA(byte nu) { a = nu; }
	public void writeX(byte nu) { x = nu; }
	public void writeY(byte nu) { y = nu; }
	public void writeP(byte nu) { p = nu; }
	public void writeS(byte nu) { s = nu; }
	public void writePC(short nu) { pc = nu; }
	public void reset() {
		state = State.RECOVERING_FROM_RESET;
	}
	public void step() {
		if(state == null) throw new RuntimeException("You must call reset() at least once on a Core before you can call step() on it");
		if(so_edge) {
			so_edge = false;
			p |= P_V_BIT;
		}
		byte opcode = memory.readOpcodeByte(pc);
		switch(state) {
		case STOPPED:
			break;
		case RECOVERING_FROM_RESET:
			memory.readByte((short)(pc+1));
			fakePush();
			fakePush();
			fakePush();
			p &= ~P_D_BIT;
			pc = memory.readVectorByte(RESET_VECTOR);
			pc |= memory.readVectorByte((short)(RESET_VECTOR+1))<<8;
			state = State.RUNNING;
			break;
		case AWAITING_INTERRUPT:
			if(irq || nmi_edge) state = State.RUNNING;
			memory.readByte(pc);
			if(state != State.RUNNING) break;
		case RUNNING:
			if(nmi_edge) {
				nmi_edge = false;
				memory.readByte((short)(pc+1));
				push((byte)(pc>>8));
				push((byte)pc);
				push(p);
				p &= ~P_D_BIT;
				pc = memory.readVectorByte(NMI_VECTOR);
				pc |= memory.readVectorByte((short)(NMI_VECTOR+1))<<8;
			}
			else if(irq) {
				memory.readByte((short)(pc+1));
				push((byte)(pc>>8));
				push((byte)pc);
				push((byte)(p&~P_B_BIT));
				p &= ~P_D_BIT;
				p |= P_I_BIT;
				pc = memory.readVectorByte(IRQ_VECTOR);
				pc |= memory.readVectorByte((short)(IRQ_VECTOR+1))<<8;
			}
			else {
				++pc;
				executeOpcode(opcode);
			}
			break;
		}
	}
	public void step(int iterations) {
		for(int i = 0; i < iterations; ++i) {
			step();
		}
	}
	public State getState() {
		return state;
	}
	public void setIRQ(boolean irq) {
		this.irq = irq;
	}
	public void setNMI(boolean nmi) {
		if(nmi && !this.nmi) nmi_edge = true;
		this.nmi = nmi;
	}
	public void setSO(boolean so) {
		if(so && !this.so) so_edge = true;
		this.so = so;
	}
}
