package name.bizna.ocmos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.Value;
import name.bizna.j6502.W65C02;
import name.bizna.j6502.AbstractCore.State;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

@Architecture.Name("OCMOS")
@Architecture.NoMemoryRequirements
public class OCMOS implements Architecture {
	
	/* maximum number of ticks of clock cycles we can queue to "catch up" */
	private static final int MAX_RUN_TIME = 20;
	
	protected W65C02 cpu;
	protected MMU mmu = new MMU(this);
	protected Machine machine;
	protected int cpuTier, cpuCyclesPerTick;
	protected long lastRunTime = Long.MIN_VALUE;
	protected boolean lastYieldWasSleep;
	
	private HashMap<Value, Byte> valueToHandle = new HashMap<Value, Byte>();
	private HashMap<Byte, Value> handleToValue = new HashMap<Byte, Value>();
	private byte nextValueHandle = 0;
	
	public OCMOS(Machine machine) {
		this.machine = machine;
	}

	@Override
	public boolean isInitialized() {
		return cpu != null;
	}

	@Override
	public boolean recomputeMemory(Iterable<ItemStack> components) {
		return mmu.recomputeMemory(components);
	}

	@Override
	public boolean initialize() {
		cpu = new W65C02(mmu);
		lastRunTime = Long.MIN_VALUE;
		lastYieldWasSleep = false;
		mmu.remapMemory();
		cpu.reset();
		mmu.reset();
		return true;
	}

	@Override
	public void close() {
		if(cpu != null) {
			cpu = null;
			lastRunTime = Long.MIN_VALUE;
		}
	}

	@Override
	public void runSynchronized() {
		mmu.runSynchronized();
	}

	@Override
	public ExecutionResult runThreaded(boolean isSynchronizedReturn) {
		try {
			long thisRunTime = machine.worldTime(); // TODO: this doesn't work quite right in worlds with frozen time
			int cyclesToRun;
			if(lastYieldWasSleep) cyclesToRun = cpuCyclesPerTick;
			else if(thisRunTime < lastRunTime) {
				MainClass.logger.warn("World time ran backwards!");
				cyclesToRun = 0;
			}
			else if(thisRunTime - lastRunTime > MAX_RUN_TIME) {
				cyclesToRun = MAX_RUN_TIME * cpuCyclesPerTick;
			}
			else cyclesToRun = (int)((thisRunTime - lastRunTime) * cpuCyclesPerTick);
			lastRunTime = thisRunTime;
			lastYieldWasSleep = false;
			mmu.allotCycles(cyclesToRun);
			mmu.countDownWatchdog();
			mmu.updateIRQ();
			ExecutionResult ret = null;
			do {
				mmu.step();
				ret = mmu.getExecutionResult();
			} while(ret == null);
			lastYieldWasSleep = ret instanceof ExecutionResult.Sleep;
			return ret;
		}
		catch(Exception e) {
			MainClass.logger.error("An unexpected exception occurred! This is a bug in OCMOS!", e);
			e.printStackTrace();
			return new ExecutionResult.Error("Internal error in OCMOS, see log for details");
		}
	}

	@Override
	public void onSignal() {
		cpu.setIRQ(true);
	}

	@Override
	public void onConnect() {
		// do nothing
	}

	@Override
	public void load(NBTTagCompound nbt) {
		initialize();
		mmu.load(nbt);
		valueToHandle.clear();
		handleToValue.clear();
		for(int i = 0; i < 256; ++i) {
			String key = "Value"+i;
			if(!nbt.hasKey(key)) continue;
			NBTTagCompound saved = nbt.getCompoundTag(key);
			if(saved != null) {
				try {
					Class<?> klaso = Class.forName(saved.getString("__class"));
					Value v = (Value)klaso.newInstance();
					v.load(saved);
					valueToHandle.put(v, (byte)i);
					handleToValue.put((byte)i, v);
				}
				catch(ClassNotFoundException e) {
					MainClass.logger.error("While loading a persisted Value: Couldn't find the class. Is it from a mod that has been removed?");
				}
				catch(ClassCastException e) {
					MainClass.logger.error("While loading a persisted Value: We found the class, but it wasn't a Value");
				}
				catch(IllegalAccessException e) {
					MainClass.logger.error("While loading a persisted Value: We found the class, but it didn't have a public no-arg constructor");
				}
				catch(InstantiationException e) {
					MainClass.logger.error("While loading a persisted Value: We found the class, but it was not constructible");
				}
			}
		}
		nextValueHandle = nbt.getByte("NextValue");
		cpu.writeA(nbt.getByte("A"));
		cpu.writeP(nbt.getByte("P"));
		cpu.writeS(nbt.getByte("S"));
		cpu.writeX(nbt.getByte("X"));
		cpu.writeY(nbt.getByte("Y"));
		cpu.writePC(nbt.getShort("PC"));
		if(nbt.hasKey("State")) cpu.setState(State.valueOf(nbt.getString("State")));
		else cpu.setState(State.RUNNING);
		if(nbt.hasKey("NMIWasSeen")) cpu.setNMIWasSeen(true);
		if(nbt.hasKey("SOWasSeen")) cpu.setSOWasSeen(true);
	}

	@Override
	public void save(NBTTagCompound nbt) {
		mmu.save(nbt);
		for(Map.Entry<Value,Byte> pair : valueToHandle.entrySet()) {
			Value val = pair.getKey();
			NBTTagCompound saved = new NBTTagCompound();
			val.save(saved);
			if(saved.hasKey("__class")) {
				MainClass.logger.warn("While saving a Value: It already gave itself a __class key, but we're about to override it!");
				saved.setString("__class", val.getClass().getName());
			}
			nbt.setTag("Value"+(pair.getValue()&0xFF), saved);
		}
		nbt.setByte("NextValue", nextValueHandle);
		nbt.setByte("A", cpu.readA());
		nbt.setByte("P", cpu.readP());
		nbt.setByte("S", cpu.readS());
		nbt.setByte("X", cpu.readX());
		nbt.setByte("Y", cpu.readY());
		nbt.setShort("PC", cpu.readPC());
		if(cpu.getState() != State.RUNNING) nbt.setString("State", cpu.getState().toString());
		if(cpu.getNMIWasSeen()) nbt.setBoolean("NMIWasSeen", true);
		if(cpu.getSOWasSeen()) nbt.setBoolean("SOWasSeen", true);
	}
	
	public byte mapValue(Value value) throws IOException {
		Byte wat = valueToHandle.get(value);
		if(wat != null) throw new RuntimeException("Same Value mapped more than once!");
		byte lastNextValueHandle = nextValueHandle;
		while(handleToValue.containsKey(nextValueHandle)) {
			++nextValueHandle;
			if(nextValueHandle == lastNextValueHandle) throw new IOException("Attempted to map too many values");
		}
		handleToValue.put(nextValueHandle, value);
		valueToHandle.put(value, nextValueHandle);
		return nextValueHandle++;
	}
	
	public Value getValue(byte handle) {
		return handleToValue.get(handle);
	}
	
	public void disposeValue(byte handle, Context ctx) {
		Value v = handleToValue.get(handle);
		if(v != null) {
			handleToValue.remove(handle);
			valueToHandle.remove(v);
			v.dispose(ctx);
		}
	}
	
	public void disposeAllValues(Context ctx) {
		for(Value v : valueToHandle.keySet()) {
			v.dispose(ctx);
		}
		handleToValue.clear();
		valueToHandle.clear();
	}

}
