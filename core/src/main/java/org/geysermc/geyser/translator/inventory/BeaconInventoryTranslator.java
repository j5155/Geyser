/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.inventory;

import it.unimi.dsi.fastutil.ints.IntSets;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.BeaconPaymentAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.geysermc.geyser.inventory.BeaconContainer;
import org.geysermc.geyser.inventory.BedrockContainerSlot;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.holder.BlockInventoryHolder;
import org.geysermc.geyser.inventory.updater.UIInventoryUpdater;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetBeaconPacket;

import java.util.OptionalInt;

public class BeaconInventoryTranslator extends AbstractBlockInventoryTranslator<BeaconContainer> {
    public BeaconInventoryTranslator() {
        super(1, new BlockInventoryHolder(Blocks.BEACON, org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType.BEACON) {
            @Override
            protected boolean checkInteractionPosition(GeyserSession session) {
                // Since we can't fall back to a virtual inventory, let's make opening one easier
                return true;
            }

            @Override
            public boolean prepareInventory(GeyserSession session, Container container) {
                // Virtual beacon inventories aren't possible - we don't want to spawn a whole pyramid!
                return super.canUseRealBlock(session, container);
            }
        }, UIInventoryUpdater.INSTANCE);
    }

    @Override
    public void updateProperty(GeyserSession session, BeaconContainer container, int key, int value) {
        //FIXME?: Beacon graphics look weird after inputting an item. This might be a Bedrock bug, since it resets to nothing
        // on BDS
        switch (key) {
            case 0:
                // Power - beacon doesn't use this, and uses the block position instead
                break;
            case 1:
                container.setPrimaryId(value == -1 ? 0 : value);
                break;
            case 2:
                container.setSecondaryId(value == -1 ? 0 : value);
                break;
        }

        // Send a block entity data packet update to the fake beacon inventory
        Vector3i position = container.getHolderPosition();
        NbtMapBuilder builder = BlockEntityTranslator.getConstantBedrockTag("Beacon", position)
                .putString("CustomName", container.getTitle())
                .putInt("primary", container.getPrimaryId())
                .putInt("secondary", container.getSecondaryId());

        BlockEntityDataPacket packet = new BlockEntityDataPacket();
        packet.setBlockPosition(position);
        packet.setData(builder.build());
        session.sendUpstreamPacket(packet);
    }

    @Override
    protected boolean shouldHandleRequestFirst(ItemStackRequestAction action, BeaconContainer container) {
        return action.getType() == ItemStackRequestActionType.BEACON_PAYMENT;
    }

    @Override
    public ItemStackResponse translateSpecialRequest(GeyserSession session, BeaconContainer container, ItemStackRequest request) {
        // Input a beacon payment
        BeaconPaymentAction beaconPayment = (BeaconPaymentAction) request.getActions()[0];
        ServerboundSetBeaconPacket packet = new ServerboundSetBeaconPacket(toJava(beaconPayment.getPrimaryEffect()), toJava(beaconPayment.getSecondaryEffect()));
        session.sendDownstreamGamePacket(packet);
        return acceptRequest(request, makeContainerEntries(session, container, IntSets.emptySet()));
    }

    private OptionalInt toJava(int effectChoice) {
        return effectChoice == 0 ? OptionalInt.empty() : OptionalInt.of(effectChoice - 1);
    }

    @Override
    public int bedrockSlotToJava(ItemStackRequestSlotData slotInfoData) {
        if (slotInfoData.getContainerName().getContainer() == ContainerSlotType.BEACON_PAYMENT) {
            return 0;
        }
        return super.bedrockSlotToJava(slotInfoData);
    }

    @Override
    public BedrockContainerSlot javaSlotToBedrockContainer(int slot, BeaconContainer container) {
        if (slot == 0) {
            return new BedrockContainerSlot(ContainerSlotType.BEACON_PAYMENT, 27);
        }
        return super.javaSlotToBedrockContainer(slot, container);
    }

    @Override
    public int javaSlotToBedrock(int slot) {
        if (slot == 0) {
            return 27;
        }
        return super.javaSlotToBedrock(slot);
    }

    @Override
    public BeaconContainer createInventory(GeyserSession session, String name, int windowId, ContainerType containerType) {
        return new BeaconContainer(session, name, windowId, this.size, containerType);
    }

    @Override
    public org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType closeContainerType(BeaconContainer container) {
        return null;
    }
}
