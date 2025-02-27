package net.earthcomputer.clientcommands.features;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;

public class WikiRetriever {

    public static final Logger LOGGER = LogManager.getLogger();

    private static final String WIKI_HOST = "https://minecraft.fandom.com/";
//    private static final String PAGE_SUMMARY_QUERY = WIKI_HOST + "api.php?action=query&prop=extracts&format=json&titles=%s";
    private static final String PAGE_SUMMARY_QUERY = WIKI_HOST + "api.php?action=query&prop=extracts&exintro=true&format=json&titles=%s";
    private static final String PAGE_TOC_PARSE = WIKI_HOST + "api.php?action=parse&prop=sections&format=json&page=%s";
    private static final String PAGE_SECTION_PARSE = WIKI_HOST + "api.php?action=parse&prop=text&format=json&page=%s&section=%s&disablelimitreport=true&disableeditsection=true";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*(/)?\\s*(\\w+).*?>", Pattern.DOTALL);
    private static final Formatting CODE_COLOR = Formatting.DARK_GREEN;
    private static final Gson GSON = new Gson();

    public static String decode(String html) {
        html = html.replaceAll("<span class=\"sprite inv-sprite\" title=\"(.*?)\".*?</span>", "[$1] ");

        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        StringBuilder raw = new StringBuilder();

        boolean bold = false, italic = false, underline = false, code = false;

        // -1 for not in list, 0 for unordered list, >= 1 for ordered list
        int listIndex = -1;

        while (matcher.find()) {
            matcher.appendReplacement(raw, "");

            boolean endTag = matcher.group(1) != null;
            String tagName = matcher.group(2);
            if (tagName == null) {
                // we're in a comment or newline
                continue;
            }
            tagName = tagName.toLowerCase(Locale.ROOT);

            if (!endTag) {
                switch (tagName) {
                    case "th": //fallthrough
                    case "b":
                        raw.append(Formatting.BOLD);
                        bold = true;
                        break;
                    case "i":
                        raw.append(Formatting.ITALIC);
                        italic = true;
                        break;
                    case "u":
                    case "dt":
                        raw.append(Formatting.UNDERLINE);
                        underline = true;
                        break;
                    case "code":
                        raw.append(CODE_COLOR);
                        if (bold) {
                            raw.append(Formatting.BOLD);
                        }
                        if (italic) {
                            raw.append(Formatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(Formatting.UNDERLINE);
                        }
                        code = true;
                        break;
                    case "dd":
                        raw.append("  ");
                        break;
                    case "ul":
                        listIndex = 0;
                        break;
                    case "ol":
                        listIndex = 1;
                        break;
                    case "li":
                        if (listIndex >= 1) {
                            raw.append("  ").append(listIndex).append(". ");
                            listIndex++;
                        } else {
                            raw.append("  \u2022");
                        }
                        break;
                    case "br":
                        raw.append("\n");
                }
            } else {
                switch (tagName) {
                    case "th":
                        raw.append(" ");
                        //fallthrough
                    case "b":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(Formatting.RESET);
                        }
                        if (italic) {
                            raw.append(Formatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(Formatting.UNDERLINE);
                        }
                        bold = false;
                        break;
                    case "i":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(Formatting.RESET);
                        }
                        if (bold) {
                            raw.append(Formatting.BOLD);
                        }
                        if (underline) {
                            raw.append(Formatting.UNDERLINE);
                        }
                        italic = false;
                        break;
                    case "dt":
                        raw.append("\n");
                        //fallthrough
                    case "u":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(Formatting.RESET);
                        }
                        if (bold) {
                            raw.append(Formatting.BOLD);
                        }
                        if (italic) {
                            raw.append(Formatting.ITALIC);
                        }
                        underline = false;
                        break;
                    case "code":
                        raw.append(Formatting.RESET);
                        if (bold) {
                            raw.append(Formatting.BOLD);
                        }
                        if (italic) {
                            raw.append(Formatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(Formatting.UNDERLINE);
                        }
                        code = false;
                        break;
                    case "ul":
                    case "ol":
                        listIndex = -1;
                        break;
                    case "dd":
                    case "li":
                    case "br":
                    case "p":
                        raw.append("\n");
                        break;
                }
            }
        }
        matcher.appendTail(raw);

        String rawStr = raw.toString();
        rawStr = rawStr.replace("&quot;", "\"");
        rawStr = rawStr.replace("&#39;", "'");
        rawStr = rawStr.replace("&lt;", "<");
        rawStr = rawStr.replace("&gt;", ">");
        rawStr = rawStr.replace("&amp;", "&");
        rawStr = rawStr.replace("&#32;", " ");
        rawStr = rawStr.replace("&#160;", " ");
        rawStr = rawStr.replaceAll("&#160;(or|\\+)\n", " $1 ");
        rawStr = rawStr.replaceAll("\\n\\s*?(§.)(?=\\n)", "$1");

        return rawStr;
    }

