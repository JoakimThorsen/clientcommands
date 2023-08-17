package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.WikiRetriever;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getViewWikiTOCTextComponent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WikiCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwiki.failed"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwiki")
            .then(argument("page", string())
                .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page"), "summary"))
                .then(argument("section", string())
                    .executes(ctx -> displayWikiPage(ctx.getSource(), getString(ctx, "page"), getString(ctx, "section"))))));
    }

    private static int displayWikiPage(FabricClientCommandSource source, String page, String section) throws CommandSyntaxException {
        String content;

        WikiRetriever.LOGGER.info(section);
        if (section.equalsIgnoreCase("toc")) {
            WikiRetriever.LOGGER.info("TOC");
            WikiRetriever.displayWikiTOC(page, source); // TOC = table of contents
            return 1;
        } else if(section.equalsIgnoreCase("summary")) {
            WikiRetriever.LOGGER.info("Summary");
            content = WikiRetriever.getWikiSummary(page);
        } else {
            WikiRetriever.LOGGER.info("section");
            content = WikiRetriever.getWikiSection(page, section);
        }

        if (content == null) {
            throw FAILED_EXCEPTION.create();
        }

        content = content.trim();
        for (String line : content.replaceAll("\n{2,}","\n\n").split("\n")) {
            source.sendFeedback(Text.literal(line));
        }
        source.sendFeedback(getViewWikiTOCTextComponent(Text.translatable("commands.cwiki.viewTOC"), page));
        return content.length();
    }

}
