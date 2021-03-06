package name.bizna.ocmos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
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
	public static final String VERSION = "0.5.2";
	static Logger logger = LogManager.getFormatterLogger("OCMOS");
	@Instance(value=MainClass.MODID)
	public static MainClass instance;
	@SidedProxy(clientSide="name.bizna.ocmos.ClientProxy", serverSide="name.bizna.ocmos.ServerProxy")
	public static ServerProxy proxy;
	private static byte[] defaultBIOSImage;
	/* Configuration defaults */
	public static final int defaultBanksPerMebibyte = 32;
	public static final int[] defaultCpuCyclesPerTick = new int[]{25000, 50000, 100000};
	public static final int[] defaultRamTier1Latency = new int[]{1, 2, 4};
	public static final int[] defaultRamTier2Latency = new int[]{1, 1, 2};
	public static final int[] defaultRamTier3Latency = new int[]{1, 1, 1};
	public static final int[] defaultRomLatency = new int[]{1, 2, 4};
	public static final int[] defaultIoLatency = new int[]{1, 2, 4};
	public static final boolean defaultAllowDebugDevice = false, defaultLogUIFErrors = false,
			defaultLogInvokes = false, defaultBIOSOptional = false, defaultLogDeadlineSlippage = false;
	public static final int defaultMaxInvokeSize = 16384;
	public static final int defaultDeadlineMilliseconds = 1000;
	/* and their associated variables */
	private static int banksPerMebibyte;
	private static int[] cpuCyclesPerTick;
	private static int[] ramTier1Latency;
	private static int[] ramTier2Latency;
	private static int[] ramTier3Latency;
	private static int[] romLatency;
	private static int[] ioLatency;
	private static boolean allowDebugDevice, logUIFErrors, logInvokes, biosOptional, logDeadlineSlippage;
	private static int maxInvokeSize, deadlineMilliseconds;
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
				"Whether to make the debugging device available.\nThis writes to the Minecraft log under computer control; you should\ndisable this unless you're debugging something that you can't debug any\nother way.\nDoes not provide input, only output.\n");
		logUIFErrors = cfg.getBoolean("logUIFErrors", Configuration.CATEGORY_GENERAL, defaultLogUIFErrors,
				"Whether to log the reason for rejecting invalid UIF transmissions.\nThis writes to the Minecraft log under (partial) computer control; you\nshould disable this unless you're debugging something that you can't\ndebug any other way.\n");
		biosOptional = cfg.getBoolean("biosOptional", Configuration.CATEGORY_GENERAL, defaultBIOSOptional,
				"Whether computers with no EEPROM installed will use the Standard BIOS.\nThis is a pretty cheaty option, but may be necessary, since there is not\ncurrently a crafting recipe for the Standard BIOS.\nIf the Standard BIOS is missing from your jar, this option will have no\neffect.\n");
		logInvokes = cfg.getBoolean("logInvokes", Configuration.CATEGORY_GENERAL, defaultLogInvokes,
				"Log ALL invokes, replies, and signals to the Minecraft log!\nYou should pretty seriously consider never enabling this.\n");
		logDeadlineSlippage = cfg.getBoolean("logDeadlineSlippage", Configuration.CATEGORY_GENERAL, defaultLogDeadlineSlippage,
				"Log whenever runThreaded terminates due to a missed deadline.\nMainly useful for debugging OCMOS.\n");
		maxInvokeSize = cfg.getInt("maxInvokeSize", Configuration.CATEGORY_GENERAL, defaultMaxInvokeSize, 128, 1<<30,
				"The maximum size, in bytes, an outgoing command can take. The higher you\nset this option, the more memory a malicious program can consume.\n");
		deadlineMilliseconds = cfg.getInt("deadlineMilliseconds", Configuration.CATEGORY_GENERAL, defaultDeadlineMilliseconds, 0, 100000000,
				"The maximum amount of time that runThreaded can run before giving up on\nmeeting the cycle budget. This option is a workaround for a difficult-\nto-debug hang in the OCMOS MMU, and should also help on overloaded\nservers.\n0 = no deadline\n");
		banksPerMebibyte = cfg.getInt("banksPerMebibyte", Configuration.CATEGORY_GENERAL, defaultBanksPerMebibyte, 1, 1<<30,
				"The number of 4096-byte banks a RAM module provides, per mebibyte of\nmemory it would provide a Lua computer.\n65C02 computers require MUCH less memory to perform a task than Lua\ncomputers do. The default attempts to provide a comfortable amount of\nmemory, without making large memory modules pointless.\n");
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
		cfg.getCategory(Configuration.CATEGORY_GENERAL).setPropertyOrder(new ArrayList<String>(Arrays.asList("cpuCyclesPerTick","deadlineMilliseconds","logDeadlineSlippage","banksPerMebibyte","romLatency","ramTier1Latency","ramTier2Latency","ramTier3Latency","ioLatency","allowDebugDevice","logUIFErrors","logInvokes","maxInvokeSize","biosOptional")));
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
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		proxy.postInit(event);
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
	boolean shouldLogInvokes() {
		return logInvokes;
	}
	boolean shouldLogDeadlineSlippage() {
		return logDeadlineSlippage;
	}
	int getMaxInvokeSize() {
		return maxInvokeSize;
	}
	byte[] getBuiltInBIOS() {
		return biosOptional ? defaultBIOSImage : null;
	}
	int getDeadlineMilliseconds() {
		return deadlineMilliseconds;
	}
	public static void setDefaultBIOSImage(byte[] defaultBIOSImage) {
		MainClass.defaultBIOSImage = defaultBIOSImage;
	}
}
