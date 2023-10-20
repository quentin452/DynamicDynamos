package com.unascribed.dyndyn;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.google.common.collect.Lists;

import cofh.thermalexpansion.block.dynamo.TileDynamoBase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

public abstract class Proxy {
	public void init() {
		DynamicDynamos.inst.log.info("Registering dynamo information syncer");
		FMLCommonHandler.instance().bus().register(this);
		MinecraftForge.EVENT_BUS.register(this);
	}

	private Map<TileDynamoBase, Integer> lastTickRate = new WeakIdentityHashMap<TileDynamoBase, Integer>();
	private List<Runnable> doLater = Lists.newArrayList();

	@SubscribeEvent
	public void onTick(ServerTickEvent e) {
		if (e.phase == Phase.END) {
			// Process Runnable tasks
			doLater.forEach(Runnable::run);
			doLater.clear(); // Clear the list in one step

			for (WorldServer w : DimensionManager.getWorlds()) {
				// Iterate through all loaded tile entities
				for (TileEntity te : (List<TileEntity>) w.loadedTileEntityList) {
					if (te instanceof TileDynamoBase) {
						TileDynamoBase tdb = (TileDynamoBase) te;
						Integer lastRate = lastTickRate.get(tdb);

						if (lastRate == null || lastRate != tdb.getInfoEnergyPerTick()) {
							UpdateDynamoEnergyRate.Message msg = new UpdateDynamoEnergyRate.Message();
							msg.x = tdb.xCoord;
							msg.y = tdb.yCoord;
							msg.z = tdb.zCoord;
							msg.energyPerTick = tdb.getInfoEnergyPerTick();

							Chunk c = w.getChunkFromBlockCoords(te.xCoord, te.zCoord);

							// Use stream to filter and send the message to players watching the chunk
							w.playerEntities.stream()
									.filter(ep -> w.getPlayerManager().isPlayerWatchingChunk((EntityPlayerMP) ep, c.xPosition, c.zPosition))
									.forEach(ep -> DynamicDynamos.inst.network.sendTo(msg, (EntityPlayerMP) ep));

							// Update the rate in the map
							lastTickRate.put(tdb, tdb.getInfoEnergyPerTick());
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void onChunkWatch(ChunkWatchEvent.Watch e) {
		for (TileEntity te : (Collection<TileEntity>)e.player.worldObj.getChunkFromChunkCoords(e.chunk.chunkXPos, e.chunk.chunkZPos).chunkTileEntityMap.values()) {
			if (te instanceof TileDynamoBase) {
				final EntityPlayerMP player = e.player;
				final TileDynamoBase tdb = (TileDynamoBase)te;
				doLater.add(new Runnable() {
					@Override
					public void run() {
						UpdateDynamoEnergyRate.Message msg = new UpdateDynamoEnergyRate.Message();
						msg.x = tdb.xCoord;
						msg.y = tdb.yCoord;
						msg.z = tdb.zCoord;
						msg.energyPerTick = tdb.getInfoEnergyPerTick();
						DynamicDynamos.inst.network.sendTo(msg, player);
					}
				});
			}
		}
	}
}
