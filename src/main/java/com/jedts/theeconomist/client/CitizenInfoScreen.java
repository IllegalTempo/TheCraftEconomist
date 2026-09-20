package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.info.CitizenInfoScreenData;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CitizenInfoScreen extends Screen {
    private final CitizenEntity citizen;
    private final CitizenInfoScreenData data;

    public CitizenInfoScreen(CitizenEntity citizen, CitizenInfoScreenData data) {
        super(Component.literal("Citizen information"));
        this.citizen = citizen;
        this.data = data;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        int leftWidth = Math.max(120, width / 4);
        graphics.fill(0, 0, leftWidth, height, 0xCC18202A);
        graphics.fill(leftWidth, 0, width, height, 0xCC101419);
        graphics.outline(8, 8, leftWidth - 16, height - 16, 0xFF6D8799);
        graphics.outline(leftWidth + 12, 8, width - leftWidth - 20, height - 16, 0xFF6D8799);
        graphics.centeredText(font, Component.literal("Citizen"), leftWidth / 2, 18, 0xFFFFFFFF);
        int preview = Math.min(leftWidth - 32, 128);
        EntityRenderState renderState = minecraft.getEntityRenderDispatcher().extractEntity(citizen, delta);
        graphics.entity(renderState, 1.0f, new Vector3f(0.0f, 0.0f, 0.0f),
                new Quaternionf().rotateY((float) Math.PI), new Quaternionf(),
                (leftWidth - preview) / 2, 40, preview, preview);

        int x = leftWidth + 28;
        int y = 24;
        graphics.text(font, Component.literal(data.name()), x, y, 0xFFFFFFFF);
        y += 22;
        y = line(graphics, "Life stage: " + data.lifeStage(), x, y);
        y = line(graphics, "Model: " + data.model(), x, y);
        y = line(graphics, "Profile: " + data.profile(), x, y);
        y += 8;
        y = line(graphics, "Health: " + data.health(), x, y);
        y = line(graphics, "Movement speed: " + data.movementSpeed(), x, y);
        y = line(graphics, "Follow range: " + data.followRange(), x, y);
        y += 8;
        line(graphics, "Position: " + data.position(), x, y);
    }

    private int line(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(font, Component.literal(text), x, y, 0xFFD5DEE5);
        return y + 16;
    }
}
