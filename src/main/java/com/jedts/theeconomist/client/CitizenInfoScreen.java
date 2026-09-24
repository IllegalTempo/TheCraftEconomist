package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoDetails;
import com.jedts.theeconomist.citizen.info.CitizenInfoRequestPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoScreenData;
import com.jedts.theeconomist.citizen.trade.CitizenTradeAction;
import com.jedts.theeconomist.citizen.trade.CitizenTradeActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class CitizenInfoScreen extends Screen {
    private enum Page { OVERVIEW, WORK, DECISION }
    private static CitizenInfoScreen current;

    private final CitizenEntity citizen;
    private CitizenInfoScreenData data;
    private Page page = Page.OVERVIEW;
    private int overviewScroll;
    private int overviewBottom;
    private int workScroll;
    private int workBottom;
    private int decisionScroll;
    private int decisionBottom;
    private final DecisionTabState decisionState = new DecisionTabState();
    private List<DecisionRow> decisionRows = List.of();
    private int decisionTrackTop;
    private int decisionTrackHeight;
    private int decisionThumbHeight;
    private int decisionThumbTop;
    private int decisionMaxScroll;
    private int scrollbarDragOffset;
    private boolean draggingDecisionScrollbar;
    private int refreshTicks;

    private record DecisionRow(String actionId, int top, int bottom) { }

    public CitizenInfoScreen(CitizenEntity citizen, CitizenInfoScreenData data) {
        super(Component.literal("Citizen information"));
        this.citizen = citizen;
        this.data = data;
    }

    @Override
    protected void init() {
        current = this;
        int leftWidth = leftWidth();
        int rightWidth = width - leftWidth - 40;
        int tabWidth = Math.max(48, rightWidth / 3 - 5);
        addRenderableWidget(Button.builder(Component.literal("Overview"), button -> page = Page.OVERVIEW)
                .bounds(leftWidth + 16, 16, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Work"), button -> page = Page.WORK)
                .bounds(leftWidth + 20 + tabWidth, 16, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Decision"), button -> page = Page.DECISION)
                .bounds(leftWidth + 24 + tabWidth * 2, 16, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Trade"), button -> {
                    CitizenTradeScreen.await(citizen.getId());
                    ClientPlayNetworking.send(new CitizenTradeActionPayload(citizen.getId(),
                            CitizenTradeAction.OPEN.ordinal(), 0, 0, java.util.UUID.randomUUID()));
                }).bounds(12, height - 34, Math.max(56, leftWidth - 24), 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        int leftWidth = leftWidth();
        graphics.fill(0, 0, leftWidth, height, 0xCC18202A);
        graphics.fill(leftWidth, 0, width, height, 0xCC101419);
        graphics.outline(8, 8, leftWidth - 16, height - 16, 0xFF6D8799);
        graphics.outline(leftWidth + 8, 8, width - leftWidth - 16, height - 16, 0xFF6D8799);
        graphics.centeredText(font, Component.literal("Citizen"), leftWidth / 2, 18, 0xFFFFFFFF);
        int preview = Math.min(leftWidth - 32, Math.min(128, height - 100));
        if (preview > 0) {
            EntityRenderState renderState = minecraft.getEntityRenderDispatcher().extractEntity(citizen, delta);
            graphics.entity(renderState, 1.0f, new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf().rotateY((float) Math.PI), new Quaternionf(),
                    (leftWidth - preview) / 2, 40, preview, preview);
        }

        int contentX = leftWidth + 17;
        graphics.enableScissor(leftWidth + 12, 44, width - 12, height - 13);
        int contentTop = renderCraftingPreview(graphics, contentX, mouseX, mouseY);
        if (page == Page.OVERVIEW) renderOverview(graphics, contentX, contentTop);
        else if (page == Page.WORK) renderWork(graphics, contentX, mouseX, mouseY, contentTop);
        else renderDecisions(graphics, contentX, contentTop);
        graphics.disableScissor();
        // Widgets go last so the panel fills cannot cover the Trade or tab buttons.
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private int renderCraftingPreview(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY) {
        var preview = data.craftingPreview().orElse(null);
        if (preview == null) return 52;
        int left = x - 6;
        int top = 46;
        int cardWidth = Math.max(96, width - left - 21);
        graphics.fill(left, top, left + cardWidth, top + 80, 0xDD27343E);
        graphics.outline(left, top, cardWidth, 80, 0xFF6D8799);
        ItemStack result = preview.result();
        graphics.text(font, Component.literal(trim("Crafting: " + result.getHoverName().getString(), cardWidth - 12)),
                left + 6, top + 5, 0xFFFFD36A);
        List<ItemStack> ingredients = preview.ingredients();
        for (int slot = 0; slot < ingredients.size(); slot++) {
            int slotX = left + 7 + slot % 3 * 18;
            int slotY = top + 21 + slot / 3 * 18;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF53616A);
            ItemStack ingredient = ingredients.get(slot);
            if (ingredient.isEmpty()) continue;
            graphics.item(ingredient, slotX + 1, slotY + 1);
            if (mouseY >= slotY && mouseY < slotY + 18 && mouseX >= slotX && mouseX < slotX + 18)
                graphics.setTooltipForNextFrame(font, ingredient, mouseX, mouseY);
        }
        graphics.text(font, Component.literal("→"), left + 65, top + 43, 0xFFD5DEE5);
        if (!result.isEmpty()) {
            int resultX = left + 83;
            int resultY = top + 40;
            graphics.item(result, resultX, resultY);
            graphics.itemDecorations(font, result, resultX, resultY);
            if (mouseY >= resultY && mouseY < resultY + 18 && mouseX >= resultX && mouseX < resultX + 18)
                graphics.setTooltipForNextFrame(font, result, mouseX, mouseY);
        }
        return top + 88;
    }

    private void renderOverview(GuiGraphicsExtractor graphics, int x, int top) {
        int y = top - overviewScroll;
        y = line(graphics, data.name(), x, y, 0xFFFFFFFF);
        y += 5;
        y = line(graphics, "Life stage: " + data.lifeStage(), x, y);
        y = line(graphics, "Gender: " + data.gender(), x, y);
        y = line(graphics, "Family role: " + data.familyRole(), x, y);
        for (var row : font.split(Component.literal("Family: " + data.familyMembers()), width - x - 16)) {
            graphics.text(font, row, x, y, 0xFFB8DFA8);
            y += 14;
        }
        y = line(graphics, "Behavior: " + data.activeBehavior(), x, y);
        for (var row : font.split(Component.literal("Status: " + data.behaviorStatus()), width - x - 16)) {
            graphics.text(font, row, x, y, 0xFFB8DFA8);
            y += 14;
        }
        y = line(graphics, "Model: " + data.model(), x, y);
        y = line(graphics, "Profile: " + data.profile(), x, y);
        y += 5;
        y = line(graphics, "Health: " + data.health(), x, y);
        y = line(graphics, "Movement speed: " + data.movementSpeed(), x, y);
        y = line(graphics, "Follow range: " + data.followRange(), x, y);
        y += 5;
        y = line(graphics, "Hunger: " + data.hunger(), x, y);
        y = line(graphics, "Energy: " + data.energy(), x, y);
        y = line(graphics, "Safety: " + data.safety(), x, y);
        y = line(graphics, "Morale: " + data.morale(), x, y);
        y = line(graphics, "Intelligence: " + data.intelligence(), x, y);
        y = line(graphics, "Anger: " + data.anger(), x, y);
        y = line(graphics, "Education: " + data.education(), x, y);
        y += 5;
        y = line(graphics, "Occupation: " + data.occupation(), x, y);
        y = line(graphics, "Employer: " + (data.employer().isBlank() ? "none" : data.employer()), x, y);
        y = line(graphics, "Wage/day: " + data.wage(), x, y);
        y = line(graphics, "Work hours: " + data.workHours(), x, y);
        y += 5;
        y = line(graphics, "Contract: " + data.contractStatus(), x, y);
        if (!data.contractTarget().isBlank()) {
            y = line(graphics, "Target: " + data.contractTarget(), x, y);
            y = line(graphics, "Bounty: " + data.contractBounty() + " Crowns", x, y);
            y = line(graphics, "Deadline: " + data.contractDeadline(), x, y);
        }
        y += 5;
        y = line(graphics, "Position: " + data.position(), x, y);
        overviewBottom = y + overviewScroll;
    }

    private void renderWork(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY, int top) {
        CitizenInfoDetails details = data.details().orElse(null);
        if (details == null) {
            line(graphics, "Loading work and inventory...", x, 52);
            return;
        }
        int y = top - workScroll;
        y = line(graphics, "Work & Inventory", x, y, 0xFFFFFFFF);
        for (var row : font.split(Component.literal("Status: " + details.farmStatus()), width - x - 16)) {
            graphics.text(font, row, x, y, 0xFFB8DFA8);
            y += 14;
        }
        y = line(graphics, "Home: " + details.home(), x, y);
        y = line(graphics, "Claimed farmland: " + details.claimedFarmland(), x, y);
        y = line(graphics, "Wheat: 4 reserved, " + details.sellableWheat() + " for sale", x, y);
        y = line(graphics, "Crowns carried: " + details.crownBalance(), x, y);
        y = line(graphics, "Wheat price: " + details.wheatPrice() + " Crowns", x, y);
        y = line(graphics, "Pays for seeds: " + details.seedPrice() + " Crowns", x, y);
        y = line(graphics, "Pays for hoe: " + details.hoePrice() + " Crowns", x, y);
        y += 4;
        line(graphics, "Inventory (27 slots)", x, y, 0xFFFFFFFF);
        int gridTop = y + 17;
        int columns = Math.min(9, Math.max(1, (width - x - 16) / 18));
        workBottom = gridTop + ((details.inventory().size() + columns - 1) / columns) * 18 + workScroll;
        for (int slot = 0; slot < details.inventory().size(); slot++) {
            int slotX = x + slot % columns * 18;
            int slotY = gridTop + slot / columns * 18;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF53616A);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF27343E);
            ItemStack stack = details.inventory().get(slot);
            if (stack.isEmpty()) continue;
            graphics.item(stack, slotX + 1, slotY + 1);
            graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
            if (mouseY >= 44 && mouseY < height - 13 && mouseX >= slotX && mouseX < slotX + 18
                    && mouseY >= slotY && mouseY < slotY + 18)
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private void renderDecisions(GuiGraphicsExtractor graphics, int x, int top) {
        decisionTrackTop = top - 6;
        decisionTrackHeight = Math.max(0, height - decisionTrackTop - 14);
        int viewportHeight = decisionTrackHeight;
        decisionRows = new java.util.ArrayList<>();
        int y = top - decisionScroll;
        y = decisionLine(graphics, "Live action decisions", x, y, 0xFFFFFFFF);
        y = decisionLine(graphics, "Scores combine urgency, benefit, capability, opportunity, cost, and risk.", x, y);
        y += 4;
        if (data.decisions().isEmpty()) {
            y = decisionLine(graphics, "No decision data yet.", x, y);
        } else {
            for (var decision : data.decisions().stream()
                    .sorted(java.util.Comparator.comparingDouble(com.jedts.theeconomist.citizen.info.CitizenDecisionView::score)
                            .reversed()).toList()) {
                int rowTop = y - 2;
                graphics.fill(x - 3, rowTop, width - 25, rowTop + 16,
                        decisionState.isExpanded(decision.actionId()) ? 0xCC33424E : 0x88303A43);
                graphics.text(font, Component.literal(decisionState.isExpanded(decision.actionId()) ? "▾" : "▸"),
                        x, y, 0xFFB8DFA8);
                graphics.text(font, Component.literal(trim(decision.displayName(), width - x - 126)),
                        x + 11, y, decision.active() ? 0xFFFFD36A : 0xFFFFFFFF);
                String scoreLabel = Math.round(decision.score()) + "/100";
                graphics.text(font, Component.literal(scoreLabel), width - 25 - font.width(scoreLabel), y,
                        decision.active() ? 0xFFFFD36A : 0xFFB8DFA8);
                y += 16;
                int headerBottom = y;
                if (decisionState.isExpanded(decision.actionId())) {
                    y = decisionLine(graphics, "Score: " + Math.round(decision.score()) + "/100", x + 11, y,
                            decision.active() ? 0xFFFFD36A : 0xFFD5DEE5);
                    y = decisionLine(graphics, "Eligible: " + yesNo(decision.eligible()), x + 11, y);
                    y = decisionLine(graphics, "Selected: " + yesNo(decision.selected()), x + 11, y);
                    y = decisionLine(graphics, "Active: " + yesNo(decision.active()), x + 11, y);
                    y = decisionLine(graphics, "Emergency override: " + yesNo(decision.emergencyOverride()), x + 11, y);
                    for (var factor : decision.factors())
                        y = decisionLine(graphics, factor.id() + ": " + String.format(java.util.Locale.ROOT, "%.2f", factor.value())
                                + " (" + String.format(java.util.Locale.ROOT, "%+.1f", factor.scoreContribution()) + ")", x + 11, y);
                    if (!decision.explanation().isBlank()) y = decisionLine(graphics, decision.explanation(), x + 11, y, 0xFFB8DFA8);
                    y += 3;
                }
                // Only the visible action header toggles. Expanded details remain readable/selectable
                // without making the hit target overlap the next collapsed action.
                decisionRows.add(new DecisionRow(decision.actionId(), rowTop, headerBottom));
            }
        }
        decisionBottom = y + decisionScroll;
        int contentHeight = Math.max(0, decisionBottom - decisionTrackTop);
        decisionMaxScroll = DecisionTabLayout.maxScroll(contentHeight, viewportHeight);
        decisionScroll = Math.max(0, Math.min(decisionMaxScroll, decisionScroll));
        decisionThumbHeight = DecisionTabLayout.thumbHeight(decisionTrackHeight, contentHeight, viewportHeight);
        decisionThumbTop = DecisionTabLayout.thumbTop(decisionTrackTop, decisionScroll, decisionMaxScroll,
                decisionThumbHeight, decisionTrackHeight);
        if (decisionMaxScroll > 0) {
            graphics.fill(width - 18, decisionTrackTop, width - 12, decisionTrackTop + decisionTrackHeight, 0xCC36424C);
            graphics.fill(width - 18, decisionThumbTop, width - 12, decisionThumbTop + decisionThumbHeight, 0xFFB8DFA8);
        }
    }

    private int decisionLine(GuiGraphicsExtractor graphics, String value, int x, int y) {
        return decisionLine(graphics, value, x, y, 0xFFD5DEE5);
    }

    private int decisionLine(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, Component.literal(trim(value, width - x - 28)), x, y, color);
        return y + 14;
    }

    private String yesNo(boolean value) { return value ? "yes" : "no"; }

    private int line(GuiGraphicsExtractor graphics, String value, int x, int y) {
        return line(graphics, value, x, y, 0xFFD5DEE5);
    }

    private int line(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, Component.literal(trim(value, width - x - 16)), x, y, color);
        return y + 14;
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        while (!value.isEmpty() && font.width(value + "...") > maxWidth) value = value.substring(0, value.length() - 1);
        return value + "...";
    }

    private int leftWidth() {
        return Math.min(Math.max(120, width / 4), Math.max(80, width - 136));
    }

    public static boolean active(int entityId) { return current != null && current.citizen.getId() == entityId; }

    public static void update(int entityId, CitizenInfoScreenData data) {
        if (current != null && current.citizen.getId() == entityId) current.data = data;
    }

    @Override
    public void tick() {
        super.tick();
        if (current != this || ++refreshTicks < 20) return;
        refreshTicks = 0;
        if (minecraft != null && minecraft.getConnection() != null
                && ClientPlayNetworking.canSend(CitizenInfoRequestPayload.TYPE))
            ClientPlayNetworking.send(new CitizenInfoRequestPayload(citizen.getId()));
    }

    @Override
    public void removed() {
        if (current == this) current = null;
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= leftWidth()) {
            if (page == Page.OVERVIEW)
                overviewScroll = (int) Math.max(0, Math.min(Math.max(0, overviewBottom - (height - 14)),
                        overviewScroll - vertical * 18));
            else if (page == Page.WORK) workScroll = (int) Math.max(0, Math.min(Math.max(0, workBottom - (height - 14)),
                    workScroll - vertical * 18));
            else decisionScroll = (int) Math.max(0, Math.min(decisionMaxScroll,
                    decisionScroll - vertical * 18));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (page == Page.DECISION && event.button() == 0 && decisionMaxScroll > 0
                && mouseX >= width - 22 && mouseX <= width - 8
                && mouseY >= decisionTrackTop && mouseY < decisionTrackTop + decisionTrackHeight) {
            if (mouseY >= decisionThumbTop && mouseY < decisionThumbTop + decisionThumbHeight) {
                draggingDecisionScrollbar = true;
                setDragging(true);
                scrollbarDragOffset = (int)mouseY - decisionThumbTop;
            } else {
                scrollbarDragOffset = decisionThumbHeight / 2;
                decisionScroll = DecisionTabLayout.scrollFromThumb((int)mouseY, decisionTrackTop,
                        scrollbarDragOffset, decisionTrackHeight, decisionBottom - decisionTrackTop,
                        decisionTrackHeight);
            }
            return true;
        }
        if (page == Page.DECISION && mouseY >= 44 && mouseY < height - 13) {
            for (DecisionRow row : decisionRows) if (mouseY >= row.top() && mouseY < row.bottom()) {
                decisionState.toggle(row.actionId());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (page == Page.DECISION && draggingDecisionScrollbar && event.button() == 0) {
            decisionScroll = DecisionTabLayout.scrollFromThumb((int)event.y(), decisionTrackTop, scrollbarDragOffset,
                    decisionTrackHeight, decisionBottom - decisionTrackTop, decisionTrackHeight);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            draggingDecisionScrollbar = false;
            setDragging(false);
        }
        return super.mouseReleased(event);
    }
}
