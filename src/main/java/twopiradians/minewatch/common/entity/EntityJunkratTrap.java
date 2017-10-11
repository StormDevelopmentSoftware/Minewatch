package twopiradians.minewatch.common.entity;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import twopiradians.minewatch.common.Minewatch;
import twopiradians.minewatch.common.item.weapon.ItemMWWeapon;
import twopiradians.minewatch.common.sound.ModSoundEvents;
import twopiradians.minewatch.common.tickhandler.TickHandler;
import twopiradians.minewatch.common.tickhandler.TickHandler.Handler;
import twopiradians.minewatch.common.tickhandler.TickHandler.Identifier;
import twopiradians.minewatch.common.util.EntityHelper;
import twopiradians.minewatch.common.util.Handlers;
import twopiradians.minewatch.packet.SPacketSimple;

public class EntityJunkratTrap extends EntityLivingBaseMW {

	public EntityLivingBase trappedEntity;
	public int trappedTicks;
	private boolean prevOnGround;
	public static final Handler TRAPPED = new Handler(Identifier.JUNKRAT_TRAP, false) {};

	public EntityJunkratTrap(World worldIn) {
		this(worldIn, null);
	}

	public EntityJunkratTrap(World worldIn, EntityLivingBase throwerIn) {
		super(worldIn, throwerIn);
		this.setSize(1.3f, 0.3f);
		this.lifetime = Integer.MAX_VALUE;
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(Math.max(1, 100.0D*ItemMWWeapon.damageScale));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance){
		return distance < 2000;
	}

	@Override
	public void onUpdate() {
		if (this.onGround)
			this.rotationPitch = 0;
		
		// prevOnGround
		if (prevOnGround != onGround && onGround)
			this.world.playSound(null, this.getPosition(), ModSoundEvents.junkratTrapLand, SoundCategory.PLAYERS, 1.0f, 1.0f);
		this.prevOnGround = this.onGround;

		// don't impact when not on ground (bc it jumps around a bit)
		this.skipImpact = !this.onGround;

		// gravity
		this.motionY -= 0.05D;

		// gradual slowdown
		double d1 = this.onGround ? 0.1d : this.inWater ? 0.6d : 0.97d;
		this.motionX *= d1;
		this.motionY *= d1;
		this.motionZ *= d1;

		// check for entities to trap
		if (!this.world.isRemote && this.trappedEntity == null && 
				this.onGround && this.getThrower() instanceof EntityLivingBase) {
			List<Entity> entities = this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().expandXyz(0.5d));
			for (Entity entity : entities) 
				if (!(entity instanceof EntityLivingBaseMW) && entity instanceof EntityLivingBase && EntityHelper.shouldHit(this.getThrower(), entity, false) && 
						!TickHandler.hasHandler(entity, Identifier.JUNKRAT_TRAP) && EntityHelper.attemptDamage(this.getThrower(), entity, 80, true)) {
					if (((EntityLivingBase)entity).getHealth() > 0) {
						this.trappedEntity = (EntityLivingBase) entity;
						this.lifetime = this.ticksExisted + 70;
						TickHandler.register(false, Handlers.PREVENT_MOVEMENT.setTicks(70).setEntity(entity),
								TRAPPED.setTicks(70).setEntity(entity));
						Minewatch.network.sendToAll(new SPacketSimple(25, this, false, this.trappedEntity));
						world.playSound(null, this.getPosition(), ModSoundEvents.junkratTrapTrigger, SoundCategory.PLAYERS, 1.0f, 1.0f);
					}
					else
						this.setDead();
					break;
				}
		}

		// set position of trapped entity
		if (this.trappedEntity != null) {
			this.trappedTicks++;
			this.trappedEntity.setPosition(this.posX, this.posY, this.posZ);
		}

		// check to set dead
		if (!this.world.isRemote && !(this.getThrower() instanceof EntityLivingBase))
			this.setDead();
		else if (!this.world.isRemote && this.trappedEntity != null && this.trappedEntity.getHealth() <= 0) 
			this.setDead();

		super.onUpdate();
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (this.trappedEntity == null)
			return super.attackEntityFrom(source, amount);
		else 
			return false;
	}

	@Override
	protected void onImpact(RayTraceResult result) {}

	@Override
	public void setDead() {
		this.isDead = true;
		if (!this.world.isRemote) {
			for (EntityPlayer player : world.playerEntities) 
				Minewatch.proxy.stopSound(player, ModSoundEvents.junkratTrapTrigger, SoundCategory.PLAYERS);
			this.world.playSound(null, this.getPosition(), ModSoundEvents.junkratTrapBreak, SoundCategory.PLAYERS, 1.0f, 1.0f);
			Minewatch.network.sendToDimension(new SPacketSimple(26, this, true, posX, posY, posZ), this.world.provider.getDimension());
		}
	}

}