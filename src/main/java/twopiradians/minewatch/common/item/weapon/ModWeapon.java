package twopiradians.minewatch.common.item.weapon;

import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import twopiradians.minewatch.common.Minewatch;
import twopiradians.minewatch.common.item.armor.ModArmor;

public class ModWeapon extends Item
{
	/**Used to uniformly scale damage for all weapons/abilities*/
	public static final float DAMAGE_SCALE = 10f;

	/**Cooldown in ticks for MC cooldown and nbt cooldown (if hasOffhand)*/
	protected int cooldown;
	/**ArmorMaterial that determines which set this belongs to*/
	protected ArmorMaterial material;
	/**Will give shooting hand MC cooldown = cooldown/2 if true*/
	protected boolean hasOffhand;
	protected ResourceLocation scope;

	protected ModWeapon() {
		if (scope != null)
			MinecraftForge.EVENT_BUS.register(this);
	}

	/**Called on server when right click is held and cooldown is not active*/
	protected void onShoot(World worldIn, EntityPlayer playerIn, EnumHand hand) {}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand hand) {
		// check that item does not have MC cooldown (and nbt cooldown if it hasOffhand)
		if (cooldown >= 0 && playerIn != null && playerIn.getHeldItem(hand) != null 
				&& !playerIn.getCooldownTracker().hasCooldown(playerIn.getHeldItem(hand).getItem()) 
				&& (!hasOffhand || (playerIn.getHeldItem(hand).hasTagCompound() 
						&& playerIn.getHeldItem(hand).getTagCompound().getInteger("cooldown") <= 0))) {	
			if (playerIn.getHeldItem(hand).getItem() instanceof ItemReinhardtHammer)
				return new ActionResult(EnumActionResult.PASS, playerIn.getHeldItem(hand));	
			if (!worldIn.isRemote) {
				onShoot(worldIn, playerIn, hand);
				// set MC cooldown/2 and nbt cooldown if hasOffhand, otherwise just set MC cooldown
				if (playerIn.getHeldItem(getInactiveHand(playerIn)) != null 
						&& playerIn.getHeldItem(getInactiveHand(playerIn)).getItem() != Items.AIR
						&& playerIn.getHeldItem(hand).getItem() != playerIn.getHeldItem(getInactiveHand(playerIn)).getItem())
					playerIn.getCooldownTracker().setCooldown(playerIn.getHeldItem(getInactiveHand(playerIn)).getItem(), cooldown+1);
				if (hasOffhand) {
					playerIn.getCooldownTracker().setCooldown(playerIn.getHeldItem(hand).getItem(), cooldown/2);
					playerIn.getHeldItem(hand).getTagCompound().setInteger("cooldown", cooldown);
				}
				else 
					playerIn.getCooldownTracker().setCooldown(playerIn.getHeldItem(hand).getItem(), cooldown);
				// only damage item if 
				if (!ModArmor.isSet(playerIn, material))
					playerIn.getHeldItem(hand).damageItem(1, playerIn);
			}
			return new ActionResult(EnumActionResult.SUCCESS, playerIn.getHeldItem(hand));
		}
		else
			return new ActionResult(EnumActionResult.PASS, playerIn.getHeldItem(hand));	
	}

	@Override
	public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {	
		if (cooldown > 0 && hasOffhand && !worldIn.isRemote) {
			if (!stack.hasTagCompound())
				stack.setTagCompound(new NBTTagCompound());
			int cooldown = stack.getTagCompound().getInteger("cooldown");
			if (stack.getTagCompound().getInteger("cooldown") > 0)
				stack.getTagCompound().setInteger("cooldown", --cooldown);
		}
		if (entityIn != null && entityIn instanceof AbstractClientPlayer && ((EntityPlayer)entityIn).getHeldItemMainhand() != null) {
			AbstractClientPlayer player = (AbstractClientPlayer)entityIn;
			if (player.getHeldItemMainhand().getItem() instanceof ItemAnaRifle 
					&& Minewatch.keyMode.isKeyDown(player) && entityIn.ticksExisted % 10 == 0) {
				AxisAlignedBB aabb = entityIn.getEntityBoundingBox().expandXyz(30);
				List<Entity> list = entityIn.world.getEntitiesWithinAABBExcludingEntity(entityIn, aabb);
				if (!list.isEmpty()) {
					Iterator<Entity> iterator = list.iterator();            
					while (iterator.hasNext()) {
						Entity entityInArea = iterator.next();
						if (entityInArea != null && entityInArea instanceof EntityPlayer 
								&& ((EntityPlayer)entityInArea).isOnSameTeam(player) 
								&& ((EntityPlayer)entityInArea).getHealth() < ((EntityPlayer)entityInArea).getMaxHealth()) {
							Minewatch.proxy.spawnParticlesHealthPlus(player.world, entityInArea.posX, 
									entityInArea.posY+2.5d, entityInArea.posZ, 0, 0, 0, 3);
						}
						else if (entityInArea != null && entityInArea instanceof EntityLiving) {
							entityInArea.world.spawnParticle(EnumParticleTypes.HEART, entityInArea.posX, 
									entityInArea.posY+2d, entityInArea.posZ, 0, 1, 0, new int[0]);
						}
					}
				}
			}
		}
	}

	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		if (oldStack.getItem() instanceof ItemHanzoBow)
			return !oldStack.equals(newStack);
		return oldStack.getItem() != newStack.getItem();
	}

	public int getItemStackLimit(ItemStack stack) {
		return 1;
	}

	public int getItemEnchantability() {
		return 0;
	}

	public EnumHand getInactiveHand(EntityPlayer player) {
		if (!(player.getHeldItemMainhand().getItem() instanceof ModWeapon))
			return EnumHand.MAIN_HAND;
		else
			return EnumHand.OFF_HAND;
	}

	/**Change the FOV when scoped*/
	@SubscribeEvent
	public void onEvent(FOVModifier event) {
		if (event.getEntity() != null && event.getEntity() instanceof EntityPlayer && event.getEntity().isSneaking()
				&& ((((EntityPlayer)event.getEntity()).getHeldItemMainhand() != null 
				&& ((EntityPlayer)event.getEntity()).getHeldItemMainhand().getItem() instanceof ItemAnaRifle)
				|| (((EntityPlayer)event.getEntity()).getHeldItemOffhand() != null 
				&& ((EntityPlayer)event.getEntity()).getHeldItemOffhand().getItem() instanceof ItemAnaRifle))) {
			event.setFOV(20f);
		}
	}

	/**Rendering the scopes for rifles*/
	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onEvent(RenderGameOverlayEvent.Pre event) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		boolean offhand = false;
		boolean mainhand = false;
		if (player != null && player.getHeldItemMainhand() != null && player.getHeldItemMainhand().getItem() instanceof ModWeapon
				&& ((ModWeapon)player.getHeldItemMainhand().getItem()).scope != null)
			mainhand = true;
		else if (player != null && player.getHeldItemOffhand() != null  && player.getHeldItemOffhand().getItem() instanceof ModWeapon
				&& ((ModWeapon)player.getHeldItemOffhand().getItem()).scope != null)
			offhand = true;
		else
			return;
		if (player != null && player.isSneaking() && (mainhand || offhand)) {
			int height = event.getResolution().getScaledHeight();
			int width = event.getResolution().getScaledWidth();
			int imageSize = 256;
			GlStateManager.pushMatrix();
			Minecraft.getMinecraft().getTextureManager().bindTexture(mainhand ? ((ModWeapon)player.getHeldItemMainhand().getItem()).scope 
					: ((ModWeapon)player.getHeldItemOffhand().getItem()).scope);
			GlStateManager.color(1, 1, 1, 0.165f);
			GlStateManager.enableBlend();
			GlStateManager.enableDepth();
			GuiUtils.drawTexturedModalRect(width/2-imageSize/2, height/2-imageSize/2, 0, 0, imageSize, imageSize, 0);
			GlStateManager.disableDepth();
			GlStateManager.popMatrix();
		}
	}
}
