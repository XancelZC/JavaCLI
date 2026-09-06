package com.javacli.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知乎专用内容提取器。
 *
 * <p>核心机制：
 * <ol>
 *   <li>优先检测是否存在反爬验证（zh-zse-ck / 登录墙 / 安全验证）；</li>
 *   <li>优先从 SSR 挂载的 {@code <script id="js-initialData">} 中解析 JSON 状态；</li>
 *   <li>提取问题详情（questions）、精选与高赞回答（answers，按点赞数降序）及专栏文章（articles）；</li>
 *   <li>若无 initialData 则尝试常见知乎 DOM 结构兜底提取。</li>
 * </ol>
 */
public class ZhihuExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZhihuExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final HtmlExtractor htmlExtractor;

    public ZhihuExtractor(HtmlExtractor htmlExtractor) {
        this.htmlExtractor = htmlExtractor;
    }

    /**
     * 判断给定 URL 或 HTML 是否属于知乎页面。
     */
    public static boolean isZhihu(String url, String html) {
        if (url != null && (url.contains("zhihu.com") || url.contains("zhuanlan.zhihu.com"))) {
            return true;
        }
        if (html != null && (html.contains("id=\"js-initialData\"")
                || html.contains("id=\"zh-zse-ck\"")
                || html.contains("static.zhihu.com"))) {
            return true;
        }
        return false;
    }

    /**
     * 检测 HTML 是否为知乎反爬挑战页或强登录墙。
     */
    public static boolean isChallengeOrLoginWall(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return html.contains("id=\"zh-zse-ck\"")
                || html.contains("zse_ck")
                || html.contains("安全验证 - 知乎")
                || (html.contains("请您登录后查看更多专业优质内容") && html.length() < 3000);
    }

    /**
     * 尝试提取知乎页面内容，成功返回 Extracted，若无法作为知乎结构提取则返回 null。
     */
    public HtmlExtractor.Extracted extract(Document doc, String baseUrl) {
        if (doc == null) {
            return null;
        }

        // 1. 尝试从 <script id="js-initialData"> 提取
        Element scriptTag = doc.selectFirst("script#js-initialData");
        if (scriptTag != null) {
            String jsonStr = scriptTag.data();
            if (jsonStr == null || jsonStr.isBlank()) {
                jsonStr = scriptTag.html();
            }
            if (jsonStr != null && !jsonStr.isBlank()) {
                try {
                    HtmlExtractor.Extracted extracted = extractFromInitialData(jsonStr, baseUrl);
                    if (extracted != null && !extracted.markdown().isBlank()) {
                        return extracted;
                    }
                } catch (Exception e) {
                    log.debug("解析知乎 js-initialData 异常: {}", e.getMessage());
                }
            }
        }

        // 2. 尝试从 DOM 兜底提取知乎专有类名
        return extractFromZhihuDom(doc, baseUrl);
    }

    private HtmlExtractor.Extracted extractFromInitialData(String json, String baseUrl) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode entities = root.path("initialState").path("entities");
        if (entities.isMissingNode() || entities.isNull()) {
            return null;
        }

        // A. 专栏文章 (articles)
        JsonNode articlesNode = entities.path("articles");
        if (articlesNode.isObject() && !articlesNode.isEmpty()) {
            for (JsonNode article : articlesNode) {
                String title = article.path("title").asText("");
                String author = article.path("author").path("name").asText("知乎用户");
                int voteupCount = article.path("voteupCount").asInt(0);
                long created = article.path("created").asLong(0);
                String contentHtml = article.path("content").asText("");

                StringBuilder sb = new StringBuilder();
                if (!title.isBlank()) {
                    sb.append("# ").append(title).append("\n\n");
                }
                sb.append("**作者**：").append(author);
                if (voteupCount > 0) {
                    sb.append(" | **赞同**：").append(voteupCount);
                }
                if (created > 0) {
                    sb.append(" | **发布时间**：").append(formatTimestamp(created));
                }
                sb.append("\n\n---\n\n");

                String contentMd = renderHtml(contentHtml, baseUrl);
                sb.append(contentMd);
                return new HtmlExtractor.Extracted(title, sb.toString().trim());
            }
        }

        // B. 问答 (questions + answers)
        JsonNode questionsNode = entities.path("questions");
        String questionTitle = "";
        String questionDetail = "";

        if (questionsNode.isObject() && !questionsNode.isEmpty()) {
            for (JsonNode q : questionsNode) {
                questionTitle = q.path("title").asText("");
                questionDetail = q.path("detail").asText("");
                break;
            }
        }

        JsonNode answersNode = entities.path("answers");
        List<AnswerItem> answers = new ArrayList<>();
        if (answersNode.isObject()) {
            for (JsonNode ans : answersNode) {
                String id = ans.path("id").asText("");
                String author = ans.path("author").path("name").asText("知乎用户");
                int voteup = ans.path("voteupCount").asInt(0);
                long created = ans.path("createdTime").asLong(0);
                long updated = ans.path("updatedTime").asLong(0);
                String contentHtml = ans.path("content").asText("");
                if (contentHtml.isBlank()) {
                    contentHtml = ans.path("excerpt").asText("");
                }
                answers.add(new AnswerItem(id, author, voteup, created, updated, contentHtml));
            }
        }

        // 按点赞数降序排列
        answers.sort(Comparator.comparingInt(AnswerItem::voteupCount).reversed());

        if (questionTitle.isBlank() && answers.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (!questionTitle.isBlank()) {
            sb.append("# ").append(questionTitle).append("\n\n");
        }
        if (!questionDetail.isBlank()) {
            String detailMd = renderHtml(questionDetail, baseUrl);
            if (!detailMd.isBlank()) {
                sb.append("> **问题描述**：\n>\n");
                for (String line : detailMd.split("\n")) {
                    sb.append("> ").append(line).append("\n");
                }
                sb.append("\n");
            }
        }

        if (!answers.isEmpty()) {
            sb.append("## 回答列表（共 ").append(answers.size()).append(" 个回答）\n\n");
            for (int i = 0; i < answers.size(); i++) {
                AnswerItem ans = answers.get(i);
                sb.append("### 回答 ").append(i + 1).append(" · ").append(ans.author())
                        .append(" (▲ ").append(ans.voteupCount()).append(" 赞同)\n");
                if (ans.createdTime() > 0) {
                    sb.append("*发布于 ").append(formatTimestamp(ans.createdTime())).append("*\n\n");
                } else {
                    sb.append("\n");
                }
                sb.append(renderHtml(ans.contentHtml(), baseUrl));
                sb.append("\n\n---\n\n");
            }
        }

        return new HtmlExtractor.Extracted(questionTitle, sb.toString().trim());
    }

    private HtmlExtractor.Extracted extractFromZhihuDom(Document doc, String baseUrl) {
        Element titleEl = doc.selectFirst("h1.QuestionHeader-title, h1.Post-Title, h1");
        String title = titleEl != null ? titleEl.text().trim() : "";

        // 尝试抓取专栏正文
        Element postContent = doc.selectFirst(".Post-RichTextContainer, .Post-RichText");
        if (postContent != null) {
            String md = renderHtml(postContent.html(), baseUrl);
            if (!md.isBlank()) {
                String full = (!title.isBlank() ? "# " + title + "\n\n" : "") + md;
                return new HtmlExtractor.Extracted(title, full.trim());
            }
        }

        // 尝试抓取回答正文
        Elements answerEls = doc.select(".AnswerItem, .ContentItem");
        if (!answerEls.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!title.isBlank()) {
                sb.append("# ").append(title).append("\n\n");
            }
            int count = 0;
            for (Element ansEl : answerEls) {
                Element authorEl = ansEl.selectFirst(".AuthorInfo-name, [itemprop=name]");
                String author = authorEl != null ? authorEl.text().trim() : "知乎用户";
                Element contentEl = ansEl.selectFirst(".RichContent-inner, .RichText");
                if (contentEl != null) {
                    count++;
                    sb.append("### 回答 ").append(count).append(" · ").append(author).append("\n\n");
                    sb.append(renderHtml(contentEl.html(), baseUrl)).append("\n\n---\n\n");
                }
            }
            if (count > 0) {
                return new HtmlExtractor.Extracted(title, sb.toString().trim());
            }
        }

        return null;
    }

    private String renderHtml(String htmlFragment, String baseUrl) {
        if (htmlFragment == null || htmlFragment.isBlank()) {
            return "";
        }
        if (htmlExtractor != null) {
            return htmlExtractor.renderFragment(htmlFragment, baseUrl);
        }
        return htmlFragment;
    }

    private String formatTimestamp(long epochSec) {
        try {
            if (epochSec > 1_000_000_000_000L) {
                epochSec /= 1000;
            }
            return DATE_FORMATTER.format(Instant.ofEpochSecond(epochSec));
        } catch (Exception e) {
            return String.valueOf(epochSec);
        }
    }

    private record AnswerItem(
            String id,
            String author,
            int voteupCount,
            long createdTime,
            long updatedTime,
            String contentHtml
    ) {}
}
