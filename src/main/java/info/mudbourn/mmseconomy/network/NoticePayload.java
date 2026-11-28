package info.mudbourn.mmseconomy.network;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: a message for one player, shown even while a screen is open.
public record NoticePayload(String message, boolean failed) implements CustomPacketPayload {

    public static final Type<NoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "notice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        NoticePayload::message,
        ByteBufCodecs.BOOL,
        NoticePayload::failed,
        NoticePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
