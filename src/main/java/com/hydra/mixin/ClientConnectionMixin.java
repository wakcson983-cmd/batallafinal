package com.hydra.mixin;

import com.hydra.RuletaHook;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void hydra$peek(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (packet instanceof CustomPayloadS2CPacket p) {
            RuletaHook.inspect(p.payload());
        }
    }
}
