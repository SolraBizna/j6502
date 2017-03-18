package name.bizna.ocmos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import li.cil.oc.api.Machine;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

@Mod(name = MainClass.NAME, modid = MainClass.MODID, version = MainClass.VERSION)
public class MainClass {
	public static final String MODID = "OCMOS";
	public static final String NAME = "OpenComputers 65C02 (OCMOS)";
	public static final String VERSION = "0.4";
	static Logger logger = LogManager.getFormatterLogger("OCMOS");
	@Instance(value=MainClass.MODID)
	public static MainClass instance;
	/* Configuration defaults */
	public static final int defaultBanksPerMebibyte = 32;
	public static final int[] defaultCpuCyclesPerTick = new int[]{25000, 50000, 100000};
	public static final int[] defaultRamTier1Latency = new int[]{1, 2, 4};
	public static final int[] defaultRamTier2Latency = new int[]{1, 1, 2};
	public static final int[] defaultRamTier3Latency = new int[]{1, 1, 1};
	public static final int[] defaultRomLatency = new int[]{1, 2, 4};
	public static final int[] defaultIoLatency = new int[]{1, 2, 4};
	public static final boolean defaultAllowDebugDevice = false, defaultLogUIFErrors = false;
	public static final int defaultMaxInvokeSize = 16384;
	/* and their associated variables */
	private static int banksPerMebibyte;
	private static int[] cpuCyclesPerTick;
	private static int[] ramTier1Latency;
	private static int[] ramTier2Latency;
	private static int[] ramTier3Latency;
	private static int[] romLatency;
	private static int[] ioLatency;
	private static boolean allowDebugDevice, logUIFErrors;
	private static int maxInvokeSize;
	/**
	 * @return The number of cycles that a CPU at a given tier can execute in one Minecraft tick.
	 */
	public int getCPUCyclesPerTick(int tier) { return cpuCyclesPerTick[tier]; }
	/**
	 * @param cpuTier The tier of CPU performing the access.
	 * @param ramTier The tier of RAM module being accessed.
	 * @return The number of cycles that the access will take.
	 */
	public int getRAMLatency(int cpuTier, int ramTier) {
		switch(ramTier) {
		case 0: return ramTier1Latency[cpuTier];
		case 1: return ramTier2Latency[cpuTier];
		case 2: return ramTier3Latency[cpuTier];
		}
		throw new ArrayIndexOutOfBoundsException();
	}
	/**
	 * @return The number of cycles that an access to ROM will take at the given CPU tier.
	 */
	public int getROMLatency(int cpuTier) {
		return romLatency[cpuTier];
	}
	/**
	 * @return The number of cycles that an IO will take at the given CPU tier.
	 */
	public int getIOLatency(int cpuTier) {
		return ioLatency[cpuTier];
	}
	/**
	 * @param amount The `memory.amount` value from an OC memory module
	 * @return The number of 4KiB banks of memory that OC memory module contains when used with OCMOS
	 */
	public int getMemoryModuleBankCount(double amount) {
		int ret = (int)Math.floor(amount * banksPerMebibyte / 1024.0);
		if(ret < 1) return 1;
		else return ret;
	}
	private static int[] getTierBasedIntList(Configuration cfg, String name, String category, int[] def, String comment) {
		Property prop = cfg.get(category, name, def, comment);
		int[] ret = prop.getIntList();
		if(ret == null || ret.length < 3) {
			ret = def;
			prop.set(ret);
		}
		return ret;
	}
	private void readConfig(FMLPreInitializationEvent event) {
		Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
		allowDebugDevice = cfg.getBoolean("allowDebugDevice", Configuration.CATEGORY_GENERAL, defaultAllowDebugDevice,
				"Whether to make the debugging device available.\nThis writes to the Minecraft log under computer control; you should\ndisable this unless you're debugging something that you can't debug any other way.\nDoes not provide input, only output.\n");
		logUIFErrors = cfg.getBoolean("logUIFErrors", Configuration.CATEGORY_GENERAL, defaultLogUIFErrors,
				"Whether to log the reason for rejecting invalid UIF transmissions.\nThis writes to the Minecraft log under (partial) computer control; you should\ndisable this unless you're debugging something that you can't debug\nany other way.\n");
		maxInvokeSize = cfg.getInt("maxInvokeSize", Configuration.CATEGORY_GENERAL, defaultMaxInvokeSize, 128, 1<<30,
				"The maximum size, in bytes, an outgoing command can take. The higher you set this option, the more memory a malicious program can consume.\n");
		cpuCyclesPerTick = getTierBasedIntList(cfg, "cpuCyclesPerTick", Configuration.CATEGORY_GENERAL, defaultCpuCyclesPerTick,
				"CPU cycles per Minecraft tick, at each CPU tier.\nDefault values are 25000, 50000, 100000 (500KHz/1MHz/2MHz)");
		ramTier1Latency = getTierBasedIntList(cfg, "ramTier1Latency", Configuration.CATEGORY_GENERAL, defaultRamTier1Latency,
				"CPU cycles per tier 1 RAM access, at each CPU tier.");
		ramTier2Latency = getTierBasedIntList(cfg, "ramTier2Latency", Configuration.CATEGORY_GENERAL, defaultRamTier2Latency,
				"CPU cycles per tier 2 RAM access, at each CPU tier.");
		ramTier3Latency = getTierBasedIntList(cfg, "ramTier3Latency", Configuration.CATEGORY_GENERAL, defaultRamTier3Latency,
				"CPU cycles per tier 3 RAM access, at each CPU tier.");
		romLatency = getTierBasedIntList(cfg, "romLatency", Configuration.CATEGORY_GENERAL, defaultRomLatency,
				"CPU cycles per ROM access, at each CPU tier.");
		ioLatency = getTierBasedIntList(cfg, "ioLatency", Configuration.CATEGORY_GENERAL, defaultIoLatency,
				"CPU cycles per IO-space access, at each CPU tier.");
		for(int n = 0; n < 3; ++n) {
			if(cpuCyclesPerTick[n] < 1) cpuCyclesPerTick[n] = 1;
			if(ramTier1Latency[n] < 1) ramTier1Latency[n] = 1;
			if(ramTier2Latency[n] < 1) ramTier2Latency[n] = 1;
			if(ramTier3Latency[n] < 1) ramTier3Latency[n] = 1;
			if(romLatency[n] < 1) romLatency[n] = 1;
			if(ioLatency[n] < 1) ioLatency[n] = 1;
		}
		cfg.getCategory(Configuration.CATEGORY_GENERAL).setPropertyOrder(new ArrayList<String>(Arrays.asList("cpuCyclesPerTick","romLatency","ramTier1Latency","ramTier2Latency","ramTier3Latency","ioLatency","allowDebugDevice","logUIFErrors","maxInvokeSize")));
		cfg.save();
	}
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		readConfig(event);
	}
	@EventHandler
	public void init(FMLInitializationEvent event) {
		Machine.add(OCMOS.class);
	}
	@NetworkCheckHandler
	public boolean versionOkay(Map<String,String> mods, Side side) {
		return true;
	}
	boolean shouldAllowDebugDevice() {
		return allowDebugDevice;
	}
	boolean shouldLogUIFErrors() {
		return logUIFErrors;
	}
	int getMaxInvokeSize() {
		return maxInvokeSize;
	}
}
