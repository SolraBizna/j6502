package name.bizna.ocmos;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import li.cil.oc.api.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public class ClientProxy extends ServerProxy {
	@Override
	public void postInit(FMLPostInitializationEvent event) {
		byte[] defaultBIOSImage = null;
		IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
		try {
			IResource res = rm.getResource(new ResourceLocation("ocmos", "ocmosbios.cabe"));
			InputStream stream = res.getInputStream();
			byte[] buf = new byte[4096];
			int len = stream.read(buf);
			if(len <= 6)
				MainClass.logger.error("ocmosbios.cabe is too small to possibly be valid");
			else {
				defaultBIOSImage = Arrays.copyOf(buf, len);
				MainClass.logger.info("Found ocmosbios.cabe");
			}
		}
		catch(IOException e) {
			MainClass.logger.error("IOException while loading ocmosbios.cabe", e);
		}
		if(defaultBIOSImage != null) {
			MainClass.setDefaultBIOSImage(defaultBIOSImage);
			Items.registerEEPROM("EEPROM (OCMOS BIOS)", defaultBIOSImage, null, false);
			// TODO: Get this to work some day, behind a config option
			/*
			ItemStack stack = new ItemStack(Items.get("eeprom").item());
			NBTTagCompound compound = new NBTTagCompound();
			compound.setByteArray("oc:eeprom", defaultBIOSImage);
			compound.setString("oc:label", "EEPROM (OCMOS BIOS)");
			stack.setTagCompound(compound);
			GameRegistry.addShapelessRecipe(stack, Items.get("eeprom").item(), net.minecraft.init.Items.feather);
			*/
		}
	}
}
