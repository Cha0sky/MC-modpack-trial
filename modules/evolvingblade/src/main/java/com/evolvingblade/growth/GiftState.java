package com.evolvingblade.growth;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * 赠剑状态。整条生命周期里每个玩家<b>最多</b>成功获得一把剑：
 *
 * <ul>
 *   <li>{@link #NOT_GRANTED}：尚未确认选择 / 尚未发过剑；</li>
 *   <li>{@link #PENDING}：已确认但背包放不下，等待有空间后补发（绝不掉落、绝不重复）；</li>
 *   <li>{@link #GRANTED}：已经<b>成功</b>交付到背包，之后永不再发。</li>
 * </ul>
 */
public enum GiftState {
    NOT_GRANTED,
    PENDING,
    GRANTED;

    public static final Codec<GiftState> CODEC = Codec.STRING.flatXmap(
            name -> parse(name)
                    .<DataResult<? extends GiftState>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown gift state: " + name)),
            state -> DataResult.success(state.name()));

    public static java.util.Optional<GiftState> parse(String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(GiftState.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }
}
