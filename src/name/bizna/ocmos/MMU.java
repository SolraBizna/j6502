package name.bizna.ocmos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import li.cil.oc.api.Driver;
import li.cil.oc.api.driver.Item;
import li.cil.oc.api.driver.item.Processor;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.machine.Signal;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import name.bizna.j6502.AbstractCore.State;
import name.bizna.j6502.Memory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class MMU implements Memory {

	private static final int BANK_SIZE = 4096;
	private static final int ROM_SIZE = 4096;
	private static final int ROM_MASK = 4095;
	
	private static final int BUILTIN_MEMORY_SIZE = 512;
	private static final int MMU_REGISTER_SPACE_SIZE = 64;

	private static final int MMU_REG_SUPERVISOR_BANKS_LOW = 0x00;
	private static final int MMU_REG_SUPERVISOR_BANKS_HIGH = 0x10;
	private static final int MMU_REG_USER_BANKS_LOW = 0x20;
	private static final int MMU_REG_USER_BANKS_HIGH = 0x30;
	private static final int MMU_REG_MAX_VALID_BANK_LOW = 0x00;
	private static final int MMU_REG_MAX_VALID_BANK_HIGH = 0x10;
	
	private static final byte FLAG_MAPPED_ROM = 0x01;
	private static final byte FLAG_USER_MODE = 0x02;
	private static final byte FLAG_PRIVILEGED_WAI = 0x04;
	private static final byte FLAG_PRIVILEGED_STP = 0x08;
	private static final byte FLAG_PRIVILEGED_SEI = 0x10;
	private static final byte FLAG_LAST_NMI_WAS_PRIVILEGED_OPERATION = 0x20;
	private static final byte FLAG_LAST_IRQBRK_WAS_USER = 0x40;
	private static final byte FLAG_LAST_NMI_WAS_USER = (byte)0x80;
	
	private static final byte[] CABE_PREFIX_PART_1 = ("--[").getBytes(Charset.forName("UTF-8"));
	private static final byte[] CABE_PREFIX_PART_2 = ("[CABE:OCMOS:").getBytes(Charset.forName("UTF-8"));
	
	protected final OCMOS parent;
	protected boolean romMappingValid, naughtyOperationInProgress, clearNaughtyNMIOnNextRead, naughtyNMI, overflow, needSynchronizedCall, inSynchronizedCall;
	protected int ramTier, cpuTier;
	protected byte romArray[] = new byte[ROM_SIZE];
	protected byte ramArray[];
	protected byte nodeAddress[], tmpAddress[];
	protected byte builtInMemory[] = new byte[BUILTIN_MEMORY_SIZE]; // the Flags register is part of this array
	protected byte mmuRegisters[] = new byte[MMU_REGISTER_SPACE_SIZE];
	protected byte bufferedSignal[];
	protected byte naughtyByte;
	protected int cycleBudget = 0, numMappedRamBanks = 0, fakePushStage;
	protected short lastFetchedOpcodeAddress;
	protected byte lastFetchedOpcode;
	protected short fakePushAddress;
	protected String mappedRedstoneAddr;
	protected ByteArrayOutputStream invokeStream = new ByteArrayOutputStream();
	protected ByteArrayOutputStream complexCrashStream, debugLineStream;
	protected ExecutionResult executionResult;
	protected byte watchdog;
	protected byte[][] componentList;
	protected int currentComponentInList, currentPositionInComponent;
	/**
	 * Whether the current mode transition is TO User mode.
	 */
	protected boolean transitioningToUserMode;
	protected enum ModeTransitionState {
		AWAITING_FIRST_OPCODE_FETCH, TRANSITION_ON_NEXT_OPCODE_FETCH,
		AWAITING_TWO_MORE_READS, AWAITING_ONE_MORE_READ, TRANSITION_ON_NEXT_READ
	}
	protected ModeTransitionState modeTransitionState = null;
	private static enum UIFPortState { INPUT, OUTPUT, IDLE; }
	private static enum UIFOutputState { AWAITING_TAG_FIRST_BYTE, AWAITING_TAG_SECOND_BYTE, AWAITING_BYTES, COMPLETE, TOO_LONG, MALFORMED; }
	/* uh, I think I meant to factor this class more than I did... */
	private abstract class UIFPort {
		UIFPortState portState = UIFPortState.IDLE;
		UIFOutputState outputState = null;
		byte[] currentInputThing;
		int currentInputPos, outputLevelsDeep, outputRemBytes;
		ByteArrayOutputStream outstream;
		int maxOutputSize;
		short outputTag;
		public void save(NBTTagCompound nbt) {
			if(portState != UIFPortState.IDLE) nbt.setString("portState", portState.toString());
			if(outputState != null) {
				nbt.setString("outputState", outputState.toString());
				nbt.setByteArray("outstream", outstream.toByteArray());
				nbt.setInteger("outputLevelsDeep", outputLevelsDeep);
				nbt.setInteger("outputRemBytes", outputRemBytes);
				if(outputState != UIFOutputState.AWAITING_TAG_FIRST_BYTE)
					nbt.setShort("outputTag", outputTag);
			}
			if(currentInputThing != null) {
				nbt.setByteArray("currentInputThing", currentInputThing);
				nbt.setInteger("currentInputPos", currentInputPos);
			}
		}
		public void load(NBTTagCompound nbt) {
			if(nbt.hasKey("portState")) portState = UIFPortState.valueOf(nbt.getString("portState"));
			else portState = UIFPortState.IDLE;
			if(nbt.hasKey("outputState")) {
				outputState = UIFOutputState.valueOf(nbt.getString("outputState"));
				byte[] stream = nbt.getByteArray("outstream");
				outstream.write(stream, 0, stream.length);
				outputLevelsDeep = nbt.getInteger("outputLevelsDeep");
				outputRemBytes = nbt.getInteger("outputRemBytes");
				if(nbt.hasKey("outputTag"))
					outputTag = nbt.getShort("outputTag");
			}
			else outputState = null;
			if(nbt.hasKey("currentInputThing")) {
				currentInputThing = nbt.getByteArray("currentInputThing");
				currentInputPos = nbt.getInteger("currentInputPos");
			}
		}
		protected UIFPort(int maxOutputSize) {
			this.maxOutputSize = maxOutputSize;
			if(maxOutputSize != 0) outstream = new ByteArrayOutputStream();
		}
		protected abstract byte[] getNextInputThing();
		public void write(byte b) {
			if(portState != UIFPortState.OUTPUT) {
				currentInputThing = null;
				currentInputPos = 0;
				if(maxOutputSize == 0) {
					portState = UIFPortState.IDLE;
					return;
				}
				else {
					portState = UIFPortState.OUTPUT;
					outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
					outputLevelsDeep = 0;
				}
			}
			assert(portState == UIFPortState.OUTPUT);
			switch(outputState) {
			case AWAITING_TAG_FIRST_BYTE:
				outputState = UIFOutputState.AWAITING_TAG_SECOND_BYTE;
				outputTag = (short)(b << 8);
				break;
			case AWAITING_TAG_SECOND_BYTE:
				outputTag |= b&0xFF;
				switch((int)outputTag) {
				default:
					if((outputTag & 0xFFFF) < 0x4000) {
						outputState = UIFOutputState.AWAITING_BYTES;
						outputRemBytes = outputTag;
					}
					else if((outputTag & 0xFFFF) < 0x8000) {
						outputState = UIFOutputState.AWAITING_BYTES;
						outputRemBytes = outputTag - 0x4000;
					}
					else if((outputTag & 0xFFFF) < 0x8100) {
						Value v = parent.getValue((byte)(outputTag & 0xFF));
						if(v == null) {
							if(MainClass.instance.shouldLogUIFErrors()) MainClass.logger.error("Invalid Value handle written");
							outputState = UIFOutputState.MALFORMED;
						}
						else
							outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
					}
					else {
						if(MainClass.instance.shouldLogUIFErrors()) MainClass.logger.error("Invalid UIF tag written");
						outputState = UIFOutputState.MALFORMED;
					}
					break;
				case PackedUIF.UIFTAG_END:
					if(outputLevelsDeep > 0) {
						--outputLevelsDeep;
						outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
					}
					else
						outputState = UIFOutputState.COMPLETE;
					break;
				case PackedUIF.UIFTAG_DOUBLE:
					outputState = UIFOutputState.AWAITING_BYTES;
					outputRemBytes = 8;
					break;
				case PackedUIF.UIFTAG_INTEGER:
					outputState = UIFOutputState.AWAITING_BYTES;
					outputRemBytes = 4;
					break;
				case PackedUIF.UIFTAG_ARRAY:
				case PackedUIF.UIFTAG_COMPOUND:
					++outputLevelsDeep;
					outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
					// some day, we may want to more aggressively validate Compounds here
					break;
				case PackedUIF.UIFTAG_UUID:
					outputState = UIFOutputState.AWAITING_BYTES;
					outputRemBytes = 16;
					break;
				case PackedUIF.UIFTAG_NULL:
				case PackedUIF.UIFTAG_TRUE:
				case PackedUIF.UIFTAG_FALSE:
					outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
				}
				break;
			case AWAITING_BYTES:
				if(--outputRemBytes == 0)
					outputState = UIFOutputState.AWAITING_TAG_FIRST_BYTE;
				break;
			case COMPLETE:
			case MALFORMED:
			case TOO_LONG:
				// do not add the byte to the stream
				return;
			}
			if(outstream.size() >= maxOutputSize) outputState = UIFOutputState.TOO_LONG;
			else outstream.write(b);
		}
		public byte read() throws LimitReachedException {
			if(portState != UIFPortState.INPUT) {
				Byte ret = null;
				currentInputThing = null;
				if(outstream != null) {
					if(outputState == UIFOutputState.COMPLETE) {
						try {
							DataInputStream instream = new DataInputStream(new ByteArrayInputStream(outstream.toByteArray()));
							Object target = PackedUIF.read(instream, parent);
							Object commandObj = PackedUIF.read(instream, parent);
							String command = commandObj instanceof String ? (String)commandObj : null;
							Object[] params;
							if(command != null) {
								ArrayList<Object> paramList = new ArrayList<Object>();
								while(true) {
									Object o = PackedUIF.read(instream, parent);
									if(o == PackedUIF.endTag) break;
									else paramList.add(o);
								}
								params = paramList.toArray();
							}
							else params = null;
							if(target instanceof UUID) {
								String addr = target.toString();
								Node us = parent.machine.node();
								if(us == null || us.network() == null) throw new EscapeRetry(lastFetchedOpcodeAddress);
								Node node = us.network().node(addr);
								if(node == null || !node.canBeReachedFrom(us) || !(node instanceof Component)) {
									ret = -1;
									triggerOverflow();
								}
								else if(command == null)
									ret = 0x02;
								else {
									Component component = (Component)node;
									Callback callback = component.annotation(command);
									if(callback == null) ret = 0x02;
									else if(!callback.direct() && !inSynchronizedCall) {
										needSynchronizedCall = true;
										throw new EscapeRetry(lastFetchedOpcodeAddress);
									}
									else {
										if(MainClass.instance.shouldLogInvokes())
											MainClass.logger.info("Invoke: %s %s %s", addr, command, Arrays.toString(params));
										Object[] reply = parent.machine.invoke(addr, command, params);
										if(reply == null) reply = new Object[0];
										if(MainClass.instance.shouldLogInvokes())
											MainClass.logger.info(" -> %s", Arrays.toString(reply));
										ret = 0x00;
										outstream.reset();
										DataOutputStream dataout = new DataOutputStream(outstream);
										for(int n = 0; n < reply.length; ++n) {
											PackedUIF.write(dataout, reply[n], parent);
										}
										PackedUIF.write(dataout, PackedUIF.endTag, parent);
										currentInputThing = outstream.toByteArray();
										currentInputPos = 0;
									}
								}
							}
							else if(target instanceof Value) {
								if(command == null) ret = 0x02;
								else {
									Value value = (Value)target;
									/*if(command.equals("_apply"))
									value.apply(parent.machine, new Arguments(params));
								else if(command.equals("_unapply"))
									value.apply(parent.machine, new Arguments(params));
								else if(command.equals("_apply"))
									value.apply(parent.machine, new Arguments(params));
								else */ {
									// TODO: determine whether a direct call is needed, instead of assuming
									if(!inSynchronizedCall) {
										needSynchronizedCall = true;
										throw new EscapeRetry(lastFetchedOpcodeAddress);
									}
									if(MainClass.instance.shouldLogInvokes())
										MainClass.logger.info("Invoke: %s %s %s", value, command, Arrays.toString(params));
									Object[] reply = parent.machine.invoke(value, command, params);
									if(reply == null) reply = new Object[0];
									if(MainClass.instance.shouldLogInvokes())
										MainClass.logger.info(" -> %s", Arrays.toString(reply));
									ret = 0x00;
									outstream.reset();
									DataOutputStream dataout = new DataOutputStream(outstream);
									for(int n = 0; n < reply.length; ++n) {
										PackedUIF.write(dataout, reply[n], parent);
									}
									PackedUIF.write(dataout, PackedUIF.endTag, parent);
									currentInputThing = outstream.toByteArray();
									currentInputPos = 0;
								}
								}
							}
							else {
								ret = -1;
								triggerOverflow();
							}
						}
						catch(NoSuchMethodException e) {
							ret = 0x02;
						}
						catch(IOException e) {
							if(MainClass.instance.shouldLogUIFErrors()) MainClass.logger.warn(e);
							ret = 0x7E;
						}
						catch(EscapeRetry e) {
							throw e;
						}
						catch(LimitReachedException e) {
							throw e;
						}
						catch(Exception e) {
							ret = 0x01;
							outstream.reset();
							DataOutputStream dataout = new DataOutputStream(outstream);
							try {
								PackedUIF.write(dataout, e.getLocalizedMessage(), parent);
								PackedUIF.write(dataout, PackedUIF.endTag, parent);
								currentInputThing = outstream.toByteArray();
							}
							catch(IOException f) {
								MainClass.logger.error("Unexpected IOException while serializing an error message", f);
								MainClass.logger.error("For good measure, here's the exception we were trying to serialize", e);
								currentInputThing = null;
							}
							currentInputPos = 0;
						}
					}
					else if(outputState == UIFOutputState.TOO_LONG)
						ret = 0x7F;
					else {
						ret = 0x7E;
						if(outputState != UIFOutputState.MALFORMED && MainClass.instance.shouldLogUIFErrors())
							MainClass.logger.warn("UIF message was not complete");
					}
					outputState = null;
					outstream.reset();
				}
				portState = UIFPortState.INPUT;
				if(ret != null) return ret;
			}
			assert(portState == UIFPortState.INPUT);
			if(currentInputThing == null) {
				currentInputThing = getNextInputThing();
				currentInputPos = 0;
				if(currentInputThing == null || currentInputThing.length == 0) {
					portState = UIFPortState.IDLE;
					triggerOverflow();
					return -1;
				}
			}
			else if(currentInputPos >= currentInputThing.length) {
				portState = UIFPortState.IDLE;
				triggerOverflow();
				return -1;
			}
			return currentInputThing[currentInputPos++];
		}
		public boolean hasInputLeft() {
			return currentInputThing != null && currentInputPos < currentInputThing.length;
		}
	}
	protected class UIFSignalPort extends UIFPort {
		public UIFSignalPort() {
			super(0);
		}
		@Override
		protected byte[] getNextInputThing() {
			if(bufferedSignal == null) {
				tryPopSignal();
				if(bufferedSignal == null) {
					updateIRQ();
					return null;
				}
			}
			byte[] ret = bufferedSignal;
			bufferedSignal = null;
			updateIRQ();
			return ret;
		}
	}
	protected class UIFInvokePort extends UIFPort {
		public UIFInvokePort() {
			super(MainClass.instance.getMaxInvokeSize());
		}
		@Override
		protected byte[] getNextInputThing() { return null; }
	}
	
	protected UIFPort invokePort = new UIFInvokePort();
	protected UIFPort signalPort = new UIFSignalPort();
	
	protected class DiskDrive {
		private byte addr1 = -1, addr2 = -1, sectorSize = 0, numSectors1 = 0, numSectors2 = 0, sector1 = 0, sector2 = 0;
		private String mappedDrive;
		private byte[] sectorBuffer;
		private int sectorReadPos = -1;
		private int sectorWritePos = -1;
		public byte read(int a) throws LimitReachedException {
			switch(a) {
			case 0: return addr1;
			case 1: return addr2;
			case 2: return sectorSize;
			case 3:
				if(sectorWritePos != -1) {
					sectorWritePos = -1;
					sectorBuffer = null;
				}
				if(sectorSize == 0 || (sectorBuffer != null && sectorReadPos >= sectorBuffer.length)
				|| sector2 > numSectors2 || (sector2 == numSectors2 && sector1 >= numSectors1)
				|| mappedDrive == null) {
					triggerOverflow();
					return -1;
				}
				if(sectorReadPos == -1 || sectorBuffer == null) {
					if(!inSynchronizedCall) {
						needSynchronizedCall = true;
						throw new EscapeRetry(lastFetchedOpcodeAddress);
					}
					try {
						if(MainClass.instance.shouldLogInvokes())
							MainClass.logger.info("Reading drive %s sector %d", mappedDrive, (sector1&0xFF)|((sector2&0xFF)<<8));
						sectorBuffer = simpleByteArrayCall(mappedDrive, "readSector", (sector1&0xFF)|((sector2&0xFF)<<8));
						if(sectorBuffer == null) {
							triggerOverflow();
							return -1;
						}
						sectorReadPos = 0;
					}
					catch(Exception e) {
						// ...
						triggerOverflow();
						return -1;
					}
				}
				assert(sectorBuffer != null);
				return sectorBuffer[sectorReadPos++];
			case 4: return numSectors1;
			case 5: return numSectors2;
			case 6: return sector1;
			case 7: return sector2;
			default: return -1;
			}
		}
		public void write(int a, byte value) throws LimitReachedException {
			switch(a) {
			case 0: addr1 = value; rescan(); break;
			case 1: addr2 = value; rescan(); break;
			case 3:
				if(sectorReadPos != -1) {
					sectorReadPos = -1;
					sectorBuffer = null;
				}
				if(sectorSize == 0 || (sectorBuffer != null && sectorWritePos >= sectorBuffer.length)
				|| (sectorBuffer == null && sectorWritePos >= 0)
				|| sector2 > numSectors2 || (sector2 == numSectors2 && sector1 >= numSectors1)
				|| mappedDrive == null) {
					triggerOverflow();
					break;
				}
				if(sectorWritePos == -1) {
					sectorBuffer = new byte[(sectorSize&0xFF)<<8];
					sectorWritePos = 0;
				}
				if(sectorWritePos == sectorBuffer.length-1 && !inSynchronizedCall) {
					needSynchronizedCall = true;
					throw new EscapeRetry(lastFetchedOpcodeAddress);
				}
				sectorBuffer[sectorWritePos++] = value;
				if(sectorWritePos == sectorBuffer.length) {
					try {
						MainClass.logger.info("Writing drive %s sector %d", mappedDrive, (sector1&0xFF)|((sector2&0xFF)<<8));
						simpleVoidCall(mappedDrive, "writeSector", (sector1&0xFF)|((sector2&0xFF)<<8), sectorBuffer);
						sectorBuffer = null;
					}
					catch(Exception e) {
						// ...
						triggerOverflow();
						break;
					}
				}
				break;
			case 6: sector1 = value; sectorReadPos = -1; sectorWritePos = -1; sectorBuffer = null; break;
			case 7: sector2 = value; sectorReadPos = -1; sectorWritePos = -1; sectorBuffer = null; break;
			default: break;
			}
		}
		private void rescan() throws LimitReachedException {
			mappedDrive = null;
			String startsWith = String.format("%02x%02x", addr1&0xFF, addr2&0xFF);
			Map<String,String> components = parent.machine.components();
			synchronized(components) {
				for(Map.Entry<String, String> entry : components.entrySet()) {
					if(entry.getKey().startsWith(startsWith) && entry.getValue().equals("drive")) {
						mappedDrive = entry.getKey();
						break;
					}
				}
			}
			if(mappedDrive != null) {
				int sectorSize = simpleIntCall(mappedDrive, "getSectorSize");
				if((sectorSize & 255) != 0) {
					MainClass.logger.warn("Mapped drive %s, but it has a sector size that is not a multiple of 256. This drive cannot be used.");
					mappedDrive = null;
					return;
				}
				else if(sectorSize >= 65536) {
					MainClass.logger.warn("Mapped drive %s, but it has a sector size that is absurdly large. This drive cannot be used.");
					mappedDrive = null;
					return;
				}
				this.sectorSize = (byte)(sectorSize >> 8);
				int numSectors = simpleIntCall(mappedDrive, "getCapacity") / sectorSize;
				if(numSectors > 65535) numSectors = 65535;
				numSectors1 = (byte)(numSectors);
				numSectors2 = (byte)(numSectors>>8);
			}
		}
		public void load(NBTTagCompound nbt) {
			byte[] regs = nbt.getByteArray("regs");
			addr1 = regs[0];
			addr2 = regs[1];
			sectorSize = regs[2];
			numSectors1 = regs[3];
			numSectors2 = regs[4];
			sector1 = regs[5];
			sector2 = regs[6];
			if(nbt.hasKey("mappedDrive"))
				mappedDrive = nbt.getString("mappedDrive");
			else
				mappedDrive = null;
			if(nbt.hasKey("sectorReadPos"))
				sectorReadPos = nbt.getInteger("sectorReadPos");
			else
				sectorReadPos = -1;
			if(nbt.hasKey("sectorWritePos"))
				sectorWritePos = nbt.getInteger("sectorWritePos");
			else
				sectorWritePos = -1;
			if(nbt.hasKey("sectorBuffer"))
				sectorBuffer = nbt.getByteArray("sectorBuffer");
			else
				sectorBuffer = null;
		}
		public void save(NBTTagCompound nbt) {
			nbt.setByteArray("regs", new byte[]{addr1, addr2, sectorSize, numSectors1, numSectors2, sector1, sector2});
			if(mappedDrive != null)
				nbt.setString("mappedDrive", mappedDrive);
			if(sectorReadPos >= 0)
				nbt.setInteger("sectorReadPos", sectorReadPos);
			if(sectorWritePos >= 0)
				nbt.setInteger("sectorWritePos", sectorWritePos);
			if(sectorBuffer != null)
				sectorBuffer = nbt.getByteArray("sectorBuffer");
		}
	}
	protected DiskDrive diskDrives[] = new DiskDrive[]{new DiskDrive(),new DiskDrive(),new DiskDrive(),new DiskDrive()};
	
	private static class DisplayMode {
		public final int w, h;
		DisplayMode(int w, int h) { this.w = w; this.h = h; }
	}
	private static final DisplayMode[] DISPLAY_MODES = new DisplayMode[]{
			new DisplayMode(50, 16),
			new DisplayMode(80, 25),
			new DisplayMode(80, 30),
			new DisplayMode(120, 37),
			new DisplayMode(120, 45),
			new DisplayMode(160, 50),
	};

	protected class Terminal {
		private static final byte BEEP_COOLDOWN_LENGTH = 3;
		boolean initialized, autoLinebreak, didScrollForLinebreak, didOutputChar;
		String mappedGPU, mappedScreen;
		byte curW, curH, maxW, maxH;
		byte[] codeUnitBuf = new byte[4];
		int codeUnitPos;
		int cursorX, cursorY;
		byte beepCooldown;
		String hiddenCharacter;
		int currentMode;
		public byte read() {
			return (byte)(initialized ? 0 : -1);
		}
		private void reinitialize() throws LimitReachedException {
			initialized = false;
			mappedGPU = null;
			mappedScreen = null;
			Map<String,String> components = parent.machine.components();
			synchronized(components) {
				for(Map.Entry<String, String> entry : components.entrySet()) {
					if(mappedGPU == null && entry.getValue().equals("gpu"))
						mappedGPU = entry.getKey();
					if(mappedScreen == null && entry.getValue().equals("screen"))
						mappedScreen = entry.getKey();
					if(mappedGPU != null && mappedScreen != null) break;
				}
			}
			if(mappedGPU != null && mappedScreen != null) {
				if(!inSynchronizedCall) {
					needSynchronizedCall = true;
					throw new EscapeRetry(lastFetchedOpcodeAddress);
				}
				simpleVoidCall(mappedGPU, "bind", mappedScreen, true);
				cursorX = 1;
				cursorY = 1;
				currentMode = 0;
				DisplayMode max = simpleDisplayModeCall(mappedGPU, "maxResolution");
				if(max == null) return;
				else if(max.w > 255 || max.h > 255 || max.w < DISPLAY_MODES[0].w || max.h < DISPLAY_MODES[0].h) {
					MainClass.logger.error("This screen/gpu combo has a weird max resolution... sticking our head in the sand.");
					return;
				}
				maxW = (byte)max.w;
				maxH = (byte)max.h;
				curW = (byte)DISPLAY_MODES[0].w;
				curH = (byte)DISPLAY_MODES[0].h;
				codeUnitPos = 0;
				hiddenCharacter = null;
				simpleVoidCall(mappedGPU, "setResolution", curW&0xFF, curH&0xFF);
				simpleVoidCall(mappedGPU, "fill", 1, 1, curW&0xFF, curH&0xFF, " ");
				autoLinebreak = true;
				initialized = true;
			}
		}
		public void write(byte value) throws LimitReachedException {
			if(!initialized) {
				if(value == 0) reinitialize();
				return;
			}
			if(hiddenCharacter != null) {
				if(value == (byte)0xFE) return; // freebie
				simpleVoidCall(mappedGPU, "set", cursorX, cursorY, hiddenCharacter);
				hiddenCharacter = null;
			}
			/* 
			 * Note: Any operation here can be interrupted due to LimitReachedException. As such, they should be programmed so that,
			 * if the exact same write is repeated after getting partway through executing, the result will be the same as if it
			 * hadn't been interrupted.
			 */
			switch(value&0xFF) {
			case 0x00:
				// Reinitialize
				reinitialize();
				break;
			case 0x03:
				// Unbackspace
				if(cursorX < (curW&0xFF)) ++cursorX;
				break;
			case 0x04:
				// Clear screen
				simpleVoidCall(mappedGPU, "fill", 1, 1, curW&0xFF, curH&0xFF, " ");
				cursorX = 1;
				cursorY = 1;
				break;
			case 0x05:
				// Get terminal info
				parent.machine.signal("terminal_size", new byte[]{curW, curH, maxW, maxH});
				break;
			case 0x06:
				// Cycle to next mode, clear screen, get terminal info
				if(!inSynchronizedCall) {
					needSynchronizedCall = true;
					throw new EscapeRetry(lastFetchedOpcodeAddress);
				}
				simpleVoidCall(mappedGPU, "fill", 1, 1, curW&0xFF, curH&0xFF, " ");
				int newMode = currentMode;
				do {
					newMode = (newMode + 1) % DISPLAY_MODES.length;
				} while(DISPLAY_MODES[newMode].w > (maxW&0xFF)
						|| DISPLAY_MODES[newMode].h > (maxH&0xFF));
				simpleVoidCall(mappedGPU, "setResolution", DISPLAY_MODES[newMode].w, DISPLAY_MODES[newMode].h);
				currentMode = newMode;
				curW = (byte)DISPLAY_MODES[newMode].w;
				curH = (byte)DISPLAY_MODES[newMode].h;
				cursorX = 1;
				cursorY = 1;
				parent.machine.signal("terminal_size", new byte[]{curW, curH, maxW, maxH});
				break;
			case 0x07:
				if(beepCooldown == 0) {
					// Beep at nemo's favorite frequency
					parent.machine.beep((short)456, (short)150);
					beepCooldown = BEEP_COOLDOWN_LENGTH;
				}
				break;
			case 0x08:
				// Backspace
				if(cursorX > 1) --cursorX;
				break;
			case 0x09: {
				// Horizontal tab
				int newCursorX = cursorX;
				do { ++newCursorX; } while((newCursorX & 7) != 0);
				if(newCursorX > (curW&0xFF)) {
					if(autoLinebreak) {
						linefeed();
						cursorX = 0;
					}
					else cursorX = (curW&0xFF);
				}
				else cursorX = newCursorX;
			} break;
			case 0x0A:
				// Carriage return
				cursorX = 1;
				break;
			case 0x0B:
				// Clear to end of line
				simpleVoidCall(mappedGPU, "fill", cursorX, cursorY, (curW&0xFF)-cursorX, 1);
				break;
			case 0x0C:
				// Line feed
				linefeed();
				break;
			case 0x0D:
				// Line break
				linefeed();
				cursorX = 1;
				break;
			case 0x0E:
				// Disable auto linebreak
				autoLinebreak = false;
				break;
			case 0x0F:
				// Enable auto linebreak
				autoLinebreak = true;
				break;
			case 0x13:
				// Shutdown
				initialized = false;
				break;
			case 0x15:
				// it wouldn't fit into the 6502 ecosystem if it didn't have a useful, buggy, undocumented feature :)
				if(cursorY > 1) --cursorY;
				break;
			case 0x01: case 0x02: case 0x10: case 0x11: case 0x12: case 0x14: case 0x16:
			case 0x17: case 0x18: case 0x19: case 0x1A: case 0x1B: case 0x1C: case 0x1D:
			case 0x1E: case 0x1F:
				// undefined
				break;
			default:
				outputCharByte(value);
				break;
			case 0xFE:
				// cursor blink
				hiddenCharacter = simpleStringCall(mappedGPU, "get", cursorX, cursorY);
				simpleVoidCall(mappedGPU, "set", cursorX, cursorY, "▎");
				if(hiddenCharacter == null) hiddenCharacter = " "; // this is apparently a thing.
				break;
			case 0xF8: case 0xF9: case 0xFA: case 0xFB: case 0xFC: case 0xFD: case 0xFF:
				// do nothing
				break;
			}
		}
		private void linefeed() throws LimitReachedException {
			if(cursorY < (curH&0xFF)) ++cursorY;
			else {
				if(!didScrollForLinebreak) {
					simpleVoidCall(mappedGPU, "copy", 1, 2, curW&0xFF, (curH&0xFF)-1, 0, -1);
					didScrollForLinebreak = true;
				}
				simpleVoidCall(mappedGPU, "fill", 1, cursorY, curW&0xFF, 1, " ");
				cursorX = 0;
				didScrollForLinebreak = false;
			}
		}
		/* this has to be written the way it is because the final write (and ONLY the final write) can get interrupted */
		private void outputCharByte(byte v) throws LimitReachedException {
			String charToOutput = null;
			if(codeUnitPos == 0) {
				codeUnitBuf[codeUnitPos++] = v;
				if((codeUnitBuf[0] & 0x80) == 0)
					charToOutput = String.valueOf((char)codeUnitBuf[0]);
			}
			else if((codeUnitBuf[0] & 0x80) == 0)
				charToOutput = String.valueOf((char)codeUnitBuf[0]);
			else if((codeUnitBuf[0] & 0xE0) == 0xC0) {
				if(codeUnitPos < 2)
					codeUnitBuf[codeUnitPos++] = v;
				if(codeUnitPos == 2) {
					int codePoint = ((codeUnitBuf[0]&0x1F)<<6) | ((codeUnitBuf[1]&0x3F));
					if(codePoint >= 0x80 && codePoint < 0x800) charToOutput = String.valueOf((char)codePoint);
					else charToOutput = "�";
				}
			}
			else if((codeUnitBuf[0] & 0xF0) == 0xE0) {
				if(codeUnitPos < 3) codeUnitBuf[codeUnitPos++] = v;
				if(codeUnitPos == 3) {
					int codePoint = ((codeUnitBuf[0]&0x0F)<<12) | ((codeUnitBuf[1]&0x3F)<<6) | ((codeUnitBuf[2]&0x3F));
					if(codePoint >= 0x800 && codePoint < 0x10000
							&& (codePoint < 0xD800 || codePoint >= 0xE000)) charToOutput = String.valueOf((char)codePoint);
					else charToOutput = "�";
				}
			}
			else if((codeUnitBuf[0] & 0xF8) == 0xF0) {
				if(codeUnitPos < 4) codeUnitBuf[codeUnitPos++] = v;
				if(codeUnitPos == 4) {
					int codePoint = ((codeUnitBuf[0]&0x0F)<<18) | ((codeUnitBuf[1]&0x3F)<<12) | ((codeUnitBuf[2]&0x3F)<<6) | ((codeUnitBuf[2]&0x3F));
					if(codePoint >= 0x10000 && codePoint < 0x110000) charToOutput = new String(codeUnitBuf);
					else charToOutput = "�";
				}
			}
			else charToOutput = "�";
			if(charToOutput != null) {
				if(!didOutputChar) {
					simpleVoidCall(mappedGPU, "set", cursorX, cursorY, charToOutput);
					didOutputChar = true;
				}
				// TODO: character width, sigh
				int newCursorX = cursorX + 1;
				if(newCursorX > (curW&0xFF)) {
					if(autoLinebreak) {
						newCursorX = 0;
						linefeed();
					}
				}
				cursorX = newCursorX;
				didOutputChar = false;
				codeUnitPos = 0;
			}
		}
		public void load(NBTTagCompound nbt) {
			if(nbt.hasKey("autoLinebreak")) autoLinebreak = true;
			if(nbt.hasKey("didScrollForLinebreak")) didScrollForLinebreak = true;
			if(nbt.hasKey("didOutputChar")) didOutputChar = true;
			mappedGPU = nbt.getString("mappedGPU");
			mappedScreen = nbt.getString("mappedScreen");
			byte[] dm = nbt.getByteArray("displayMode");
			curW = dm[0];
			curH = dm[1];
			maxW = dm[2];
			maxH = dm[3];
			for(currentMode = 0; currentMode < DISPLAY_MODES.length; ++currentMode) {
				if(DISPLAY_MODES[currentMode].w == (curW&0xFF) && DISPLAY_MODES[currentMode].h == (curH&0xFF)) {
					break;
				}
			}
			if(currentMode >= DISPLAY_MODES.length) {
				MainClass.logger.info("Warning: a saved terminal was set to a mode that is not currently supported");
				currentMode = DISPLAY_MODES.length-1;
			}
			cursorX = nbt.getByte("cursorX") & 0xFF;
			cursorY = nbt.getByte("cursorY") & 0xFF;
			if(nbt.hasKey("hiddenCharacter"))
				hiddenCharacter = nbt.getString("hiddenCharacter");
			if(nbt.hasKey("codeUnitPos")) {
				codeUnitPos = nbt.getByte("codeUnitPos");
				codeUnitBuf = nbt.getByteArray("codeUnitBuf");
			}
			if(nbt.hasKey("beepCooldown"))
				beepCooldown = nbt.getByte("beepCooldown");
			initialized = true;
		}
		public NBTTagCompound save() {
			if(!initialized) return null;
			NBTTagCompound nbt = new NBTTagCompound();
			if(autoLinebreak)
				nbt.setBoolean("autoLinebreak", true);
			if(didScrollForLinebreak)
				nbt.setBoolean("didScrollForLinebreak", true);
			if(didOutputChar)
				nbt.setBoolean("didOutputChar", true);
			nbt.setString("mappedGPU", mappedGPU);
			nbt.setString("mappedScreen", mappedScreen);
			nbt.setByteArray("displayMode", new byte[]{curW, curH, maxW, maxH});
			if(codeUnitPos > 0) {
				for(int i = codeUnitPos; i < codeUnitBuf.length; ++i)
					codeUnitBuf[i] = 0;
				nbt.setByteArray("codeUnitBuf", codeUnitBuf);
				nbt.setByte("codeUnitPos", (byte)codeUnitPos);
			}
			nbt.setByte("cursorX", (byte)cursorX);
			nbt.setByte("cursorY", (byte)cursorY);
			if(hiddenCharacter != null)
				nbt.setString("hiddenCharacter", hiddenCharacter);
			if(beepCooldown != 0)
				nbt.setByte("beepCooldown", beepCooldown);
			return nbt;
		}
		public void coolIt() {
			if(beepCooldown > 0) beepCooldown = 0;
		}
	}
	protected Terminal terminal = new Terminal();
	
	protected void updateNMI() {
		parent.cpu.setNMI(naughtyNMI || watchdog == -1);
	}
	
	public MMU(OCMOS parent) {
		this.parent = parent;
	}
	
	public byte readROMByte(short addr) {
		addr &= ROM_MASK;
		if(!romMappingValid)
			attemptShadowEEPROM();
		assert(romMappingValid);
		return romArray[addr];
	}
	
	private static boolean arrayMatch(byte[] src, byte[] ck, int offset) throws ArrayIndexOutOfBoundsException {
		for(int n = 0; n < ck.length; ++n) {
			if(src[n+offset] != ck[n]) return false;
		}
		return true;
	}
	
	private void mapROMImage(byte[] src) {
		boolean isCABEImage = false;
		int shimLength = 0;
		try {
			if(arrayMatch(src, CABE_PREFIX_PART_1, 0)) {
				while(src[CABE_PREFIX_PART_1.length + shimLength] == '=') ++shimLength;
				if(arrayMatch(src, CABE_PREFIX_PART_2, CABE_PREFIX_PART_1.length + shimLength)) {
					int start = CABE_PREFIX_PART_1.length + shimLength + CABE_PREFIX_PART_2.length;
					int end = Math.min(ROM_SIZE, src.length);
					for(int n = start; n < end - 2 - shimLength; ++n) {
						if(src[n] == ']' && src[n+1+shimLength] == ']') {
							boolean shimPresent = true;
							for(int m = n + 1; m < n + 1 + shimLength; ++m) {
								if(src[m] != '=') {
									shimPresent = false;
									break;
								}
							}
							if(shimPresent) {
								end = n;
								isCABEImage = true;
								break;
							}
						}
					}
					if(isCABEImage) {
						int length = end-start;
						for(int n = 0; n < length; ++n) {
							romArray[n] = src[n+start];
						}
						if(length > 6 && length < ROM_SIZE) {
							/* copy the interrupt vectors to the end of the array */
							for(int offset = 1; offset <= 6; ++offset) {
								romArray[ROM_SIZE-offset] = romArray[length-offset];
								romArray[length-offset] = -1;
							}
						}
					}
				}
			}
			if(!isCABEImage) {
				int length = Math.min(ROM_SIZE, src.length);
				for(int n = 0; n < length; ++n) {
					romArray[n] = src[n];
				}
				if(length > 6 && length < ROM_SIZE) {
					/* copy the interrupt vectors to the end of the array */
					for(int offset = 1; offset <= 6; ++offset) {
						romArray[length-offset] = -1;
						romArray[ROM_SIZE-offset] = src[src.length-offset];
					}
				}
			}
		}
		catch(ArrayIndexOutOfBoundsException ex) {}
	}
	
	public void attemptShadowEEPROM() {
		Map<String,String> components = parent.machine.components();
		String addr = null;
		for(Map.Entry<String, String> entry : components.entrySet()) {
			if(entry.getValue().equals("eeprom")) {
				addr = entry.getKey();
				break;
			}
		}
		// one way or another...
		romMappingValid = true;
		for(int n = 0; n < romArray.length; ++n) romArray[n] = -1;
		if(addr == null) {
			byte[] builtInBIOS = MainClass.instance.getBuiltInBIOS();
			if(builtInBIOS != null) mapROMImage(builtInBIOS);
		}
		else {
			try {
				Object[] result = parent.machine.invoke(addr, "get", new Object[0]);
				if(result == null || result.length < 1 || !(result[0] instanceof byte[])) {
					MainClass.logger.error("Got an invalid result from calling \"get\" on an EEPROM");
				}
				else {
					mapROMImage((byte[])result[0]);
				}
			}
			catch(NullPointerException e) {
				MainClass.logger.error("NullPointerException while mapping EEPROM", e);
			}
			catch(LimitReachedException e) {
				MainClass.logger.error("LimitReachedException while mapping EEPROM", e);
			}
			catch(Exception e) {
				MainClass.logger.error("Unexpected exception while mapping EEPROM", e);
			}
		}
	}
	
	private void simpleVoidCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			parent.machine.invoke(addr, commandName, args);
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleVoidCall", e);
		}
	}
	
	private byte[] simpleByteArrayCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			Object[] result = parent.machine.invoke(addr, commandName, args);
			if(result != null && result.length > 0 && result[0] instanceof byte[])
				return (byte[])result[0];
			else return null;
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleByteArrayCall", e);
			return null;
		}
	}
	
	private String simpleStringCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			Object[] result = parent.machine.invoke(addr, commandName, args);
			if(result != null && result.length > 0 && result[0] instanceof String)
				return (String)result[0];
			else return null;
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleStringCall", e);
			return null;
		}
	}
	
	private byte simpleByteCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			Object[] result = parent.machine.invoke(addr, commandName, args);
			if(result != null && result.length > 0 && result[0] instanceof Number)
				return ((Number)result[0]).byteValue();
			else return -1;
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleNumberCall", e);
			return -1;
		}
	}

	private int simpleIntCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			Object[] result = parent.machine.invoke(addr, commandName, args);
			if(result != null && result.length > 0 && result[0] instanceof Number)
				return ((Number)result[0]).intValue();
			else return -1;
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleNumberCall", e);
			return -1;
		}
	}

	private DisplayMode simpleDisplayModeCall(String addr, String commandName, Object... args) throws LimitReachedException {
		try {
			Object[] result = parent.machine.invoke(addr, commandName, args);
			if(result != null && result.length > 1 && result[0] instanceof Number && result[1] instanceof Number)
				return new DisplayMode(((Number)result[0]).intValue(), ((Number)result[1]).intValue());
			else return null;
		}
		catch(LimitReachedException e) {
			throw e;
		}
		catch(Exception e) {
			MainClass.logger.warn("Unexpected exception during simpleDisplayModeCall", e);
			return null;
		}
	}

	private byte toBCDByte(int n) {
		return (byte)(((n/10)<<4) | (n%10));
	}
	
	private byte readByte(short addr, boolean isOpcodeRead) {
		if(naughtyOperationInProgress) {
			modeTransitionState = null;
			--cycleBudget;
			return naughtyByte;
		}
		if(clearNaughtyNMIOnNextRead) {
			naughtyNMI = false;
			updateNMI();
			--cycleBudget;
			return 0;
		}
		if(!isOpcodeRead) {
			if(modeTransitionState == ModeTransitionState.AWAITING_TWO_MORE_READS)
				modeTransitionState = ModeTransitionState.AWAITING_ONE_MORE_READ;
			else if(modeTransitionState == ModeTransitionState.AWAITING_ONE_MORE_READ)
				modeTransitionState = ModeTransitionState.TRANSITION_ON_NEXT_READ;
			else if(modeTransitionState == ModeTransitionState.TRANSITION_ON_NEXT_READ) {
				modeTransitionState = null;
				if(transitioningToUserMode) setFlag(FLAG_USER_MODE);
				else clearFlag(FLAG_USER_MODE);
			}
			else modeTransitionState = null;
		}
		if((addr&0xF000) >= 0xF000 && !getFlag(FLAG_USER_MODE) && getFlag(FLAG_MAPPED_ROM)) {
			if(!romMappingValid) attemptShadowEEPROM();
			cycleBudget -= MainClass.instance.getROMLatency(cpuTier);
			return romArray[addr&ROM_MASK];
		}
		int bank;
		int segment = (addr&0xFFFF)>>>12;
		if(getFlag(FLAG_USER_MODE)) {
			bank = (mmuRegisters[MMU_REG_USER_BANKS_LOW + segment]&0xFF)|((mmuRegisters[MMU_REG_USER_BANKS_HIGH + segment]&0xFF)<<8);
			if((bank & (isOpcodeRead ? 0x9000 : 0x8000)) != 0) {
				naughtyOperation((byte)(isOpcodeRead ? 0x00 : 0x01), addr);
				--cycleBudget;
				return 0;
			}
		}
		else if(segment == 0)
			bank = 0;
		else
			bank = (mmuRegisters[MMU_REG_SUPERVISOR_BANKS_LOW + segment]&0xFF)|((mmuRegisters[MMU_REG_SUPERVISOR_BANKS_HIGH + segment]&0xFF)<<8);
		bank &= 0x1FFF;
		if(bank == 0) {
			addr &= 0x0FFF;
			if(addr < 0x200) {
				--cycleBudget;
				return builtInMemory[addr&0x1FF];
			}
			else if(addr < 0x240) {
				--cycleBudget;
				return mmuRegisters[addr&0x3F];
			}
			else {
				cycleBudget -= MainClass.instance.getIOLatency(cpuTier);
				try {
					if(addr >= 0x280 && addr <= 0x2F5) {
						switch(addr) {
						case 0x280:
							return simpleByteCall(mappedRedstoneAddr, "getWakeThreshold");
						case 0x2F4:
							return simpleByteCall(mappedRedstoneAddr, "getWirelessInput");
						case 0x2F5:
							return simpleByteCall(mappedRedstoneAddr, "getWirelessOutput");
						default:
							int side = addr%6;
							int wat = addr/6-1;
							if(wat == -1)
								return simpleByteCall(mappedRedstoneAddr, "getComparatorInput", side);
							boolean isOutput = (wat&1) != 0;
							int color = wat/2-1;
							if(color == -1)
								return simpleByteCall(mappedRedstoneAddr, isOutput ? "getOutput" : "getInput", side);
							else
								return simpleByteCall(mappedRedstoneAddr, isOutput ? "getBundledOutput" : "getBundledInput", side, color);
						}
					}
					else if(addr >= 0x260 && addr <= 0x27F) {
						return diskDrives[(addr-0x260)>>3].read(addr&7);
					}
					else if(addr >= 0x248 && addr <= 0x24F) {
						switch(addr) {
						case 0x248: return toBCDByte((int)(parent.machine.worldTime()%20));
						case 0x249: return toBCDByte((int)(parent.machine.worldTime()/20%60));
						case 0x24A: return toBCDByte((int)((parent.machine.worldTime()+6000)/(20*60)%20));
						case 0x24B: return (byte)((parent.machine.worldTime()+6000)/(20*60*20));
						case 0x24C: return (byte)(((parent.machine.worldTime()+6000)/(20*60*20))>>>8);
						case 0x24D: return (byte)((parent.machine.worldTime()/24000)&7);
						case 0x24E: return (byte)(int)Math.floor(parent.machine.upTime()*20+0.5);
						case 0x24F: return (byte)((int)Math.floor(parent.machine.upTime()*20+0.5)>>>8);
						}
					}
					else if(addr >= 0x250 && addr <= 0x25F) {
						if(nodeAddress == null) retrieveNodeAddress();
						return nodeAddress[addr-0x250];
					}
					else if(addr >= 0x280 && addr <= 0x28F) {
						if(tmpAddress == null) retrieveTmpAddress();
						return tmpAddress[addr-0x280];
					}
					else switch(addr) {
					case 0x240: return signalPort.read();
					case 0x241: return watchdog;
					case 0x242: return invokePort.read();
					case 0x243: return (byte)Math.floor(((Connector)parent.machine.node()).globalBuffer()*255/((Connector)parent.machine.node()).globalBufferSize());
					case 0x244: /* open bus */ break;
					case 0x245: /* open bus */ break;
					case 0x246:
						if(componentList == null || currentComponentInList >= componentList.length
						|| currentPositionInComponent >= componentList[currentComponentInList].length) {
							triggerOverflow();
							return -1;
						}
						else return componentList[currentComponentInList][currentPositionInComponent++];
					case 0x247: return (byte)(debugLineStream == null ? 0 : -1);
					case 0x2FF: return terminal.read();
					case 0x287:
						// unreachable hack to inform javac that yes, we can, in fact, have a NoSuchMethodException in this block
						throw new NoSuchMethodException();
					}
				}
				catch(NoSuchMethodException e) {
					return -1;
				}
				catch(LimitReachedException e) {
					executionResult = new ExecutionResult.Sleep(1);
					throw new EscapeRetry(lastFetchedOpcodeAddress);
				}
			}
		}
		else if(bank <= numMappedRamBanks) {
			cycleBudget -= MainClass.instance.getRAMLatency(cpuTier, ramTier);
			return ramArray[((bank-1)<<12)|(addr&0x0FFF)];
		}
		return -1; // fake open bus
	}
	
	@Override
	public byte readByte(short addr) {
		return readByte(addr, false);
	}

	@Override
	public byte readVectorByte(short addr) {
		naughtyOperationInProgress = false;
		switch(addr&0xFFFF) {
		case 0xfffa: // NMI
			if(getFlag(FLAG_USER_MODE)) {
				clearFlag(FLAG_USER_MODE);
				setFlag(FLAG_LAST_NMI_WAS_USER);
			}
			else clearFlag(FLAG_LAST_NMI_WAS_USER);
			break;
		case 0xfffc: // Reset
			break;
		case 0xfffe: // IRQ/BRK
			if(getFlag(FLAG_USER_MODE)) {
				clearFlag(FLAG_USER_MODE);
				setFlag(FLAG_LAST_IRQBRK_WAS_USER);
			}
			else clearFlag(FLAG_LAST_IRQBRK_WAS_USER);
			break;
		}
		return readByte(addr);
	}

	public void reset() {
		watchdog = 0;
		builtInMemory = new byte[builtInMemory.length];
		mmuRegisters = new byte[mmuRegisters.length];
		if(ramArray != null)
			ramArray = new byte[ramArray.length];
		builtInMemory[0] = FLAG_MAPPED_ROM;
		complexCrashStream = null;
		debugLineStream = MainClass.instance.shouldAllowDebugDevice() ? new ByteArrayOutputStream() : null;
		for(byte bank = 0; bank < 16; ++bank) {
			if(bank != 0) {
				mmuRegisters[MMU_REG_SUPERVISOR_BANKS_LOW + bank] = bank;
				mmuRegisters[MMU_REG_SUPERVISOR_BANKS_HIGH + bank] = 0;
			}
			mmuRegisters[MMU_REG_USER_BANKS_LOW + bank] = bank;
			mmuRegisters[MMU_REG_USER_BANKS_HIGH + bank] = 0;
		}
		reloadComponentList();
	}

	@Override
	public byte readOpcodeByte(short addr) {
		if(overflow && parent.cpu.getSOWasSeen()) {
			overflow = false;
			parent.cpu.setSO(false);
		}
		if(naughtyOperationInProgress) {
			modeTransitionState = null;
			naughtyOperationInProgress = false;
			clearNaughtyNMIOnNextRead = true;
			return (byte)0xC2;// one of the many NOPs
		}
		if(modeTransitionState == ModeTransitionState.AWAITING_FIRST_OPCODE_FETCH)
			; // ... handle later
		else if(modeTransitionState == ModeTransitionState.TRANSITION_ON_NEXT_OPCODE_FETCH) {
			modeTransitionState = null;
			if(transitioningToUserMode) setFlag(FLAG_USER_MODE);
			else clearFlag(FLAG_USER_MODE);
		}
		else modeTransitionState = null;
		lastFetchedOpcodeAddress = addr;
		naughtyOperationInProgress = false;
		if(getFlag(FLAG_USER_MODE) && (addr&0xFFF)==0xFFF) {
			// extra read to check privilege
			lastFetchedOpcode = (byte)0xC2;
			naughtyByte = (byte)0xC2;
			readByte((short)(addr+1), true);
			if(naughtyOperationInProgress) return lastFetchedOpcode;
		}
		byte ret = readByte(addr, true);
		if(!naughtyOperationInProgress && getFlag(FLAG_USER_MODE)) {
			switch(ret) {
			case (byte)0xCB: if(getFlag(FLAG_PRIVILEGED_WAI)) naughtyOperation((byte)0x03, addr); break;
			case (byte)0xDB: if(getFlag(FLAG_PRIVILEGED_STP)) naughtyOperation((byte)0x03, addr); break;
			case 0x78: if(getFlag(FLAG_PRIVILEGED_SEI)) naughtyOperation((byte)0x03, addr); break;
			}
		}
		lastFetchedOpcode = ret;
		if((ret & 0x0F) == 0x0F) {
			if(ret < 0) naughtyByte = 0; // BBS*
			else naughtyByte = -1; // BBR*
		}
		else if((ret & 0xE0) == 0x20) naughtyByte = -1; // covers the ANDs
		else if((ret & 0xE0) == 0xE0) naughtyByte = -1; // covers the SBCs
		else naughtyByte = 0;
		if(!naughtyOperationInProgress && modeTransitionState == ModeTransitionState.AWAITING_FIRST_OPCODE_FETCH) {
			if(ret == 0x6C) modeTransitionState = ModeTransitionState.AWAITING_TWO_MORE_READS;
		}
		return ret;
	}

	@Override
	public void writeByte(short addr, byte value) {
		if(naughtyOperationInProgress) {
			--cycleBudget;
			return;
		}
		int bank;
		int segment = (addr&0xFFFF)>>>12;
		if(getFlag(FLAG_USER_MODE)) {
			bank = (mmuRegisters[MMU_REG_USER_BANKS_LOW + segment]&0xFF)|((mmuRegisters[MMU_REG_USER_BANKS_HIGH + segment]&0xFF)<<8);
			if((bank & 0x4000) != 0) {
				naughtyOperation((byte)0x02, addr);
				builtInMemory[4] = value;
				--cycleBudget;
				return;
			}
		}
		else if(segment == 0)
			bank = 0;
		else
			bank = (mmuRegisters[MMU_REG_SUPERVISOR_BANKS_LOW + segment]&0xFF)|((mmuRegisters[MMU_REG_SUPERVISOR_BANKS_HIGH + segment]&0xFF)<<8);
		if(fakePushStage > 0 && ((addr&0xFFFF)<0x200)) {
			switch(fakePushStage--) {
			case 3: value = (byte)(fakePushAddress>>>8); break;
			case 2: value = (byte)(fakePushAddress&0xFF); break;
			}
		}
		bank &= 0x1FFF;
		if(bank == 0) {
			addr &= 0x0FFF;
			if(addr < 0x200) {
				if(addr == 0) {
					if((value & FLAG_USER_MODE) != (builtInMemory[0] & FLAG_USER_MODE)) {
						queueModeTransition((value & FLAG_USER_MODE) != 0);
						value = (byte)(value & ~FLAG_USER_MODE | (builtInMemory[0] & FLAG_USER_MODE));
					}
				}
				builtInMemory[addr&0x1FF] = value;
				--cycleBudget;
				return;
			}
			else if(addr < 0x240) {
				if(addr != 0x200 && addr != 0x210) {
					mmuRegisters[addr&0x3F] = value;
					--cycleBudget;
					return;
				}
			}
			else {
				cycleBudget -= MainClass.instance.getIOLatency(cpuTier);
				try {
					if(addr >= 0x280 && addr <= 0x2F5) {
						if(!inSynchronizedCall) {
							needSynchronizedCall = true;
							throw new EscapeRetry(lastFetchedOpcodeAddress);
						}
						switch(addr) {
						case 0x280:
							simpleVoidCall(mappedRedstoneAddr, "setWakeThreshold", value&0xFF);
							break;
						case 0x2F4:
							/* ignored */
							break;
						case 0x2F5:
							simpleVoidCall(mappedRedstoneAddr, "setWirelessOutput", value&0xFF);
							break;
						default:
							int side = (addr-0x281)%6;
							int wat = (addr-0x281)/6-1;
							if(wat == -1)
								break;
							boolean isOutput = (wat&1) != 0;
							if(!isOutput) break;
							int color = wat/2-1;
							if(color == -1)
								simpleVoidCall(mappedRedstoneAddr, "setOutput", side, value&0xFF);
							else
								simpleVoidCall(mappedRedstoneAddr, "setBundledOutput", side, color, value&0xFF);
							break;
						}
					}
					else if(addr >= 0x260 && addr <= 0x27F) {
						diskDrives[(addr-0x260)>>3].write(addr&7, value);
					}
					else if(addr >= 0x248 && addr <= 0x24F) {
						if(!needIRQ()) {
							// can't sleep when there are INTERRUPTS to eat
							executionResult = new ExecutionResult.Sleep(value&0xFF);
						}
					}
					else if(addr >= 0x250 && addr <= 0x25F) {
						/* ignore */
					}
					else if(addr >= 0x280 && addr <= 0x28F) {
						/* ignore */
					}
					else switch(addr) {
					case 0x240: signalPort.write(value); break;
					case 0x241: watchdog = value; updateNMI(); break;
					case 0x242: invokePort.write(value); break;
					case 0x243: parent.disposeValue(value, parent.machine); break;
					case 0x244: executionResult = new ExecutionResult.Error(String.format("Crash! $%02X", value)); break;
					case 0x245:
						if(complexCrashStream == null)
							complexCrashStream = new ByteArrayOutputStream();
						if(complexCrashStream.size() >= 2048 || value == 0) {
							try {
								executionResult = new ExecutionResult.Error(complexCrashStream.toString("UTF-8"));
							} catch (UnsupportedEncodingException e) {
								MainClass.logger.error("Eclipse said this exception would happen. I said I knew better. If you're seeing this message, either you're reading my source code or I was wrong.", e);
							}
						}
						else complexCrashStream.write(value);
						break;
					case 0x246:
						switch(value) {
						case 0: incrementComponentListCursor(); break;
						case -1: reloadComponentList(); break;
						}
						break;
					case 0x247:
						if(debugLineStream != null) {
							if(value == '\n' || value == '\r' || value == 0 || debugLineStream.size() == 256) {
								try {
									MainClass.logger.info("Debug output: "+debugLineStream.toString("UTF-8"));
								} catch (UnsupportedEncodingException e) {
									MainClass.logger.error("Eclipse said this exception would happen. I said I knew better. If you're seeing this message, either you're reading my source code or I was wrong.", e);
								}
								debugLineStream.reset();
							}
							if(!(value == '\n' || value == '\r' || value == 0) && debugLineStream.size() < 256) {
								debugLineStream.write(value);
							}
						}
						break;
					case 0x2FF:
						terminal.write(value);
						break;
					case 0x287:
						// unreachable hack to inform javac that yes, we can, in fact, have a NoSuchMethodException in this block
						throw new NoSuchMethodException();
					}
				}
				catch(NoSuchMethodException e) {
					// do nothing
				}
				catch(LimitReachedException e) {
					executionResult = new ExecutionResult.Sleep(1);
					throw new EscapeRetry(lastFetchedOpcodeAddress);
				}
			}
		}
		else if(bank <= numMappedRamBanks) {
			ramArray[((bank-1)<<12)|(addr&0x0FFF)] = value;
			cycleBudget -= MainClass.instance.getRAMLatency(cpuTier, ramTier);
		}
	}
	
	public boolean recomputeMemory(Iterable<ItemStack> components) {
		numMappedRamBanks = 0;
		ramTier = 3;
		for(ItemStack stack : components) {
			Item driver = Driver.driverFor(stack);
			if(driver instanceof li.cil.oc.api.driver.item.Memory) {
				double amount = ((li.cil.oc.api.driver.item.Memory)driver).amount(stack);
				int tier = (int)Math.floor(amount);
				if(tier < 0) tier = 0;
				else if(tier > 2) tier = 2;
				if(tier < ramTier) ramTier = tier;
				numMappedRamBanks += MainClass.instance.getMemoryModuleBankCount(amount);
			}
			else if(driver instanceof Processor) {
				cpuTier = driver.tier(stack);
				parent.cpuCyclesPerTick = MainClass.instance.getCPUCyclesPerTick(cpuTier);
			}
		}
		if(numMappedRamBanks > 0x1FFF) numMappedRamBanks = 0x1FFF;
		remapMemory();
		return true;
	}
	
	public void runSynchronized() {
		inSynchronizedCall = true;
		cycleBudget = 1;
		try {
			parent.cpu.step();
			needSynchronizedCall = false;
		}
		catch(EscapeRetry e) {}
		cycleBudget = 0;
		inSynchronizedCall = false;
	}
	
	public void remapMemory() {
		romMappingValid = false;
		int newMemoryAmount = numMappedRamBanks * BANK_SIZE;
		if(ramArray == null || newMemoryAmount > ramArray.length) {
			if(ramArray == null)
				ramArray = new byte[newMemoryAmount];
			else
				ramArray = Arrays.copyOf(ramArray, newMemoryAmount);
		}
		else if(ramArray != null && newMemoryAmount < ramArray.length) {
			ramArray = new byte[newMemoryAmount];
			if(parent.machine.isRunning())
				parent.machine.crash("RAM amount reduced while machine was running");
		}
		else ramArray = new byte[newMemoryAmount];
		mmuRegisters[MMU_REG_MAX_VALID_BANK_LOW] = (byte)numMappedRamBanks;
		mmuRegisters[MMU_REG_MAX_VALID_BANK_HIGH] = (byte)(numMappedRamBanks>>8);
	}

	public void load(NBTTagCompound nbt) {
		if(nbt.hasKey("romMappingValid")) {
			romMappingValid = true;
			romArray = nbt.getByteArray("romArray");
		}
		naughtyOperationInProgress = nbt.hasKey("naughtyOperationInProgress");
		clearNaughtyNMIOnNextRead = nbt.hasKey("clearNaughtyNMIOnNextRead");
		naughtyNMI = nbt.hasKey("naughtyNMI");
		overflow = nbt.hasKey("overflow");
		needSynchronizedCall = nbt.hasKey("needSynchronizedCall");
		if(nbt.hasKey("ramArray"))
			ramArray = nbt.getByteArray("ramArray");
		else
			ramArray = null;
		if(nbt.hasKey("nodeAddress"))
			nodeAddress = nbt.getByteArray("nodeAddress");
		else
			nodeAddress = null;
		if(nbt.hasKey("tmpAddress"))
			tmpAddress = nbt.getByteArray("tmpAddress");
		else
			tmpAddress = null;
		builtInMemory = nbt.getByteArray("builtInMemory");
		mmuRegisters = nbt.getByteArray("mmuRegisters");
		if(nbt.hasKey("bufferedSignal"))
			bufferedSignal = nbt.getByteArray("bufferedSignal");
		else
			bufferedSignal = null;
		if(nbt.hasKey("cycleBudget"))
			cycleBudget = nbt.getInteger("cycleBudget");
		else
			cycleBudget = 0;
		if(nbt.hasKey("fakePushStage")) {
			// needed?
			fakePushStage = nbt.getInteger("fakePushStage");
			fakePushAddress = nbt.getShort("fakePushAddress");
		}
		else {
			fakePushStage = 0;
			fakePushAddress = 0;
		}
		if(nbt.hasKey("mappedRedstoneAddr"))
			mappedRedstoneAddr = nbt.getString("mappedRedstoneAddr");
		else
			mappedRedstoneAddr = null;
		invokeStream.reset();
		try {
			if(nbt.hasKey("invokeStream"))
				invokeStream.write(nbt.getByteArray("invokeStream"));
			if(nbt.hasKey("complexCrashStream")) {
				complexCrashStream = new ByteArrayOutputStream();
				complexCrashStream.write(nbt.getByteArray("complexCrashStream"));
			}
			if(nbt.hasKey("debugLineStream") && MainClass.instance.shouldAllowDebugDevice()) {
				debugLineStream = new ByteArrayOutputStream();
				debugLineStream.write(nbt.getByteArray("debugLineStream"));
			}
		}
		catch(IOException e) {
			throw new RuntimeException(e);
		}
		if(nbt.hasKey("watchdog"))
			watchdog = nbt.getByte("watchdog");
		else
			watchdog = 0;
		if(nbt.hasKey("currentPositionInComponent")) {
			int len = 0;
			while(nbt.hasKey("componentList["+len+"]")) ++len;
			componentList = new byte[len][];
			for(int n = 0; n < componentList.length - currentComponentInList; ++n) {
				componentList[currentComponentInList+n] = nbt.getByteArray("componentList["+n+"]");
			}
			currentPositionInComponent = nbt.getInteger("currentPositionInComponent");
		}
		if(nbt.hasKey("modeTransitionState")) {
			nbt.setString("modeTransitionState", modeTransitionState.toString());
			transitioningToUserMode = nbt.getBoolean("transitioningToUserMode");
		}
		else
			modeTransitionState = null;
		invokePort.load(nbt.getCompoundTag("invokePort"));
		signalPort.load(nbt.getCompoundTag("signalPort"));
		for(int n = 0; n < diskDrives.length; ++n) {
			if(nbt.hasKey("diskDrive"+n))
				diskDrives[n].load(nbt.getCompoundTag("diskDrive"+n));
		}
		if(nbt.hasKey("terminal")) terminal.load(nbt.getCompoundTag("terminal"));
		updateNMI();
		updateIRQ();
		if(overflow) parent.cpu.setSO(true);
	}

	public void save(NBTTagCompound nbt) {
		if(romMappingValid)
			nbt.setByteArray("romArray", romArray);
		if(naughtyOperationInProgress)
			nbt.setBoolean("naughtyOperationInProgress", naughtyOperationInProgress);
		if(clearNaughtyNMIOnNextRead)
			nbt.setBoolean("clearNaughtyNMIOnNextRead", clearNaughtyNMIOnNextRead);
		if(naughtyNMI)
			nbt.setBoolean("naughtyNMI", naughtyNMI);
		if(overflow)
			nbt.setBoolean("overflow", overflow);
		if(needSynchronizedCall)
			nbt.setBoolean("needSynchronizedCall", needSynchronizedCall);
		if(ramArray != null)
			nbt.setByteArray("ramArray", ramArray);
		if(nodeAddress != null)
			nbt.setByteArray("nodeAddress", nodeAddress);
		if(tmpAddress != null)
			nbt.setByteArray("tmpAddress", tmpAddress);
		nbt.setByteArray("builtInMemory", builtInMemory);
		nbt.setByteArray("mmuRegisters", mmuRegisters);
		if(bufferedSignal != null)
			nbt.setByteArray("bufferedSignal", bufferedSignal);
		if(cycleBudget != 0)
			nbt.setInteger("cycleBudget", cycleBudget);
		if(fakePushStage != 0) {
			// needed?
			nbt.setInteger("fakePushStage", fakePushStage);
			nbt.setShort("fakePushAddress", fakePushAddress);
		}
		if(mappedRedstoneAddr != null)
			nbt.setString("mappedRedstoneAddr", mappedRedstoneAddr);
		if(invokeStream.size() > 0)
			nbt.setByteArray("invokeStream", invokeStream.toByteArray());
		if(complexCrashStream != null)
			nbt.setByteArray("complexCrashStream", complexCrashStream.toByteArray());
		if(debugLineStream != null)
			nbt.setByteArray("debugLineStream", debugLineStream.toByteArray());
		if(watchdog != 0)
			nbt.setByte("watchdog", watchdog);
		if(componentList != null && currentComponentInList < componentList.length) {
			for(int n = 0; n < componentList.length - currentComponentInList; ++n) {
				nbt.setByteArray("componentList["+n+"]", componentList[currentComponentInList+n]);
			}
			nbt.setInteger("currentPositionInComponent", currentPositionInComponent);
		}
		if(modeTransitionState != null) {
			nbt.setString("modeTransitionState", modeTransitionState.toString());
			nbt.setBoolean("transitioningToUserMode", transitioningToUserMode);
		}
		NBTTagCompound sub = new NBTTagCompound();
		invokePort.save(sub);
		nbt.setTag("invokePort", sub);
		sub = new NBTTagCompound();
		signalPort.save(sub);
		nbt.setTag("signalPort", sub);
		for(int n = 0; n < diskDrives.length; ++n) {
			sub = new NBTTagCompound();
			diskDrives[n].save(sub);
			nbt.setTag("diskDrive"+n, sub);
		}
		sub = terminal.save();
		if(sub != null) nbt.setTag("terminal", sub);
	}

	public void allotCycles(int cycles) {
		cycleBudget += cycles;
	}
	
	private boolean mayExecute() {
		if(executionResult != null) return false;
		if(cycleBudget <= 0) return false;
		State state = parent.cpu.getState();
		if(state == State.AWAITING_INTERRUPT) {
			if(bufferedSignal == null) updateIRQ();
			if(bufferedSignal == null) return false;
		}
		return true;
	}
	
	protected void tryPopSignal() {
		assert(bufferedSignal == null);
		Signal newSignal = parent.machine.popSignal();
		if(newSignal != null) {
			if(MainClass.instance.shouldLogInvokes())
				MainClass.logger.info("Signal: %s %s", newSignal.name(), Arrays.toString(newSignal.args()));
			bufferedSignal = massageSignal(newSignal);
		}
	}
	
	protected byte[] massageSignal(Signal sig) {
		if((sig.name().equals("component_removed") || sig.name().equals("component_added"))
				&& sig.args().length > 2 && sig.args()[1].equals("eeprom")) {
			romMappingValid = false;
		}
		try {
			ByteArrayOutputStream retStream = new ByteArrayOutputStream();
			DataOutputStream dataStream = new DataOutputStream(retStream);
			PackedUIF.write(dataStream, sig.name(), parent);
			for(Object o : sig.args()) {
				PackedUIF.write(dataStream, o, parent);
			}
			dataStream.writeShort(PackedUIF.UIFTAG_END);
			return retStream.toByteArray();
		}
		catch(Exception e) {
			MainClass.logger.error("Exception while massaging incoming signal.", e);
			return null;
		}
	}
	
	public void step() {
		if(mayExecute()) {
			try {
				parent.cpu.step();
			}
			catch(EscapeRetry e) {
				parent.cpu.writePC(e.getRetryPC());
			}
			if(needSynchronizedCall) {
				assert(executionResult == null);
				executionResult = new ExecutionResult.SynchronizedCall();
			}
		}
		else {
			if(cycleBudget > 0) cycleBudget = 0;
			if(executionResult == null) executionResult = new ExecutionResult.Sleep(1);
		}
	}

	public ExecutionResult getExecutionResult() {
		if(executionResult != null) {
			ExecutionResult ret = executionResult;
			executionResult = null;
			if(ret instanceof ExecutionResult.Error) {
				if(debugLineStream != null && debugLineStream.size() > 0) {
					try {
						MainClass.logger.info("When we crashed, this was in the debug device buffer: "+debugLineStream.toString("UTF-8"));
					} catch (UnsupportedEncodingException e) {
						MainClass.logger.error("Eclipse said this exception would happen. I said I knew better. If you're seeing this message, either you're reading my source code or I was wrong.", e);
					}
					debugLineStream.reset();
				}
			}
			return ret;
		}
		else if(parent.cpu.getState() == State.STOPPED) return new ExecutionResult.Shutdown(false);
		return null;
	}
	
	private boolean getFlag(byte flag) {
		return (builtInMemory[0] & flag) != 0;
	}
	
	private void setFlag(byte flag) {
		builtInMemory[0] |= flag;
	}
	
	private void clearFlag(byte flag) {
		builtInMemory[0] &= ~flag;
	}
	
	private void naughtyOperation(byte type, short addr) {
		modeTransitionState = null;
		if(!naughtyOperationInProgress) {
			builtInMemory[1] = type;
			builtInMemory[2] = (byte)addr;
			builtInMemory[3] = (byte)(addr>>8);
			cycleBudget -= 3;
			naughtyOperationInProgress = true;
			fakePushStage = 3;
			fakePushAddress = lastFetchedOpcodeAddress;
			setFlag(FLAG_LAST_NMI_WAS_PRIVILEGED_OPERATION);
			naughtyNMI = true;
			updateNMI();
		}
	}
	
	private void queueModeTransition(boolean userMode) {
		transitioningToUserMode = userMode;
		modeTransitionState = ModeTransitionState.AWAITING_FIRST_OPCODE_FETCH;
	}
	
	public void countDownWatchdog() {
		terminal.coolIt();
		if(watchdog != 0 && watchdog != -1) ++watchdog;
		updateNMI();
	}
	
	public boolean needIRQ() {
		return bufferedSignal != null || signalPort.hasInputLeft();
	}
	
	public void updateIRQ() {
		if(bufferedSignal == null) tryPopSignal();
		parent.cpu.setIRQ(needIRQ());
	}
	
	private void triggerOverflow() {
		overflow = true;
		parent.cpu.setSO(true);
	}

	private byte[] getBinaryUUID(String s) {
		if(s == null) return new byte[16];
		try {
			UUID uuid = UUID.fromString(s);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
			DataOutputStream stream = new DataOutputStream(bytes);
			stream.writeLong(uuid.getMostSignificantBits());
			stream.writeLong(uuid.getLeastSignificantBits());
			return bytes.toByteArray();
		}
		catch(IOException e) {
			MainClass.logger.error("Unexpected IOException while parsing an address", e);
			return new byte[16];
		}
	}
	
	private void retrieveNodeAddress() {
		if(parent.machine.node() == null) throw new EscapeRetry(lastFetchedOpcodeAddress);
		nodeAddress = getBinaryUUID(parent.machine.node().address());
	}
	
	private void retrieveTmpAddress() {
		tmpAddress = getBinaryUUID(parent.machine.tmpAddress());
	}
	
	private void incrementComponentListCursor() {
		++currentComponentInList;
		currentPositionInComponent = 0;
	}
	
	private void reloadComponentList() {
		Map<String,String> components = parent.machine.components();
		currentComponentInList = 0;
		currentPositionInComponent = 0;
		mappedRedstoneAddr = null;
		synchronized(components) {
			ArrayList<byte[]> componentList = new ArrayList<byte[]>(components.size());
			ByteArrayOutputStream stream = new ByteArrayOutputStream(32);
			DataOutputStream data = new DataOutputStream(stream);
			for(Map.Entry<String, String> entry : components.entrySet()) {
				stream.reset();
				if(mappedRedstoneAddr == null && entry.getValue().equals("redstone"))
					mappedRedstoneAddr = entry.getKey();
				byte[] namebytes = entry.getValue().getBytes(Charset.forName("UTF-8"));
				UUID uuid = UUID.fromString(entry.getKey());
				try {
					data.write(namebytes, 0, namebytes.length);
					data.write(0);
					data.writeLong(uuid.getMostSignificantBits());
					data.writeLong(uuid.getLeastSignificantBits());
				}
				catch(IOException e) {
					MainClass.logger.error(e); // this exception is pretty unlikely.
				}
				componentList.add(stream.toByteArray());
			}
			this.componentList = componentList.toArray(new byte[componentList.size()][]);
		}
	}
	
}
