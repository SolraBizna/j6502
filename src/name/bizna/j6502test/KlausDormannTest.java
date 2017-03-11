package name.bizna.j6502test;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import name.bizna.j6502.AbstractCore;
import name.bizna.j6502.Memory;

/**
 * This is an incredibly quick and dirty hack to run Klaus Dormann's 6502 Functional Test using the W65C02_Compatish core.
 */
public class KlausDormannTest {
	
	// I could have stopped to refactor this in a sane way, or...
	private static long cycleCount = 0;
	private static InputStream s19file = KlausDormannTest.class.getResourceAsStream("6502_functional_test.s19");
	//private static boolean trace = false;
	private static AbstractCore core;
	public static void main(String[] args) throws IOException {
		final InputStream romfile = new GZIPInputStream(KlausDormannTest.class.getResourceAsStream("s19load.rom.gz"));
		byte[] memory = new byte[65536];
		romfile.read(memory, 0x9000, 0x7000); // ignore errors?!
		final Memory mem = new Memory() {
			
			@Override
			public void writeByte(short addr, byte value) {
				++cycleCount;
				memory[addr&0xFFFF] = value;
				//if(trace && (addr&0xFF00) != 0x0100) System.out.println(String.format("WRITE %04X %02X", addr, value));
			}
			
			@Override
			public byte readVectorByte(short addr) {
				++cycleCount;
				//if(trace) System.out.println(String.format("VEC %04X %02X", addr, memory[addr&0xFFFF]));
				return memory[addr&0xFFFF];
			}
			
			@Override
			public byte readOpcodeByte(short addr) {
				++cycleCount;
				//if(trace)
				//	System.out.println(String.format("OP %04X %02X A:%02X X:%02X Y:%02X S:%02X P:%02X", addr, memory[addr&0xFFFF],
				//			core.readA(), core.readX(), core.readY(), core.readS(), core.readP()));
				return memory[addr&0xFFFF];
			}
			
			@Override
			public byte readByte(short addr) {
				++cycleCount;
				if((addr&0xFFFF) == 0xFFF9) {
					if(s19file == null) return -1;
					byte[] ret = new byte[1];
					try {
						if(s19file.read(ret, 0, 1) == 1) {
							//if(trace) System.out.println(String.format("RD %04X %02X", addr, ret[0]));
							return ret[0];
						}
					}
					catch(IOException e) {
						e.printStackTrace();
					}
					System.out.println("Finished reading S19 file.");
					s19file = null;
					//trace = false;
					//if(trace) System.out.println(String.format("RD %04X %02X", addr, (byte)-1));
					return -1;
				}
				else {
					//if(trace) System.out.println(String.format("RD %04X %02X", addr, memory[addr&0xFFFF]));
					return memory[addr&0xFFFF];
				}
			}
		};
		core = new W65C02_Compatish(mem);
		core.reset();
		System.out.println("Begin test");
		long start = System.currentTimeMillis();
		int desired_iterations = 50000000;
		short lastPC = core.readPC();
		while(desired_iterations-- > 0) {
			core.step();
			short thisPC = core.readPC();
			if(thisPC == lastPC) {
				System.out.println("Hit an infinite loop.");
				break;
			}
			lastPC = thisPC;
		}
		long stop = System.currentTimeMillis();
		System.out.println(String.format("Did %d cycles in %d milliseconds.", cycleCount, stop-start));
		System.out.println(String.format("That puts us right around %.3fMHz.", (cycleCount / 1000.0) / (stop-start)));
		System.out.println(String.format("The ending PC was: $%04X", core.readPC()&0xFFFF));
	}

}