    public static String getWikiSummary(String pageName) {
        URL url;
        try {
            String encodedPage = URLEncoder.encode(pageName, "UTF-8");
            url = new URL(String.format(PAGE_SUMMARY_QUERY, encodedPage));
            LOGGER.info("Summary url: " + url.toString());
        } catch (UnsupportedEncodingException | MalformedURLException e) {
            return null;
        }

        SummaryQueryResult result;
        try (InputStream in = url.openConnection().getInputStream()) {
            result = GSON.fromJson(new InputStreamReader(in), SummaryQueryResult.class);
        } catch (IOException e) {
            return null;
        }

        if (result.query.pages.isEmpty()) { return null; }
        var page = result.query.pages.values().iterator().next();
        if (page.missing != null || page.extract == null) { return null; }

        String html = page.extract;
        return decode(html);
    }

    public static ParseTOCResult getTOCData(String pageName) {
        URL url;
        try {
            String encodedPage = URLEncoder.encode(pageName, "UTF-8");
            url = new URL(String.format(PAGE_TOC_PARSE, encodedPage));
            LOGGER.info("TOCData url: " + url.toString());
        } catch (UnsupportedEncodingException | MalformedURLException e) {
            return null;
        }

        ParseTOCResult result;
        try (InputStream in = url.openConnection().getInputStream()) {
            result = GSON.fromJson(new InputStreamReader(in), ParseTOCResult.class);
            LOGGER.info(result);
        } catch (IOException e) {
            return null;
        }

        if (result.error != null || result.parse.sections.length == 0) {
            return null;
        }

        return result;
    }

    public static void displayWikiTOC(String pageName, FabricClientCommandSource source) {

        ParseTOCResult TOCData = getTOCData(pageName);
        if(TOCData == null) {
            return;
        }

        MutableText toc = Text.literal("");
        for (ParseTOCResult.Parse.Section currentSection : TOCData.parse.sections) {
            toc.append("\n");
            for (int i = 1; i < currentSection.toclevel; i++) { toc.append(" "); }
            toc.append(getWikiTOCTextComponent(pageName, currentSection.number, currentSection.line));
        }
        source.sendFeedback(toc);
    }
    public static String getSectionIndex(String pageName, String section) {
        ParseTOCResult TOCData = getTOCData(pageName);
        if (TOCData == null) { return null; }
        for (ParseTOCResult.Parse.Section currentSection : TOCData.parse.sections) {
            if(currentSection.anchor.equals(section) || currentSection.number.equals(section)) {
                return currentSection.index;
            }
        }
        return "0";
    }

    public static String getWikiSection(String pageName, String section) {

        String sectionIndex = getSectionIndex(pageName, section);
        URL url;
        try {
            String encodedPage = URLEncoder.encode(pageName, "UTF-8");
            url = new URL(String.format(PAGE_SECTION_PARSE, encodedPage, sectionIndex));
            LOGGER.info("Section URL: " + url.toString());
        } catch (UnsupportedEncodingException | MalformedURLException e) {
            return null;
        }

        ParseSectionResult result;
        try (InputStream in = url.openConnection().getInputStream()) {
            result = GSON.fromJson(new InputStreamReader(in), ParseSectionResult.class);
        } catch (IOException e) {
            return null;
        }

        if (result.error != null || result.parse.text.section == null) {
            return null;
        }
        String html = result.parse.text.section;
        return decode(html);
    }

    private static class SummaryQueryResult {
        public String batchcomplete;
        public Query query;
        private static class Query {
            private Map<String, Page> pages;
            private static class Page {
                public int pageid;
                public String title;
                public String extract;
                public String missing;
            }
        }
    }

    private static class ParseSectionResult {
        public Object error;
        public Parse parse;
        private static class Parse {
            public String title;
            public Text text;
            private static class Text {
                @SerializedName("*") public String section;
            }
        }
    }

    private static class ParseTOCResult {
        public Object error;
        public Parse parse;
        private static class Parse {
            private Section sections[];
            private static class Section {
                public int toclevel;
                public String index;
                public String line;
                public String number;
                public String anchor;
            }
        }
    }

}